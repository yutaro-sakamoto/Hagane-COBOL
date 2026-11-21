Thank you for your interest in contributing to Hagane COBOL.
A summary of how to contribute is below.

# Issues

Although any topics related to Hagane COBOL can be posted in [Issues](https://github.com/yutaro-sakamoto/Hagane-COBOL/issues), please submit ones written in English or Japanese.

# Pull Requests

We will check pull requests that passed all CI checks running both tests and static code analysis.
The static analysis checks whether C and Java source files are formatted using [clang-format](https://clang.llvm.org/docs/ClangFormat.html) and [google-java-format](https://github.com/google/google-java-format) respectively, and whether [PMD](https://pmd.github.io/) finds no error and warning in Java source files.

Before you submit pull requests, you should run `./format` in order to format files in this repository.

The below sections describe how to setup and run static code analysis.

## Setup Development Environment

We strongly recommend using [Visual Studio Code with Dev Containers](https://code.visualstudio.com/docs/devcontainers/containers) for a consistent development environment. Follow the steps below to set up your development environment.

1. Install [Docker](https://www.docker.com/get-started) on your machine.
1. Install [Visual Studio Code](https://code.visualstudio.com/).
1. Install the [Remote - Containers](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers) extension in Visual Studio Code.
1. Clone the repository.
1. Open the repository in Visual Studio Code.
1. Press `Ctrl+Shift+P` and Select `Dev Containers: Reopen in Container`.
1. Wait for the DevContainer to start up and the build to complete. It may take several minutes to complete this process.
1. (Optional) Press `Ctrl+Shift+@` to open a new terminal of Visual Studio code.
1. (Optional) [Setup credentials for git](https://code.visualstudio.com/remote/advancedcontainers/sharing-git-credentials).

## Run static analysis

> [!CAUTION]
> Since the behavior of these tools may differ from the one in other operatins systems, we recommend that you run `./format` in Visual Studio Code with Dev Containers described in the previous section.

### check with clang-format and google-java-format

Run `./format` in the top directory of Hagane COBOL.
If you want to make sure all files are formatted, run `./check-format` in the top directory of Hagane COBOL.

### PMD

Run the following command in libcobj/ directory:
```
./gradlew pmdMain
```

## Website

The source of the [website](https://haganecobol.org/) is in `website/` and is built with [VitePress](https://vitepress.dev/) (English pages at `website/`, Japanese pages at `website/ja/`).
It is built by CI on every pull request and push, and deployed to GitHub Pages whenever `main` is updated.
To preview it locally with Node.js installed (CI uses Node.js 22), run the following commands:
```
cd website
npm ci
npm run dev
```

`npm run build` generates the static site in `website/.vitepress/dist/`, and `npm run preview` serves the built site.
The Javadoc of libcobj is added to the site only by CI, so it is not included in a local build.
The English Javadoc is served under `/javadoc/libcobj/` and the Japanese one under `/ja/javadoc/libcobj/`.

## Javadoc of libcobj

The Javadoc comments in the libcobj sources are written in Japanese.
The English Javadoc is built from the translations in `libcobj/javadoc-i18n/en/`, which are keyed by the Japanese text.
Run the following commands in libcobj/ directory:
```
./gradlew javadoc                   # Japanese Javadoc (app/build/docs/javadoc/)
./gradlew javadocEn                 # English Javadoc (app/build/docs/javadoc-en/)
./gradlew syncJavadocTranslations   # add new or changed Javadoc texts to the translation files
./gradlew checkJavadocTranslations  # list untranslated texts and out-of-sync translation files
./gradlew testJavadocI18n           # run the unit tests of the translation tool
```

When you add or change Javadoc comments, run `./gradlew syncJavadocTranslations`, fill in the empty `en` entries, and run `./gradlew syncJavadocTranslations` again.
CI fails if any Japanese text in the Javadoc has no English translation, or if the translation files are out of sync with the sources.
To run the same check locally, use `./gradlew checkJavadocTranslations -PfailOnMissingTranslation`.
See [libcobj/javadoc-i18n/README.md](./libcobj/javadoc-i18n/README.md) for details.
