Hagane COBOLへのコントリビュートを検討頂きありがとうございます。
下記にコントリビュートの手順を示します。

# Issues

Hagane COBOLに関するトピックは[Issues](https://github.com/yutaro-sakamoto/Hagane-COBOL/issues)に投稿してください。ただし、英語か日本語での記載をお願いします。

# Pull Requests
CIはテストとコードの静的解析を実行します。
CIの静的解析はCとJavaのソースコードがそれぞれ[clang-format](https://clang.llvm.org/docs/ClangFormat.html) and [google-java-format](https://github.com/google/google-java-format)で整形されているか、
[PMD](https://pmd.github.io/)によるJavaソースコードの静的解析でエラーや警告が表示されないかをチェックします。

Pull Request提出時には、./formatを実行してリポジトリ内のコードをフォーマットしてください。

下記にそれぞれのツールのセットアップと使用方法を説明します。

## 開発環境のセットアップ

一貫した開発環境を確保するために、[Visual Studio Code with Dev Containers](https://code.visualstudio.com/docs/devcontainers/containers)の使用を強く推奨します。以下の手順に従って開発環境をセットアップしてください。

1. [Docker](https://www.docker.com/get-started)をインストールします。
1. [Visual Studio Code](https://code.visualstudio.com/)をインストールします。
1. Visual Studio Codeに[Remote - Containers](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers)拡張機能をインストールします。
1. リポジトリをクローンします。
1. Visual Studio Codeでリポジトリを開きます。
1. `Ctrl+Shift+P`を押して、`Dev Containers: Reopen in Container`を選択します。
1. DevContainerの起動とビルドが完了するまで待ちます。このプロセスは数分かかることがあります。
1. （オプション）`Ctrl+Shift+@`を押して、Visual Studio Codeの新しいターミナルを開きます。
1. （オプション）[gitの認証情報を設定](https://code.visualstudio.com/remote/advancedcontainers/sharing-git-credentials)します。


## テストの実行

CIはトップディレクトリの[`./local-ci`](local-ci)でHagane COBOLをビルドしてLinux向けのテストを実行しています。手元のLinux環境でも同じテストを実行できます。
作業ツリーの内容(未コミットの変更と、無視されていない未追跡ファイルを含む)を`.local-ci/`以下に複製し、そこでShift_JIS版とUTF-8版のコンパイラをビルドして、選択したタスクを並列に実行します。そのため作業ツリー内のビルド結果や`local/`には影響しません。
タスクには、autotestの各スイート(`test:sjis:*`、`test:utf8:*`)、NIST COBOL85(`nist:*`)、ESQLのテスト(`esql:*`)、libcobjの単体テスト(`unit:libcobj`)、Spring Bootのsmoke test(`smoke:spring-boot`)、linter(`lint:*`)があります。

```
./local-ci -l                        # タスクの一覧を表示する
./local-ci 'test:sjis:run' 'lint:*'  # パターンに一致するタスクを実行する
./local-ci -x 'nist:*'               # パターンに一致するタスクを除いて実行する
./local-ci --clean                   # .local-ci/ を削除する
```

全タスクの実行には時間がかかるため、push前には変更に関係するタスクだけを実行し、全体はCIに任せるのが現実的です。
`-j`、`-t`、`--shard`などすべてのオプションはスクリプト冒頭のコメント(`./local-ci -h`)を参照してください。
各タスクのログは`.local-ci/logs/<タスク名の':'を'_'に置き換えた名前>.log`に出力され、失敗したautotestの詳細は`.local-ci/tree-{sjis,utf8}/tests/<スイート名>.dir/`に残ります。

注意点:

* ESQLのテストは`initdb`と`pg_ctl`で専用のPostgreSQLサーバを起動するため、PostgreSQLのサーバのバイナリが必要です。自動で見つからない場合は`LOCAL_CI_PG_BIN`で場所を指定してください。
* Dockerが無い環境では、`unit:libcobj`はTestcontainersを使うテストクラスとカバレッジの検証を省略します。
* `nkf`がインストールされていない場合、`test:utf8:cobol_utf8`はスキップされます。
* `localhost:5432`で何かが待ち受けていると、`esql:sjis:esql-sqlca`は失敗します。
* `lint:cppcheck`の結果はcppcheckのバージョンによって変わり(CIはAlmaLinux 9のEPELのcppcheckを使用)、`lint:javadoc`はjavadocの警告が1件でもあると失敗します。そのため、この2つは変更に問題が無くても手元では失敗することがあります。

## 静的解析の実行

> [!CAUTION]
> これらのツールの動作は他のオペレーティングシステムとは異なる場合があります。上記のVisual Studio Code with Dev Containersの環境でフォーマッタを実行することを推奨します。

### clang-formatとgoogle-java-format

Hagane COBOLのトップディレクトリで`./format`を実行してください。
`./check-format`を実行することで、フォーマットが完了したかを確認できます。

`./local-ci 'lint:*'`を実行すると、CIが行う静的解析(clang-format、cppcheck、spotless、SpotBugs、PMD、javadoc、javadocの記述漏れの検査)をまとめて実行できます。

### PMD

Hagane COBOLのlibcobj/ディレクトリで下記のコマンドを実行してください。
```
./gradlew pmdMain
```

## Webサイト

[Webサイト](https://yutaro-sakamoto.github.io/Hagane-COBOL/ja/)のソースは`website/`にあり、[VitePress](https://vitepress.dev/)でビルドします(英語版は`website/`直下、日本語版は`website/ja/`)。
CIがPull Requestとpushのたびにビルドし、`main`が更新されるとGitHub Pagesに公開します。
ローカルで確認するには、Node.jsをインストールした環境(CIではNode.js 22を使用)で下記のコマンドを実行してください。
```
cd website
npm ci
npm run dev
```

`npm run build`を実行すると`website/.vitepress/dist/`に静的サイトが生成され、`npm run preview`でビルド結果を確認できます。
libcobjのJavadoc(`/javadoc/libcobj/`以下で公開)はCIでのみサイトに追加されるため、ローカルのビルドには含まれません。
