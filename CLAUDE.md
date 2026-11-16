これはHagane COBOLと呼ばれるCOBOLからJavaへのトランスパイラのリポジトリである。

# 開発方法

## 作業用ディレクトリ

**Claudeの作業用ディレクトリはリポジトリルート直下の `.claude-work/` である。** 絶対パスは `$(git rev-parse --show-toplevel)/.claude-work`。
`.claude/settings.json`の`env`で`CLAUDE_WORK_DIR`として宣言してあり、`.gitignore`で除外している。

- タスク定義(`.claude-work/task.md`)、調査メモ、一時スクリプト、CIログやアーティファクトの退避、
  ESQLテスト用のPostgreSQLクラスタ、動作確認用に書き捨てるCOBOLソースなど、
  **成果物ではない一時ファイルは全てこの下に置く**。リポジトリのルートや`/tmp`に散らかさない。
- **この配下は中身がいつ消えてもよい前提で使う**。逆に言えば、ここに残した情報を長期的な記録として当てにしない。
- worktreeで作業する場合、`.claude-work/`はworktreeごとに独立する（本体とは共有しない）。

一方、以下の2つは`.gitignore`で除外しているが**作業用ディレクトリではない。Claudeが勝手に消してはいけない**。

| ディレクトリ | 中身 | 消すとどうなるか |
|---|---|---|
| `local/` | `make install`のインストール先。`bin/cobjrun`が実行時にこの下のjarを探す | 再ビルドが必要になる |
| `wt/` | `git worktree`の実体。**未コミットの変更を含む本物のチェックアウト** | 作業が失われ、取り返しがつかない |

なお、autotestが作る`tests/{テスト名}.dir/`は生成場所をautotestが決めるため移動できない。
ESQLテストのUnix socketも、パスの107バイト制限を超えるおそれがあるため`.claude-work/`配下には置かない。

## ビルド方法

トップディレクトリのconfigure.acや各フォルダのMakefile.amを修正したときは、トップディレクトリで以下のコマンドを実行する

```bash
autoreconf && ./configure --prefix=local/ && make && make install
```

それ以外のコードを修正したときは、以下のコマンドを実行する

```bash
./configure --prefix=/local/ && make && make install
```

インストール先はlocal/であることに注意。

### ビルド時の落とし穴

- **sandboxを無効にする**: `make` / `make install` / `./format` / `./local-ci` / `tests/`配下のテスト実行は、libcobjのビルドでGradle wrapperが`~/.gradle/`に書き込むため(`./local-ci`はさらにESQLテスト用のPostgreSQLとSpring Bootのsmoke testがポートを開くため)、デフォルトのsandboxでは`Read-only file system`で失敗する。これらは最初から`dangerouslyDisableSandbox: true`で実行する（`configure`と`autom4te`はsandbox内で動く）。
- **UTF-8ビルドは`touch cobj/*.m4`が必須**: `./configure --enable-utf8`の後は必ず`touch cobj/*.m4 && make`する。`cobj/scanner.l`と`cobj/pplex.l`はm4生成物だがgitにコミットされており、checkout直後は`.l`と`.l.m4`のmtimeが同じでmakeが再生成しない。結果、Cコードは`I18N_UTF8`定義済みなのにレキサーはShift_JIS版という混成ビルドができ、日本語の利用者定義語が全滅する。`tests/cobol_utf8`が大量に「syntax error」で落ちたらまずこれを疑う（コードの不具合ではない）。SJIS版に戻すときも同様。
- **`Makefile.in`と`configure`は手で編集しない**: これらは`Makefile.am`と`configure.ac`からautoreconfが自動生成するファイル。編集するのは常に`.am`/`.ac`のほうで、生成物はautoreconfで作り直す。
- **autoreconfの出力は確認してからコミットする**: この環境で実行するとaclocal.m4からgettext/libtoolのm4マクロが脱落するなど、意図しない環境差分が混ざることがある(2026-08-05に15000行超の差分を確認)。CIはチェックイン済みのconfigure/Makefile.inをそのまま使うため、壊れた再生成ファイルをコミットするとビルドが壊れる。コミット前に`git diff --stat`で変更が意図した範囲に収まっているか確認すること。

## テスト方法

**テストの全体はCIで回す。** ローカルでスイート全体を流すと時間がかかりすぎるため、
一通り修正したらpushしてCIに任せ、結果は`gh`で確認するのが基本の流れ。

ただし、**変更箇所に対応するテストケースだけをローカルで先に回してからpushする**のは、状況に応じて選んでよい。
不具合の修正中で同じテストを何度も回す場合や、CIの1往復が惜しい小さな変更では、そのほうが速い。
逆に、複数OSでの差異が問われる場合は、ローカルで確かめても意味が薄いのでCIに任せる。
ローカルで先に回すときは、後述の`./local-ci`でタスクを選んで実行するのが手軽(Shift_JIS版とUTF-8版の両方をCIと同じ手順でビルドする)。

tests/以下の構成:

- cobol85/ - COBOL 85の文法に基づくテストコード
- \*.at - たとえばmisc.atであれば、misc.src/以下のどのテストスクリプトを参照するかを定義する。command-line-options.at等も同じ規則。
  - makeを実行するとテストスクリプトが生成される。`make misc`とすればmisc.atに基づいて生成され、tests/で`./misc`を実行すれば走る。

ローカルで一部だけ回すときは、関係ない部分を`#`でコメントアウトしてから`make`でスクリプトを再生成する
(COBOL85以外はテストが長くなることがあるため)。実行は`{テスト名}.dir/`で行われ、失敗の記録はテスト終了後もそこに残る。

### local-ci: CIのテスト一式を手元で回す

リポジトリルートの`./local-ci`は、CIのLinuxジョブ(`.github/workflows/local-ci.yml`)が実際に実行しているスクリプトで、
同じテスト一式を1台のマシンで並列に回せる。push前に変更に関係するタスクだけを先に流す用途に使う
(全タスクの実行は重いので、基本は上のとおりCIに任せる)。**sandbox無効で実行すること。**

- 作業ツリー(未コミットの変更と、無視されていない未追跡ファイルを含む)を`.local-ci/tree-*`に複製し、
  そこでShift_JIS版とUTF-8版のコンパイラをビルドする。in-treeのビルド結果や`local/`には触れない。UTF-8版の`touch cobj/*.m4`も自動で行う。
- タスクはautotestの各スイート(`test:{sjis,utf8}:<スイート>`)、NIST COBOL85(`nist:{sjis,utf8}:<モジュール>`)、
  ESQL(`esql:*`。`initdb`/`pg_ctl`で専用のPostgreSQLを起動するのでPostgreSQLのサーバーバイナリが要る)、
  libcobjの単体テスト(`unit:libcobj`)、Spring Bootのsmoke test(`smoke:spring-boot`)、
  linter(`lint:clang-format`、`lint:cppcheck`、`lint:spotless`、`lint:spotbugs`、`lint:pmd`、`lint:javadoc`、`lint:missing-javadoc`)。
  選んだタスクが依存するビルドやDB起動は自動で追加される。
- 使い方の詳細はスクリプト冒頭のコメント(`./local-ci -h`)。よく使うのは次のとおり。

```bash
./local-ci -l                          # タスクの一覧
./local-ci 'test:sjis:run' 'lint:*'    # パターン(bashのglob)で選んで実行
./local-ci -x 'nist:*'                 # 除外して実行
./local-ci -j 8 -t 4 'test:utf8:*'     # 同時タスク数 / autotestの-j
./local-ci --clean                     # .local-ci/ を削除
```

- ログは`.local-ci/logs/<タスク名の:を_に置換>.log`(例: `test_sjis_run.log`)。autotestの失敗の詳細は
  `.local-ci/tree-{sjis,utf8}/tests/<スイート>.dir/`に残る。
- 環境によって手元の結果がCIと変わるもの(コードの問題ではない):
  - Dockerが無いと、`unit:libcobj`はTestcontainersを使う3つのテストクラスとカバレッジ検証を飛ばす。
  - `nkf`が無いと`test:utf8:cobol_utf8`はskipになる。
  - localhost:5432で何かが待ち受けていると、`esql:sjis:esql-sqlca`のconnect-disconnectが落ちる(起動時に警告が出る)。
  - `lint:cppcheck`はcppcheckのバージョンで結果が変わる(CIはAlmaLinux 9のEPELのcppcheckを使う)。
  - `lint:javadoc`はjavadocの警告が1件でもあると失敗するので、既存の警告で落ちることがある。

### CIの既知のフレーク

LinuxのテストはOS × 文字コード(sjis, utf8) × シャード(1/3, 2/3, 3/3)のmatrixで`local-ci`を呼ぶジョブに集約されており、ジョブ名は`local-ci (ubuntu:26.04, sjis, 1/2) / test`のような形になる(AlmaLinux 9のジョブはpull_requestでのみ走る)。失敗したジョブは失敗タスクのログを折りたたみグループで出力し、`.local-ci/logs/`とautotestの`.dir/`を`local-ci-ubuntu-26.04-sjis-1-2`のような名前のアーティファクトにアップロードする。

AlmaLinux 9のfile-lock系スイート(`local-ci (almalinux:9, ...)`ジョブ内の`test:*:file-lock*`タスク)が`COB4J_REAL_JAVA does not point at a real java`を大量に出して落ちることがある。コード変更なしの再実行で成功するリポジトリ全体の既存問題。自分の変更を疑う前に失敗ログ(またはアーティファクトを`gh run download`したもの)で確認し、該当すれば`gh run rerun <run-id> --failed`する。なお`local-ci`のmatrixは`fail-fast: false`なので、1ジョブが落ちても他のジョブはcancelされない。

## コード変更終了後

一通りコードの修正が完了したら、トップディレクトリで`./format`を実行してコードを整形すること。
(Stopフックの`.claude/hooks/format-on-stop.sh`が変更のあったC/Javaソースを検出して自動実行するが、手動で実行しても構わない)
また、`code-reviewer`と`docs-sync-inspector`のサブエージェントを**同一メッセージ内で同時に**呼び出して、コードレビューとドキュメントの更新を並行して実施すること。
コードレビューに組み込みの`/code-review`スキルを使ってはいけない(トークン消費が大きいため`.claude/settings.json`の`skillOverrides`で非表示化済み。ユーザが明示的に`/code-review`と打った場合のみ動く)。レビューは常に`code-reviewer`サブエージェントで行う。

## サブエージェントの使い分け

以下は`.claude/agents/`で定義している。`docs-sync-inspector`以外は**読み取り専用**で、報告するだけでコードは変更しない。調査やログ確認を自分の会話に抱え込まずに委譲することで、メインの文脈を実装そのものに使えるようにする。

| エージェント | 使う場面 |
|---|---|
| `cobj-explorer` | 「この構文/機能はどこで処理されている?」を調べるとき。cobj/の1万行級のCソースやlibcobjの104ファイルを横断する調査は、**自分でgrepせず必ずこれに投げる** |
| `ci-triage` | CIが失敗したとき。`gh`でログを取り、既知のフレークか本当の回帰かを判定する。**巨大なCIログを自分で読まない** |
| `commit-preflight` | コミット/PRの直前。生成物の混入、CRLF・Shift_JISの事故、フォーマット漏れ、PMD違反を検査する |
| `code-reviewer` | 実装が一段落したとき、コミット前 |
| `docs-sync-inspector` | コード変更後にドキュメントの整合を取るとき。**これだけはドキュメントを実際に編集する**(コードは変更しない) |

複数を同時に走らせられる場面(レビューとドキュメント更新など)では、1つのメッセージ内で複数のエージェントを呼び出して並行させること。

## git

- **リモート名は環境によって異なる**。`origin`が存在しない構成もあるので、pushや`gh`の操作の前に必ず`git remote -v`で宛先を確認すること。
  - 上流は opensourcecobol/opensourcecobol4j。PRは上流の**デフォルトブランチ**に出す(`/pr`)。
    ブランチ名を決め打ちにせず、`git remote show <リモート名>`の`HEAD branch`か`gh repo view <owner>/<repo> --json defaultBranchRef`で取得すること。
  - 自分のフォークは上流へ出す前の確認用に使う。PRのソースブランチはそちらにpushし、必要ならフォーク内でDraft PRを出してCIを回す(`/local-pr`)。
- **`git commit`は変更済みの追跡ファイルを全て巻き込む**: `.git/hooks/pre-commit`が`./format`を実行したあと`git add -u`をするため、明示的に`git add`したファイルだけをコミットすることはできない。生成物などを意図的に除外したい場合は`git commit --no-verify`を使う(この場合`./format`も走らないので必要なら手動で実行する)。またフックが`./format`経由でgradleを呼ぶため、**コミット自体もsandbox無効化が必要**。
- **`CLAUDE.md`と`.claude/`はgit管理下**。`git worktree add`で自動的にチェックアウトされるので、worktreeにリンクを張る必要はない。ただし`.claude/settings.local.json`（通知フックなどマシン固有の個人設定）は`.gitignore`で除外しているので、worktreeで使いたい場合は各自でコピーする。

# フォルダ構成

以下に主要なフォルダとファイルの説明を示す。

- cobj/ - COBOLからJavaへの変換のためのコードを格納
  - cobj.c - cobjコマンド(Hagane COBOLのメインコマンド)のコード
  - codegen.c - COBOLからJavaへのコード生成を行うコード
  - esql.c - EXEC SQL文の解析と処理を行うコード
  - esql-scanner.l - flex向けのEmbedded SQLレキサー定義
  - esql-parser.y - bison向けのEmbedded SQLパーサー定義
  - typeck.c - codegen.c実行前に型チェックなどの多様な処理を行うコード
  - parser.y - bison向けのCOBOLパーサー定義
  - scanner.l - flex向けのCOBOLレキサー定義
  - scanner.l.m4 - m4マクロで、scanner.lを生成するためのファイル
- libcobj/ - libcobj.jarのためのコードを格納する。libocobj.jarは、COBOLからJavaへの変換で生成されたJavaコードが依存するライブラリ
- website/ - GitHub Pagesで公開するWebサイト(VitePress)のソース。英語版は直下、日本語版はja/以下。libcobjのJavadocはCIがビルド時に追加する
- local-ci - CIのLinuxテスト一式をローカルで並列実行するスクリプト。CIのテストジョブもこれを使う。「local-ci: CIのテスト一式を手元で回す」の節を参照
- .claude-work/ - Claudeの作業用ディレクトリ(git管理外)。「作業用ディレクトリ」の節を参照
- .local-ci/ - `./local-ci`の作業ディレクトリ(git管理外)。複製したソースツリー、ログ、PostgreSQLのデータが入る。`./local-ci --clean`で消してよい
- local/ - `make install`のインストール先(git管理外)
- wt/ - `git worktree`の置き場(git管理外)

# ファイル種別ごとの注意事項

対象ファイルを読んだときだけ読み込まれるルールを`.claude/rules/`に置いている（CRLF改行、Shift_JISの.atファイル、PMDの制約など）。全部を常時読む必要はない。
