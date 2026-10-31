# Neutral Liquid Glass popups

Updated 2026-10-31. Supersedes the earlier playback-tinted menu material in this file. Reference: `D:/liquid glass/empee3-liquid-glass-spec/docs/LIQUID_GLASS_UI_SPEC.md`, both supplied videos and the newer maximum-clear screenshots. Existing `claudeinstructions.md` changes are preserved.

## Implementation

`PopupGlassSurface`, `Modifier.popupGlass` and `PopupGlassTheme` share the existing app backdrop and RenderEffect/AGSL lens. Retained GPU layers separate diffusion from the changing aperture. Compiled shaders are remembered; no CPU bitmap readback or per-frame background capture is introduced. Diffusion recording is held during the morph; Android may still propagate source RenderNode dirtiness. The first source recording now invalidates the popup draw even when the initial backdrop was unavailable.

Android 13+ uses a neutral, luminance-aware contrast shader: dark scene detail passes through unchanged, bright highlights are compressed for light labels, and the light profile lifts dark areas for dark labels. Fill, rim and reflection are neutral; saturation is 1, dispersion is 0, and no artwork palette selects material colour. Blue visible over the SOS cover is transmitted scene colour. Diffusion is 4–14 dp; fill alpha is 0.04–0.24 dark / 0.06–0.26 light. The thinner rim replaces the earlier heavy cloudy edge.

Android 12 retains diffusion with a conservative contrast fallback. Android 8–11, disabled glass and skins retain opaque neutral fallback. This implementation follows the supplied references and Apple's [material guidance](https://developer.apple.com/videos/play/wwdc2025/219/) and [morphing guidance](https://developer.apple.com/videos/play/wwdc2025/323/); it uses Android rendering rather than Apple's renderer. Permanent cards, navigation and mini-player material retain their existing treatment.

## Preference and motion

`PlaybackStateProto.popup_glass_opacity` remains optional field **36**, persisted by the existing ThemeRepository/DataStore. Absence defaults to **0.5**, explicit zero is preserved, nonfinite values reset to default and finite values clamp to 0–1. Appearance retains the preview, clear-to-higher-contrast slider and reset to 50%. Sidebar frostiness and Room schemas are unchanged.

Eight overflow trigger sites now provide measured window bounds through `GlassMenuAnchor`: album, artist, playlist, library, Now Playing, online lyrics, Transition Studio and transition editor. The original glyph hides while its shared popup glyph and aperture expand from the actual trigger bounds to the final panel. Other selectors use their measured parent anchor. Rows are measured at their final size; clipping and label visibility follow the same responsive spring, with a 150 ms reverse exit. Selection callbacks and confirmation opening wait until the reverse completes. Multi-selection remains open.

The native popup window is bounded to the union of trigger and panel plus optical margin. Transparent union padding dismisses the popup, while native Back/outside dismissal, keyboard focus and scrolling remain. Dynamic outline resolution occurs inside the graphics-layer observer so clipping follows the current aperture. Dialogs retain centred entrance, sheets retain native Material motion, and the sound preset field retains exposed-dropdown semantics. System animation duration scale applies.

## Popup inventory

| Surface | Previous implementation | Migration |
|---|---|---|
| Album, artist, playlist and Now Playing overflow | Shared GlassMenu | Neutral material and anchored morph |
| Shared song rows: Home, Library, search, Smart Playlists | Shared GlassMenu | Same shared implementation |
| Online lyrics, Transition Studio/editor overflow | Shared GlassMenu | Same shared implementation |
| Live-cover controls, resolution and compression panel | Custom Popup with tinted fill | Shared GlassMenu shell; specialised controls/callbacks retained |
| Artwork provider, animated-artwork resolution, metadata provider/field/mode selectors | Material DropdownMenu | Shared GlassMenu; multi-selection retained |
| Confirmations, playlist naming/editing, bookmarks, fix-tags, scan location, theme rollback, saved sound preset | GlassAlertDialog | Shared neutral material and centred entrance |
| Lyrics removal/search and animated-artwork notice/completion | Plain AlertDialog | GlassAlertDialog |
| Queue/history and transition option/duration sheets | ModalBottomSheet | GlassModalBottomSheet; native state/gestures retained |
| Sound preset selector | ExposedDropdownMenu | Shared neutral optical modifier and 28 dp outline; native exposed-field semantics retained |
| Circular/square artwork crop dialogs | Dedicated editing canvas | Intentionally opaque to preserve image-editing contrast and colour judgement |
| Full-screen Transition Studio host / expanded player, lyrics and audio details | Full-screen editor/content surfaces | Excluded; retain navigation and permanent content presentation |
| VYBE / featured cards | No independent overflow implementation | Shared song menus where present; no VYBE redesign |

Permanent cards, album tiles, track rows, mini-player, navigation and ordinary Settings groups were not converted into popup glass. Playback, queue, lyrics, metadata and compression business logic are unchanged.

## Verification — correction pass

- **249 targeted JVM tests passed:** design system 54 and library 195. Prior DataStore 31 checks passed in the first pass; persistence implementation was unchanged in this correction.
- **8 Android interaction tests passed on Galaxy S23** (`OK (8 tests)`, 11.101 s): repeated dismissal, disabled actions, scrolling/confirmation, light/bottom placement, RTL/solid fallback, multi-selection and native light/dark contrast captures.
- Native screenshots show neutral material over neutral backgrounds. Representative material pixels yield 7.08:1 dark-profile and 8.36:1 light-profile label contrast; these are sampled fixture results, not a claim about every transmitted scene pixel.
- Optimized ARM64 release/R8 assembly and signature verification passed. Actual SOS artwork, underlying controls and refraction were visible in the running release. Settings reached 0% and 100%, reset to 50%, and retained 0% after process restart; sidebar frostiness remained 46% in the isolated copy.
- Two eight-cycle runs used the optimized isolated release while music played. Playback advanced from 0 to 13,634 ms during the recorded run and paused successfully afterward. Normal-time recording and sequential frames show forward/reverse morphing and repeated reopening.
- **Performance remains imperfect:** without recording, Android gfxinfo reported 536 frames, 75 deadline misses (13.99%), p50 6 ms / p90 17 ms / p95 38 ms / p99 48 ms. GPU p50/p90/p99 were 3/7/12 ms. The recorded run reported 548 frames, 12.41% deadline misses and p50/p90/p99 5/14/48 ms. These whole-process measurements include native window creation, backdrop and permanent chrome; they do not isolate menu shader cost. No zero-jank claim is made.
- Changed production Kotlin formatting passes. Whitespace checks pass. The latest design-system Detekt run retains three existing findings in `Glass.kt`; the selected library run retains 24 older findings. New anchor/layout/optics helpers have no findings. No rule or baseline was weakened.
- Validation covers one Galaxy S23 and static covers with active audio. The copied library contained no saved live-cover files, so live video/TextureView behaviour and older Android/OEM rendering remain unverified. No dependency, package identity, signing configuration, playback business logic or Room schema changes. Validation preceded the user-requested commit/push; pre-existing `claudeinstructions.md` edits preserved.

## Device isolation

The real `com.lochanpthakke.empee3` installation remains the earlier ARM64 debug APK, SHA-256 `57f50049d342fa6e06143074829e3cb8125a66ad79f216359f8c037875307f09`, installed on the user's earlier explicit request. Its media session remained paused at position 256 throughout correction validation. Its database/settings were read for an isolated seed and were not overwritten.

Correction tests used separately installed `com.lochanpthakke.empee3.perf` and `.perf.test`, seeded with a copy of the library/preferences. Clone-only scan settings allowed MediaStore scanning, so its album counts may differ from the real app. Metadata API credentials were excluded from the clone seed. Test installations, temporary phone files and the diagnostic log property were cleaned up after validation. The corrected base-package release APK was subsequently installed with `adb install -r` at the user's explicit request, preserving existing app data. The earlier paused-session observation applies to isolated validation before this installation.

## Files and modules

Affected modules: `app`, `core:designsystem`, `core:datastore`, `core:datastore-proto`, `feature:library`, `feature:settings`.

- `app/src/androidTest/java/com/lochanpthakke/empee3/GlassMenuInteractionTest.kt`
- `app/src/main/java/com/lochanpthakke/empee3/MainScaffold.kt`
- `core/datastore-proto/src/main/proto/playback_state.proto`
- `core/datastore/src/main/java/com/lochanpthakke/empee3/core/datastore/repository/PopupGlassPreference.kt`
- `core/datastore/src/main/java/com/lochanpthakke/empee3/core/datastore/repository/ThemeRepository.kt`
- `core/datastore/src/main/java/com/lochanpthakke/empee3/core/datastore/repository/ThemeRepositoryImpl.kt`
- `core/datastore/src/test/java/com/lochanpthakke/empee3/core/datastore/PopupGlassPreferenceTest.kt`
- `core/designsystem/src/main/java/com/lochanpthakke/empee3/core/designsystem/component/GlassAlertDialog.kt`
- `core/designsystem/src/main/java/com/lochanpthakke/empee3/core/designsystem/component/GlassMenu.kt`
- `core/designsystem/src/main/java/com/lochanpthakke/empee3/core/designsystem/component/GlassModalBottomSheet.kt`
- `core/designsystem/src/main/java/com/lochanpthakke/empee3/core/designsystem/component/LiquidMenuSurface.kt`
- `core/designsystem/src/main/java/com/lochanpthakke/empee3/core/designsystem/component/PopupGlassPreview.kt`
- `core/designsystem/src/main/java/com/lochanpthakke/empee3/core/designsystem/component/PopupMorph.kt`
- `core/designsystem/src/main/java/com/lochanpthakke/empee3/core/designsystem/theme/EmPee3Theme.kt`
- `core/designsystem/src/main/java/com/lochanpthakke/empee3/core/designsystem/theme/Glass.kt`
- `core/designsystem/src/main/java/com/lochanpthakke/empee3/core/designsystem/theme/GlassBackdrop.kt`
- `core/designsystem/src/main/java/com/lochanpthakke/empee3/core/designsystem/theme/GlassEnvironmentOptics.kt`
- `core/designsystem/src/main/java/com/lochanpthakke/empee3/core/designsystem/theme/PopupGlass.kt`
- `core/designsystem/src/test/java/com/lochanpthakke/empee3/core/designsystem/component/GlassMenuTest.kt`
- `core/designsystem/src/test/java/com/lochanpthakke/empee3/core/designsystem/component/LiquidMenuMaterialTest.kt`
- `core/designsystem/src/test/java/com/lochanpthakke/empee3/core/designsystem/component/PopupMorphTest.kt`
- `docs/liquid-glass-overflow-menus.md`
- `feature/library/src/main/java/com/lochanpthakke/empee3/feature/library/AnimatedArtworkScreen.kt`
- `feature/library/src/main/java/com/lochanpthakke/empee3/feature/library/ArtworkManagerScreen.kt`
- `feature/library/src/main/java/com/lochanpthakke/empee3/feature/library/MetadataEditorScreen.kt`
- `feature/library/src/main/java/com/lochanpthakke/empee3/feature/library/OnlineLyricsUi.kt`
- `feature/library/src/main/java/com/lochanpthakke/empee3/feature/library/QueueSheet.kt`
- `feature/library/src/main/java/com/lochanpthakke/empee3/feature/library/components/LiveCoverControl.kt`
- `feature/library/src/main/java/com/lochanpthakke/empee3/feature/library/transition/TransitionOptionSheets.kt`
- `feature/settings/src/main/java/com/lochanpthakke/empee3/feature/settings/sound/SoundSettingsScreen.kt`
- `feature/settings/src/main/java/com/lochanpthakke/empee3/feature/settings/theme/PopupGlassControls.kt`
- `feature/settings/src/main/java/com/lochanpthakke/empee3/feature/settings/theme/ThemeSettingsScreen.kt`
- `feature/settings/src/main/java/com/lochanpthakke/empee3/feature/settings/theme/ThemeViewModel.kt`

- `core/designsystem/src/main/java/com/lochanpthakke/empee3/core/designsystem/component/AnchoredMenuLayout.kt`

- `core/designsystem/src/main/java/com/lochanpthakke/empee3/core/designsystem/component/GlassMenuAnchorState.kt`

- `core/designsystem/src/main/java/com/lochanpthakke/empee3/core/designsystem/theme/CachedPopupOptics.kt`

## Release artifact

ARM64-only optimized release APK: **37,215,203 bytes**, package `com.lochanpthakke.empee3`, versionCode 1 / versionName 1.0. SHA-256 `f07d71470844178c1f8e8e4702a9ec6f836cf3c4e119e084736ad98217ac68f7`. `apksigner verify --print-certs` passes with the existing certificate SHA-256 `5bb0fc457b96d9fb6cbc606009de44d633bee95b58fec2dc633a7a4eb54a9ce5` (existing Android Debug certificate used by this project's release configuration). APK, screenshot and normal-speed video accompany this report.
