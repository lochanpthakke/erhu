# Unified glass overflow menus

## Result and scope

All eight existing three-dot overflow implementations now use the same design-system menu. The screenshot informed the neutral frosted surface, rounded corners, spacing and quiet border. Sequential frames from the 18.32-second reference video informed the anchored expansion, one small overshoot, settling, grouped player actions and dismissal. Reference video frames were inspected; no claim of a completed on-device visual review is made here.

| Menu implementation | Screens covered | Existing actions retained |
|---|---|---|
| `LibraryScreen.kt` / `LibrarySongRow` | Library tracks, Home recent tracks, search, album tracks, artist tracks, playlist tracks and Smart Playlists | Play next, add to queue, radio, add to playlist, fix/view metadata, conditional remove from playlist |
| `AlbumDetailScreen.kt` | Album detail overflow | Play next, queue, radio, tag repair, online/local cover selection, Transition Studio |
| `ArtistDetailScreen.kt` | Artist detail overflow | Play next, queue, radio, tag repair, online/local artist photo selection |
| `NowPlayingMenu.kt` | Now Playing | Track information/audio details; bookmarks/loop, playlist and conditional hidden state; conditional radio/refresh, queue, visualizer and EQ |
| `PlaylistDetailScreen.kt` | Playlist detail overflow | Edit and delete, with the existing delete confirmation |
| `OnlineLyricsUi.kt` | Lyrics source overflow | Conditional download/removal, refresh, search and file-lyrics selection; existing removal confirmation retained |
| `transition/TransitionStudioScreen.kt` | Transition Studio track overflow | Edit transition and conditional reset to Auto |
| `transition/TransitionEditorScreen.kt` | Transition editor overflow | Undo, conditional cue reset and enabled/disabled preset reset |

The audited Home featured cards, artist/album list cards and VYBE do not have their own three-dot menus. No new actions or triggers were invented. The shared track row covers its existing callers. Ordinary form selectors in artwork tools, metadata-repair options and Sound settings remain form dropdowns; the dedicated live-cover control remains its existing non-overflow popup. This task does not redesign those controls.

## Shared component

Three production files under `core/designsystem/.../component` provide the system:

- `GlassMenu.kt`: anchored popup lifetime, coordinated animation, neutral glass/solid colors, scrolling, keyboard routing, single-selection gate and optional confirmation dialog.
- `GlassMenuItem.kt`: shared action row, icons, primary/secondary text, enabled/destructive states, confirmation descriptor, section headings and dividers.
- `GlassMenuPositionProvider.kt`: trailing-edge anchoring, above/below selection, window-edge clamping and trigger-relative transform origin.

Menus use 28 dp corners, a 0.5 dp highlight, a restrained 4 dp shadow, 20 dp leading icons, 18 dp horizontal row padding and at least 52 dp row height. Width adapts within 240–320 dp and incoming window constraints; height fits content up to the smaller of 600 dp or screen height minus 80 dp, then scrolls. Text wraps rather than truncating actions. Destructive actions use the existing error role; the menu surface and ordinary text do not use the accent/purple palette.

Glass-on uses a neutral translucent surface with a 90–95% opacity safety floor; glass-off uses an opaque neutral surface. Foreground follows light/dark contrast. This deliberately uses the permitted translucent fallback: the existing `GlassBackdrop` records positions in the parent Compose root, while a popup has a separate window/root. It does not blur text, capture the whole app each animation frame, add a blur dependency or modify the existing glass engine. Artwork/UI remains subtly visible through the tint, without claiming true backdrop blur.

One interruptible transition scales the entire menu from 0.9 to 1 around the trigger-facing pivot. The existing `Motion.expressive` spring has a tighter 0.001 scale threshold so the small overshoot is retained; a 6 dp positional expansion uses the existing press spring. Opacity uses the existing 120 ms fade. Closing reverses scale/fade, retaining the popup until exit finishes. Rows move together, with no independent wobble, staggering delays or perpetual animation. Compose's platform duration scale applies.

The focusable [Compose Popup](https://developer.android.com/reference/kotlin/androidx/compose/ui/window/PopupProperties) provides outside/Back dismissal and window clipping. Arrow navigation and Escape are handled inside the popup's focus owner. Section headings and pane titles provide semantics, while Material action rows retain touch, focus and disabled behavior. The single-selection gate rejects repeated callbacks during exit; navigation callbacks are invoked immediately after dismissal is requested rather than delayed until animation completion. Optional confirmation survives the popup exit. Existing screen-owned confirmation flows remain in place.

## Preservation and checks

The eight menu files were saved outside Git before editing. An automated comparison confirms every inline `onClick` body in each file is unchanged apart from whitespace. Now Playing's action bindings and conditions remain unchanged. Repository-wide trigger search confirms all eight `MoreVert` overflow implementations use `GlassMenu`; remaining Material dropdowns are the ordinary form selectors listed above.

All prior working-tree files outside the eight intentionally edited UI files retain their initial hashes. Playback, queue, database, library/lyrics/VYBE business logic, compression implementation, build configuration and dependencies were not changed. Nothing was reset, stashed, committed or pushed.

Added validation files:

- `core/designsystem/src/test/.../GlassMenuTest.kt`: positioning/RTL/edge-size matrix, tiny/unmeasured windows, glass/solid text and heading contrast against black/white artwork, and bounded spring overshoot/settling.
- `app/src/androidTest/.../GlassMenuInteractionTest.kt`: repeated open/Back/outside dismissal, disabled actions, double-tap guard, long-menu scrolling, confirmation cancellation/acceptance, bottom-edge light mode and RTL solid mode. Designed for an isolated `-PbenchApp` test package, with synthetic content and no media-library access.

Final results below distinguish executed JVM checks from compiled, unexecuted device tests.

## Final validation — 2026-10-31

- **244 unit/regression tests passed**, zero failures/errors/skips: design system 48 (including seven focused menu tests), library 195, app 1.
- ARM64 debug app and Android interaction-test APK assembly passed. The five new interaction tests compiled successfully; they were **not executed**.
- Standalone KtLint check passed on all 13 changed Kotlin files. `git diff --check` passed. Final source hashes match the source used for the build.
- Detekt remains nonzero for the existing 7 design-system and 87 library findings. No new menu source/test file has a finding. The combined Gradle command failed solely for these existing Detekt findings; no rule or baseline was weakened.
- All eight three-dot implementations use the shared component. Saved click-handler comparison and the prior-change hash inventory passed. The branch and HEAD remain unchanged; nothing is staged, committed or pushed.
- The user selected **Skip device checks**. No phone UI interaction, test-app installation, main-app update, playback change, screenshot capture or device visual/motion validation was performed for this task. The installed app and its data remain untouched.

Debug APK (not a release build): `outputs/unified-menus/empee3-unified-menus-debug-arm64.apk` in the chat workspace, **63,909,668 bytes**, SHA-256 `53d1b02d94c148942b7ac0399ef293bbcb16a947417f2ce470bd7a0b5a0bc43e`. Package `com.lochanpthakke.empee3`, ARM64 only; signature verified against the existing certificate. It was not installed.

### Remaining limits

The actual appearance over album art, full-motion feel, physical-device edge/inset behavior, and runtime Back/outside/keyboard/rapid-tap/confirmation interactions remain unverified on a device. JVM geometry, contrast and spring tests passed; compiling interaction tests is not a substitute for running them. No true backdrop blur is claimed: the shared menu uses the deliberate contrast-protected translucent fallback, and glass-off remains solid. Ordinary form selectors and the live-cover control remain outside this overflow-menu redesign. Release/R8 behavior was not exercised by this UI task.

The changed production files are the eight menu files in the coverage table and the three new design-system files listed above. The two validation files and this report complete the task's file set. Raw build/format logs, source hashes, inventory and preservation proofs are retained outside Git under `work/unified-menus`; a copy of the report and final evidence is under `outputs/unified-menus`.


## Subsequent debug installation — 2026-10-31

The user requested installation and reconnected the S23. A fresh complete backup was validated at `D:\android-toolchain\dbbackup\pre-unified-menus-20261031-003216` (69 files; SQLite quick_check ok, schema 14, 208 songs and 1 artwork record). The same-code, same-signature Phase 5 debug bridge enabled the backup. The unified-menu ARM64 debug APK was installed with `adb install -r`: **Success**. The installed APK SHA-256 matches `53d1b02d94c148942b7ac0399ef293bbcb16a947417f2ce470bd7a0b5a0bc43e`.

This supersedes the earlier not-installed status. No app uninstall, data clear, artwork removal/compression, app launch or menu/device test was performed during installation. The app was left unopened.
