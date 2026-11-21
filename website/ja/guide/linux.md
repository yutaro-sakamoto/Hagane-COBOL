# Linuxへのインストール

Hagane COBOLは、下記の環境でテストされています。

| OS | JDK |
| -- | -- |
| Ubuntu 26.04 | OpenJDK 21 |
| AlmaLinux 9 | OpenJDK 11 |

## 1. 依存パッケージのインストール

::: code-group

```bash [Ubuntu]
sudo apt-get update
sudo apt-get install -y default-jdk build-essential bison flex gettext texinfo libgmp-dev autoconf
```

```bash [AlmaLinux]
sudo dnf -y update
sudo dnf install -y java-11-openjdk-devel gcc make bison flex automake autoconf diffutils gettext
```

:::

## 2. Hagane COBOLのビルドとインストール

[最新のリリース](https://github.com/yutaro-sakamoto/Hagane-COBOL/releases)のソースコードをダウンロードし、ビルドしてインストールします。

::: code-group

```bash [Shift_JISのソースコード(既定)]
curl -L -o hagane-cobol-v2.0.0.tar.gz https://github.com/yutaro-sakamoto/Hagane-COBOL/archive/refs/tags/v2.0.0.tar.gz
tar zxvf hagane-cobol-v2.0.0.tar.gz
cd Hagane-COBOL-2.0.0
./configure --prefix=/usr/
make
sudo make install
```

```bash [UTF-8のソースコード]
curl -L -o hagane-cobol-v2.0.0.tar.gz https://github.com/yutaro-sakamoto/Hagane-COBOL/archive/refs/tags/v2.0.0.tar.gz
tar zxvf hagane-cobol-v2.0.0.tar.gz
cd Hagane-COBOL-2.0.0
./configure --prefix=/usr/ --enable-utf8
touch cobj/*.m4
make
sudo make install
```

:::

## 3. CLASSPATHの設定

環境変数`CLASSPATH`に`libcobj.jar`を追加します。たとえば`~/.bashrc`に次の行を書きます。

```bash
export CLASSPATH="$CLASSPATH:/usr/lib/hagane-cobol/libcobj.jar"
```

::: warning 1.0.0からの移行
Hagane COBOL 1.0.0は`libcobj.jar`を`/usr/lib/opensourcecobol4j/libcobj.jar`にインストールしていました。
`CLASSPATH`のこのパスを新しいパスに置き換え、COBOLプログラムの変換とコンパイルをやり直してください。
`make install`は1.0.0のファイルを削除しないため、不要であれば手動で削除してください。
:::

## 4. インストールの確認

```bash
cobj --version
```

最初のプログラムのコンパイルと実行は、[使い方](./usage)をご覧ください。
