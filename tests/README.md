Execute make command to generate the following test scripts.

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

For example, execute ./command-line-options in order to run command-line-options test.

`multithread` runs generated programs from several threads of one JVM at the
same time. Its test programs are driven by the small Java harnesses in
`multithread.src/harness/`, which start the threads and synchronise them.
See [the multi-threading guide](../doc/multithreading.md) for the run-unit model
the suite checks.

## ESQL tests

The ESQL suites -- `esql-basic`, `esql-cobol-data`, `esql-misc`, `esql-sql-data`,
`esql-sqlca`, `esql-multithread` and `esql-utf8` -- additionally need a PostgreSQL server.
`embed_db_info.sh` in this directory is what tells the test programs where to
find it. The copy checked in here points at `localhost:5432`, which is right for
a PostgreSQL running on the same machine; edit it if yours is somewhere else.

On Linux, CI runs these suites with `../local-ci` (see below), which starts its
own PostgreSQL server and rewrites `embed_db_info.sh` in its copy of the work
tree, so the file checked in here is left as it is.
`.github/workflows/db-settings/embed_db_info_windows.sh` is the copy the Windows
CI uses: it is the one for the cluster `win/start-test-postgresql.ps1` starts,
and that script also reads its own defaults out of it, so the two cannot
disagree about the port. `embed_db_info_postgresql_15.sh` and
`embed_db_info_postgresql_9.6.sh` next to it named GitHub Actions service
containers of the former Linux workflows and are not used any more.

On Windows, `win/start-test-postgresql.ps1` brings up a cluster to test against.
It initialises one on first use and reuses it afterwards, and `-Stop` shuts it
down. From this directory, in Git Bash:

```
powershell -File ../win/start-test-postgresql.ps1
cp ../.github/workflows/db-settings/embed_db_info_windows.sh embed_db_info.sh
./esql-basic
```

That `cp` overwrites a tracked file; `git checkout embed_db_info.sh` puts the
default back.

`esql-utf8` needs a compiler configured with `--enable-utf8`, so it does not run
against the Shift_JIS Windows build.

## Running tests on Windows

The Windows CI runs the tests in three jobs, `windows-test (other-1)`,
`windows-test (other-2)` and `windows-test (nist-esql)`, defined by the matrix
in `.github/workflows/pull-request.yml` and `push.yml` and run by
`.github/workflows/windows-test.yml`. Each job calls `../win/run-tests.sh`,
which takes a list of items and runs a few of them at a time:

- `SUITE` -- an autotest suite of this directory (`run`, `misc`, `esql-basic`, ...)
- `nist:MODULE` -- a NIST COBOL85 module in `cobol85/` (`nist:NC`, ...)
- `A+B+...` -- items run one after another in the same slot. The ESQL suites
  are run this way because they share one database.

```
../win/run-tests.sh -P 2 -j 4 run misc esql-basic+esql-sqlca nist:NC
```

`-P` is the number of items that run at the same time and `-j` is passed to
each autotest suite. The output of each item goes to
`win-logs/<item>.log` (`:` and `+` in the name are replaced by `_` and `-`),
and the failed items are listed at the end. In GitHub Actions their logs are
also printed in collapsible groups, and a failed job uploads `win-logs/`,
the `*.dir/` directories and the NIST `report.txt` files as the artifact
`windows-test-<group>-log`.

The script also works on your own Windows machine in Git Bash. Install Hagane
COBOL with `win/make-install.ps1`, put the generated test scripts and
`atconfig` in this directory (they are made by `make` here on a Linux machine,
see `.github/workflows/windows-generate-test-scripts.yml`), and set up the test
environment as the "Set up test environment" step of
`.github/workflows/windows-test.yml` does (`atlocal` from `atlocal-win.env`,
the paths in `atconfig`, `CLASSPATH`, and `embed_db_info.sh` for the ESQL
suites). For the ESQL suites, start PostgreSQL first as described above.

## Running the CI test set with local-ci

`../local-ci` (in the top directory) is the script the Linux CI jobs run. It
copies the work tree to `.local-ci/`, builds the Shift_JIS and UTF-8 compilers
there and runs the test suites of this directory, NIST COBOL85, the ESQL tests
(with a private PostgreSQL server started by `initdb`/`pg_ctl`), the libcobj
unit tests, the Spring Boot smoke test and the linters in parallel. The build
in your own work tree is not touched.

```
cd ..
./local-ci -l                          # list the tasks
./local-ci 'test:sjis:*'               # all suites with the Shift_JIS compiler
./local-ci test:utf8:misc 'esql:*'     # select tasks by name or glob pattern
./local-ci --no-esql                   # all tasks except the ESQL ones
```

Without the PostgreSQL server binaries (`initdb`/`pg_ctl`), `local-ci` warns
at startup and reports the `esql:*` tasks as `SKIP`, which does not fail the
run unless `LOCAL_CI_STRICT=1` (CI sets it). `--no-esql` leaves out the ESQL
tasks and the PostgreSQL startup on purpose, and `LOCAL_CI_PG_BIN=none` makes
`local-ci` treat PostgreSQL as missing.

The task names of the suites in this directory are `test:sjis:<suite>` and
`test:utf8:<suite>`, and the ESQL suites are `esql:sjis:<suite>` and
`esql:utf8:esql-utf8`. The log of each task is
`.local-ci/logs/<task name with ':' replaced by '_'>.log`, and the details of
failed test groups stay in `.local-ci/tree-{sjis,utf8}/tests/<suite>.dir/`.
Run `./local-ci -h` for all options. See also the "Run tests" section of
[CONTRIBUTING.md](../CONTRIBUTING.md).
