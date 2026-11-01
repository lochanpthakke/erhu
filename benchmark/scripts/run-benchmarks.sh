#!/usr/bin/env bash
# The only supported way to run erhu's benchmarks on a phone (Git Bash on Windows).
#
#   benchmark/scripts/run-benchmarks.sh LABEL [extra `am instrument` arguments...]
#   benchmark/scripts/run-benchmarks.sh --recover LABEL   (after the PC side died mid-run: wait for the
#       phone's run to end, then move its results/traces to LABEL and clean the phone)
#   e.g. run-benchmarks.sh release-partial -e class com.erhu.erhu.benchmark.JourneyBenchmarks#albumsScroll
#
# Before: refuses to start unless the phone is connected over USB, idle (home screen or the
# benchmark app in front) and has plenty of free space; removes any leftover benchmark trace.
# During: a watchdog ON THE PHONE (device-watchdog.sh) stops the run and deletes its trace if the
# cable is pulled, the run dies, the trace grows too big or storage runs low.
# After (always, also on Ctrl+C or errors): stops the test, deletes the system trace, moves the
# Perfetto traces to the PC and deletes them from the phone, and reports the free space.
#
# Results: $BENCH_OUT/LABEL/ (default D:\android-toolchain\smoothness\LABEL): result.txt, traces/.
set -u
export MSYS_NO_PATHCONV=1

A=${ADB:-/d/android-toolchain/sdk/platform-tools/adb.exe}
HERE=$(cd "$(dirname "$0")" && pwd)
RECOVER=0
[ "${1:-}" = "--recover" ] && { RECOVER=1; shift; }
LABEL=${1:?usage: run-benchmarks.sh [--recover] LABEL [am instrument args...]}
shift
OUT=${BENCH_OUT:-/d/android-toolchain/smoothness}/$LABEL
TEST_PKG=com.erhu.erhu.benchmark
APP_PKG=com.lochanpthakke.empee3.bench
RUNNER=$TEST_PKG/androidx.test.runner.AndroidJUnitRunner
MEDIA=/sdcard/Android/media/$TEST_PKG
WATCHDOG=/data/local/tmp/erhu-bench-watchdog.sh
MIN_FREE_KB=10000000

fail() { echo "run-benchmarks: $*" >&2; exit 1; }
# Moves the phone's saved traces/results to $OUT/traces, deleting them from the phone only when every
# file arrived on the PC (otherwise they stay there and the problem is reported).
move_traces() {
    "$A" shell ls "$MEDIA" > /dev/null 2>&1 || return 0
    local on_phone on_pc
    on_phone=$("$A" shell ls "$MEDIA" | grep -c .)
    "$A" pull "$MEDIA/." "$(cygpath -w "$OUT/traces")" > /dev/null
    on_pc=$(ls "$OUT/traces" | grep -c .)
    if [ "$on_pc" -ge "$on_phone" ]; then
        "$A" shell rm -rf "$MEDIA"
        echo "run-benchmarks: moved $on_phone files to $OUT/traces"
    else
        echo "run-benchmarks: only $on_pc of $on_phone files copied; leaving them on the phone ($MEDIA)" >&2
    fi
}
free_kb() { "$A" shell df -k /data | tail -1 | awk '{print $4}' | tr -d '\r'; }

if [ "$RECOVER" = 1 ]; then
    [ "$("$A" get-state 2>/dev/null | tr -d '\r')" = device ] || fail "no phone connected"
    while "$A" shell pidof "$TEST_PKG" > /dev/null; do echo "run-benchmarks: waiting for the phone's run to end"; sleep 30; done
    mkdir -p "$OUT/traces"
    "$A" shell sh "$WATCHDOG" clean | tr -d '\r'
    move_traces
    "$A" shell cat /data/local/tmp/erhu-bench-watchdog.log 2>/dev/null | tr -d '\r' > "$OUT/watchdog.log"
    "$A" shell rm -f /data/local/tmp/erhu-bench-watchdog.log
    "$A" shell am force-stop "$APP_PKG"
    echo "run-benchmarks: recovered into $OUT (results: traces/*benchmarkData.json); phone free space $(( $(free_kb) / 1000000 )) GB"
    exit 0
fi

# ---- before ----
[ "$("$A" get-state 2>/dev/null | tr -d '\r')" = device ] || fail "no phone connected"
"$A" shell dumpsys battery | grep -q "USB powered: true" || fail "phone is not on USB"
focus=$("$A" shell dumpsys window | grep mCurrentFocus | tr -d '\r')
echo "$focus" | grep -qE "LauncherActivity|$APP_PKG" || fail "phone is in use ($focus); not starting"
"$A" shell pm list packages | grep -q "package:$APP_PKG$" || fail "$APP_PKG is not installed (see benchmark/README.md)"
"$A" shell pm list packages | grep -q "package:$TEST_PKG$" || fail "$TEST_PKG is not installed"
[ -e "$OUT" ] && fail "$OUT already exists; choose another label"

# The watchdog script must have Unix line endings to run on the phone.
tr -d '\r' < "$HERE/device-watchdog.sh" > "$HERE/.device-watchdog.lf" &&
    "$A" push "$(cygpath -w "$HERE/.device-watchdog.lf")" "$WATCHDOG" > /dev/null &&
    "$A" shell chmod 755 "$WATCHDOG" || fail "could not install the watchdog"
rm -f "$HERE/.device-watchdog.lf"
"$A" shell sh "$WATCHDOG" clean | tr -d '\r'
[ "$(free_kb)" -ge "$MIN_FREE_KB" ] || fail "less than $((MIN_FREE_KB / 1000000)) GB free on the phone"

mkdir -p "$OUT/traces"
"$A" shell dumpsys battery | grep -E "^  (level|temperature)" | tr -d '\r' > "$OUT/conditions.txt"

# ---- after (always) ----
cleanup() {
    trap - EXIT INT TERM HUP
    echo "run-benchmarks: cleaning up"
    "$A" shell touch /data/local/tmp/erhu-bench-watchdog.stop 2>/dev/null
    "$A" shell am force-stop "$TEST_PKG" 2>/dev/null
    "$A" shell am force-stop "$APP_PKG" 2>/dev/null
    "$A" shell sh "$WATCHDOG" clean 2>/dev/null | tr -d '\r'
    move_traces
    "$A" shell cat /data/local/tmp/erhu-bench-watchdog.log 2>/dev/null | tr -d '\r' > "$OUT/watchdog.log"
    "$A" shell rm -f /data/local/tmp/erhu-bench-watchdog.log
    "$A" shell dumpsys battery | grep -E "^  temperature" | tr -d '\r' >> "$OUT/conditions.txt"
    echo "run-benchmarks: phone free space $(( $(free_kb) / 1000000 )) GB; results in $OUT"
}
trap cleanup EXIT INT TERM HUP

# ---- during ----
"$A" shell "setsid nohup sh $WATCHDOG > /dev/null 2>&1 < /dev/null &"
sleep 1
# Filtered here, not with pgrep on the phone: a pattern in the adb command line would match itself.
"$A" shell ps -A -o ARGS | tr -d '\r' | grep -q "^sh $WATCHDOG$" || fail "the watchdog did not start"
echo "run-benchmarks: running $LABEL (watchdog active on the phone)"
"$A" shell am instrument -w "$@" "$RUNNER" > "$OUT/result.txt" 2>&1
tail -3 "$OUT/result.txt"
