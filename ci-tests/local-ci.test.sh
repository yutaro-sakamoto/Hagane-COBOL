#!/bin/bash
#
# Tests of local-ci. Most tests run the real script in a small git repository
# with fake tasks (LOCAL_CI_TASK_DEFS) that sleep, pass, fail or skip and log
# when they start and end. The --shard and --no-esql tests use the real task
# definitions with -l, which does not run anything.
#
# Linux only (local-ci needs setsid, flock and /proc).
# Usage: ci-tests/local-ci.test.sh   (T_FILTER='l_shard*' to run some tests)

# shellcheck source=ci-tests/lib.sh
. "$(dirname "$0")/lib.sh"

LOCAL_CI=$REPO_ROOT/local-ci

# Creates $REPO, a git repository with a copy of local-ci, and $DEFS
make_repo() {
  REPO=$T_TMP/repo
  mkdir -p "$REPO/tests"
  cp "$LOCAL_CI" "$REPO/"
  cp "$REPO_ROOT/tests/embed_db_info.sh" "$REPO/tests/"
  echo original > "$REPO/tracked.txt"
  echo gone > "$REPO/deleted.txt"
  echo ignored.txt > "$REPO/.gitignore"
  git -C "$REPO" init -q
  git -C "$REPO" add -A
  git -C "$REPO" -c user.name=test -c user.email=test@example.invalid \
    -c commit.gpgsign=false commit -q -m init
  export T_MARK=$T_TMP/mark
  mkdir -p "$T_MARK"
  : > "$T_MARK/events"
  WORK=$T_TMP/work
  DEFS=$T_TMP/defs.sh
}

# Writes the task definitions (stdin) after the helpers the fake tasks use
defs() {
  cat > "$DEFS" << 'EOF'
# fake NAME RC SECONDS: logs start/end, prints a line and exits with RC
fake() {
  echo "$(date +%s%N) start $1" >> "$T_MARK/events"
  echo "output of $1"
  sleep "$3"
  echo "$(date +%s%N) end $1" >> "$T_MARK/events"
  return "$2"
}
EOF
  cat >> "$DEFS"
}

# run [ENV=VALUE...] -- ARGS...: runs local-ci in $REPO, sets OUT and RC
run() {
  local envs=()
  while [ $# -gt 0 ] && [ "$1" != -- ]; do
    envs+=("$1")
    shift
  done
  shift
  OUT=$(cd "$REPO" && env -u GITHUB_ACTIONS -u LOCAL_CI_STRICT -u LOCAL_CI_PG_BIN \
    LOCAL_CI_DIR="$WORK" LOCAL_CI_TASK_DEFS="$DEFS" ${envs[@]+"${envs[@]}"} \
    ./local-ci "$@" 2>&1)
  RC=$?
}

# Starts local-ci in the background with its own pid in $BG_PID
run_bg() {
  (cd "$REPO" && exec env -u GITHUB_ACTIONS LOCAL_CI_DIR="$WORK" \
    LOCAL_CI_TASK_DEFS="$DEFS" ./local-ci "$@" > "$T_TMP/bg.out" 2>&1) &
  BG_PID=$!
}

# wait_for FILE TEXT [SECONDS]
wait_for() {
  local i
  for ((i = 0; i < ${3:-30} * 10; i++)); do
    grep -qF -- "$2" "$1" 2> /dev/null && return 0
    sleep 0.1
  done
  fail "timed out waiting for [$2] in $1"
  return 1
}

# The real task list (-l does not run anything)
real_list() {
  env -u LOCAL_CI_TASK_DEFS LOCAL_CI_DIR="$T_TMP/work" "$LOCAL_CI" -l "$@" 2>&1 | awk '{print $1}'
}

process_running() { pgrep -f -- "^$1\$" > /dev/null; }

# The long sleeps of the fake tasks are "sleep 33.7N". Kill any that a failed
# test left behind (the pattern cannot match this shell's own command line).
kill_leftovers() { pkill -f '^sleep 33\.7[0-9]$' 2> /dev/null; }
trap 'kill_leftovers; rm -rf "$T_ROOT"' EXIT

# ---------------------------------------------------------------------------

l_help() {
  OUT=$("$LOCAL_CI" -h 2>&1)
  assert_eq "$?" 0 "exit status"
  assert_contains "$OUT" "--shard K/N"
  assert_contains "$OUT" "--no-esql"
}

l_unknown_option() {
  make_repo
  defs <<< 'add_task a "" fake a 0 0'
  run -- --bogus
  assert_eq "$RC" 2 "exit status"
  assert_contains "$OUT" "unknown option: --bogus"
}

l_option_without_value() {
  make_repo
  defs <<< 'add_task a "" fake a 0 0'
  run -- -j
  assert_eq "$RC" 2 "exit status"
  assert_contains "$OUT" "-j needs a value"
}

l_list_all_tasks() {
  make_repo
  defs << 'EOF'
add_task a "" fake a 0 0
add_task b "a" fake b 0 0
add_task c "b" fake c 0 0
add_task x "~a" fake x 0 0
EOF
  run -- -l
  assert_eq "$RC" 0 "exit status"
  assert_eq "$(awk '{print $1}' <<< "$OUT" | tr '\n' ' ')" "a b c x " "tasks"
  assert_contains "$(grep '^c ' <<< "$OUT")" " b" "dependency column"
}

l_pattern_adds_dependencies() {
  make_repo
  defs << 'EOF'
add_task a "" fake a 0 0
add_task b "a" fake b 0 0
add_task c "b" fake c 0 0
add_task x "~a" fake x 0 0
EOF
  run -- -l c
  assert_eq "$(awk '{print $1}' <<< "$OUT" | tr '\n' ' ')" "a b c " "selected"
  run -- -l 'x'
  assert_eq "$(awk '{print $1}' <<< "$OUT" | tr '\n' ' ')" "x " "~ dependency is not added"
}

l_exclude() {
  make_repo
  defs << 'EOF'
add_task a "" fake a 0 0
add_task b "a" fake b 0 0
add_task c "b" fake c 0 0
EOF
  run -- -l -x c
  assert_eq "$(awk '{print $1}' <<< "$OUT" | tr '\n' ' ')" "a b " "selected"
  # A dependency of a selected task is kept even if it matches -x
  run -- -l -x b c
  assert_eq "$(awk '{print $1}' <<< "$OUT" | tr '\n' ' ')" "a b c " "dependency kept"
}

l_no_matching_task() {
  make_repo
  defs <<< 'add_task a "" fake a 0 0'
  run -- zzz
  assert_eq "$RC" 2 "exit status"
  assert_contains "$OUT" "warning: no task matches 'zzz'"
  assert_contains "$OUT" "no task matches"
}

l_all_pass() {
  make_repo
  defs << 'EOF'
add_task a "" fake a 0 0
add_task b "" fake b 0 0
EOF
  run --
  assert_eq "$RC" 0 "exit status"
  assert_contains "$OUT" "PASS  a"
  assert_contains "$OUT" "PASS  b"
  assert_contains "$OUT" "pass: 2  fail: 0  skip: 0"
}

l_pass_fail_skip() {
  make_repo
  defs << 'EOF'
add_task ok "" fake ok 0 0
add_task ns:bad "" fake bad 1 0
add_task sk "" bash -c 'echo "tool not found"; exit 77'
EOF
  run --
  assert_eq "$RC" 1 "exit status"
  assert_contains "$OUT" "pass: 1  fail: 1  skip: 1"
  assert_contains "$OUT" "FAIL  ns:bad"
  assert_contains "$OUT" "$WORK/logs/ns_bad.log"
  assert_contains "$OUT" "SKIP  sk: tool not found" "last line of the log as reason"
  assert_contains "$(cat "$WORK/logs/ns_bad.log")" "output of bad"
}

l_failed_dependency_skips_dependents() {
  make_repo
  defs << 'EOF'
add_task bad "" fake bad 1 0
add_task c "bad" fake c 0 0
add_task d "c" fake d 0 0
add_task e "" fake e 0 0
EOF
  run --
  assert_eq "$RC" 1 "exit status"
  assert_contains "$OUT" "SKIP  c: bad did not pass"
  assert_contains "$OUT" "SKIP  d: c did not pass"
  assert_contains "$OUT" "PASS  e"
  assert_not_contains "$(cat "$T_MARK/events")" "start c" "c must not run"
  assert_not_contains "$(cat "$T_MARK/events")" "start d" "d must not run"
}

l_tilde_dependency_waits_but_does_not_block() {
  make_repo
  defs << 'EOF'
add_task bad "" fake bad 1 1
add_task x "~bad" fake x 0 0
EOF
  run --
  assert_eq "$RC" 1 "exit status"
  assert_contains "$OUT" "PASS  x"
  ended_before "$T_MARK/events" bad x || fail "x started before bad ended"
}

l_dependencies_run_first() {
  make_repo
  defs << 'EOF'
add_task a "" fake a 0 1
add_task b "a" fake b 0 0
add_task c "a b" fake c 0 0
EOF
  run --
  assert_eq "$RC" 0 "exit status"
  ended_before "$T_MARK/events" a b || fail "b started before a ended"
  ended_before "$T_MARK/events" b c || fail "c started before b ended"
}

l_strict_counts_skips_as_failures() {
  make_repo
  defs << 'EOF'
add_task ok "" fake ok 0 0
add_task sk "" bash -c 'exit 77'
EOF
  run --
  assert_eq "$RC" 0 "exit status without LOCAL_CI_STRICT"
  run LOCAL_CI_STRICT=1 --
  assert_eq "$RC" 1 "exit status with LOCAL_CI_STRICT=1"
  assert_contains "$OUT" "skipped tasks count as failures"
}

l_parallel_limit() {
  make_repo
  defs << 'EOF'
for n in a b c d e f; do add_task $n "" fake $n 0 1; done
EOF
  run -- -j 2
  assert_eq "$RC" 0 "exit status"
  assert_eq "$(max_concurrency "$T_MARK/events")" 2 "max concurrency with -j 2"
  : > "$T_MARK/events"
  run -- -j 6
  assert_eq "$(max_concurrency "$T_MARK/events")" 6 "max concurrency with -j 6"
  : > "$T_MARK/events"
  run -- -j 1
  assert_eq "$(max_concurrency "$T_MARK/events")" 1 "max concurrency with -j 1"
}

l_timeout() {
  make_repo
  defs << 'EOF'
add_task slow "" fake slow 0 33.71
add_task ok "" fake ok 0 0
EOF
  local start=$SECONDS
  run LOCAL_CI_TIMEOUT=2 --
  assert_eq "$RC" 1 "exit status"
  assert_contains "$OUT" "FAIL  slow"
  assert_contains "$OUT" "PASS  ok"
  assert_contains "$(cat "$WORK/logs/slow.log")" "timed out after 2s"
  [ $((SECONDS - start)) -lt 25 ] || fail "took $((SECONDS - start))s"
  sleep 1
  process_running "sleep 33.71" && fail "the timed out task is still running"
  assert_not_contains "$(cat "$T_MARK/events")" "end slow"
}

l_killed_task_counts_as_failure() {
  make_repo
  # Kill the task runner (the parent of timeout) so that no exit code is
  # written, as if the process had been killed from outside
  defs << 'EOF'
kill_runner() { kill -9 "$(awk '{print $4}' "/proc/$PPID/stat")"; sleep 2; }
add_task victim "" kill_runner
add_task ok "" fake ok 0 0
EOF
  run --
  assert_eq "$RC" 1 "exit status"
  assert_contains "$OUT" "FAIL  victim"
  assert_contains "$OUT" "PASS  ok"
  sleep 2
}

l_logs_in_github_actions() {
  make_repo
  defs << 'EOF'
add_task bad "" bash -c 'echo "::warning::from the task"; exit 1'
add_task good "" fake good 0 0
EOF
  run GITHUB_ACTIONS=true --
  assert_contains "$OUT" "::group::log of bad"
  assert_contains "$OUT" "::endgroup::"
  assert_contains "$OUT" "::error title=local-ci::bad failed"
  assert_contains "$OUT" " ::warning::from the task" "escaped workflow command"
  assert_not_contains "$OUT" $'\n::warning::' "unescaped workflow command"
  assert_not_contains "$OUT" "log of good"
  run --
  assert_not_contains "$OUT" "::group::" "outside GitHub Actions"
}

l_copies_the_work_tree() {
  make_repo
  echo modified > "$REPO/tracked.txt"
  echo new > "$REPO/untracked.txt"
  echo secret > "$REPO/ignored.txt"
  rm "$REPO/deleted.txt"
  defs << 'EOF'
show_tree() {
  local t k
  for k in sjis utf8 lint unit; do [ -d "$(tree_dir $k)" ] && echo "tree $k"; done
  t=$(tree_dir sjis)
  echo "tracked: $(cat "$t/tracked.txt")"
  ls -a "$t"
  grep '^DB_' "$t/tests/embed_db_info.sh"
}
add_task show "" show_tree
EOF
  run --
  assert_eq "$RC" 0 "exit status"
  local log
  log=$(cat "$WORK/logs/show.log")
  assert_contains "$log" "tree sjis"
  assert_contains "$log" "tree utf8"
  assert_contains "$log" "tree lint"
  assert_contains "$log" "tree unit"
  assert_contains "$log" "tracked: modified" "uncommitted change"
  assert_contains "$log" "untracked.txt"
  assert_not_contains "$log" "ignored.txt"
  assert_not_contains "$log" "deleted.txt"
  assert_contains "$log" 'DB_NAME=${LOCAL_CI_DB_NAME:-testdb}'
  assert_contains "$log" "DB_HOST=localhost"
  assert_not_contains "$log" "DB_PORT=5432" "the private server's port"
  assert_eq "$(cat "$REPO/tests/embed_db_info.sh")" "$(cat "$REPO_ROOT/tests/embed_db_info.sh")" \
    "the original embed_db_info.sh"
}

l_tasks_run_without_locale() {
  make_repo
  defs <<< 'add_task show "" bash -c '\''echo "LANG=[${LANG:-}] LC_ALL=[${LC_ALL:-}] LC_CTYPE=[${LC_CTYPE:-}]"'\'
  run LANG=ja_JP.UTF-8 LC_ALL=C LC_CTYPE=C --
  assert_contains "$(cat "$WORK/logs/show.log")" "LANG=[] LC_ALL=[] LC_CTYPE=[]"
}

l_second_run_is_locked_out() {
  make_repo
  defs <<< 'add_task slow "" fake slow 0 4'
  run_bg
  wait_for "$T_MARK/events" "start slow" || return
  run --
  assert_eq "$RC" 1 "exit status of the second run"
  assert_contains "$OUT" "another local-ci is running"
  wait "$BG_PID"
  assert_eq "$?" 0 "exit status of the first run"
}

l_interrupt_stops_tasks() {
  make_repo
  defs <<< 'add_task slow "" fake slow 0 33.72'
  run_bg
  wait_for "$T_MARK/events" "start slow" || return
  kill -INT "$BG_PID"
  wait "$BG_PID"
  assert_eq "$?" 130 "exit status"
  assert_contains "$(cat "$T_TMP/bg.out")" "interrupted"
  sleep 1
  process_running "sleep 33.72" && fail "the task is still running"
}

l_leftover_tasks_are_killed() {
  make_repo
  defs << 'EOF'
add_task slow "" fake slow 0 33.73
add_task quick "" fake quick 0 0
EOF
  run_bg slow
  wait_for "$T_MARK/events" "start slow" || return
  kill -KILL "$BG_PID"
  wait "$BG_PID" 2> /dev/null
  process_running "sleep 33.73" || fail "the task should outlive the killed run"
  run -- quick
  assert_eq "$RC" 0 "exit status of the next run"
  sleep 1
  process_running "sleep 33.73" && fail "the leftover task is still running"
}

l_clean() {
  make_repo
  defs <<< 'add_task a "" fake a 0 0'
  run --
  assert_file "$WORK/logs"
  run -- --clean
  assert_eq "$RC" 0 "exit status"
  assert_no_file "$WORK"
}

l_longest_tasks_start_first() {
  make_repo
  defs << 'EOF'
add_task a "" fake a 0 0
add_task b "" fake b 0 2
add_task c "" fake c 0 1
EOF
  run -- -j 1
  assert_eq "$(start_order "$T_MARK/events")" "a b c" "first run, no durations yet"
  # Durations are whole seconds and include up to 1s of polling
  assert_eq "$(awk '{print $1}' "$WORK/durations" | sort | tr '\n' ' ')" "a b c " "durations recorded"
  : > "$T_MARK/events"
  run -- -j 1
  assert_eq "$(start_order "$T_MARK/events")" "b c a" "second run, by last duration"
  # Durations of tasks that did not run are kept
  local b_before
  b_before=$(grep '^b ' "$WORK/durations")
  run -- -j 1 a
  assert_eq "$(grep '^b ' "$WORK/durations")" "$b_before" "duration of b after a run without b"
}

l_shard_rejects_bad_values() {
  make_repo
  defs <<< 'add_task a "" fake a 0 0'
  local v
  for v in 0/2 3/2 1/0 x 1 1/ /2 -1/2 1/2/3; do
    run -- --shard "$v"
    assert_eq "$RC" 2 "exit status for --shard $v"
  done
  run -- -s 1/1 -l
  assert_eq "$RC" 0 "-s 1/1"
}

l_shard_without_tasks() {
  make_repo
  defs << 'EOF'
add_task a "" fake a 0 0
add_task b "" fake b 0 0
EOF
  run -- --shard 3/3
  assert_eq "$RC" 0 "exit status"
  assert_contains "$OUT" "shard 3/3 has no tasks"
}

l_shard_with_fake_tasks() {
  make_repo
  defs << 'EOF'
add_task build:x "" fake build 0 0
for n in t1 t2 t3 t4 t5; do add_task $n build:x fake $n 0 0; done
EOF
  local one two
  run -- -l --shard 1/2
  one=$(awk '{print $1}' <<< "$OUT" | sort | tr '\n' ' ')
  run -- -l --shard 2/2
  two=$(awk '{print $1}' <<< "$OUT" | sort | tr '\n' ' ')
  assert_contains "$one" "build:x" "shard 1 has the build"
  assert_contains "$two" "build:x" "shard 2 has the build"
  assert_eq "$(echo "$one $two" | tr ' ' '\n' | grep '^t' | sort | tr '\n' ' ')" "t1 t2 t3 t4 t5 " "union"
  run -- --shard 2/2
  assert_eq "$RC" 0 "exit status"
  assert_eq "$(start_order "$T_MARK/events" | tr ' ' '\n' | grep '^t' | sort | tr '\n' ' ')" \
    "$(echo "$two" | tr ' ' '\n' | grep '^t' | sort | tr '\n' ' ')" "the shard runs what -l shows"
}

# Every selected task is in exactly one shard, with its dependencies
check_partition() {
  local n=$1 k all got dup t d
  shift
  all=$(real_list "$@" | grep -v '^build:\|^pg:' | sort)
  got=$(for ((k = 1; k <= n; k++)); do real_list --shard "$k/$n" "$@" | grep -v '^build:\|^pg:'; done | sort)
  dup=$(uniq -d <<< "$got")
  [ -z "$dup" ] || fail "N=$n $*: in more than one shard: $dup"
  assert_eq "$(uniq <<< "$got")" "$all" "N=$n $*: union of the shards"
  for ((k = 1; k <= n; k++)); do
    local listing
    listing=$(env -u LOCAL_CI_TASK_DEFS LOCAL_CI_DIR="$T_TMP/work" "$LOCAL_CI" -l --shard "$k/$n" "$@")
    while read -r t d; do
      for d in $d; do
        [ "${d:0:1}" = "~" ] && continue
        grep -q "^$d " <<< "$listing" || fail "N=$n k=$k: $t needs $d, which is missing"
      done
    done <<< "$listing"
  done
}

l_shard_partitions_the_real_tasks() {
  local n
  for n in 1 2 3 4 5; do
    check_partition $n
    check_partition $n 'test:sjis:*' 'nist:sjis:*' 'esql:sjis:*' unit:libcobj esql:examples smoke:spring-boot
    check_partition $n 'test:utf8:*' 'nist:utf8:*' 'esql:utf8:*'
  done
}

l_shard_is_deterministic() {
  local a b
  a=$(real_list --shard 2/3 'test:*' 'nist:*')
  b=$(LC_ALL=C real_list --shard 2/3 'test:*' 'nist:*')
  assert_eq "$b" "$a" "same shard with another locale"
  b=$(real_list --shard 2/3 'nist:*' 'test:*')
  assert_eq "$b" "$a" "same shard with the patterns in another order"
}

l_shard_is_balanced() {
  # LPT: the shards differ by at most the largest single estimate
  local fn n k loads max_task
  fn=$T_TMP/static.sh
  sed -n '/^static_duration() {/,/^}/p' "$LOCAL_CI" > "$fn"
  # shellcheck disable=SC1090
  . "$fn"
  max_task=$(for t in $(real_list 'test:*' 'nist:*' 'esql:*' 'unit:*' 'smoke:*'); do
    case $t in build:* | pg:*) ;; *) static_duration "$t" ;; esac
  done | sort -n | tail -n 1)
  for n in 2 3 4; do
    loads=$(for ((k = 1; k <= n; k++)); do
      local sum=0 t
      for t in $(real_list --shard "$k/$n" 'test:*' 'nist:*' 'esql:*' 'unit:*' 'smoke:*'); do
        case $t in build:* | pg:*) ;; *) sum=$((sum + $(static_duration "$t"))) ;; esac
      done
      echo $sum
    done | sort -n)
    [ $(($(tail -n 1 <<< "$loads") - $(head -n 1 <<< "$loads"))) -le "$max_task" ] ||
      fail "N=$n loads $(echo $loads) differ by more than $max_task"
  done
}

l_no_esql() {
  local list
  list=$(real_list --no-esql)
  assert_not_contains "$list" "esql:"
  assert_not_contains "$list" "pg:start"
  assert_contains "$list" "test:sjis:run"
  assert_contains "$(real_list)" "esql:sjis:esql-basic" "without --no-esql"
}

l_without_postgresql() {
  make_repo
  defs << 'EOF'
add_task pg:start "" do_pg_start
add_task esql:e1 "pg:start" fake e1 0 0
add_task t1 "" fake t1 0 0
EOF
  run LOCAL_CI_PG_BIN=none --
  assert_eq "$RC" 0 "exit status"
  assert_contains "$OUT" "PostgreSQL (initdb/pg_ctl) not found; the esql:* tasks will be skipped"
  assert_contains "$OUT" "SKIP  pg:start"
  assert_contains "$OUT" "SKIP  esql:e1: pg:start did not pass"
  assert_contains "$OUT" "PASS  t1"
  assert_not_contains "$OUT" "LOCAL_CI_PG_BIN=none" "no warning for the documented value"
  run LOCAL_CI_PG_BIN=none LOCAL_CI_STRICT=1 --
  assert_eq "$RC" 1 "exit status with LOCAL_CI_STRICT=1"
  run "LOCAL_CI_PG_BIN=$T_TMP/nonexistent" --
  assert_contains "$OUT" "warning: no initdb/pg_ctl in LOCAL_CI_PG_BIN=$T_TMP/nonexistent"
  run LOCAL_CI_PG_BIN=none -- --no-esql
  assert_eq "$RC" 0 "exit status with --no-esql"
  assert_not_contains "$OUT" "not found" "--no-esql does not warn"
  assert_not_contains "$OUT" "esql:e1"
  assert_contains "$OUT" "pass: 1  fail: 0  skip: 0"
}

for t in $(declare -F | awk '{print $3}' | grep '^l_'); do
  run_test "$t"
  kill_leftovers
done
finish
