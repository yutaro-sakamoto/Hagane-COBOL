#!/bin/bash
#
# Tests of win/run-tests.sh. They run the real script in a fake source tree
# whose test suites and NIST report.sh are small scripts, so they work both on
# Linux and in Git Bash on Windows.
#
# Usage: ci-tests/run-tests.test.sh   (T_FILTER='w_nist*' to run some tests)

# shellcheck source=ci-tests/lib.sh
. "$(dirname "$0")/lib.sh"

# Creates a fake tree in $T_TMP/tree with a copy of win/run-tests.sh
make_tree() {
  TREE=$T_TMP/tree
  mkdir -p "$TREE/win" "$TREE/tests/cobol85"
  cp "$REPO_ROOT/win/run-tests.sh" "$TREE/win/"
  export T_MARK=$T_TMP/mark
  mkdir -p "$T_MARK"
  : > "$T_MARK/events"
  # report.sh of the fake NIST modules: the module directory says what to do
  cat > "$TREE/tests/cobol85/report.sh" << 'EOF'
#!/bin/sh
echo "cobj: $(command -v cobj)" > cobj-path
if [ -f rc ] && [ "$(cat rc)" != 0 ]; then
  echo "report.sh failed"
  exit "$(cat rc)"
fi
if [ -f result ]; then
  printf 'Compile error: 0\nSuccessfully executed:   12 %s\n' "$(cat result)" > report.txt
else
  echo "no summary" > report.txt
fi
EOF
}

# mk_suite NAME RC SECONDS [extra shell commands]: an autotest suite stand-in
mk_suite() {
  cat > "$TREE/tests/$1" << EOF
#!/bin/bash
echo "\$(date +%s%N) start $1" >> "\$T_MARK/events"
echo "args: \$*"
echo "output of $1"
${4:-}
sleep $3
echo "\$(date +%s%N) end $1" >> "\$T_MARK/events"
exit $2
EOF
  chmod +x "$TREE/tests/$1"
}

# mk_nist MODULE RESULT [RC]
mk_nist() {
  mkdir -p "$TREE/tests/cobol85/$1"
  echo "$2" > "$TREE/tests/cobol85/$1/result"
  echo "${3:-0}" > "$TREE/tests/cobol85/$1/rc"
}

# run [ENV=VALUE...] -- ARGS...: runs the script, sets OUT and RC
run() {
  local envs=()
  while [ $# -gt 0 ] && [ "$1" != -- ]; do
    envs+=("$1")
    shift
  done
  shift
  OUT=$(env -u GITHUB_ACTIONS ${envs[@]+"${envs[@]}"} bash "$TREE/win/run-tests.sh" "$@" 2>&1)
  RC=$?
}

LOGS() { echo "$TREE/tests/win-logs"; }

w_usage_without_items() {
  make_tree
  run -- -P 2
  assert_eq "$RC" 2 "exit status"
  assert_contains "$OUT" "Usage: win/run-tests.sh"
}

w_unknown_option() {
  make_tree
  run -- -x a
  assert_eq "$RC" 2 "exit status"
}

w_all_pass() {
  make_tree
  mk_suite a 0 0
  mk_suite b 0 0
  run -- a b
  assert_eq "$RC" 0 "exit status"
  assert_contains "$OUT" "PASS  a"
  assert_contains "$OUT" "PASS  b"
  assert_contains "$OUT" "pass: 2  fail: 0"
}

w_failure_is_reported_and_others_run() {
  make_tree
  mk_suite a 0 0
  mk_suite bad 1 0
  mk_suite c 0 0
  run -- bad a c
  assert_eq "$RC" 1 "exit status"
  assert_contains "$OUT" "pass: 2  fail: 1"
  assert_contains "$OUT" "FAIL  bad  $(LOGS)/bad.log"
  assert_contains "$OUT" "PASS  c"
  assert_contains "$(cat "$(LOGS)/bad.log")" "output of bad"
}

w_every_failure_counts() {
  make_tree
  mk_suite a 1 0
  mk_suite b 2 0
  mk_suite c 0 0
  run -- a b c
  assert_eq "$RC" 1 "exit status"
  assert_contains "$OUT" "pass: 1  fail: 2"
  assert_contains "$OUT" "FAIL  a  "
  assert_contains "$OUT" "FAIL  b  "
}

w_missing_suite_fails() {
  make_tree
  mk_suite a 0 0
  run -- a nosuch
  assert_eq "$RC" 1 "exit status"
  assert_contains "$OUT" "FAIL  nosuch"
  assert_contains "$(cat "$(LOGS)/nosuch.log")" "nosuch"
}

w_autotest_jobs_option() {
  make_tree
  mk_suite a 0 0
  run -- -j 7 a
  assert_contains "$(cat "$(LOGS)/a.log")" "args: -j7"
  run -- a
  assert_contains "$(cat "$(LOGS)/a.log")" "args: -j4" "default -j"
}

w_suites_run_in_tests_dir() {
  make_tree
  mk_suite a 0 0 'echo "cwd: $(pwd)"'
  pushd "$T_TMP" > /dev/null || return
  run -- a
  popd > /dev/null || return
  assert_contains "$(cat "$(LOGS)/a.log")" "cwd: $TREE/tests"
}

w_parallel_limit() {
  make_tree
  local n
  for n in a b c d e; do mk_suite $n 0 1; done
  run -- -P 2 a b c d e
  assert_eq "$RC" 0 "exit status"
  assert_eq "$(max_concurrency "$T_MARK/events")" 2 "max concurrency with -P 2"
  : > "$T_MARK/events"
  run -- -P 5 a b c d e
  assert_eq "$(max_concurrency "$T_MARK/events")" 5 "max concurrency with -P 5"
  : > "$T_MARK/events"
  run -- -P 1 a b c
  assert_eq "$(max_concurrency "$T_MARK/events")" 1 "max concurrency with -P 1"
}

w_default_parallelism_is_2() {
  make_tree
  local n
  for n in a b c d; do mk_suite $n 0 1; done
  run -- a b c d
  assert_eq "$(max_concurrency "$T_MARK/events")" 2 "max concurrency"
}

w_start_order_follows_arguments() {
  make_tree
  local n
  for n in d b a c; do mk_suite $n 0 0; done
  run -- -P 1 d b a c
  assert_eq "$(start_order "$T_MARK/events")" "d b a c" "start order"
}

w_sequence_runs_in_order_in_one_slot() {
  make_tree
  mk_suite a 0 1
  mk_suite b 0 0
  mk_suite c 0 0
  mk_suite other 0 1
  run -- -P 2 a+b+c other
  assert_eq "$RC" 0 "exit status"
  ended_before "$T_MARK/events" a b || fail "b started before a ended"
  ended_before "$T_MARK/events" b c || fail "c started before b ended"
  assert_eq "$(max_concurrency "$T_MARK/events")" 2 "a+b+c uses one slot"
  local log
  log=$(cat "$(LOGS)/a-b-c.log")
  assert_eq "$(lines_matching "$log" '^(=== |output of )')" \
    "=== a output of a === b output of b === c output of c" "log of the sequence"
  assert_contains "$OUT" "PASS  a+b+c"
  assert_contains "$OUT" "pass: 2  fail: 0"
}

w_sequence_continues_after_a_failure() {
  make_tree
  mk_suite a 0 0
  mk_suite b 3 0
  mk_suite c 0 0
  run -- a+b+c
  assert_eq "$RC" 1 "exit status"
  assert_contains "$(start_order "$T_MARK/events")" "a b c" "all items ran"
  assert_contains "$OUT" "FAIL  b (in a+b+c)"
  assert_not_contains "$OUT" "FAIL  a (in"
  assert_not_contains "$OUT" "FAIL  c (in"
  assert_contains "$OUT" "FAIL  a+b+c"
  assert_contains "$OUT" "pass: 0  fail: 1"
}

w_single_item_has_no_in_line() {
  make_tree
  mk_suite bad 1 0
  run -- bad
  assert_not_contains "$OUT" "(in bad)"
}

w_nist_pass() {
  make_tree
  mk_nist NC "(100.00%)"
  run -- nist:NC
  assert_eq "$RC" 0 "exit status"
  assert_contains "$OUT" "PASS  nist:NC"
  assert_contains "$(cat "$(LOGS)/nist_NC.log")" "Successfully executed:" "report.txt in the log"
}

w_nist_below_100_percent_fails() {
  make_tree
  mk_nist NC "(99.50%)"
  mk_nist SM "(100.00%)"
  run -- nist:NC nist:SM
  assert_eq "$RC" 1 "exit status"
  assert_contains "$OUT" "FAIL  nist:NC"
  assert_contains "$OUT" "PASS  nist:SM"
}

w_nist_100_percent_elsewhere_does_not_pass() {
  make_tree
  mk_nist NC "(50.00%)"
  # 100.00% on another line must not count
  cat > "$TREE/tests/cobol85/report.sh" << 'EOF2'
#!/bin/sh
echo "Compile error: (100.00%)" > report.txt
echo "Successfully executed: 1 (50.00%)" >> report.txt
EOF2
  run -- nist:NC
  assert_eq "$RC" 1 "exit status"
}

w_nist_report_failure_fails() {
  make_tree
  mk_nist NC "(100.00%)" 1
  run -- nist:NC
  assert_eq "$RC" 1 "exit status"
  assert_contains "$(cat "$(LOGS)/nist_NC.log")" "report.sh failed"
}

w_nist_without_summary_fails() {
  make_tree
  mkdir -p "$TREE/tests/cobol85/NC"
  run -- nist:NC
  assert_eq "$RC" 1 "exit status"
}

w_nist_missing_module_fails() {
  make_tree
  run -- nist:XX
  assert_eq "$RC" 1 "exit status"
  assert_contains "$OUT" "FAIL  nist:XX"
}

w_nist_finds_cobj_on_path() {
  make_tree
  mk_nist NC "(100.00%)"
  local inst=$T_TMP/inst
  mkdir -p "$inst/bin" "$inst/config"
  printf '#!/bin/sh\n' > "$inst/bin/cobj"
  chmod +x "$inst/bin/cobj"
  run "COB4J_INSTALL_DIR=$inst" -- nist:NC
  assert_eq "$RC" 0 "exit status"
  assert_contains "$(cat "$TREE/tests/cobol85/NC/cobj-path")" "$inst/bin/cobj"
}

w_missing_install_dir_is_ignored() {
  make_tree
  mk_suite a 0 0
  run "COB4J_INSTALL_DIR=$T_TMP/none" -- a
  assert_eq "$RC" 0 "exit status"
}

w_suites_and_nist_mixed() {
  make_tree
  mk_suite a 0 0
  mk_nist NC "(100.00%)"
  run -- nist:NC a+nist:NC
  assert_eq "$RC" 0 "exit status"
  assert_contains "$OUT" "pass: 2  fail: 0"
}

w_diff_ignores_trailing_cr() {
  make_tree
  printf 'a\r\nb\r\n' > "$TREE/tests/crlf.txt"
  printf 'a\nb\n' > "$TREE/tests/lf.txt"
  mk_suite a 0 0 'diff crlf.txt lf.txt || exit 9'
  run -- a
  assert_eq "$RC" 0 "exit status"
}

w_diff_still_finds_differences() {
  make_tree
  printf 'a\r\n' > "$TREE/tests/x.txt"
  printf 'b\n' > "$TREE/tests/y.txt"
  mk_suite a 0 0 'if diff x.txt y.txt > /dev/null; then exit 9; fi'
  run -- a
  assert_eq "$RC" 0 "exit status"
}

w_github_actions_groups() {
  make_tree
  mk_suite bad 1 0 'echo "::warning::from the test"'
  mk_suite good 0 0
  run GITHUB_ACTIONS=true -- bad good
  assert_contains "$OUT" "::group::log of bad"
  assert_contains "$OUT" "::endgroup::"
  assert_contains "$OUT" "::error title=windows-test::bad failed"
  assert_contains "$OUT" " ::warning::from the test" "escaped workflow command"
  assert_not_contains "$OUT" $'\n::warning::' "unescaped workflow command"
  assert_not_contains "$OUT" "log of good"
}

w_no_groups_outside_github_actions() {
  make_tree
  mk_suite bad 1 0
  run -- bad
  assert_not_contains "$OUT" "::group::"
}

w_log_directory_is_reset() {
  make_tree
  mk_suite a 0 0
  mkdir -p "$(LOGS)"
  echo stale > "$(LOGS)/old.log"
  run -- a
  assert_no_file "$(LOGS)/old.log" "stale log"
  assert_file "$(LOGS)/a.log"
}

w_item_timeout() {
  make_tree
  mk_suite hang 0 30
  mk_suite ok 0 0
  local start=$SECONDS
  run -- -t 2 hang ok
  assert_eq "$RC" 1 "exit status"
  assert_contains "$OUT" "TIMEOUT hang (2s)"
  assert_contains "$OUT" "FAIL  hang"
  assert_contains "$OUT" "PASS  ok"
  assert_contains "$(cat "$(LOGS)/hang.log")" "=== hang timed out after 2s"
  [ $((SECONDS - start)) -lt 20 ] || fail "took $((SECONDS - start))s"
  sleep 1
  assert_not_contains "$(cat "$T_MARK/events")" "end hang" "the hung suite was stopped"
}

w_timeout_in_a_sequence_continues() {
  make_tree
  mk_suite hang 0 30
  mk_suite after 0 0
  run -- -t 2 hang+after
  assert_eq "$RC" 1 "exit status"
  assert_contains "$OUT" "TIMEOUT hang (2s)"
  assert_contains "$OUT" "FAIL  hang (in hang+after)"
  assert_contains "$(start_order "$T_MARK/events")" "hang after" "the next item ran"
}

w_items_within_the_limit_pass() {
  make_tree
  mk_suite a 0 1
  mk_nist NC "(100.00%)"
  run -- -t 10 a nist:NC
  assert_eq "$RC" 0 "exit status"
  assert_not_contains "$OUT" "TIMEOUT"
}

w_works_from_any_directory() {
  make_tree
  mk_suite a 0 0
  pushd / > /dev/null || return
  run -- a
  popd > /dev/null || return
  assert_eq "$RC" 0 "exit status"
}

for t in $(declare -F | awk '{print $3}' | grep '^w_'); do
  run_test "$t"
done
finish
