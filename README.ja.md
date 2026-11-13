<h1><img src="docs/branding/erhu-cube.png" alt="erhuのアプリアイコン" width="64"> <img src="docs/branding/erhu-wordmark-on-dark.png#gh-dark-mode-only" alt="erhu" width="95"><img src="docs/branding/erhu-wordmark-on-light.png#gh-light-mode-only" alt="erhu" width="95"> <sub>iOS版</sub></h1>

<sub>🌐 [English](README.md) · [العربية](README.ar.md) · [বাংলা](README.bn.md) · [Deutsch](README.de.md) · [Español](README.es.md) · [فارسی](README.fa.md) · [Filipino](README.fil.md) · [Français](README.fr.md) · [हिन्दी](README.hi.md) · [Bahasa Indonesia](README.id.md) · [Italiano](README.it.md) · **日本語** · [한국어](README.ko.md) · [Nederlands](README.nl.md) · [Polski](README.pl.md) · [Português (Brasil)](README.pt-BR.md) · [Русский](README.ru.md) · [தமிழ்](README.ta.md) · [తెలుగు](README.te.md) · [ไทย](README.th.md) · [Türkçe](README.tr.md) · [Українська](README.uk.md) · [اردو](README.ur.md) · [Tiếng Việt](README.vi.md) · [简体中文](README.zh-Hans.md) · [繁體中文](README.zh-Hant.md)</sub>

> この翻訳は機械翻訳です。おかしな箇所があれば[issueを作成](https://github.com/MeezaUni/Erhu-iOS/issues)するか、[英語のREADME](README.md)をご覧ください。

自分のコレクションのためのiPhone用音楽プレーヤー。

<a href="https://github.com/MeezaUni/Erhu-iOS/releases"><img src="docs/badges/download-ios.svg" alt="iOS版erhuをダウンロード"></a>
<a href="https://github.com/lochanpthakke/erhu/releases"><img src="docs/badges/download-android.svg" alt="Android版erhuをダウンロード"></a>
<a href="https://ko-fi.com/kshitij38758"><img src="docs/badges/support-ko-fi.svg" alt="Ko-fiでerhuを支援"></a>

<img src="images/showcase/hero.png#gh-dark-mode-only" alt="iPhoneで表示したerhuのホームと再生中画面" width="1200"><img src="images/showcase/light/hero.png#gh-light-mode-only" alt="iPhoneで表示したerhuのホームと再生中画面" width="1200">

## ベータ3

3回目のベータは、iOS 17以降向けの署名なしIPAとして提供しています。サイドロードツールを使い、自分のApple IDでインストールします。ほかのiPhoneやiOSバージョンとの互換性はまだテスト中です。

2つのバリエーションがあります：

| ビルド | 追加される内容 | ダウンロード |
| --- | --- | --- |
| **静止アートワーク** | 静止したアルバムアートワーク付きのアプリ全体 | [IPAをダウンロード](https://github.com/MeezaUni/Erhu-iOS/releases/download/v1.0-beta.3/Erhu-v1.0-beta.3-static.ipa) |
| **アニメーションアートワーク** | 上記のすべてに加え、アプリ内とロック画面でダウンロード済みのアニメーションアルバムアートワーク | [IPAをダウンロード](https://github.com/MeezaUni/Erhu-iOS/releases/download/v1.0-beta.3/Erhu-v1.0-beta.3-animated.ipa) |

[リリースノート](https://github.com/MeezaUni/Erhu-iOS/releases/tag/v1.0-beta.3) · より新しい開発ビルド： [静止](https://github.com/MeezaUni/Erhu-iOS/actions?query=workflow%3AiOS+branch%3Awithout-animated-art) · [アニメーション](https://github.com/MeezaUni/Erhu-iOS/actions?query=workflow%3AiOS+branch%3Awith-animated-art) （成功した実行を開き、**Erhu-ipa**をダウンロード）

[SideStore](https://sidestore.io/)または[AltStore Classic](https://altstore.io/)でIPAをインストールし、**+**、“ファイル”App、または**Wi-Fi転送**で音楽を追加します。erhuはiPhoneに保存された音楽を再生します。ラジオステーションとスマートプレイリストは、あなたのライブラリから曲を選びます。

無料のApple IDでは、**7日**以内にアプリを更新してください。通常の上限である**アクティブなApp 3個**には、インストーラも含まれます。既存のアプリに新しいIPAを上書きインストールすると、ライブラリ、設定、お気に入り、再生履歴はそのまま残ります。[インストールガイド](docs/INSTALLING.md)を参照してください。

## 機能

- アルバム、アーティスト、プレイリスト、お気に入り、再生履歴、再生統計。
- ライブラリから作るスマートプレイリストとラジオステーション。
- ジャンルとムードで絞り込む曲：チル、ハイエナジー、深夜、気分が上がる、パーティー、ラブソング、切ない・内省的、ダーク、集中、懐かしの曲。
- アプリに組み込まれた、ムード・アーティスト・曲によるデバイス上のVYBE検索。
- 埋め込み歌詞、LRCファイル、保存済みの結果、LRCLIBからの同期歌詞。オフライン用のダウンロードにも対応。
- キューの管理、A–Bリピート、ブックマーク、確認と取り消しができるタグの自動修正、オンラインまたはローカルのアートワーク。
- 保存前に現在の画像と照合する、より高解像度のアートワークとアーティスト写真への差し替え。
- アルバム・プレイリスト・曲のペアごとのクロスフェードと、編集できるカーブを備えたトランジションスタジオ。
- プリセット付き8バンドイコライザとビジュアライザ。
- アートワークの色に合わせたテーマ、ライト・ダーク・システムの外観、読み込んだテーマパッケージ。
- 再生中画面のスワイプできるアートワークカルーセルと、浮かぶガラス風ミニプレーヤー。
- アプリ内とロック画面のアニメーションアルバムアートワーク（アニメーションアートワーク版のみ）。
- コンピュータのブラウザからのWi-Fi転送と、新しいリリースの手動・自動アップデート確認。

MP3、AAC、ALAC、FLAC、WAV、AIFF、CAF、Ogg Vorbis/Opusに対応し、必要に応じてM4A、M4B、MP4コンテナにも対応します。保護された（DRM）ファイルには対応していません。

## スクリーンショット

現在のビルドをiPhoneで撮影したものです。実際の画面を写しています。

### ホームと発見

<p>
  <a href="images/showcase/home.png#gh-dark-mode-only"><img src="images/showcase/home.png#gh-dark-mode-only" alt="スマートプレイリストとおすすめのラジオステーションがあるerhuのホーム（iPhoneで撮影）" width="270"></a><a href="images/showcase/light/home.png#gh-light-mode-only"><img src="images/showcase/light/home.png#gh-light-mode-only" alt="スマートプレイリストとおすすめのラジオステーションがあるerhuのホーム（iPhoneで撮影）" width="270"></a>
  <a href="images/showcase/radio.png#gh-dark-mode-only"><img src="images/showcase/radio.png#gh-dark-mode-only" alt="A Sky Full of Starsから作ったerhuのラジオステーション（iPhoneで撮影）" width="270"></a><a href="images/showcase/light/radio.png#gh-light-mode-only"><img src="images/showcase/light/radio.png#gh-light-mode-only" alt="A Sky Full of Starsから作ったerhuのラジオステーション（iPhoneで撮影）" width="270"></a>
  <a href="images/showcase/vybe.png#gh-dark-mode-only"><img src="images/showcase/vybe.png#gh-dark-mode-only" alt="erhuのVYBEで深夜向けの曲を検索し、ライブラリの近い曲を表示（iPhoneで撮影）" width="270"></a><a href="images/showcase/light/vybe.png#gh-light-mode-only"><img src="images/showcase/light/vybe.png#gh-light-mode-only" alt="erhuのVYBEで深夜向けの曲を検索し、ライブラリの近い曲を表示（iPhoneで撮影）" width="270"></a>
</p>

### 音楽を聴く

<p>
  <a href="images/showcase/now-playing.png#gh-dark-mode-only"><img src="images/showcase/now-playing.png#gh-dark-mode-only" alt="erhuの再生中画面（iPhoneで撮影）" width="270"></a><a href="images/showcase/light/now-playing.png#gh-light-mode-only"><img src="images/showcase/light/now-playing.png#gh-light-mode-only" alt="erhuの再生中画面（iPhoneで撮影）" width="270"></a>
  <a href="images/showcase/lyrics.png#gh-dark-mode-only"><img src="images/showcase/lyrics.png#gh-dark-mode-only" alt="erhuの同期歌詞（iPhoneで撮影）" width="270"></a><a href="images/showcase/light/lyrics.png#gh-light-mode-only"><img src="images/showcase/light/lyrics.png#gh-light-mode-only" alt="erhuの同期歌詞（iPhoneで撮影）" width="270"></a>
  <a href="images/showcase/queue.png#gh-dark-mode-only"><img src="images/showcase/queue.png#gh-dark-mode-only" alt="erhuのキュー（iPhoneで撮影）" width="270"></a><a href="images/showcase/light/queue.png#gh-light-mode-only"><img src="images/showcase/light/queue.png#gh-light-mode-only" alt="erhuのキュー（iPhoneで撮影）" width="270"></a>
</p>

### あなたのライブラリ

<p>
  <a href="images/showcase/library.png#gh-dark-mode-only"><img src="images/showcase/library.png#gh-dark-mode-only" alt="プレイリスト、お気に入り、アーティストが並ぶerhuのライブラリ（iPhoneで撮影）" width="270"></a><a href="images/showcase/light/library.png#gh-light-mode-only"><img src="images/showcase/light/library.png#gh-light-mode-only" alt="プレイリスト、お気に入り、アーティストが並ぶerhuのライブラリ（iPhoneで撮影）" width="270"></a>
  <a href="images/showcase/albums.png#gh-dark-mode-only"><img src="images/showcase/albums.png#gh-dark-mode-only" alt="最近再生した順に並べたerhuのアルバム（iPhoneで撮影）" width="270"></a><a href="images/showcase/light/albums.png#gh-light-mode-only"><img src="images/showcase/light/albums.png#gh-light-mode-only" alt="最近再生した順に並べたerhuのアルバム（iPhoneで撮影）" width="270"></a>
  <a href="images/showcase/songs-moods.png#gh-dark-mode-only"><img src="images/showcase/songs-moods.png#gh-dark-mode-only" alt="ムード「深夜」で絞り込んだerhuの曲（iPhoneで撮影）" width="270"></a><a href="images/showcase/light/songs-moods.png#gh-light-mode-only"><img src="images/showcase/light/songs-moods.png#gh-light-mode-only" alt="ムード「深夜」で絞り込んだerhuの曲（iPhoneで撮影）" width="270"></a>
</p>

### ライブラリツール

<p>
  <a href="images/showcase/artwork-fetcher.png#gh-dark-mode-only"><img src="images/showcase/artwork-fetcher.png#gh-dark-mode-only" alt="iPhoneのアートワーク取得画面。アルバムとアーティスト写真のチェック" width="270"></a><a href="images/showcase/light/artwork-fetcher.png#gh-light-mode-only"><img src="images/showcase/light/artwork-fetcher.png#gh-light-mode-only" alt="iPhoneのアートワーク取得画面。アルバムとアーティスト写真のチェック" width="270"></a>
  <a href="images/showcase/artwork-upgrade.png#gh-dark-mode-only"><img src="images/showcase/artwork-upgrade.png#gh-dark-mode-only" alt="iPhoneのアルバムアートワークの差し替え画面。より高解像度の候補と、印の付いたアニメーションアートワーク" width="270"></a><a href="images/showcase/light/artwork-upgrade.png#gh-light-mode-only"><img src="images/showcase/light/artwork-upgrade.png#gh-light-mode-only" alt="iPhoneのアルバムアートワークの差し替え画面。より高解像度の候補と、印の付いたアニメーションアートワーク" width="270"></a>
  <a href="images/showcase/auto-fix-tags.png#gh-dark-mode-only"><img src="images/showcase/auto-fix-tags.png#gh-dark-mode-only" alt="iPhoneのタグの自動修正画面。一致した曲と、確認が必要な不確かな候補" width="270"></a><a href="images/showcase/light/auto-fix-tags.png#gh-light-mode-only"><img src="images/showcase/light/auto-fix-tags.png#gh-light-mode-only" alt="iPhoneのタグの自動修正画面。一致した曲と、確認が必要な不確かな候補" width="270"></a>
</p>

### オーディオ設定とトランジションスタジオ

<p>
  <a href="images/showcase/audio-controls.png#gh-dark-mode-only"><img src="images/showcase/audio-controls.png#gh-dark-mode-only" alt="プリセット「低音ブースト」を選んだerhuのイコライザ（iPhoneで撮影）" width="270"></a><a href="images/showcase/light/audio-controls.png#gh-light-mode-only"><img src="images/showcase/light/audio-controls.png#gh-light-mode-only" alt="プリセット「低音ブースト」を選んだerhuのイコライザ（iPhoneで撮影）" width="270"></a>
  <a href="images/showcase/transition-studio.png#gh-dark-mode-only"><img src="images/showcase/transition-studio.png#gh-dark-mode-only" alt="iPhoneのトランジションスタジオ。アルバムの設定と曲のペア" width="270"></a><a href="images/showcase/light/transition-studio.png#gh-light-mode-only"><img src="images/showcase/light/transition-studio.png#gh-light-mode-only" alt="iPhoneのトランジションスタジオ。アルバムの設定と曲のペア" width="270"></a>
  <a href="images/showcase/transition-editor.png#gh-dark-mode-only"><img src="images/showcase/transition-editor.png#gh-dark-mode-only" alt="iPhoneのトランジションエディタ。波形と編集できるカーブ" width="270"></a><a href="images/showcase/light/transition-editor.png#gh-light-mode-only"><img src="images/showcase/light/transition-editor.png#gh-light-mode-only" alt="iPhoneのトランジションエディタ。波形と編集できるカーブ" width="270"></a>
</p>

[アルバムの順番とトランジション](docs/ALBUM_ORDER_AND_TRANSITIONS.md) · これらのツールはアプリ内のイントロダクションで紹介しています。**設定 → Erhuについて → イントロダクション**からもう一度開けます。

### アニメーションアートワーク

<a href="images/showcase/animated-artwork.webp#gh-dark-mode-only"><img src="images/showcase/animated-artwork.webp#gh-dark-mode-only" alt="アルバムページ、再生中画面、ロック画面のアニメーションアルバムアートワーク（iPhoneで録画）" width="1200"></a><a href="images/showcase/light/animated-artwork.webp#gh-light-mode-only"><img src="images/showcase/light/animated-artwork.webp#gh-light-mode-only" alt="アルバムページ、再生中画面、ロック画面のアニメーションアルバムアートワーク（iPhoneで録画）" width="1200"></a>

アニメーションアートワークはアニメーションアートワーク版にのみ含まれます。ロック画面にはiOS 26以降が必要で、アニメーションを表示するタイミングはiOSが決めます。

[アニメーションアートワークの仕組み](https://github.com/MeezaUni/Erhu-iOS/blob/with-animated-art/docs/ANIMATED_COVERS.md)

## VYBE

VYBEは、ムードやアーティスト、好きな曲など、聴きたいものの説明をもとにライブラリを検索します。処理はすべてiPhone上で行われ、何もアップロードされず、クラウドでの推論もありません。

iOSでは、VYBEのモデルがアプリに組み込まれているため（圧縮したCore MLのMiniLM、約17 MB）、ダウンロードは不要です。イントロダクションか設定でオンにし、ライブラリのインデックス作成中はアプリを開いたままにしてください。[VYBEの詳細](docs/VYBE.md)を参照してください。

## ビルド

iOS版erhuは、Swift、SwiftUI、AVFoundation、Core Audioを使用しています。

Xcodeと[XcodeGen](https://github.com/yonaskolb/XcodeGen)を入れたMacで：

```sh
xcodegen generate
open Erhu.xcodeproj
```

[iOSワークフロー](.github/workflows/ios.yml)は署名なしのIPAをビルドし、両方のアートワークブランチについて、ネイティブ操作のチェックとユニットテストをシミュレータで実行します。[ソースからのビルド](docs/BUILDING.md)を参照してください。

`without-animated-art`はデフォルトのブランチで、静止アートワーク版をビルドします。`with-animated-art`はアニメーション版をビルドします。共通の変更は両方で同期しています。

## フィードバック

不具合は[GitHub Issues](https://github.com/MeezaUni/Erhu-iOS/issues)で報告してください。iPhoneの機種、iOSのバージョン、アプリのバージョン、アートワークの種類、問題を再現する手順を書き添えてください。

[コントリビュート](CONTRIBUTING.md) · [セキュリティポリシー](SECURITY.md) · [行動規範](CODE_OF_CONDUCT.md)

## erhuを支援する

erhuは無料で、広告もアカウントもありません。App Storeに出すためのApple Developer費用を一緒にまかなってくださる方は、[Ko-fiでコーヒーをおごって](https://ko-fi.com/kshitij38758)いただけるとうれしいです。同じリンクはアプリの**設定 → Erhuについて**にもあります。

## コントリビューター

- [Azeem](https://github.com/MeezaUni)（iOS）
- [Lochan Thakke](https://github.com/lochanpthakke)と[Kshitij Kekunnaya](https://github.com/Kshitij1425)（[Android版erhu](https://github.com/lochanpthakke/erhu)の作者）

## ライセンス

[GNU General Public License v3.0](LICENSE)。Androidから引き継いだコードとerhuのブランドについては、[ライセンスと許諾](docs/LICENSING-PERMISSIONS.md)に記載しています。サードパーティのコンポーネントは、それぞれ独自のライセンスに従います（[ThirdParty](ThirdParty/)）。

<!-- Built by Tools/readme_i18n/build.py from template.md and strings/<language>.json. Edit those and run the script instead of editing this file. -->
