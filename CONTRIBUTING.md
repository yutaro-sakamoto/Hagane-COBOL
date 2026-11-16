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

## Run tests

CI builds Hagane COBOL and runs the Linux tests with [`./local-ci`](local-ci) in the top directory, and you can run the same tests on your own Linux machine with it.
It copies the work tree (including uncommitted changes and untracked files that are not ignored) to `.local-ci/`, builds the Shift_JIS and UTF-8 compilers there, and runs the selected tasks in parallel, so the build in your work tree and `local/` are left untouched.
The tasks are the autotest suites (`test:sjis:*`, `test:utf8:*`), NIST COBOL85 (`nist:*`), the ESQL tests (`esql:*`), the unit tests of libcobj (`unit:libcobj`), the Spring Boot smoke test (`smoke:spring-boot`) and the linters (`lint:*`).

```
./local-ci -l                        # list the tasks
./local-ci 'test:sjis:run' 'lint:*'  # run the tasks matching the patterns
./local-ci -x 'nist:*'               # run all tasks except the matching ones
./local-ci --clean                   # delete .local-ci/
```

Running all tasks takes a long time, so a practical flow is to run the tasks related to your change before pushing and leave the full set to CI.
See the comment at the top of the script (`./local-ci -h`) for all options such as `-j`, `-t` and `--shard`.
The log of each task is written to `.local-ci/logs/<task name with ':' replaced by '_'>.log`, and the details of failed autotest cases stay in `.local-ci/tree-{sjis,utf8}/tests/<suite>.dir/`.

Notes:

* The ESQL tests start a private PostgreSQL server with `initdb` and `pg_ctl`, so the PostgreSQL server binaries must be installed. Set `LOCAL_CI_PG_BIN` if they are not found automatically.
* Without Docker, `unit:libcobj` skips the test classes that use Testcontainers and the coverage verification.
* `test:utf8:cobol_utf8` is skipped if `nkf` is not installed.
* `esql:sjis:esql-sqlca` fails if something listens on `localhost:5432`.
* The results of `lint:cppcheck` depend on the cppcheck version (CI uses the one from EPEL on AlmaLinux 9), and `lint:javadoc` fails on any javadoc warning, so these two may fail locally without a problem in your change.

## Run static analysis

> [!CAUTION]
> Since the behavior of these tools may differ from the one in other operatins systems, we recommend that you run `./format` in Visual Studio Code with Dev Containers described in the previous section.

### check with clang-format and google-java-format

Run `./format` in the top directory of Hagane COBOL.
If you want to make sure all files are formatted, run `./check-format` in the top directory of Hagane COBOL.

`./local-ci 'lint:*'` runs all the static analysis checks that CI runs (clang-format, cppcheck, spotless, SpotBugs, PMD, javadoc and the check for missing javadoc) at once.

### PMD

Run the following command in libcobj/ directory:
```
./gradlew pmdMain
```

## Website

The source of the [website](https://yutaro-sakamoto.github.io/Hagane-COBOL/) is in `website/` and is built with [VitePress](https://vitepress.dev/) (English pages at `website/`, Japanese pages at `website/ja/`).
It is built by CI on every pull request and push, and deployed to GitHub Pages whenever `main` is updated.
To preview it locally with Node.js installed (CI uses Node.js 22), run the following commands:
```
cd website
npm ci
npm run dev
```

`npm run build` generates the static site in `website/.vitepress/dist/`, and `npm run preview` serves the built site.
The Javadoc of libcobj (served under `/javadoc/libcobj/`) is added to the site only by CI, so it is not included in a local build.
