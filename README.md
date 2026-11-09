<h1><img src="images/branding/erhu-cube.png" alt="erhu app icon" width="64"> <img src="images/branding/erhu-wordmark.png" alt="erhu" width="160"></h1>

An Android music player for your own collection.

[![iOS repository](https://img.shields.io/badge/iOS-Erhu--iOS-181717?style=for-the-badge)](https://github.com/MeezaUni/Erhu-iOS)

<img src="images/showcase/hero.png" alt="erhu Home and Now Playing on a Galaxy S23" width="1200">

## Current beta — 1.0.15

The current beta is available as an ARM64 APK for Android 8.0 and newer. It has been tested on a Samsung Galaxy S23 running One UI. Compatibility with other devices is still being tested.

[Download erhu 1.0.15 beta](https://github.com/lochanpthakke/erhu/releases/tag/erhu-v1.0.15-beta.27)

Install the APK from the release page and grant music access when prompted. erhu plays music stored on your device; radio stations select tracks from your own library.

Existing installations can update in place. The application ID and signing certificate are unchanged, so an update preserves library data, preferences, favourites and playback history.

## Features

- Albums, artists, playlists, favourites and listening history.
- Smart playlists and radio stations generated from your library.
- On-device VYBE search using local song metadata.
- Synced lyrics from embedded lyrics, cached results and LRCLIB.
- Queue management, Auto Fix Tags and online or local artwork selection.
- Library Builder with reviewed file moves into artist and album folders.
- Transition Studio with global, collection and track-pair crossfades, plus Volume, EQ and Filter controls.
- Floating navigation, adjustable glass surfaces and Now Playing artwork transitions.
- Three Home screen widget styles with playback controls and resizable layouts.
- Manual update checks and a monthly automatic check for published releases, including the current beta channel.
- Remote announcements with an inbox and optional Android notifications.

The optional Musixmatch synced-lyrics provider requires a licensed API key. It is disabled when no key is configured. See [provider configuration](docs/synced-lyrics-providers.md).

## Screenshots

Captured from erhu 1.0.2 on a Galaxy S23, including working One UI widgets.

The library tools and Transition Studio below were captured from the current installed build on 3 November 2026. They show the real interfaces; no repairs, file moves or transition changes were applied during capture.

### Home and discovery

<table>
  <tr>
    <th width="33%">Home</th>
    <th width="33%">Radio stations</th>
    <th width="33%">VYBE</th>
  </tr>
  <tr>
    <td><a href="images/showcase/home.png"><img src="images/showcase/home.png" alt="erhu home, captured on a Galaxy S23" width="260"></a></td>
    <td><a href="images/showcase/radio.png"><img src="images/showcase/radio.png" alt="erhu radio stations, captured on a Galaxy S23" width="260"></a></td>
    <td><a href="images/showcase/vybe.png"><img src="images/showcase/vybe.png" alt="erhu vybe, captured on a Galaxy S23" width="260"></a></td>
  </tr>
</table>

[See the radio, featured-album and recently-played shelves](images/showcase/home-discovery.png).

### The listening experience

<table>
  <tr>
    <th width="33%">Now Playing</th>
    <th width="33%">Synced lyrics</th>
    <th width="33%">Queue</th>
  </tr>
  <tr>
    <td><a href="images/showcase/now-playing.png"><img src="images/showcase/now-playing.png" alt="erhu now playing, captured on a Galaxy S23" width="260"></a></td>
    <td><a href="images/showcase/lyrics.png"><img src="images/showcase/lyrics.png" alt="erhu synced lyrics, captured on a Galaxy S23" width="260"></a></td>
    <td><a href="images/showcase/queue.png"><img src="images/showcase/queue.png" alt="erhu queue, captured on a Galaxy S23" width="260"></a></td>
  </tr>
</table>

### Your library and audio controls

<table>
  <tr>
    <th width="33%">Library</th>
    <th width="33%">Albums</th>
    <th width="33%">Equalizer</th>
  </tr>
  <tr>
    <td><a href="images/showcase/library.png"><img src="images/showcase/library.png" alt="erhu library, captured on a Galaxy S23" width="260"></a></td>
    <td><a href="images/showcase/albums.png"><img src="images/showcase/albums.png" alt="erhu albums, captured on a Galaxy S23" width="260"></a></td>
    <td><a href="images/showcase/equalizer.png"><img src="images/showcase/equalizer.png" alt="erhu equalizer, captured on a Galaxy S23" width="260"></a></td>
  </tr>
</table>

<details>
  <summary>Floating album controls</summary>
  <p><img src="images/showcase/glass.png" alt="Album actions on the actual translucent popup surface" width="420"></p>
</details>

### Library tools

<table>
  <tr>
    <th width="33%">Library Builder</th>
    <th width="33%">Artwork Fetcher</th>
    <th width="33%">Auto Fix Tags</th>
  </tr>
  <tr>
    <td><a href="images/showcase/library-builder.png"><img src="images/showcase/library-builder.png" alt="Library Builder on a Galaxy S23, showing folder scanning and organization controls" width="260"></a></td>
    <td><a href="images/showcase/artwork-fetcher.png"><img src="images/showcase/artwork-fetcher.png" alt="Artwork Fetcher on a Galaxy S23, showing providers, album search and resolution targets" width="260"></a></td>
    <td><a href="images/showcase/auto-fix-tags.png"><img src="images/showcase/auto-fix-tags.png" alt="Auto Fix Tags on a Galaxy S23, showing repair search, metadata fields and matching mode" width="260"></a></td>
  </tr>
</table>

- **Library Builder:** scan your configured music folder or choose another folder, review proposed destinations, resolve conflicts, then execute the moves. Files are organized as artist / album / track title.
- **Artwork Fetcher:** find album covers and artist photos, choose a provider and resolution target, inspect candidates and apply your selection. Album menus also accept a local cover image.
- **Auto Fix Tags:** add tracks, albums or artists to a repair queue, select the metadata fields to change, and use automatic matching or manual result selection. File writes may require Android approval.

### Transition Studio

Choose the default crossfade for all music, override it for an album or playlist, or edit one track pair. Volume, EQ and Filter open their own floating panels. Preview a pair when available, then Save to apply the draft.

<table>
  <tr>
    <th width="50%">Defaults and track pairs</th>
    <th width="50%">Floating Volume controls</th>
  </tr>
  <tr>
    <td><a href="images/showcase/transition-studio.png"><img src="images/showcase/transition-studio.png" alt="Transition Studio on a Galaxy S23, showing global defaults, album settings and track pairs" width="340"></a></td>
    <td><a href="images/showcase/transition-volume.png"><img src="images/showcase/transition-volume.png" alt="The actual floating Volume panel in Transition Studio on a Galaxy S23" width="340"></a></td>
  </tr>
</table>

[Using Transition Studio](docs/transition-studio.md)

The in-app introduction includes these tools and a separate Transition Studio page. Reopen it from **Settings → Diagnostics & About → Introduction**.

### Widgets

Music, Glass and Vinyl playback controls, shown with real artwork and track information.

<img src="images/showcase/widgets.png" alt="Working Music and Glass 4 by 2 widgets and the Vinyl 3 by 3 widget in One UI" width="960">

## VYBE setup

VYBE searches your library using descriptions of what you want to hear. It runs on the device and needs a separate EmbeddingGemma model.

Download the [VYBE model pack](https://github.com/lochanpthakke/erhu/releases/download/v1.0.2-beta.1/vybe-embeddinggemma-300m-int4int8.zip), read the included model terms, and extract `embedding_gemma.task`. Open **VYBE** in erhu, tap **Choose model file**, and select the extracted file. Leave the app open while it indexes your library.

The model is about 184 MB before compression. It is supplied under Google's Gemma Terms of Use, separately from erhu's GPL-3.0 licence. See [VYBE setup](docs/vybe-setup.md) for the model source, checksum and terms.

## Build

erhu uses Kotlin, Jetpack Compose and Media3.

Use JDK 21, the Android SDK configured by the project and the checked-in Gradle wrapper.

```sh
./gradlew :app:assembleDebug
```

The [release workflow](.github/workflows/release.yml) runs unit tests and release lint, builds the optimized ARM64 APK, and verifies its package, version and signing certificate. Signing material is supplied through GitHub Actions Secrets.

The source namespace is `com.erhu.erhu`. The installed application ID remains `com.lochanpthakke.empee3` for compatibility with existing installations.

## Feedback

Report bugs through [GitHub Issues](https://github.com/lochanpthakke/erhu/issues). Include the device model, Android and One UI versions where relevant, app version, and steps to reproduce the problem.

## Contributors

- [Kshitij Kekunnaya](https://github.com/Kshitij1425)
- [Lochan Thakke](https://github.com/lochanpthakke)

## License

[GNU General Public License v3.0](LICENSE). Read the [Privacy Policy](docs/privacy-policy.md) for local storage, online services and permissions.
