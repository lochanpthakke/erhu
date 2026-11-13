<h1><img src="docs/branding/erhu-cube.png" alt="erhu 應用圖示" width="64"> <img src="docs/branding/erhu-wordmark-on-dark.png#gh-dark-mode-only" alt="erhu" width="95"><img src="docs/branding/erhu-wordmark-on-light.png#gh-light-mode-only" alt="erhu" width="95"> <sub>iOS 版</sub></h1>

<sub>🌐 [English](README.md) · [العربية](README.ar.md) · [বাংলা](README.bn.md) · [Deutsch](README.de.md) · [Español](README.es.md) · [فارسی](README.fa.md) · [Filipino](README.fil.md) · [Français](README.fr.md) · [हिन्दी](README.hi.md) · [Bahasa Indonesia](README.id.md) · [Italiano](README.it.md) · [日本語](README.ja.md) · [한국어](README.ko.md) · [Nederlands](README.nl.md) · [Polski](README.pl.md) · [Português (Brasil)](README.pt-BR.md) · [Русский](README.ru.md) · [தமிழ்](README.ta.md) · [తెలుగు](README.te.md) · [ไทย](README.th.md) · [Türkçe](README.tr.md) · [Українська](README.uk.md) · [اردو](README.ur.md) · [Tiếng Việt](README.vi.md) · [简体中文](README.zh-Hans.md) · **繁體中文**</sub>

> 本譯文由機器翻譯。如有不妥之處，請[提交 issue](https://github.com/MeezaUni/Erhu-iOS/issues) 或閱讀[英文 README](README.md)。

為你自己的音樂收藏打造的 iPhone 音樂播放器。

<a href="https://github.com/MeezaUni/Erhu-iOS/releases"><img src="docs/badges/download-ios.svg" alt="下載 iOS 版 erhu"></a>
<a href="https://github.com/lochanpthakke/erhu/releases"><img src="docs/badges/download-android.svg" alt="下載 Android 版 erhu"></a>
<a href="https://ko-fi.com/kshitij38758"><img src="docs/badges/support-ko-fi.svg" alt="在 Ko-fi 上支援 erhu"></a>

<img src="images/showcase/hero.png#gh-dark-mode-only" alt="iPhone 上的 erhu 主頁和正在播放" width="1200"><img src="images/showcase/light/hero.png#gh-light-mode-only" alt="iPhone 上的 erhu 主頁和正在播放" width="1200">

## Beta 3

第三個測試版以未簽名 IPA 的形式提供，適用於 iOS 17 及更高版本，需要透過側載工具用你自己的 Apple ID 安裝。與其他 iPhone 機型和 iOS 版本的相容性仍在測試中。

共有兩個版本：

| 版本 | 新增內容 | 下載 |
| --- | --- | --- |
| **靜態封面** | 使用靜態專輯封面的完整應用 | [下載 IPA](https://github.com/MeezaUni/Erhu-iOS/releases/download/v1.0-beta.3/Erhu-v1.0-beta.3-static.ipa) |
| **動態封面** | 包含以上全部內容，另加應用內和鎖定畫面上已下載的動態專輯封面 | [下載 IPA](https://github.com/MeezaUni/Erhu-iOS/releases/download/v1.0-beta.3/Erhu-v1.0-beta.3-animated.ipa) |

[發行說明](https://github.com/MeezaUni/Erhu-iOS/releases/tag/v1.0-beta.3) · 更新的開發版本： [靜態](https://github.com/MeezaUni/Erhu-iOS/actions?query=workflow%3AiOS+branch%3Awithout-animated-art) · [動態](https://github.com/MeezaUni/Erhu-iOS/actions?query=workflow%3AiOS+branch%3Awith-animated-art) （開啟一次成功的執行並下載 **Erhu-ipa**）

用 [SideStore](https://sidestore.io/) 或 [AltStore Classic](https://altstore.io/) 安裝 IPA，然後透過 **+**、「檔案」App 或 **Wi-Fi 傳輸**新增音樂。erhu 播放儲存在你 iPhone 上的音樂；電台和智慧播放清單從你自己的資料庫中挑選歌曲。

使用免費 Apple ID 時，需要在 **7 天**內重新整理 App；通常的 **3 個啟用中的 App**上限也包括你的安裝工具。在現有 App 上覆蓋安裝更新的 IPA，會保留你的資料庫、設定、收藏和收聽記錄。請參閱[安裝指南](docs/INSTALLING.md)。

## 功能

- 專輯、藝人、播放清單、收藏、收聽記錄和收聽統計。
- 根據你的資料庫生成的智慧播放清單和電台。
- 按流派和情緒篩選歌曲：放鬆、活力滿滿、深夜、好心情、派對、情歌、傷感與沉思、暗黑、專注和懷舊。
- 內建於 App、在裝置上執行的 VYBE 搜尋，可按情緒、藝人或歌曲查詢。
- 來自內嵌歌詞、LRC 檔案、快取結果和 LRCLIB 的同步歌詞，並支援離線下載。
- 佇列管理、A–B 重複播放、書籤、帶審閱和還原的自動修復標籤，以及線上或本地封面。
- 更高解析度的封面和藝人照片升級，儲存前會與當前圖片進行比對。
- 過渡工作室，支援專輯、播放清單和曲目對的交叉淡化，曲線可編輯。
- 帶預設的 8 段均衡器，以及視覺化效果。
- 隨封面配色的主題，淺色、深色和跟隨系統外觀，以及匯入的主題包。
- 正在播放中可滑動的封面輪播，以及懸浮的玻璃迷你播放器。
- 應用內和鎖定畫面上的動態專輯封面（僅限動態封面版本）。
- 從電腦瀏覽器進行 Wi-Fi 傳輸，以及手動和自動檢查新版本。

支援 MP3、AAC、ALAC、FLAC、WAV、AIFF、CAF 和 Ogg Vorbis/Opus，並在適用時支援 M4A、M4B 和 MP4 容器。不支援受保護（DRM）的檔案。

## 截圖

使用當前版本在 iPhone 上擷取，展示的是真實介面。

### 主頁與發現

<p>
  <a href="images/showcase/home.png#gh-dark-mode-only"><img src="images/showcase/home.png#gh-dark-mode-only" alt="erhu 主頁，包含一個智慧播放清單和精選電台，截於 iPhone" width="270"></a><a href="images/showcase/light/home.png#gh-light-mode-only"><img src="images/showcase/light/home.png#gh-light-mode-only" alt="erhu 主頁，包含一個智慧播放清單和精選電台，截於 iPhone" width="270"></a>
  <a href="images/showcase/radio.png#gh-dark-mode-only"><img src="images/showcase/radio.png#gh-dark-mode-only" alt="基於 A Sky Full of Stars 生成的 erhu 電台，截於 iPhone" width="270"></a><a href="images/showcase/light/radio.png#gh-light-mode-only"><img src="images/showcase/light/radio.png#gh-light-mode-only" alt="基於 A Sky Full of Stars 生成的 erhu 電台，截於 iPhone" width="270"></a>
  <a href="images/showcase/vybe.png#gh-dark-mode-only"><img src="images/showcase/vybe.png#gh-dark-mode-only" alt="erhu 的 VYBE 搜尋「深夜」，顯示資料庫中最接近的歌曲，截於 iPhone" width="270"></a><a href="images/showcase/light/vybe.png#gh-light-mode-only"><img src="images/showcase/light/vybe.png#gh-light-mode-only" alt="erhu 的 VYBE 搜尋「深夜」，顯示資料庫中最接近的歌曲，截於 iPhone" width="270"></a>
</p>

### 聆聽體驗

<p>
  <a href="images/showcase/now-playing.png#gh-dark-mode-only"><img src="images/showcase/now-playing.png#gh-dark-mode-only" alt="erhu 的正在播放，截於 iPhone" width="270"></a><a href="images/showcase/light/now-playing.png#gh-light-mode-only"><img src="images/showcase/light/now-playing.png#gh-light-mode-only" alt="erhu 的正在播放，截於 iPhone" width="270"></a>
  <a href="images/showcase/lyrics.png#gh-dark-mode-only"><img src="images/showcase/lyrics.png#gh-dark-mode-only" alt="erhu 的同步歌詞，截於 iPhone" width="270"></a><a href="images/showcase/light/lyrics.png#gh-light-mode-only"><img src="images/showcase/light/lyrics.png#gh-light-mode-only" alt="erhu 的同步歌詞，截於 iPhone" width="270"></a>
  <a href="images/showcase/queue.png#gh-dark-mode-only"><img src="images/showcase/queue.png#gh-dark-mode-only" alt="erhu 的佇列，截於 iPhone" width="270"></a><a href="images/showcase/light/queue.png#gh-light-mode-only"><img src="images/showcase/light/queue.png#gh-light-mode-only" alt="erhu 的佇列，截於 iPhone" width="270"></a>
</p>

### 你的資料庫

<p>
  <a href="images/showcase/library.png#gh-dark-mode-only"><img src="images/showcase/library.png#gh-dark-mode-only" alt="erhu 資料庫，包含播放清單、收藏和藝人，截於 iPhone" width="270"></a><a href="images/showcase/light/library.png#gh-light-mode-only"><img src="images/showcase/light/library.png#gh-light-mode-only" alt="erhu 資料庫，包含播放清單、收藏和藝人，截於 iPhone" width="270"></a>
  <a href="images/showcase/albums.png#gh-dark-mode-only"><img src="images/showcase/albums.png#gh-dark-mode-only" alt="按最近播放排序的 erhu 專輯，截於 iPhone" width="270"></a><a href="images/showcase/light/albums.png#gh-light-mode-only"><img src="images/showcase/light/albums.png#gh-light-mode-only" alt="按最近播放排序的 erhu 專輯，截於 iPhone" width="270"></a>
  <a href="images/showcase/songs-moods.png#gh-dark-mode-only"><img src="images/showcase/songs-moods.png#gh-dark-mode-only" alt="按「深夜」情緒篩選的 erhu 歌曲，截於 iPhone" width="270"></a><a href="images/showcase/light/songs-moods.png#gh-light-mode-only"><img src="images/showcase/light/songs-moods.png#gh-light-mode-only" alt="按「深夜」情緒篩選的 erhu 歌曲，截於 iPhone" width="270"></a>
</p>

### 資料庫工具

<p>
  <a href="images/showcase/artwork-fetcher.png#gh-dark-mode-only"><img src="images/showcase/artwork-fetcher.png#gh-dark-mode-only" alt="iPhone 上的封面獲取工具，顯示專輯和藝人照片檢查" width="270"></a><a href="images/showcase/light/artwork-fetcher.png#gh-light-mode-only"><img src="images/showcase/light/artwork-fetcher.png#gh-light-mode-only" alt="iPhone 上的封面獲取工具，顯示專輯和藝人照片檢查" width="270"></a>
  <a href="images/showcase/artwork-upgrade.png#gh-dark-mode-only"><img src="images/showcase/artwork-upgrade.png#gh-dark-mode-only" alt="iPhone 上的升級專輯封面，列出更高解析度的匹配結果，並標出了一個動態封面" width="270"></a><a href="images/showcase/light/artwork-upgrade.png#gh-light-mode-only"><img src="images/showcase/light/artwork-upgrade.png#gh-light-mode-only" alt="iPhone 上的升級專輯封面，列出更高解析度的匹配結果，並標出了一個動態封面" width="270"></a>
  <a href="images/showcase/auto-fix-tags.png#gh-dark-mode-only"><img src="images/showcase/auto-fix-tags.png#gh-dark-mode-only" alt="iPhone 上的自動修復標籤，列出已匹配的曲目和待審閱的不確定匹配" width="270"></a><a href="images/showcase/light/auto-fix-tags.png#gh-light-mode-only"><img src="images/showcase/light/auto-fix-tags.png#gh-light-mode-only" alt="iPhone 上的自動修復標籤，列出已匹配的曲目和待審閱的不確定匹配" width="270"></a>
</p>

### 音訊控制與過渡工作室

<p>
  <a href="images/showcase/audio-controls.png#gh-dark-mode-only"><img src="images/showcase/audio-controls.png#gh-dark-mode-only" alt="使用「低音增強」預設的 erhu 均衡器，截於 iPhone" width="270"></a><a href="images/showcase/light/audio-controls.png#gh-light-mode-only"><img src="images/showcase/light/audio-controls.png#gh-light-mode-only" alt="使用「低音增強」預設的 erhu 均衡器，截於 iPhone" width="270"></a>
  <a href="images/showcase/transition-studio.png#gh-dark-mode-only"><img src="images/showcase/transition-studio.png#gh-dark-mode-only" alt="iPhone 上的過渡工作室，顯示專輯設定和曲目對" width="270"></a><a href="images/showcase/light/transition-studio.png#gh-light-mode-only"><img src="images/showcase/light/transition-studio.png#gh-light-mode-only" alt="iPhone 上的過渡工作室，顯示專輯設定和曲目對" width="270"></a>
  <a href="images/showcase/transition-editor.png#gh-dark-mode-only"><img src="images/showcase/transition-editor.png#gh-dark-mode-only" alt="iPhone 上的過渡編輯器，顯示波形和可編輯的曲線" width="270"></a><a href="images/showcase/light/transition-editor.png#gh-light-mode-only"><img src="images/showcase/light/transition-editor.png#gh-light-mode-only" alt="iPhone 上的過渡編輯器，顯示波形和可編輯的曲線" width="270"></a>
</p>

[專輯順序與過渡](docs/ALBUM_ORDER_AND_TRANSITIONS.md) · 應用內的介紹會講解這些工具；可在 **設定 → 關於 Erhu → 介紹** 中重新開啟。

### 動態封面

<a href="images/showcase/animated-artwork.webp#gh-dark-mode-only"><img src="images/showcase/animated-artwork.webp#gh-dark-mode-only" alt="專輯頁面、正在播放和鎖定畫面上的動態專輯封面，在 iPhone 上錄製" width="1200"></a><a href="images/showcase/light/animated-artwork.webp#gh-light-mode-only"><img src="images/showcase/light/animated-artwork.webp#gh-light-mode-only" alt="專輯頁面、正在播放和鎖定畫面上的動態專輯封面，在 iPhone 上錄製" width="1200"></a>

動態封面僅在動態封面版本中提供。鎖定畫面需要 iOS 26 或更高版本，何時顯示動畫由 iOS 決定。

[動態封面的工作原理](https://github.com/MeezaUni/Erhu-iOS/blob/with-animated-art/docs/ANIMATED_COVERS.md)

## VYBE

VYBE 會根據你對想聽內容的描述來搜尋資料庫，比如一種情緒、一位藝人或一首你喜歡的歌。它完全在你的 iPhone 上執行，不會上傳任何內容，也沒有云端推理。

在 iOS 上，VYBE 的模型已內建在 App 中（經過壓縮的 Core ML MiniLM，約 17 MB），因此無需下載。在介紹中或「設定」裡開啟它，然後在它為你的資料庫建立索引時保持 App 開啟。請參閱 [VYBE 詳情](docs/VYBE.md)。

## 構建

iOS 版 erhu 使用 Swift、SwiftUI、AVFoundation 和 Core Audio。

在裝有 Xcode 和 [XcodeGen](https://github.com/yonaskolb/XcodeGen) 的 Mac 上：

```sh
xcodegen generate
open Erhu.xcodeproj
```

[iOS 工作流](.github/workflows/ios.yml) 會構建未簽名的 IPA，並在模擬器上為兩個封面分支執行原生互動檢查和單元測試。請參閱[從原始碼構建](docs/BUILDING.md)。

`without-animated-art` 是預設分支，構建靜態封面版本；`with-animated-art` 構建動態版本。共享的更改會在兩個分支上保持同步。

## 反饋

請透過 [GitHub Issues](https://github.com/MeezaUni/Erhu-iOS/issues) 報告問題。請註明你的 iPhone 機型、iOS 版本、應用版本、封面版本以及重現問題的步驟。

[參與貢獻](CONTRIBUTING.md) · [安全政策](SECURITY.md) · [行為準則](CODE_OF_CONDUCT.md)

## 支援 erhu

erhu 免費，沒有廣告，也不需要帳號。如果你願意幫忙支付 Apple Developer 費用，讓它能上架 App Store，可以[在 Ko-fi 上請我喝杯咖啡](https://ko-fi.com/kshitij38758)。同樣的連結也在App 的 **設定 → 關於 Erhu** 中。

## 貢獻者

- [Azeem](https://github.com/MeezaUni)（iOS）
- [Lochan Thakke](https://github.com/lochanpthakke) 和 [Kshitij Kekunnaya](https://github.com/Kshitij1425)，[Android 版 erhu](https://github.com/lochanpthakke/erhu) 的作者

## 許可證

[GNU General Public License v3.0](LICENSE)。源自 Android 的程式碼和 erhu 品牌的說明見[許可與授權](docs/LICENSING-PERMISSIONS.md)。第三方元件保留各自的許可證（[ThirdParty](ThirdParty/)）。

<!-- Built by Tools/readme_i18n/build.py from template.md and strings/<language>.json. Edit those and run the script instead of editing this file. -->
