# Hagane COBOL

[English version README](./README.md)

"Hagane COBOL"は、COBOLソースコードをJavaソースコードに変換するCOBOLコンパイラです。
"Hagane COBOL"は、COBOLからCソースコードに変換するCOBOLコンパイラ["opensource COBOL"](https://github.com/opensourcecobol/opensource-cobol)をもとに開発されています。
（実際、このリポジトリには多くの["opensource COBOL"](https://github.com/opensourcecobol/opensource-cobol)のソースファイルが含まれています。）

変更履歴は[CHANGELOG.md](./CHANGELOG.md)をご覧ください。

インストール方法や使い方のガイド、`libcobj.jar`のAPIリファレンスは、Hagane COBOLのWebサイト<https://haganecobol.org/ja/>で公開しています。

## opensource COBOL 4JからHagane COBOLへの名称変更

Hagane COBOLは、これまで[opensource COBOL 4J](https://github.com/opensourcecobol/opensourcecobol4j)のフォークとしてこのリポジトリで開発されてきました。
バージョン1.0.0からは、新しい名称**Hagane COBOL**のもとで独立してリリースします。
あわせて、リポジトリも`yutaro-sakamoto/opensourcecobol4j`から[yutaro-sakamoto/Hagane-COBOL](https://github.com/yutaro-sakamoto/Hagane-COBOL)に移りました。

* バージョン番号は1.0.0から数え直します。Hagane COBOL 1.0.0はopensource COBOL 4J 2.1.0をもとにしています。
  opensource COBOL 4J 2.1.0からの変更点は[ReleaseNote.md](./ReleaseNote.md)をご覧ください。
* 1.0.0では、コマンド名(`cobj`、`cobj-idx`、`cobj-api`)、ランタイムライブラリ`libcobj.jar`、
  Javaパッケージ`jp.osscons.opensourcecobol.libcobj`、
  インストール先(`/usr/lib/opensourcecobol4j/libcobj.jar`、`C:\opensourcecobol4j`)は変わりませんでした。
* DockerイメージはDocker Hubではなく、GitHub Container Registry(`ghcr.io/yutaro-sakamoto/hagane-cobol`)で配布します。

### 2.0.0での変更

2.0.0では、opensource COBOL 4Jから引き継いでいた名前をHagane COBOLのものに変更しました。
コマンド名と`libcobj.jar`は変わりません。

* libcobjのJavaパッケージは`jp.osscons.opensourcecobol.libcobj`から`org.haganecobol.libcobj`に変わりました
  (MavenのgroupIdも`jp.osscons.opensourcecobol`から`org.haganecobol`に変わりました)。
* インストール先が変わりました。`libcobj.jar`は`/usr/lib/hagane-cobol/libcobj.jar`
  (以前は`/usr/lib/opensourcecobol4j/libcobj.jar`)に、Windowsのインストール先は
  `C:\hagane-cobol`(以前は`C:\opensourcecobol4j`)になります。
* `configure`のパッケージ名(tarballや`share/`配下のディレクトリ名)は`hagane-cobol-2.0.0`になりました。

1.0.0から移行する場合は、次の点に注意してください。

* COBOLプログラムの変換とコンパイルをやり直してください。libcobjや生成されたプログラムを
  Javaから直接利用しているコードは`import`文の書き換えが必要です。
* 古いインストール先を参照している`CLASSPATH`、`PATH`やスクリプトを更新してください。
* `make install`は1.0.0でインストールしたファイルを削除しません。不要であれば手動で削除してください。

詳しくは[CHANGELOG.md](./CHANGELOG.md)をご覧ください。

## ライセンス
ランタイムライブラリであるlibcobjはLGPL 3、それ以外のソフトウェアやライブラリはGPL 3の下で配布されています。

## 動作環境
Hagane COBOLは、下記の環境でテストされています：

* Ubuntu 26.04 と OpenJDK 21
* AlmaLinux 9 と OpenJDK 11
* Windows と OpenJDK 21

古いバージョンの動作環境については、[doc/requirements-all.md](./doc/requirements-all.md)をご覧ください。


## インストール(Linux)

### 依存ライブラリのインストール
下記のコマンドを実行します。

#### Ubuntu 26.04
```
sudo apt-get update
sudo apt-get install -y default-jdk build-essential bison flex gettext texinfo libgmp-dev autoconf
```

#### AlmaLinux 9
```
dnf -y update
dnf install -y java-11-openjdk-devel gcc make bison flex automake autoconf diffutils gettext
```

### Hagane COBOLのインストール
```
curl -L -o hagane-cobol-v2.0.0.tar.gz https://github.com/yutaro-sakamoto/Hagane-COBOL/archive/refs/tags/v2.0.0.tar.gz
tar zxvf hagane-cobol-v2.0.0.tar.gz
cd Hagane-COBOL-2.0.0
./configure --prefix=/usr/
make
sudo make install
```

UTF-8のCOBOLソースコード対応版コンパイラをインストールする場合は、下記のコマンドを実行する。

```
curl -L -o hagane-cobol-v2.0.0.tar.gz https://github.com/yutaro-sakamoto/Hagane-COBOL/archive/refs/tags/v2.0.0.tar.gz
tar zxvf hagane-cobol-v2.0.0.tar.gz
cd Hagane-COBOL-2.0.0
./configure --prefix=/usr/ --enable-utf8
touch cobj/*.m4
make
sudo make install
```

### $CLASSPATHの設定
/usr/lib/hagane-cobol/libcobj.jarを環境変数$CLASSPATHに追加します。
例えば、Unix系システムで次のコードを実行します。

```bash
export CLASSPATH="$CLASSPATH:/usr/lib/hagane-cobol/libcobj.jar"
```

Hagane COBOLの前身であるopensource COBOL 4Jのインストール方法は、[opensource COBOL 4Jのリポジトリ](https://github.com/opensourcecobol/opensourcecobol4j)をご覧ください。

## インストール (Windows)
### Visual Studioのインストール
Windows版のHagane COBOLはVisual Studioに含まれるCLコンパイラを使用します。
そのため、まずは[Visual Studio](https://visualstudio.microsoft.com/)をインストールする必要があります。

### ソリューションファイルのビルド
1. Hagane COBOLのファイル一式をダウンロードする。
2. Visual Studioでwin/opensourcecobol4j.slnを開く。
3. ”Debug”または”Release”モードを選択する。
    ![alt text](image/readme1.png)
4. ”ビルド” -> ”ソリューションのビルド” を選択する。
    ![alt text](image/readme2.png)
    ビルドが完了すると、`win/x64/Debug`または`win/x64/Release`に"cobj.exe"が作成される。

### Javaファイルのビルド
1. JDKをインストールする。
    https://www.oracle.com/java/technologies/downloads/?er=221886#java8-windows
2. PowerShellを開く。
3. "libcobj"ディレクトリに移動し、`.\gradlew shadowJar`を実行する。
    ```
    cd libcobj
    .\gradlew shadowJar
    ```
    これにより、`libcobj\app\build\libs\`に"libcobj.jar"が作成される。

### ファイルを適切な位置に配置
1. Debugモードでビルドした場合、`win/make-install.ps1`の6行目を`\x64\Release\cobj.exe`から`\x64\Debug\cobj.exe`に変更する。
2. PowerShellを開く。
3. ”win”ディレクトリに移動し、`make-install.ps1`を実行する。
    ```
    cd win
    .\make-install.ps1
    ```
* 各ファイルは以下の場所に配置されます。
    | ファイル名 | 配置場所 |
    |---|---|
    | cobj.exe | C:\hagane-cobol\bin |
    | libcobj.jar | C:\hagane-cobol\lib |
    | configファイル | C:\hagane-cobol\config |
    | copyファイル | C:\hagane-cobol\copy |

* ファイルの配置場所を変更したい場合は、`make-install.ps1`に記載してあるパスを編集してください。

### 環境変数の設定
1. `C:\hagane-cobol\bin`を環境変数PATHに追加する。
2. 環境変数CLASSPATHを`.;C:\hagane-cobol\lib\libcobj.jar`にする(カレントディレクトリのプログラムを実行するため、先頭の`.`が必要)。

## Dockerを使ったインストール
Hagane COBOLのDockerコンテナをGitHub Container Registryで配布しています。

```bash
docker pull ghcr.io/yutaro-sakamoto/hagane-cobol:2.0.0
docker run -it --rm ghcr.io/yutaro-sakamoto/hagane-cobol:2.0.0
```

UTF-8のCOBOLソースコード向けのコンパイラは、イメージ`ghcr.io/yutaro-sakamoto/hagane-cobol:2.0.0-utf8`に入っています。

以下のコマンドを実行して、"Hello World"のCOBOLプログラムを実行します。

``` bash
# サンプルディレクトリに移動
$ cd /root/cobol_sample

# COBOLをJavaに変換し、Javaソースファイルをコンパイル
$ cobj HELLO.cbl

# "Hello World"を実行
$ java HELLO
HELLO WORLD!
```

## Maven Centralからlibcobj.jarを利用する

ランタイムライブラリ`libcobj.jar`は、リリースのたびに`org.haganecobol:libcobj`として[Maven Central](https://central.sonatype.com/artifact/org.haganecobol/libcobj)に公開しています。
MavenやGradleでビルドするJavaプロジェクトは、Hagane COBOLをインストールしなくても、これを依存関係に加えることで、`cobj`が生成したJavaプログラムをコンパイル・実行したり、Javaから呼び出したりできます。

Maven (`pom.xml`):

```xml
<dependency>
  <groupId>org.haganecobol</groupId>
  <artifactId>libcobj</artifactId>
  <version>2.0.0</version>
</dependency>
```

Gradle (`build.gradle.kts`):

```kotlin
dependencies {
    implementation("org.haganecobol:libcobj:2.0.0")
}
```

libcobjのバージョンは、COBOLプログラムを変換した`cobj`のバージョンと揃えてください。
Maven Centralにあるのは、Maven Centralへの公開を始めた後のリリースだけです。公開済みのバージョンは[バージョン一覧](https://central.sonatype.com/artifact/org.haganecobol/libcobj/versions)で確認できます。
`make install`でインストールされる`libcobj.jar`やGitHubのリリースに添付される`libcobj.jar`は依存ライブラリを同梱していますが、Maven Centralのものはlibcobj本体だけを含みます。依存ライブラリ(PostgreSQLのJDBCドライバやSQLite JDBCなど)はMavenやGradleが自動的にダウンロードします。

## 使い方
コンパイルします。
```bash
cobj [COBOL source file]
```
(cobjコマンドにより、カレントディレクトリに[PROGRAM-ID].javaと[PROGRAM-ID].classが生成されます。)

実行します。
```bash
java [PROGRAM-ID]
```

> **Tip:** 複数のCOBOLソースファイルをコンパイルする場合は、`cobj`をファイルごとに個別に呼び出すのではなく、すべてのファイルを一度の`cobj`呼び出しに渡すことを推奨します。これにより、合計のコンパイル時間を大幅に短縮できます。
>
> ```bash
> # 推奨: すべてのファイルを一度にコンパイル
> cobj file1.cbl file2.cbl file3.cbl
>
> # ワイルドカードも使用可能
> cobj *.cbl
>
> # 非推奨: ファイルごとに個別にコンパイル
> cobj file1.cbl
> cobj file2.cbl
> cobj file3.cbl
> ```

## Embedded SQL (ESQL)

Hagane COBOLは、PostgreSQL向けのEmbedded SQL (`EXEC SQL`) をサポートしており、COBOLプログラムからホスト変数を使用してSQL文を直接実行できます。

- [ESQLガイド](./doc/esql-guide_JP.md) -- サポートされるSQL文、ホスト変数の型、エラーハンドリング、SQL実行ログの有効化・設定方法などを網羅したドキュメント。
- [ESQL設計ドキュメント](./doc/esql-design_JP.md) -- コンパイルパイプライン、AST ノード、ランタイム構造などの内部設計メモ。
- [ESQLサンプル](./example/esql/) -- 基本操作、カーソル、プリペアドステートメント、テーブルへの登録・取得を示す実行可能なサンプルプログラム。

## ドキュメント

* [ランタイムライブラリ`libcobj.jar`のAPIリファレンス](https://haganecobol.org/ja/javadoc/libcobj/index.html)
* [Hagane COBOL: Java変換解説](./doc/converted_Java_file_JP.md)
* [configuration-params-JP](./doc/configuration-params-JP.md)
* [INDEXEDファイルのロックの仕様](./doc/specification-locking-indexed-file_JP.md)
* [環境変数リファレンス](./doc/environment_variables_JP.md)
* [マルチスレッドのJavaアプリケーションから変換後のプログラムを呼び出す](./doc/multithreading_JP.md)

## SBOM

各[リリース](https://github.com/yutaro-sakamoto/Hagane-COBOL/releases)には、`libcobj.jar`の[CycloneDX](https://cyclonedx.org/) 1.6形式のSBOMが、`libcobj-sbom.json`と`libcobj-sbom.xml`の2つの形式で添付されます。

`libcobj.jar`はshadow jar(fat jar)であるため、同梱されているサードパーティ製ライブラリをjar自体から特定することはできません。
SBOMにはそれらのライブラリがバージョン・ライセンスとともに記載されており、Trivy・Grype・Dependency-Track等の脆弱性スキャナに読み込ませることができます。

ソースツリーから生成する場合は、下記のコマンドを実行します。

```bash
cd libcobj
./gradlew cyclonedxDirectBom
# app/build/reports/libcobj-sbom.json および libcobj-sbom.xml
```

## 実装状況
実装済み機能：
* 基本的なデータ操作 (MOVE, COMPUTEなど)
* 制御文 (IF, PERFORM, GO TOなど)
* 一部のI/O機能 (DISPLAY, ACCEPTなど)
* CALLによる呼び出し
* SEQUENTIALファイルの入出力機能
* RELATIVEファイルの入出力機能
* INDEXEDファイルの入出力機能
* SORT文
* 組み込み関数 (ACOS, LENGTH, MAXなど)

## NIST85テストの状況
Hagane COBOLは[NIST COBOL85 test suite](https://www.itl.nist.gov/div897/ctg/cobol_form.htm)によりテストされています。

主要テスト結果：

```
------ Directory Information -------   --- Total Tests Information ---
Module Programs Executed Error Crash   Pass Fail Deleted Inspect Total
------ -------- -------- ----- -----  ----- ---- ------- ------- -----
NC           90       90     0     0   4352    0       6      11  4369   COBOLの中核機能のテスト
SM           15       15     0     0    290    0       3       1   294   COPY句のテスト
IC           13       13     0     0     97    0       0       0    97   CALL文のテスト
SQ           81       81     0     0    512    0       6      81   599   Sequentialファイルの入出力テスト
IX           39       39     0     0    507    0       1       0   508   Indexedファイルの入出力テスト
ST           39       39     0     0    278    0       0       0   278   SORT文のテスト
SG            5        5     0     0    193    0       0       0   193   セグメントのテスト
OB            5        5     0     0     16    0       0       0    16   廃止された機能のテスト
IF           42       42     0     0    732    0       0       0   732   組み込み関数のテスト
RL           32       32     0     0   1827    0       5       0  1832   Relativeファイルの入出力テスト
------ -------- -------- ----- -----  ----- ---- ------- ------- -----
Total       361      361     0     0   8804    0      21      93  8918
```

追加テストの結果：

```
------ directory information -------   --- total tests information ---
module programs executed error crash   pass fail deleted inspect total
------ -------- -------- ----- -----  ----- ---- ------- ------- -----
cm            7        0     7     0      0    0       0       0     0   COMMUNICATION SECTIONのテスト
db           10        0    10     0      0    0       0       0     0   Debugging機能のテスト
rw            4        0     4     0      0    0       0       0     0   REPORT SECTIONのテスト
------ -------- -------- ----- -----  ----- ---- ------- ------- -----
total        21        0    21     0      0    0       0       0     0
```
## cobj-idx
`cobj-idx`コマンドはINDEXEDファイルを扱うためのユーティリティで、Hagane COBOLをインストールする際に、一緒にインストールされます。

```
cobj-idx - A utility tool to handle an indexed file of Hagane COBOL

Usage:
cobj-idx <sub command> [options] <indexed file>


cobj-idx info <indexed-file>
    Show information of the indexed file.

cobj-idx create <indexed file> --size=<record size> --key=<key information>
    Create a new indexed file.
    The record size and key information are specified by the options.
    By default, this command does not overwrite the indexed file.
    To overwrite the indexed file, use the --new option.
    Example) cobj-idx create test.idx --size=100 --key=2,2:5,4:d15,5
             File name: test.idx
             Record size: 100
             Primary key: 2-3
             Alternate key (No Duplicates):5-8
             Alternate key (Duplicates): 15-19

cobj-idx load <indexed file>
    Load the data from stdin into the indexed file.
    The default format of the input data is SEQUENTIAL of COBOL.

cobj-idx load <indexed file> <input file>
    Load data from the input file into the indexed file.
    The default format of the input data is SEQUENTIAL of COBOL.

cobj-idx unload <indexed file>
    Write the records stored in the indexed file into stdout.
    The default format of the output data is SEQUENTIAL of COBOL.

cobj-idx unload <indexed file> <output file>
    Write the records stored in the indexed file into the output file.
    The default format of the output data is SEQUENTIAL of COBOL.

Options:

-f <format>, --format=<format>
    Specify the format of the input and output data.
    Possible values are 'txt' and the default value 'bin'
    'bin' and 'txt' means SEQUENTIAL and LINE SEQUENTIAL respectively.
    When doing a `load`, this option specifies the format of input data which will be inserted to an indexed file.
    When doing an `unload`, this option specifies the format of output data which will be read from an indexed file.

-h --help
    Print this message.

-n, --new
    Delete all data before inserting new data. This option is only valid when the sub command is 'load'.

-v, --version
    Print the version of cobj-idx.

```

## cobj-api
`cobj-api`コマンドを使用することで、`cobj`で生成されたプログラムを呼び出すSpring boot向けのJavaファイルを簡単に生成できます。

```
Usage: cobj-api [options] <json-file>

Arguments:
  <json-file>: a json file generated by cobj with `-info-json-dir` option

Options:
  -h, --help                            Display this message
  -java-package=<package name>          Specify the package name of the generated source code
  -o=<dir>, --output-dir=<dir>          Set the output destination of the java file to an arbitrary destination
  -v, --version                         Prints the version of the cobj-api
```
cobj-apiを用いたSpring Bootアプリケーションの作成方法については、[こちら](./doc/cobj-api_SpringBoot_JP.md)をご覧ください。

## コントリビュート
コントリビュータ向けのガイドラインは[CONTRIBUTING_JP.md](./CONTRIBUTING_JP.md)を参照してください。
コントリビュータの一覧は https://github.com/yutaro-sakamoto/Hagane-COBOL/graphs/contributors に掲載されています。
