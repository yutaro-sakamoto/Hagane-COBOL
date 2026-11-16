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
```

The task names of the suites in this directory are `test:sjis:<suite>` and
`test:utf8:<suite>`, and the ESQL suites are `esql:sjis:<suite>` and
`esql:utf8:esql-utf8`. The log of each task is
`.local-ci/logs/<task name with ':' replaced by '_'>.log`, and the details of
failed test groups stay in `.local-ci/tree-{sjis,utf8}/tests/<suite>.dir/`.
Run `./local-ci -h` for all options. See also the "Run tests" section of
[CONTRIBUTING.md](../CONTRIBUTING.md).
