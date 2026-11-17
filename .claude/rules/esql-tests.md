---
paths:
  - "tests/esql-*"
  - "tests/esql-*.src/**"
  - "tests/embed_db_info.sh"
  - "cobj/esql*"
---

# ESQL テストをローカルで実行する手順

ESQLのautotestスイート（esql-basic等）はPostgreSQLが要るため通常はCI任せだが、PostgreSQLのサーバーバイナリ(`initdb`/`pg_ctl`)が入っている環境なら
**`./local-ci 'esql:*'`（sandbox無効）を使うのが一番手軽**。CIと同じ手順で、作業ツリーの複製(`.local-ci/`)の中でビルドし、
55432番以降の空きポートに専用のPostgreSQLを起動し、複製側の`tests/embed_db_info.sh`をそこに向けて書き換えてから実行する。
チェックイン済みの`embed_db_info.sh`は変更しない。`initdb`がPATHにも`/usr/lib/postgresql/*/bin`にも無ければ`LOCAL_CI_PG_BIN`で指定する。
個別のスイートだけなら`./local-ci esql:sjis:esql-basic`、UTF-8版は`esql:utf8:esql-utf8`。
PostgreSQLが見つからなければ起動時に警告が出て`esql:*`はSKIPになる(`LOCAL_CI_STRICT=1`でなければ失敗扱いにはならない)。ESQL以外だけを回したいときは`./local-ci --no-esql`。
Windowsでは`win/start-test-postgresql.ps1`でクラスタを起動し、`win/run-tests.sh`に6スイートを`esql-basic+esql-sqlca+...`の1項目として渡して順に実行する(CIの`windows-test (nist-esql)`ジョブと同じ。手順は`tests/README.md`の「Running tests on Windows」)。

in-treeのビルドで直接回したい場合は、sudoなしで私設クラスタを起動してローカル実行できる。バージョンやバイナリのパス、DBユーザ名は環境に合わせて読み替えること。

```bash
PGBIN=$(dirname "$(command -v initdb || echo /usr/lib/postgresql/16/bin/initdb)")
PGDIR=$(git rev-parse --show-toplevel)/.claude-work/pgdata   # Claudeの作業用ディレクトリ配下
PGSOCK=/tmp/pgsock        # Unix socketのパスには107バイト制限がある。PGDIR配下に置くと超えるので/tmpに置く
PGPORT=55432              # 既定の5432が塞がっていることがあるので空きポートを使う

"$PGBIN/initdb" -D "$PGDIR" -U "$USER" --auth=trust -E UTF8
mkdir -p "$PGSOCK"
"$PGBIN/pg_ctl" -D "$PGDIR" -o "-p $PGPORT -k $PGSOCK" -l "$PGDIR/log" start
"$PGBIN/createdb" -h 127.0.0.1 -p "$PGPORT" -U "$USER" testdb
```

- 既定と違うポートを使う場合は`tests/embed_db_info.sh`の`DB_PORT`を一時的に書き換える（**コミット前に必ず戻すこと**）。
- `PGDIR`はworktreeごとに独立するが、`PGSOCK`とポートは全worktreeで共有される。複数のworktreeで同時に動かすなら、
  ポートとsocketディレクトリをworktreeごとに変えて衝突を避ける。
- `esql-utf8`だけは`./configure --enable-utf8`でビルドし直す必要がある。
- **環境によっては必ず落ちるテストがある**: `esql-sqlca.src/connect-disconnect.at`は存在しないホスト`invalid`への接続でSQLSTATE 08001を期待しているが、
  名前解決が常に応答する環境（DNSのワイルドカード応答、一部のWSL環境など）では28P01が返って失敗する。環境差でありコードの問題ではない。
- 同じ`connect-disconnect.at`は、ホストを省いたDB名で既定の`localhost:5432`に接続して08001を期待するため、5432番で何か(既存のPostgreSQLなど)が待ち受けていると28P01で落ちる。`./local-ci`はこの場合に起動時に警告を出す。
