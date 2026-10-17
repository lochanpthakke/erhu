#!/system/bin/sh
# Runs ON THE PHONE during a benchmark run (started by run-benchmarks.sh, detached from adb so
# it keeps running if the USB cable is pulled). It ends the run and removes its system trace when:
#   - USB is unplugged (nobody is supervising: stop driving the phone's UI),
#   - the benchmark process has gone (finished, crashed or force-stopped) but its trace writer remains,
#   - the trace file grows past MAX_TRACE_KB, the saved traces past MAX_MEDIA_KB, or free space
#     falls below MIN_FREE_KB,
#   - MAX_RUN_SECONDS have passed.
# Why: force-stopping a Macrobenchmark run leaves `perfetto --background-wait` writing
# /data/misc/perfetto-traces/trace_output.pb until the storage is full (21 GB on 2026-10-17).
# Only that writer is touched; other perfetto sessions (the system's own) are left alone.

TEST_PKG=com.lochanpthakke.empee3.benchmark
APP_PKG=com.lochanpthakke.empee3.bench
TRACE=/data/misc/perfetto-traces/trace_output.pb
LOG=/data/local/tmp/empee3-bench-watchdog.log
STOP_FILE=/data/local/tmp/empee3-bench-watchdog.stop
# Sizes in KB: the phone's shell has 32-bit arithmetic (bytes of a multi-GB file overflow it).
MAX_TRACE_KB=3000000
MIN_FREE_KB=6000000
# Per-iteration traces collect in the test app's media folder until the PC moves them.
MEDIA=/sdcard/Android/media/$TEST_PKG
MAX_MEDIA_KB=4000000
MAX_RUN_SECONDS=10800
POLL_SECONDS=3
# The test process may take a while to appear after `am instrument` starts.
START_GRACE_SECONDS=90

log() { echo "$(date '+%H:%M:%S') $*" >> "$LOG"; }

writers() { pgrep -f "perfetto.*-o $TRACE" ; }

# Macrobenchmark runs its shell commands (including the trace start) through shellWrapper.sh
# scripts kept in its media folder; a killed run can leave them waiting.
wrappers() { pgrep -f "shellWrapper.sh .*$TEST_PKG" ; }

# Macrobenchmark's on-device trace processor (an HTTP server it starts to read traces).
processors() { pgrep -f "trace_processor_shell .*--http-port" ; }

clean_trace() {
    for pid in $(wrappers); do kill "$pid" 2>/dev/null; done
    for pid in $(writers); do kill "$pid" 2>/dev/null; done
    for pid in $(processors); do kill "$pid" 2>/dev/null; done
    sleep 1
    for pid in $(writers); do kill -9 "$pid" 2>/dev/null; done
    rm -f "$TRACE"
}

stop_run() {
    log "stopping run: $1"
    am force-stop "$TEST_PKG"
    am force-stop "$APP_PKG"
    clean_trace
    input keyevent KEYCODE_HOME
    log "stopped"
    exit 0
}

# `device-watchdog.sh clean`: remove a leftover benchmark trace now (used by run-benchmarks.sh).
# Kept in this file so the pattern below never appears in an `adb shell` command line, where
# pgrep -f would match that shell itself.
if [ "$1" = "clean" ]; then
    left=$(writers | wc -l)
    stale=$(($(wrappers | wc -l) + $(processors | wc -l)))
    clean_trace
    echo "removed benchmark trace writers: $left, leftover helper processes: $stale"
    exit 0
fi

rm -f "$STOP_FILE"
start=$(date +%s)
seen=0
gone=0
log "watchdog started (pid $$)"
while true; do
    sleep "$POLL_SECONDS"
    now=$(date +%s)
    [ -f "$STOP_FILE" ] && { log "asked to stop"; rm -f "$STOP_FILE"; exit 0; }
    dumpsys battery | grep -q "USB powered: true" || stop_run "USB unplugged"
    [ $((now - start)) -gt "$MAX_RUN_SECONDS" ] && stop_run "run longer than ${MAX_RUN_SECONDS}s"
    if [ -f "$TRACE" ]; then
        size_kb=$(du -k "$TRACE" 2>/dev/null | cut -f1)
        [ "${size_kb:-0}" -gt "$MAX_TRACE_KB" ] && stop_run "trace file at ${size_kb} KB"
    fi
    media_kb=$(du -sk "$MEDIA" 2>/dev/null | cut -f1)
    [ "${media_kb:-0}" -gt "$MAX_MEDIA_KB" ] && stop_run "saved traces at ${media_kb} KB"
    free_kb=$(df -k /data | tail -1 | awk '{print $4}')
    [ "$free_kb" -lt "$MIN_FREE_KB" ] && stop_run "free space down to ${free_kb} KB"
    if pidof "$TEST_PKG" > /dev/null; then
        seen=1
        gone=0
    elif [ "$seen" = 1 ] || [ $((now - start)) -gt "$START_GRACE_SECONDS" ]; then
        gone=$((gone + 1))
        # Gone for three polls in a row: the run is over (or was killed). Leave nothing behind.
        if [ "$gone" -ge 3 ]; then
            if [ -n "$(writers)" ] || [ -f "$TRACE" ]; then
                log "benchmark gone, removing its leftover trace"
                clean_trace
            fi
            log "benchmark finished, watchdog exiting"
            exit 0
        fi
    fi
done
