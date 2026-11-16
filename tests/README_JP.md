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
```

このディレクトリのスイートのタスク名は`test:sjis:<スイート名>`と`test:utf8:<スイート名>`,
ESQLのスイートは`esql:sjis:<スイート名>`と`esql:utf8:esql-utf8`である.
各タスクのログは`.local-ci/logs/<タスク名の':'を'_'に置き換えた名前>.log`に出力され,
失敗したテストグループの詳細は`.local-ci/tree-{sjis,utf8}/tests/<スイート名>.dir/`に残る.
すべてのオプションは`./local-ci -h`で確認できる.
[CONTRIBUTING_JP.md](../CONTRIBUTING_JP.md)の「テストの実行」も参照.
