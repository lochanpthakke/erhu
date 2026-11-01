# erhu

Android local music player built with Kotlin, Jetpack Compose, Media3, Room and DataStore.

The project owner and source namespace are **erhu** (`com.erhu.erhu`). The installed
application ID remains `com.lochanpthakke.empee3` so existing installations receive
an in-place update with their library, preferences and playback data intact.

## Build

Use JDK 21 and the configured Android SDK:

```powershell
.\gradlew.bat :app:assembleRelease -PtargetAbi=arm64-v8a
.\gradlew.bat testDebugUnitTest
```

Signing configuration is unchanged. See [identity migration](docs/identity-migration.md)
for compatibility identifiers and external repository links awaiting a confirmed replacement.
