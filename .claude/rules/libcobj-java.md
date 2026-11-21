---
paths:
  - "libcobj/**"
---

# libcobj (Java) を編集するときの注意

- **CIのPMDはコメントのみのcatchブロックを弾く**: static-analysisジョブのPMD `EmptyCatchBlock`は`allowCommentedBlocks`が無効なため、`catch (E e) { /* コメント */ }`はビルド失敗になる。意図的に例外を握りつぶすcatchには既存スタイルに合わせて`System.err.println("Failed to ...")`を入れる（例: `CobolIndexedFile.commitJdbcTransaction`）。
- **ローカルでの静的解析**: `cd libcobj && ./gradlew pmdMain spotbugsMain spotlessCheck`（sandbox無効化が必要）。
- **Javadocを編集したら英訳も更新する**: ソースのJavadocは日本語で書き、英語版は`libcobj/javadoc-i18n/en/*.yaml`の訳(日本語の原文がキー)から`./gradlew javadocEn`で生成する。Javadocの文言を追加・変更したら`cd libcobj && ./gradlew syncJavadocTranslations`でYAMLを更新し、空の`en`を埋める。未翻訳は`./gradlew checkJavadocTranslations`で一覧できる(CIでは警告のみで、未翻訳箇所は英語版でも日本語のまま出る)。詳細は`libcobj/javadoc-i18n/README.md`。
- **フォーマット**: トップディレクトリの`./format`が`./gradlew spotlessApply`を呼ぶ。Stopフックで自動実行される。
