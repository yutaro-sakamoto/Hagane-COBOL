# Translations of the libcobj Javadoc

The Javadoc comments in the libcobj sources are written in Japanese.
The website publishes the Javadoc in two languages:

| Language | Built by | Published at |
|---|---|---|
| English | `./gradlew javadocEn` | `/javadoc/libcobj/` |
| Japanese | `./gradlew javadoc` | `/ja/javadoc/libcobj/` |

The English version is built from a copy of the sources in which the Javadoc
comments are replaced with the translations in `en/`. The sources themselves
are never modified.

## Files

`en/` contains one YAML file per Java source file, at the same path relative to
`app/src/main/java/`. Each file is a list of pairs of the Japanese text and its
translation:

```yaml
- ja: |-
    現在のスレッドのEOPフラグを設定する
  en: |-
    Sets the EOP flag of the current thread.
- ja: |-
    設定する値
  en: |-
    the value to set
```

A translation unit is the main description of a comment, or the description of
a block tag (`@param`, `@return`, `@throws`, `@deprecated`, ...).
Units without Japanese characters are not translated.

The Japanese text itself is the key. When the Japanese text in a source file
changes, the old translation no longer matches, and the unit is reported as
untranslated until the translation is updated. Differences in line breaks and
spaces are ignored. A translation found in another file is also used, so moving
a comment to another class keeps its translation.

In a local build, untranslated units stay in Japanese in the English Javadoc.
CI does not allow them (see below).

## Updating the translations

After changing Javadoc comments, run in `libcobj/`:

```bash
./gradlew syncJavadocTranslations
```

This adds the new Japanese texts to `en/` with an empty `en`, and removes the
entries and files that are no longer used. Then fill in the empty `en` fields.
Always write `en` as a literal block (`|-`), and do not edit `ja` by hand.

To list the untranslated units:

```bash
./gradlew checkJavadocTranslations
```

With `-PfailOnMissingTranslation`, the task fails when untranslated units exist.
CI (the `Javadoc` workflow) runs it this way, so a pull request that adds or
changes Japanese Javadoc text must also update the translations.

The tool is `app/src/javadocI18n/java/org/haganecobol/javadoci18n/JavadocTranslator.java`.
It is not part of `libcobj.jar`.

---

# libcobjのJavadocの翻訳

libcobjのソースコードのJavadocコメントは日本語で書かれています。
Webサイトでは、Javadocを2つの言語で公開しています。

| 言語 | ビルド方法 | 公開先 |
|---|---|---|
| 英語 | `./gradlew javadocEn` | `/javadoc/libcobj/` |
| 日本語 | `./gradlew javadoc` | `/ja/javadoc/libcobj/` |

英語版は、ソースコードをコピーしてJavadocコメントを`en/`の訳文に置き換えたものからビルドします。
ソースコードそのものは変更しません。

## ファイル

`en/`には、Javaのソースファイル1つにつき1つのYAMLファイルを、`app/src/main/java/`からの相対パスと同じ位置に置きます。
各ファイルは、日本語の原文と訳文の組のリストです。

```yaml
- ja: |-
    現在のスレッドのEOPフラグを設定する
  en: |-
    Sets the EOP flag of the current thread.
- ja: |-
    設定する値
  en: |-
    the value to set
```

翻訳の単位は、コメントの本文、またはブロックタグ(`@param`、`@return`、`@throws`、`@deprecated`など)の説明文です。
日本語の文字を含まない単位は翻訳しません。

日本語の原文そのものがキーです。ソースファイルの日本語が変更されると古い訳文が一致しなくなり、訳文を更新するまでその単位は未翻訳として報告されます。
改行や空白の違いは無視します。別のファイルにある訳文も使うため、コメントを別のクラスに移動しても訳文は引き継がれます。

ローカルでビルドした場合、未翻訳の単位は英語版のJavadocでも日本語のまま残ります。
CIでは未翻訳は許されません(後述)。

## 翻訳の更新

Javadocコメントを変更したら、`libcobj/`で以下を実行します。

```bash
./gradlew syncJavadocTranslations
```

新しい日本語の原文が空の`en`とともに`en/`に追加され、使われなくなった項目とファイルは削除されます。
その後、空の`en`を埋めてください。
`en`は必ずリテラルブロック(`|-`)で書き、`ja`は手で編集しないでください。

未翻訳の単位の一覧は以下で表示できます。

```bash
./gradlew checkJavadocTranslations
```

`-PfailOnMissingTranslation`を付けると、未翻訳の単位がある場合にタスクが失敗します。
CI(`Javadoc`ワークフロー)はこの指定で実行するため、Javadocの日本語を追加・変更するPull Requestでは訳文も更新する必要があります。

ツールの実体は`app/src/javadocI18n/java/org/haganecobol/javadoci18n/JavadocTranslator.java`です。
`libcobj.jar`には含まれません。
