## Changes

- Added full localization support across 20 languages (English, Arabic, German, Spanish, French, Indonesian, Italian, Japanese, Korean, Dutch, Polish, Portuguese (Brazil), Romanian, Russian, Swedish, Thai, Turkish, Ukrainian, Vietnamese, and Simplified Chinese).
- Added "System default" as the first option in onboarding language selection, dynamically showing the device language status.
- Renamed "Delete Music" to "Delete song" across all menus and dialogs with updated translations in all supported languages.
- Fixed Now Playing expand and minimize transitions: eliminated the black background bar and clipping artifacts to keep the player backdrop continuously visible.
- Fixed album artwork resizing during expand and collapse animations: maintained strict 1:1 aspect ratio with smooth uniform scaling and coordinated corner radius morphing into the mini-player thumbnail.

## Installation

Download `erhu-1.0.19-arm64-v8a.apk`. This ARM64 beta supports Android 8.0 and newer. The original application ID and signing certificate are preserved for in-place updates. Existing users can revisit onboarding through Diagnostics & About > Introduction.

Built and verified through GitHub Actions, including formatting/static analysis, unit tests, release lint and package/signature checks.

## VYBE model

The existing `vybe-embeddinggemma-300m-int4int8.zip` model pack and its `.sha256` checksum are preserved unchanged below. Read the included README, NOTICE and Gemma terms, extract `embedding_gemma.task`, then open VYBE and select **Choose model file**. Leave erhu open while it indexes your library.

The model is distributed separately under Google's Gemma Terms of Use. Previous release downloads remain available for rollback.
