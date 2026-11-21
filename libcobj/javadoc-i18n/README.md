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

A translation unit is the main description of a comment, or the text of any
block tag (`@param`, `@return`, `@throws`, `@deprecated`, the label of `@see`,
`@since`, the description of `@serialField`, ...).
Units without Japanese characters are not translated.

The Japanese text itself is the key. When the Japanese text in a source file
changes, the old translation no longer matches, and the unit is reported as
untranslated until the translation is updated. Differences in line breaks and
spaces are ignored. Identical Japanese texts in the same source file always
share one translation; to translate them differently, change the Japanese text.

Translations are looked up only in the YAML file that corresponds to the source
file. When a comment moves to another class, `syncJavadocTranslations` copies its
translation from the old file into the new one and prints
`copied the translation from X to Y`.

In a local build, untranslated units stay in Japanese in the English Javadoc.
CI does not allow them (see below).

## Updating the translations

After changing Javadoc comments, run in `libcobj/`:

```bash
./gradlew syncJavadocTranslations
```

This adds the new Japanese texts to `en/` with an empty `en` (or with the
translation copied from another file, see above), and removes the entries and
files that are no longer used. Then fill in the empty `en` fields.
For safety, it deletes only YAML files that start with the generated header,
removes directories that become empty, and refuses to run when it would delete
more than half of the YAML files (for example, because of a wrong `--source`).
Always write `en` as a literal block (`|-`), and do not edit `ja` by hand.

To list the untranslated units and the translation files that are out of sync
with what `syncJavadocTranslations` would write (missing, stale, or no longer
used YAML files):

```bash
./gradlew checkJavadocTranslations
```

With `-PfailOnMissingTranslation`, the task fails when either of them exists.
CI (the `Javadoc` workflow) runs it this way, so a pull request that adds or
changes Japanese Javadoc text must also update the translations.
To reproduce the CI check locally:

```bash
./gradlew checkJavadocTranslations -PfailOnMissingTranslation
```

The tool is in `app/src/javadocI18n/java/org/haganecobol/javadoci18n/`
(`JavadocTranslator` is the entry point), and its unit tests are in
`app/src/javadocI18nTest/`. Run the tests with `./gradlew testJavadocI18n`.
CI runs them in the `Javadoc` workflow, and runs PMD and SpotBugs on the tool
(`pmdJavadocI18n`, `spotbugsJavadocI18n`) in the static analysis workflow.
The tool is not part of `libcobj.jar`.

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

翻訳の単位は、コメントの本文、または全てのブロックタグ(`@param`、`@return`、`@throws`、`@deprecated`、`@see`のラベル、`@since`、`@serialField`の説明など)の文言です。
日本語の文字を含まない単位は翻訳しません。

日本語の原文そのものがキーです。ソースファイルの日本語が変更されると古い訳文が一致しなくなり、訳文を更新するまでその単位は未翻訳として報告されます。
改行や空白の違いは無視します。同じソースファイル内の同一の日本語は、常に1つの訳文を共有します。別々に訳したい場合は、日本語のほうを変えてください。

訳文は、ソースファイルに対応するYAMLファイルからのみ探します。
コメントを別のクラスに移動したときは、`syncJavadocTranslations`が古いファイルから新しいファイルへ訳文をコピーし、`copied the translation from X to Y`と表示します。

ローカルでビルドした場合、未翻訳の単位は英語版のJavadocでも日本語のまま残ります。
CIでは未翻訳は許されません(後述)。

## 翻訳の更新

Javadocコメントを変更したら、`libcobj/`で以下を実行します。

```bash
./gradlew syncJavadocTranslations
```

新しい日本語の原文が空の`en`(または前述のとおり別のファイルからコピーした訳文)とともに`en/`に追加され、使われなくなった項目とファイルは削除されます。
その後、空の`en`を埋めてください。
安全のため、削除するのは生成時のヘッダで始まるYAMLファイルだけで、空になったディレクトリも削除します。また、YAMLファイルの半数を超えて削除することになる場合(`--source`の指定誤りなど)は実行を拒否します。
`en`は必ずリテラルブロック(`|-`)で書き、`ja`は手で編集しないでください。

未翻訳の単位と、`syncJavadocTranslations`が書き出す内容と食い違っている翻訳ファイル(不足しているもの、古いもの、使われなくなったYAMLファイル)の一覧は以下で表示できます。

```bash
./gradlew checkJavadocTranslations
```

`-PfailOnMissingTranslation`を付けると、そのどちらかがある場合にタスクが失敗します。
CI(`Javadoc`ワークフロー)はこの指定で実行するため、Javadocの日本語を追加・変更するPull Requestでは訳文も更新する必要があります。
CIと同じ検査をローカルで行うには以下を実行します。

```bash
./gradlew checkJavadocTranslations -PfailOnMissingTranslation
```

ツールは`app/src/javadocI18n/java/org/haganecobol/javadoci18n/`にあり(入口は`JavadocTranslator`)、単体テストは`app/src/javadocI18nTest/`にあります。
テストは`./gradlew testJavadocI18n`で実行します。
CIは`Javadoc`ワークフローでこのテストを実行し、静的解析のワークフローでツールにPMDとSpotBugs(`pmdJavadocI18n`、`spotbugsJavadocI18n`)をかけます。
ツールは`libcobj.jar`には含まれません。
