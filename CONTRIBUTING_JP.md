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


## 静的解析の実行

> [!CAUTION]
> これらのツールの動作は他のオペレーティングシステムとは異なる場合があります。上記のVisual Studio Code with Dev Containersの環境でフォーマッタを実行することを推奨します。

### clang-formatとgoogle-java-format

Hagane COBOLのトップディレクトリで`./format`を実行してください。
`./check-format`を実行することで、フォーマットが完了したかを確認できます。

### PMD

Hagane COBOLのlibcobj/ディレクトリで下記のコマンドを実行してください。
```
./gradlew pmdMain
```

## Webサイト

[Webサイト](https://haganecobol.org/ja/)のソースは`website/`にあり、[VitePress](https://vitepress.dev/)でビルドします(英語版は`website/`直下、日本語版は`website/ja/`)。
CIがPull Requestとpushのたびにビルドし、`main`が更新されるとGitHub Pagesに公開します。
ローカルで確認するには、Node.jsをインストールした環境(CIではNode.js 22を使用)で下記のコマンドを実行してください。
```
cd website
npm ci
npm run dev
```

`npm run build`を実行すると`website/.vitepress/dist/`に静的サイトが生成され、`npm run preview`でビルド結果を確認できます。
libcobjのJavadocはCIでのみサイトに追加されるため、ローカルのビルドには含まれません。
英語版は`/javadoc/libcobj/`以下、日本語版は`/ja/javadoc/libcobj/`以下で公開されます。

## libcobjのJavadoc

libcobjのソースのJavadocコメントは日本語で書きます。
英語版のJavadocは、日本語の原文をキーとする`libcobj/javadoc-i18n/en/`以下の翻訳から生成されます。
Hagane COBOLのlibcobj/ディレクトリで下記のコマンドを実行してください。
```
./gradlew javadoc                   # 日本語版Javadoc (app/build/docs/javadoc/)
./gradlew javadocEn                 # 英語版Javadoc (app/build/docs/javadoc-en/)
./gradlew syncJavadocTranslations   # 追加・変更されたJavadocの文言を翻訳ファイルに反映
./gradlew checkJavadocTranslations  # 未翻訳の文言と、ソースと食い違っている翻訳ファイルを一覧表示
./gradlew testJavadocI18n           # 翻訳ツールの単体テストを実行
```

Javadocコメントを追加・変更したときは、`./gradlew syncJavadocTranslations`を実行し、空の`en`を埋めてください。
Javadocの日本語に英訳が無いものが1つでもあるか、翻訳ファイルがソースと食い違っていると、CIは失敗します。
同じ検査をローカルで行うには`./gradlew checkJavadocTranslations -PfailOnMissingTranslation`を実行してください。
詳細は[libcobj/javadoc-i18n/README.md](./libcobj/javadoc-i18n/README.md)を参照してください。
