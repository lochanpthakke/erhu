# Liquid Glass overflow-menu material

Date: 2026-10-31. This is a visual refinement of the existing unified menu system. Actions, labels, contextual groups, row dimensions, typography, enabled conditions, confirmation callbacks, scrolling and dismissal behavior are preserved.

## Material and references

References 1–2 establish the existing contextual layout and hierarchy. Reference 3 guides the translucent, softly glossy surface, thin luminous rim and ambient artwork color. The previous near-opaque neutral fill is replaced by the existing design-system `glassPanel` renderer at its Strong level: translucent graded fill, upper sheen, corner reflection, inner rim, lower depth and outside-only soft shadow. A transparent Material surface carries the content; it no longer paints an opaque dialog-like background over the glass.

The graded base is 80% opaque in dark themes and 74% in light themes, with restrained additional highlights. These values describe the base layer, not the combined opacity of every point on the surface. The neutral anchor is achromatic so dark brown artwork stays warm and neutral artwork does not acquire a blue cast. Dominant/accent colors are mixed and desaturated before tinting the fill, reflection and rim. Ordinary text remains high-contrast theme-appropriate ink. Headings/secondary text use 90% ink, and destructive ink is graded toward the foreground while retaining the theme's error hue.

The existing 28 dp corners, content-dependent 240–320 dp width, at least 52 dp rows, 20 dp icons, section padding/dividers and height-limited scrolling remain. Transparent window padding increases to 16 dp to contain the existing glass shadow and spring movement. The existing position provider still handles trigger anchoring, RTL and screen edges.

True cross-window backdrop blur is not claimed. The safe lightweight approximation uses the player's existing layered glass drawing with `backdrop = null`, so there is no incorrect cross-root backdrop sampling, whole-screen capture, animated blur shader or text blur. Contrast reduction through the tint and restrained reflections separates the background visually; background edges are not optically blurred.

## Playback tint source and animation

`rememberPlaybackArtworkColors` observes only the current song's audio/artwork URI pair from the existing stable playback state. Queue changes and playback position do not select another palette. It first reuses an already loaded bitmap via `ArtworkLoader.peek`, then the existing cached loader if needed, and passes it to the existing `DominantColorExtractor` off the main thread. No new color-extraction algorithm, repository, dependency or playback operation was added.

One shell-level state container is provided through `LocalPlaybackArtworkColors` to the full navigation/player tree. The shell passes the stable container without reading its palette during composition; open menus are the readers. A browsed album's focused-artwork/theme input is not used to select the playback palette. With a valid playback palette, menu color grading ignores the browsed theme surface. Missing artwork/no current track produces the theme-neutral fallback. The observer is cancelled with composition and disabled when the user disables glass. `collectLatest` prevents late work for a previous track from publishing stale colors.

`LocalMenuGlassTokens` retains the theme/user glass setting for popup menus even inside the player's intentionally plain `WithoutGlass` scope. Only the popup restores those material tokens; the player itself remains unchanged. The menu overrides the page-derived tint before drawing, so a browsed album cannot leak its tint into the playback-colored material. Glass disabled still gives the solid neutral fallback.

Fill, reflection and edge colors cross-fade together through existing `Motion.fade` timing (300 ms). If a new track's artwork is still loading, the fallback can appear during that same smooth transition. Color animation lives inside the surface; it does not observe playback position or continuously animate once settled. Existing anchored spring scale, short fade, positional settling, small overshoot and reverse exit remain unchanged.

## Files changed

| File | Change |
|---|---|
| `app/.../MainScaffold.kt` | Create/provide the playback-only palette state; no commands or navigation changes |
| `core/designsystem/.../theme/PlaybackArtworkColors.kt` | New presentation observer/local using existing artwork loading and extraction |
| `core/designsystem/.../theme/Glass.kt` | Menu material local |
| `core/designsystem/.../theme/EmPee3Theme.kt` | Provide menu tokens from the existing user/theme glass setting |
| `core/designsystem/.../component/GlassMenu.kt` | Use the Liquid Glass surface and playback palette; shadow clearance |
| `core/designsystem/.../component/LiquidMenuSurface.kt` | Shared color grading and existing glass-renderer integration; animated tint |
| `core/designsystem/.../component/GlassMenuItem.kt` | Readable heading/secondary/destructive ink |
| `core/designsystem/src/test/.../GlassMenuTest.kt` | Heading contrast expectation follows the new ink |
| `core/designsystem/src/test/.../LiquidMenuMaterialTest.kt` | Warm/dark-warm/cool/green/neutral grading, complete layered contrast, current-track identity and browsed-theme independence |
| `docs/liquid-glass-overflow-menus.md` | This report |

All eight screen-level overflow-menu implementations are unchanged byte-for-byte from this task's starting state. The shared material covers Home/shared track rows, Library, search, album and artist details/tracks, Now Playing, playlist details/tracks, Smart Playlists, lyrics options, Transition Studio and its editor. VYBE and featured cards have no separate existing three-dot implementation. Ordinary form selectors and the dedicated live-cover control keep their existing roles.

## Validation and limits

Executed test counts, build identities and formatting results follow. Runtime device/visual/performance checks are separate from JVM color/geometry checks and compiled interaction-test coverage. The prior preference to skip device checks is retained; no device validation or installation is claimed.


## Final results

- **248 unit/regression tests passed**, zero failures/errors/skips: design system 52, library 195, app 1. New coverage verifies warm/dark-warm/cool/green/neutral hues, layered gloss readability (ordinary, heading and destructive ink), fallback/glass-off behavior, playback identity and independence from the browsed theme. Existing edge/RTL geometry and bounded spring tests remain green.
- ARM64 debug app and Android interaction-test APK assembly passed. The existing interaction tests compiled; they were not executed on a device.
- KtLint check passed on all nine changed Kotlin files; `git diff --check` passed. Source hashes match the verified source. No dependencies, version/signing configuration, database schema or business-logic files changed.
- Detekt retains **7 design-system, 87 library and 14 app findings**, with no finding in the changed menu/palette/shell files. The combined command exits nonzero solely for these existing findings. No rule or baseline was weakened.
- All eight screen-level menu files match their initial hashes. All unrelated initial working-tree changes are preserved. Nothing was staged, committed or pushed.
- Device checks were skipped under the user's earlier preference. No phone UI interaction, music playback/queue changes, new app installation, on-device visual inspection or performance benchmark was performed for this redesign. The previously installed debug build remains untouched.

Debug APK: `outputs/liquid-menus/empee3-liquid-menus-debug-arm64.apk` in the chat workspace; **63,909,668 bytes**, SHA-256 `99d01e755881c5f1290c55e62ebb190c369e5a6349b41df90139086a876ca323`. Package `com.lochanpthakke.empee3`, ARM64-only; existing signing certificate verified. This is a debug build, not a release/R8 build, and it was not installed.

Remaining limitations: physical-device gloss/appearance, live track-change tint motion, scrolling/playback frame times and OEM window/inset behavior were not measured or visually inspected. Computed contrast and URI-source tests complement the compiled UI tests; they do not establish an on-device visual pass. The translucent layered approximation intentionally has no true cross-window backdrop blur. Glass disabled retains the solid fallback. If new artwork is loading, the neutral fallback can cross-fade before its palette becomes available.


## Subsequent installation — 2026-10-31

The user requested installation. A fresh full backup was validated at `D:\android-toolchain\dbbackup\pre-liquid-menus-20261031-010418` (71 files, SQLite quick_check ok, schema 14, 208 songs and 3 artwork records). The already installed debug app allowed a direct backup without an intermediate build. The Liquid Glass debug APK was installed in place with `adb install -r`: **Success**. Its installed hash matches `99d01e755881c5f1290c55e62ebb190c369e5a6349b41df90139086a876ca323`.

This supersedes the earlier not-installed status. No app data was cleared, no artwork was removed or compressed, and the real app was never uninstalled. The app was left unopened; no menu/device tests were run.
