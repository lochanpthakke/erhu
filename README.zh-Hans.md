<h1><img src="docs/branding/erhu-cube.png" alt="erhu 应用图标" width="64"> <img src="docs/branding/erhu-wordmark-on-dark.png#gh-dark-mode-only" alt="erhu" width="95"><img src="docs/branding/erhu-wordmark-on-light.png#gh-light-mode-only" alt="erhu" width="95"> <sub>iOS 版</sub></h1>

<sub>🌐 [English](README.md) · [العربية](README.ar.md) · [বাংলা](README.bn.md) · [Deutsch](README.de.md) · [Español](README.es.md) · [فارسی](README.fa.md) · [Filipino](README.fil.md) · [Français](README.fr.md) · [हिन्दी](README.hi.md) · [Bahasa Indonesia](README.id.md) · [Italiano](README.it.md) · [日本語](README.ja.md) · [한국어](README.ko.md) · [Nederlands](README.nl.md) · [Polski](README.pl.md) · [Português (Brasil)](README.pt-BR.md) · [Русский](README.ru.md) · [தமிழ்](README.ta.md) · [తెలుగు](README.te.md) · [ไทย](README.th.md) · [Türkçe](README.tr.md) · [Українська](README.uk.md) · [اردو](README.ur.md) · [Tiếng Việt](README.vi.md) · **简体中文** · [繁體中文](README.zh-Hant.md)</sub>

> 本译文由机器翻译。如有不妥之处，请[提交 issue](https://github.com/MeezaUni/Erhu-iOS/issues) 或阅读[英文 README](README.md)。

为你自己的音乐收藏打造的 iPhone 音乐播放器。

<a href="https://github.com/MeezaUni/Erhu-iOS/releases"><img src="docs/badges/download-ios.svg" alt="下载 iOS 版 erhu"></a>
<a href="https://github.com/lochanpthakke/erhu/releases"><img src="docs/badges/download-android.svg" alt="下载 Android 版 erhu"></a>
<a href="https://ko-fi.com/kshitij38758"><img src="docs/badges/support-ko-fi.svg" alt="在 Ko-fi 上支持 erhu"></a>

<img src="images/showcase/hero.png#gh-dark-mode-only" alt="iPhone 上的 erhu 主页和正在播放" width="1200"><img src="images/showcase/light/hero.png#gh-light-mode-only" alt="iPhone 上的 erhu 主页和正在播放" width="1200">

## Beta 3

第三个测试版以未签名 IPA 的形式提供，适用于 iOS 17 及更高版本，需要通过侧载工具用你自己的 Apple ID 安装。与其他 iPhone 机型和 iOS 版本的兼容性仍在测试中。

共有两个版本：

| 版本 | 新增内容 | 下载 |
| --- | --- | --- |
| **静态封面** | 使用静态专辑封面的完整应用 | [下载 IPA](https://github.com/MeezaUni/Erhu-iOS/releases/download/v1.0-beta.3/Erhu-v1.0-beta.3-static.ipa) |
| **动态封面** | 包含以上全部内容，另加应用内和锁定屏幕上已下载的动态专辑封面 | [下载 IPA](https://github.com/MeezaUni/Erhu-iOS/releases/download/v1.0-beta.3/Erhu-v1.0-beta.3-animated.ipa) |

[发行说明](https://github.com/MeezaUni/Erhu-iOS/releases/tag/v1.0-beta.3) · 更新的开发版本： [静态](https://github.com/MeezaUni/Erhu-iOS/actions?query=workflow%3AiOS+branch%3Awithout-animated-art) · [动态](https://github.com/MeezaUni/Erhu-iOS/actions?query=workflow%3AiOS+branch%3Awith-animated-art) （打开一次成功的运行并下载 **Erhu-ipa**）

用 [SideStore](https://sidestore.io/) 或 [AltStore Classic](https://altstore.io/) 安装 IPA，然后通过 **+**、“文件”App 或 **Wi-Fi 传输**添加音乐。erhu 播放存储在你 iPhone 上的音乐；电台和智能播放列表从你自己的资料库中挑选歌曲。

使用免费 Apple ID 时，需要在 **7 天**内刷新应用；通常的 **3 个活跃应用**上限也包括你的安装工具。在现有应用上覆盖安装更新的 IPA，会保留你的资料库、设置、收藏和收听记录。请参阅[安装指南](docs/INSTALLING.md)。

## 功能

- 专辑、艺人、播放列表、收藏、收听记录和收听统计。
- 根据你的资料库生成的智能播放列表和电台。
- 按流派和情绪筛选歌曲：放松、活力满满、深夜、好心情、派对、情歌、伤感与沉思、暗黑、专注和怀旧。
- 内置于应用、在设备上运行的 VYBE 搜索，可按情绪、艺人或歌曲查找。
- 来自内嵌歌词、LRC 文件、缓存结果和 LRCLIB 的同步歌词，并支持离线下载。
- 队列管理、A–B 循环、书签、带审阅和还原的自动修复标签，以及在线或本地封面。
- 更高分辨率的封面和艺人照片升级，保存前会与当前图片进行比对。
- 过渡工作室，支持专辑、播放列表和曲目对的交叉淡化，曲线可编辑。
- 带预设的 8 段均衡器，以及可视化效果。
- 随封面配色的主题，浅色、深色和跟随系统外观，以及导入的主题包。
- 正在播放中可滑动的封面轮播，以及悬浮的玻璃迷你播放器。
- 应用内和锁定屏幕上的动态专辑封面（仅限动态封面版本）。
- 从电脑浏览器进行 Wi-Fi 传输，以及手动和自动检查新版本。

支持 MP3、AAC、ALAC、FLAC、WAV、AIFF、CAF 和 Ogg Vorbis/Opus，并在适用时支持 M4A、M4B 和 MP4 容器。不支持受保护（DRM）的文件。

## 截图

使用当前版本在 iPhone 上截取，展示的是真实界面。

### 主页与发现

<p>
  <a href="images/showcase/home.png#gh-dark-mode-only"><img src="images/showcase/home.png#gh-dark-mode-only" alt="erhu 主页，包含一个智能播放列表和精选电台，截于 iPhone" width="270"></a><a href="images/showcase/light/home.png#gh-light-mode-only"><img src="images/showcase/light/home.png#gh-light-mode-only" alt="erhu 主页，包含一个智能播放列表和精选电台，截于 iPhone" width="270"></a>
  <a href="images/showcase/radio.png#gh-dark-mode-only"><img src="images/showcase/radio.png#gh-dark-mode-only" alt="基于 A Sky Full of Stars 生成的 erhu 电台，截于 iPhone" width="270"></a><a href="images/showcase/light/radio.png#gh-light-mode-only"><img src="images/showcase/light/radio.png#gh-light-mode-only" alt="基于 A Sky Full of Stars 生成的 erhu 电台，截于 iPhone" width="270"></a>
  <a href="images/showcase/vybe.png#gh-dark-mode-only"><img src="images/showcase/vybe.png#gh-dark-mode-only" alt="erhu 的 VYBE 搜索“深夜”，显示资料库中最接近的歌曲，截于 iPhone" width="270"></a><a href="images/showcase/light/vybe.png#gh-light-mode-only"><img src="images/showcase/light/vybe.png#gh-light-mode-only" alt="erhu 的 VYBE 搜索“深夜”，显示资料库中最接近的歌曲，截于 iPhone" width="270"></a>
</p>

### 聆听体验

<p>
  <a href="images/showcase/now-playing.png#gh-dark-mode-only"><img src="images/showcase/now-playing.png#gh-dark-mode-only" alt="erhu 的正在播放，截于 iPhone" width="270"></a><a href="images/showcase/light/now-playing.png#gh-light-mode-only"><img src="images/showcase/light/now-playing.png#gh-light-mode-only" alt="erhu 的正在播放，截于 iPhone" width="270"></a>
  <a href="images/showcase/lyrics.png#gh-dark-mode-only"><img src="images/showcase/lyrics.png#gh-dark-mode-only" alt="erhu 的同步歌词，截于 iPhone" width="270"></a><a href="images/showcase/light/lyrics.png#gh-light-mode-only"><img src="images/showcase/light/lyrics.png#gh-light-mode-only" alt="erhu 的同步歌词，截于 iPhone" width="270"></a>
  <a href="images/showcase/queue.png#gh-dark-mode-only"><img src="images/showcase/queue.png#gh-dark-mode-only" alt="erhu 的队列，截于 iPhone" width="270"></a><a href="images/showcase/light/queue.png#gh-light-mode-only"><img src="images/showcase/light/queue.png#gh-light-mode-only" alt="erhu 的队列，截于 iPhone" width="270"></a>
</p>

### 你的资料库

<p>
  <a href="images/showcase/library.png#gh-dark-mode-only"><img src="images/showcase/library.png#gh-dark-mode-only" alt="erhu 资料库，包含播放列表、收藏和艺人，截于 iPhone" width="270"></a><a href="images/showcase/light/library.png#gh-light-mode-only"><img src="images/showcase/light/library.png#gh-light-mode-only" alt="erhu 资料库，包含播放列表、收藏和艺人，截于 iPhone" width="270"></a>
  <a href="images/showcase/albums.png#gh-dark-mode-only"><img src="images/showcase/albums.png#gh-dark-mode-only" alt="按最近播放排序的 erhu 专辑，截于 iPhone" width="270"></a><a href="images/showcase/light/albums.png#gh-light-mode-only"><img src="images/showcase/light/albums.png#gh-light-mode-only" alt="按最近播放排序的 erhu 专辑，截于 iPhone" width="270"></a>
  <a href="images/showcase/songs-moods.png#gh-dark-mode-only"><img src="images/showcase/songs-moods.png#gh-dark-mode-only" alt="按“深夜”情绪筛选的 erhu 歌曲，截于 iPhone" width="270"></a><a href="images/showcase/light/songs-moods.png#gh-light-mode-only"><img src="images/showcase/light/songs-moods.png#gh-light-mode-only" alt="按“深夜”情绪筛选的 erhu 歌曲，截于 iPhone" width="270"></a>
</p>

### 资料库工具

<p>
  <a href="images/showcase/artwork-fetcher.png#gh-dark-mode-only"><img src="images/showcase/artwork-fetcher.png#gh-dark-mode-only" alt="iPhone 上的封面获取工具，显示专辑和艺人照片检查" width="270"></a><a href="images/showcase/light/artwork-fetcher.png#gh-light-mode-only"><img src="images/showcase/light/artwork-fetcher.png#gh-light-mode-only" alt="iPhone 上的封面获取工具，显示专辑和艺人照片检查" width="270"></a>
  <a href="images/showcase/artwork-upgrade.png#gh-dark-mode-only"><img src="images/showcase/artwork-upgrade.png#gh-dark-mode-only" alt="iPhone 上的升级专辑封面，列出更高分辨率的匹配结果，并标出了一个动态封面" width="270"></a><a href="images/showcase/light/artwork-upgrade.png#gh-light-mode-only"><img src="images/showcase/light/artwork-upgrade.png#gh-light-mode-only" alt="iPhone 上的升级专辑封面，列出更高分辨率的匹配结果，并标出了一个动态封面" width="270"></a>
  <a href="images/showcase/auto-fix-tags.png#gh-dark-mode-only"><img src="images/showcase/auto-fix-tags.png#gh-dark-mode-only" alt="iPhone 上的自动修复标签，列出已匹配的曲目和待审阅的不确定匹配" width="270"></a><a href="images/showcase/light/auto-fix-tags.png#gh-light-mode-only"><img src="images/showcase/light/auto-fix-tags.png#gh-light-mode-only" alt="iPhone 上的自动修复标签，列出已匹配的曲目和待审阅的不确定匹配" width="270"></a>
</p>

### 音频控制与过渡工作室

<p>
  <a href="images/showcase/audio-controls.png#gh-dark-mode-only"><img src="images/showcase/audio-controls.png#gh-dark-mode-only" alt="使用“低音增强”预设的 erhu 均衡器，截于 iPhone" width="270"></a><a href="images/showcase/light/audio-controls.png#gh-light-mode-only"><img src="images/showcase/light/audio-controls.png#gh-light-mode-only" alt="使用“低音增强”预设的 erhu 均衡器，截于 iPhone" width="270"></a>
  <a href="images/showcase/transition-studio.png#gh-dark-mode-only"><img src="images/showcase/transition-studio.png#gh-dark-mode-only" alt="iPhone 上的过渡工作室，显示专辑设置和曲目对" width="270"></a><a href="images/showcase/light/transition-studio.png#gh-light-mode-only"><img src="images/showcase/light/transition-studio.png#gh-light-mode-only" alt="iPhone 上的过渡工作室，显示专辑设置和曲目对" width="270"></a>
  <a href="images/showcase/transition-editor.png#gh-dark-mode-only"><img src="images/showcase/transition-editor.png#gh-dark-mode-only" alt="iPhone 上的过渡编辑器，显示波形和可编辑的曲线" width="270"></a><a href="images/showcase/light/transition-editor.png#gh-light-mode-only"><img src="images/showcase/light/transition-editor.png#gh-light-mode-only" alt="iPhone 上的过渡编辑器，显示波形和可编辑的曲线" width="270"></a>
</p>

[专辑顺序与过渡](docs/ALBUM_ORDER_AND_TRANSITIONS.md) · 应用内的介绍会讲解这些工具；可在 **设置 → 关于 Erhu → 介绍** 中重新打开。

### 动态封面

<a href="images/showcase/animated-artwork.webp#gh-dark-mode-only"><img src="images/showcase/animated-artwork.webp#gh-dark-mode-only" alt="专辑页面、正在播放和锁定屏幕上的动态专辑封面，在 iPhone 上录制" width="1200"></a><a href="images/showcase/light/animated-artwork.webp#gh-light-mode-only"><img src="images/showcase/light/animated-artwork.webp#gh-light-mode-only" alt="专辑页面、正在播放和锁定屏幕上的动态专辑封面，在 iPhone 上录制" width="1200"></a>

动态封面仅在动态封面版本中提供。锁定屏幕需要 iOS 26 或更高版本，何时显示动画由 iOS 决定。

[动态封面的工作原理](https://github.com/MeezaUni/Erhu-iOS/blob/with-animated-art/docs/ANIMATED_COVERS.md)

## VYBE

VYBE 会根据你对想听内容的描述来搜索资料库，比如一种情绪、一位艺人或一首你喜欢的歌。它完全在你的 iPhone 上运行，不会上传任何内容，也没有云端推理。

在 iOS 上，VYBE 的模型已内置在应用中（经过压缩的 Core ML MiniLM，约 17 MB），因此无需下载。在介绍中或“设置”里打开它，然后在它为你的资料库建立索引时保持应用打开。请参阅 [VYBE 详情](docs/VYBE.md)。

## 构建

iOS 版 erhu 使用 Swift、SwiftUI、AVFoundation 和 Core Audio。

在装有 Xcode 和 [XcodeGen](https://github.com/yonaskolb/XcodeGen) 的 Mac 上：

```sh
xcodegen generate
open Erhu.xcodeproj
```

[iOS 工作流](.github/workflows/ios.yml) 会构建未签名的 IPA，并在模拟器上为两个封面分支运行原生交互检查和单元测试。请参阅[从源代码构建](docs/BUILDING.md)。

`without-animated-art` 是默认分支，构建静态封面版本；`with-animated-art` 构建动态版本。共享的更改会在两个分支上保持同步。

## 反馈

请通过 [GitHub Issues](https://github.com/MeezaUni/Erhu-iOS/issues) 报告问题。请注明你的 iPhone 机型、iOS 版本、应用版本、封面版本以及重现问题的步骤。

[参与贡献](CONTRIBUTING.md) · [安全政策](SECURITY.md) · [行为准则](CODE_OF_CONDUCT.md)

## 支持 erhu

erhu 免费，没有广告，也不需要账户。如果你愿意帮忙支付 Apple Developer 费用，让它能上架 App Store，可以[在 Ko-fi 上请我喝杯咖啡](https://ko-fi.com/kshitij38758)。同样的链接也在应用的 **设置 → 关于 Erhu** 中。

## 贡献者

- [Azeem](https://github.com/MeezaUni)（iOS）
- [Lochan Thakke](https://github.com/lochanpthakke) 和 [Kshitij Kekunnaya](https://github.com/Kshitij1425)，[Android 版 erhu](https://github.com/lochanpthakke/erhu) 的作者

## 许可证

[GNU General Public License v3.0](LICENSE)。源自 Android 的代码和 erhu 品牌的说明见[许可与授权](docs/LICENSING-PERMISSIONS.md)。第三方组件保留各自的许可证（[ThirdParty](ThirdParty/)）。

<!-- Built by Tools/readme_i18n/build.py from template.md and strings/<language>.json. Edit those and run the script instead of editing this file. -->
