makeコマンドを実行すると,下記のテストスクリプトが生成される

* cobj-idx
* cobol\_utf8
* command-line-options
* data-rep
* file-lock
* file-lock2
* file-lock3
* file-lock4
* file-lock5
* i18n\_sjis
* i18n\_utf8
* jp-compat
* misc
* multithread
* run
* syntax

例えば./command-line-options を実行すればコマンドラインオプションに関するテストが実行される.

multithreadは,1つのJVM内の複数のスレッドから変換後のプログラムを同時に実行するテストである.
テストプログラムは`multithread.src/harness/`にある小さなJavaのハーネスから起動され,
スレッドの生成と同期はこのハーネスが行う.
このテストが検証している実行単位のモデルについては,
[マルチスレッドのJavaアプリケーションから変換後のプログラムを呼び出す](../doc/multithreading_JP.md)を参照.

## local-ciでCIのテスト一式を実行する

トップディレクトリの`../local-ci`は,LinuxのCIジョブが実行しているスクリプトである.
作業ツリーを`.local-ci/`以下に複製し,そこでShift_JIS版とUTF-8版のコンパイラをビルドしたうえで,
このディレクトリのテストスイート,NIST COBOL85,ESQLのテスト(`initdb`/`pg_ctl`で専用のPostgreSQLサーバを起動する),
libcobjの単体テスト,Spring Bootのsmoke test,linterを並列に実行する.
自分の作業ツリーのビルド結果には影響しない.

```
cd ..
./local-ci -l                          # タスクの一覧を表示する
./local-ci 'test:sjis:*'               # Shift_JIS版のコンパイラで全スイートを実行する
./local-ci test:utf8:misc 'esql:*'     # タスク名かglobパターンで選んで実行する
./local-ci --no-esql                   # ESQL以外の全タスクを実行する
```

PostgreSQLのサーバのバイナリ(`initdb`/`pg_ctl`)が無い場合,`local-ci`は起動時に警告を出し,
`esql:*`タスクを`SKIP`として扱う.`LOCAL_CI_STRICT=1`(CIでは設定している)のとき以外は,これで全体が失敗になることはない.
`--no-esql`を指定するとESQLのタスクとPostgreSQLの起動を明示的に省き,
`LOCAL_CI_PG_BIN=none`を指定するとPostgreSQLが無いものとして扱う.

このディレクトリのスイートのタスク名は`test:sjis:<スイート名>`と`test:utf8:<スイート名>`,
ESQLのスイートは`esql:sjis:<スイート名>`と`esql:utf8:esql-utf8`である.
各タスクのログは`.local-ci/logs/<タスク名の':'を'_'に置き換えた名前>.log`に出力され,
失敗したテストグループの詳細は`.local-ci/tree-{sjis,utf8}/tests/<スイート名>.dir/`に残る.
すべてのオプションは`./local-ci -h`で確認できる.
[CONTRIBUTING_JP.md](../CONTRIBUTING_JP.md)の「テストの実行」も参照.

## Windowsでテストを実行する

WindowsのCIは,`windows-test (other-1)`,`windows-test (other-2)`,`windows-test (nist-esql)`の3つのジョブでテストを実行する.
グループ分けは`.github/workflows/pull-request.yml`と`push.yml`のmatrixで定義し,ジョブの中身は`.github/workflows/windows-test.yml`である.
各ジョブは`../win/run-tests.sh`に項目の一覧を渡し,いくつかずつ並列に実行する.項目には次のものを指定できる.

- `スイート名` -- このディレクトリのautotestスイート(`run`,`misc`,`esql-basic`など)
- `nist:モジュール名` -- `cobol85/`のNIST COBOL85のモジュール(`nist:NC`など)
- `A+B+...` -- 同じ枠の中で順に実行する項目.ESQLのスイートは1つのデータベースを共有するため,この形で順に実行する

```
../win/run-tests.sh -P 2 -j 4 run misc esql-basic+esql-sqlca nist:NC
```

`-P`は同時に実行する項目の数,`-j`は各autotestスイートに渡す値である.
各項目の出力は`win-logs/<項目名>.log`(名前の`:`と`+`はそれぞれ`_`と`-`に置き換える)に書かれ,失敗した項目は最後に一覧表示される.
GitHub Actionsでは失敗した項目のログが折りたたみグループでも出力され,失敗したジョブは`win-logs/`,`*.dir/`,NISTの`report.txt`を
`windows-test-<グループ名>-log`という名前のアーティファクトにアップロードする.

このスクリプトは手元のWindows環境のGit Bashでも使える.`win/make-install.ps1`でHagane COBOLをインストールし,
生成済みのテストスクリプトと`atconfig`をこのディレクトリに置き(Linux上でここで`make`すると生成される.
`.github/workflows/windows-generate-test-scripts.yml`を参照),
`.github/workflows/windows-test.yml`の「Set up test environment」ステップと同じようにテスト環境を整える
(`atlocal-win.env`からの`atlocal`の作成,`atconfig`内のパス,`CLASSPATH`,ESQLのスイート用の`embed_db_info.sh`).
ESQLのスイートを実行する前には,`win/start-test-postgresql.ps1`でPostgreSQLを起動しておく.
