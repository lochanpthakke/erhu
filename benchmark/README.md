# erhu benchmarks

Macrobenchmarks of everyday journeys (startup, library scrolling, album detail, player
expand/collapse, track swiping, lyrics, Library/VYBE switching). They measure frame timing
(`frameDurationCpuMs`, `frameOverrunMs`) and startup (`timeToInitialDisplayMs`,
`timeToFullDisplayMs`). Each journey's starting screen is set up untimed. Playback is never
started.

They run against **`com.lochanpthakke.empee3.bench`** only, a separate package that can't
replace the real app or the `.perf` copy:

| App build | Package | Use |
|---|---|---|
| `:app:assembleBenchmark` | `.bench` | release code and R8, profileable, debug-signed |
| `:app:assembleDebug -PbenchApp` | `.bench` | debug build of the same package, for seeding data and debug comparisons |

## 1. Seed the `.bench` package with a library (once)

A fresh install has no library. Copy one from a debuggable copy of the app, such as `.perf`:

```sh
A=/d/android-toolchain/sdk/platform-tools/adb.exe
export MSYS_NO_PATHCONV=1
$A exec-out run-as com.lochanpthakke.empee3.perf tar -cf - databases files shared_prefs no_backup > seed.tar
./gradlew :app:assembleDebug -PbenchApp
$A install -r app/build/outputs/apk/debug/app-debug.apk
$A push seed.tar /data/local/tmp/bench-seed.tar && $A shell chmod 644 /data/local/tmp/bench-seed.tar
$A shell run-as com.lochanpthakke.empee3.bench tar -xf /data/local/tmp/bench-seed.tar
$A shell rm /data/local/tmp/bench-seed.tar
$A shell pm grant com.lochanpthakke.empee3.bench android.permission.READ_MEDIA_AUDIO
$A shell appops set com.lochanpthakke.empee3.bench MANAGE_EXTERNAL_STORAGE allow
```

Installing the benchmark build over it with `install -r` keeps this data, because both builds
are signed with the debug key.

## 2. Build and install

Don't use `connectedBenchmarkAndroidTest`. It uninstalls the app afterwards, which discards the
seeded library. Install with adb instead:

```sh
./gradlew :app:assembleBenchmark :benchmark:assembleBenchmark
$A install -r app/build/outputs/apk/benchmark/app-benchmark.apk
$A install -r benchmark/build/outputs/apk/benchmark/benchmark-benchmark.apk
```

## 3. Run: only through `scripts/run-benchmarks.sh`

Never run `am instrument` directly. Stopping a run early leaves the system trace it started
still recording. On 2026-10-17 a forgotten `perfetto --background-wait` session wrote 21 GB
overnight and filled the phone. The runner prevents that:

```sh
benchmark/scripts/run-benchmarks.sh LABEL [-e class ...JourneyBenchmarks#albumsScroll] [-e compilation none|partial|full]
```

- **Before starting**, it checks that the phone is on USB, idle (home screen, unlocked) and has at
  least 10 GB free. It removes any leftover benchmark trace and helper processes.
- **During the run**, `scripts/device-watchdog.sh` runs on the phone, detached from adb, so it
  works with the cable out. It stops the run and deletes its trace within seconds if any of
  these happen:
  - USB is unplugged;
  - the test process dies;
  - the trace file passes 3 GB;
  - free space drops below 6 GB;
  - the run passes 3 hours.
  It only touches the benchmark's own trace (`trace_output.pb`), never other perfetto sessions.
- **Afterwards**, even on Ctrl+C or an error, it stops the test, kills the trace writer, the script
  runners and the trace processor, and deletes `trace_output.pb`. It moves the Perfetto traces
  (20–70 MB each) to the PC and deletes them from the phone, then reports the free space.
- **Output:** `D:\android-toolchain\smoothness\LABEL\` holds `result.txt`, `traces/`,
  `watchdog.log` and `conditions.txt`.
- **Debug build comparison:** install the `-PbenchApp` debug app and
  `:benchmark:assembleDebug -PbenchApp`'s test APK, then pass
  `-e compilation ignore -e androidx.benchmark.suppressErrors DEBUGGABLE`.

Compare runs only under the same conditions: the same build type, compilation mode,
theme/glass setting and library, with similar battery temperature. The display runs at
120 Hz, a frame budget of about 8.3 ms.

## Frame diagnostics in debug builds

Debug builds log slow frames with what the app was doing (JankStats, `perf/FrameDiagnostics.kt`):

```sh
$A logcat -s Smoothness
```

```
jank 41.3 ms overrun 33.0 ms | artwork=decoding screen=library-albums scroll=fling
summary frames 612 janky 9 worst 41.3 ms | screen=library-albums scroll=fling
```

Labels come from `Interactions` (`core:common`). The logs never include song titles, file
names, ids or other library contents.
