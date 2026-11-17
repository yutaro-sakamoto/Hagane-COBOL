#!/bin/bash
#
# run-tests.sh: run several test suites of Hagane COBOL on Windows (Git Bash),
# a few at a time, and report which ones failed. The Windows CI runs each
# group of its tests with this script in one job.
#
# Usage: win/run-tests.sh [-P N] [-j N] ITEM...
#
#   -P N   number of items that run at the same time (default: 2)
#   -j N   value passed to -j of each autotest suite (default: 4)
#
#   ITEM is one of
#     SUITE           an autotest suite in tests/ (run, misc, esql-basic, ...)
#     nist:MODULE     a NIST COBOL85 module in tests/cobol85/ (nist:NC, ...)
#     A+B+...         several items run one after another in the same slot.
#                     Use it for the ESQL suites, which share one database.
#
#   Items start in the order given, so put the long ones first.
#
# Before running it, install Hagane COBOL (win/make-install.ps1), put the
# generated test scripts, atconfig and atlocal in tests/ and set CLASSPATH,
# as .github/workflows/windows-test.yml does. The script puts the installed
# bin directory (COB4J_INSTALL_DIR, default C:/opensourcecobol4j) on PATH,
# because the NIST tests run cobj from PATH.
#
# A slot that runs several items reports each failed item on its own line.
#
# The output of each item goes to tests/win-logs/<item>.log. In GitHub
# Actions the logs of the failed items are also printed in collapsible groups.

set -u

PARALLEL=2
AT_JOBS=4
while getopts P:j: opt; do
  case $opt in
  P) PARALLEL=$OPTARG ;;
  j) AT_JOBS=$OPTARG ;;
  *) exit 2 ;;
  esac
done
shift $((OPTIND - 1))
if [ $# -eq 0 ]; then
  sed -n '3,/^$/p' "$0" | sed -e 's/^# \{0,1\}//'
  exit 2
fi

TOP=$(cd "$(dirname "$0")/.." && pwd)
LOGDIR=$TOP/tests/win-logs
rm -rf "$LOGDIR"
mkdir -p "$LOGDIR"

# Programs built on Windows print CRLF; the expected outputs use LF
mkdir -p "$LOGDIR/diff-wrapper"
printf '%s\n' '#!/bin/bash' 'exec /usr/bin/diff --strip-trailing-cr "$@"' \
  > "$LOGDIR/diff-wrapper/diff"
chmod +x "$LOGDIR/diff-wrapper/diff"
export PATH="$LOGDIR/diff-wrapper:$PATH"
# tests/cobol85/report.sh runs cobj from PATH (autotest uses COBJ in atlocal)
INSTALL_DIR=${COB4J_INSTALL_DIR:-C:/opensourcecobol4j}
if [ -d "$INSTALL_DIR/bin" ]; then
  export PATH="$PATH:$(cd "$INSTALL_DIR/bin" && pwd):$(cd "$INSTALL_DIR/config" 2> /dev/null && pwd)"
fi

stamp() { date +%H:%M:%S; }
log_of() { echo "$LOGDIR/$(echo "$1" | tr ':+' '_-').log"; }

run_item() {
  case $1 in
  nist:*)
    (
      cd "$TOP/tests/cobol85/${1#nist:}" || exit 1
      sh ../report.sh || exit 1
      cat report.txt
      grep "Successfully executed:" report.txt | grep -q "100.00%"
    )
    ;;
  *)
    (cd "$TOP/tests" && "./$1" -j"$AT_JOBS")
    ;;
  esac
}

# Runs the items of one slot one after another. Writes the exit code of the
# slot to <log>.rc.
run_slot() {
  local slot=$1 log rc=0 item start
  log=$(log_of "$slot")
  start=$SECONDS
  IFS=+ read -r -a items <<< "$slot"
  for item in "${items[@]}"; do
    echo "=== $item" >> "$log"
    if ! run_item "$item" >> "$log" 2>&1; then
      rc=1
      [ "$item" = "$slot" ] || echo "[$(stamp)] FAIL  $item (in $slot)"
    fi
  done
  echo "$rc" > "$log.rc"
  if [ $rc -eq 0 ]; then
    echo "[$(stamp)] PASS  $slot ($((SECONDS - start))s)"
  else
    echo "[$(stamp)] FAIL  $slot ($((SECONDS - start))s) log: $log"
  fi
}

running=0
for slot in "$@"; do
  if [ $running -ge "$PARALLEL" ]; then
    wait -n
    running=$((running - 1))
  fi
  echo "[$(stamp)] START $slot"
  run_slot "$slot" &
  running=$((running + 1))
done
wait

failed=()
for slot in "$@"; do
  [ "$(cat "$(log_of "$slot").rc" 2> /dev/null)" = 0 ] || failed+=("$slot")
done

echo
echo "pass: $(($# - ${#failed[@]}))  fail: ${#failed[@]}  (total ${SECONDS}s)"
for slot in "${failed[@]}"; do
  echo "  FAIL  $slot  $(log_of "$slot")"
  if [ "${GITHUB_ACTIONS:-}" = true ]; then
    echo "::group::log of $slot"
    # A line starting with :: would be read as a workflow command
    tail -n 500 "$(log_of "$slot")" | sed 's/^::/ ::/'
    echo "::endgroup::"
    echo "::error title=windows-test::$slot failed"
  fi
done
[ ${#failed[@]} -eq 0 ]
