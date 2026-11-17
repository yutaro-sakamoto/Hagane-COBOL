# Small test helpers for the tests of the test runners (local-ci and
# win/run-tests.sh). Sourced by the *.test.sh files.
#
# A test is a shell function. run_test runs it and counts it as failed if
# any assertion in it failed. Each test gets an empty directory in $T_TMP.

set -u

REPO_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
T_ROOT=$(mktemp -d "${TMPDIR:-/tmp}/ci-tests.XXXXXX")
trap 'rm -rf "$T_ROOT"' EXIT

T_PASSED=0
T_FAILED=0
T_FAILED_NAMES=()
T_CURRENT=
T_CURRENT_FAILED=0
T_FILTER=${T_FILTER:-}

fail() {
  T_CURRENT_FAILED=1
  printf '    FAIL: %s\n' "$*"
}

assert_eq() {
  # assert_eq ACTUAL EXPECTED [MESSAGE]
  [ "$1" = "$2" ] || fail "${3:-value}: expected [$2], got [$1]"
}

assert_contains() {
  # assert_contains TEXT NEEDLE [MESSAGE]
  case $1 in
  *"$2"*) ;;
  *) fail "${3:-output} does not contain [$2]"$'\n'"$(sed 's/^/      | /' <<< "$1")" ;;
  esac
}

assert_not_contains() {
  case $1 in
  *"$2"*) fail "${3:-output} contains [$2]"$'\n'"$(sed 's/^/      | /' <<< "$1")" ;;
  esac
}

assert_file() { [ -e "$1" ] || fail "${2:-file} $1 does not exist"; }
assert_no_file() { [ ! -e "$1" ] || fail "${2:-file} $1 exists"; }

# Lines matching a regex, in order, joined by spaces
lines_matching() { grep -E -- "$2" <<< "$1" | tr '\n' ' ' | sed 's/ $//'; }

# Event log written by the fake tasks: "<nanoseconds> start|end <name>"
# Prints the largest number of names that were running at the same time.
max_concurrency() {
  sort -n "$1" | awk '$2 == "start" { n++; if (n > m) m = n } $2 == "end" { n-- } END { print m + 0 }'
}

# Prints the names in the order they started
start_order() { sort -n "$1" | awk '$2 == "start" { printf "%s%s", sep, $3; sep = " " } END { print "" }'; }

# Succeeds if <first> ended before <second> started
ended_before() {
  local e s
  e=$(awk -v n="$2" '$2 == "end" && $3 == n { print $1 }' "$1")
  s=$(awk -v n="$3" '$2 == "start" && $3 == n { print $1 }' "$1")
  [ -n "$e" ] && [ -n "$s" ] && [ "$e" -le "$s" ]
}

run_test() {
  local name=$1
  if [ -n "$T_FILTER" ]; then
    case $name in
    $T_FILTER) ;;
    *) return 0 ;;
    esac
  fi
  T_CURRENT=$name
  T_CURRENT_FAILED=0
  T_TMP=$T_ROOT/$name
  mkdir -p "$T_TMP"
  "$name"
  if [ $T_CURRENT_FAILED -eq 0 ]; then
    T_PASSED=$((T_PASSED + 1))
    echo "ok - $name"
  else
    T_FAILED=$((T_FAILED + 1))
    T_FAILED_NAMES+=("$name")
    echo "not ok - $name"
  fi
}

finish() {
  echo
  echo "passed: $T_PASSED  failed: $T_FAILED"
  local n
  for n in "${T_FAILED_NAMES[@]}"; do
    echo "  failed: $n"
  done
  [ "$T_FAILED" -eq 0 ]
}
