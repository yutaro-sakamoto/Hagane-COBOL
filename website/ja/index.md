---
layout: home

hero:
  name: Hagane COBOL
  text: COBOLからJavaへのコンパイラ
  tagline: COBOLプログラムをJavaプログラムに変換し、JVMの上で動かします。
  actions:
    - theme: brand
      text: Linux
      link: /ja/guide/linux
    - theme: alt
      text: Windows
      link: /ja/guide/windows
    - theme: alt
      text: Docker
      link: /ja/guide/docker
    - theme: alt
      text: APIリファレンス (Javadoc)
      link: /ja/javadoc/libcobj/index.html
      target: _self

features:
  - icon: 🐧
    title: Linux
    details: UbuntuやAlmaLinuxでリリースのソースコードをビルドし、cobjとlibcobj.jarをインストールします。
    link: /ja/guide/linux
    linkText: Linuxへのインストール
  - icon: 🪟
    title: Windows
    details: Visual Studioでcobj.exeを、Gradleでlibcobj.jarをビルドします。
    link: /ja/guide/windows
    linkText: Windowsへのインストール
  - icon: 🐳
    title: Docker
    details: GitHub Container Registryのイメージを取得して、すぐに試せます。
    link: /ja/guide/docker
    linkText: Dockerで動かす
  - icon: 📚
    title: APIリファレンス
    details: 生成されたJavaプログラムが使うランタイムライブラリlibcobj.jarのJavadocです。
    link: /ja/javadoc/libcobj/index.html
    target: _self
    linkText: Javadocを開く
---

## Hello World

```bash
# HELLO.cblをHELLO.javaに変換し、HELLO.classにコンパイルする
cobj HELLO.cbl

# 実行する
java HELLO
```

詳しくは[使い方](./guide/usage)をご覧ください。

## opensource COBOL 4Jからの名称変更

Hagane COBOLは、これまで[opensource COBOL 4J](https://github.com/opensourcecobol/opensourcecobol4j)のフォークとして開発されてきました。
バージョン1.0.0はopensource COBOL 4J 2.1.0をもとにしており、Javaパッケージやインストール先は以前のままでした。
2.0.0では、libcobjのJavaパッケージが`jp.osscons.opensourcecobol.libcobj`から`org.haganecobol.libcobj`に変わり、
`libcobj.jar`のインストール先は`/usr/lib/hagane-cobol/libcobj.jar`(Windowsでは`C:\hagane-cobol`)になりました。
コマンド名(`cobj`、`cobj-idx`、`cobj-api`)と`libcobj.jar`は変わりません。
1.0.0から移行する場合は、プログラムの変換とコンパイルをやり直し、`CLASSPATH`を更新してください。
詳しくは[CHANGELOG](https://github.com/yutaro-sakamoto/Hagane-COBOL/blob/main/CHANGELOG.md)をご覧ください。
