<h1><img src="docs/branding/erhu-cube.png" alt="erhu 앱 아이콘" width="64"> <img src="docs/branding/erhu-wordmark-on-dark.png#gh-dark-mode-only" alt="erhu" width="95"><img src="docs/branding/erhu-wordmark-on-light.png#gh-light-mode-only" alt="erhu" width="95"> <sub>iOS용</sub></h1>

<sub>🌐 [English](README.md) · [العربية](README.ar.md) · [বাংলা](README.bn.md) · [Deutsch](README.de.md) · [Español](README.es.md) · [فارسی](README.fa.md) · [Filipino](README.fil.md) · [Français](README.fr.md) · [हिन्दी](README.hi.md) · [Bahasa Indonesia](README.id.md) · [Italiano](README.it.md) · [日本語](README.ja.md) · **한국어** · [Nederlands](README.nl.md) · [Polski](README.pl.md) · [Português (Brasil)](README.pt-BR.md) · [Русский](README.ru.md) · [தமிழ்](README.ta.md) · [తెలుగు](README.te.md) · [ไทย](README.th.md) · [Türkçe](README.tr.md) · [Українська](README.uk.md) · [اردو](README.ur.md) · [Tiếng Việt](README.vi.md) · [简体中文](README.zh-Hans.md) · [繁體中文](README.zh-Hant.md)</sub>

> 이 번역은 기계 번역입니다. 어색한 부분이 있다면 [이슈를 열거나](https://github.com/MeezaUni/Erhu-iOS/issues) [영어 README](README.md)를 읽어 주세요.

나만의 음악 컬렉션을 위한 iPhone 음악 플레이어.

<a href="https://github.com/MeezaUni/Erhu-iOS/releases"><img src="docs/badges/download-ios.svg" alt="iOS용 erhu 다운로드"></a>
<a href="https://github.com/lochanpthakke/erhu/releases"><img src="docs/badges/download-android.svg" alt="Android용 erhu 다운로드"></a>
<a href="https://ko-fi.com/kshitij38758"><img src="docs/badges/support-ko-fi.svg" alt="Ko-fi에서 erhu 후원하기"></a>

<img src="images/showcase/hero.png#gh-dark-mode-only" alt="iPhone에서 본 erhu 홈과 지금 재생 중 화면" width="1200"><img src="images/showcase/light/hero.png#gh-light-mode-only" alt="iPhone에서 본 erhu 홈과 지금 재생 중 화면" width="1200">

## 베타 3

세 번째 베타는 iOS 17 이상용 서명되지 않은 IPA로 제공되며, 사이드로딩 도구를 통해 본인의 Apple ID로 설치합니다. 다른 iPhone과 iOS 버전과의 호환성은 아직 테스트 중입니다.

두 가지 버전이 있습니다.

| 빌드 | 추가되는 기능 | 다운로드 |
| --- | --- | --- |
| **정지 아트워크** | 정지된 앨범 아트워크를 사용하는 전체 앱 | [IPA 다운로드](https://github.com/MeezaUni/Erhu-iOS/releases/download/v1.0-beta.3/Erhu-v1.0-beta.3-static.ipa) |
| **움직이는 아트워크** | 위의 모든 기능에 더해, 앱과 잠금 화면에서 다운로드한 움직이는 앨범 아트워크 | [IPA 다운로드](https://github.com/MeezaUni/Erhu-iOS/releases/download/v1.0-beta.3/Erhu-v1.0-beta.3-animated.ipa) |

[릴리스 노트](https://github.com/MeezaUni/Erhu-iOS/releases/tag/v1.0-beta.3) · 더 최신 개발 빌드: [정지](https://github.com/MeezaUni/Erhu-iOS/actions?query=workflow%3AiOS+branch%3Awithout-animated-art) · [움직임](https://github.com/MeezaUni/Erhu-iOS/actions?query=workflow%3AiOS+branch%3Awith-animated-art) (성공한 실행을 열고 **Erhu-ipa**를 다운로드하세요)

[SideStore](https://sidestore.io/) 또는 [AltStore Classic](https://altstore.io/)으로 IPA를 설치한 다음 **+**, 파일 앱 또는 **Wi-Fi 전송**으로 음악을 추가하세요. erhu는 iPhone에 저장된 음악을 재생하며, 라디오 스테이션과 스마트 플레이리스트는 사용자의 보관함에서 곡을 고릅니다.

무료 Apple ID를 사용한다면 **7일** 이내에 앱을 새로 고치세요. 일반적인 **활성 앱 3개** 제한에는 설치 도구도 포함됩니다. 기존 앱 위에 더 새로운 IPA를 설치하면 보관함, 설정, 즐겨찾기, 청취 기록이 그대로 유지됩니다. [설치 가이드](docs/INSTALLING.md)를 참고하세요.

## 기능

- 앨범, 아티스트, 플레이리스트, 즐겨찾기, 청취 기록과 청취 통계.
- 보관함으로 만드는 스마트 플레이리스트와 라디오 스테이션.
- 장르와 분위기로 거르는 곡: 칠, 에너지 넘치는, 늦은 밤, 기분 좋은, 파티, 사랑 노래, 슬프고 사색적인, 어두운, 집중, 추억의 노래.
- 앱에 내장된, 기기에서 실행되는 분위기·아티스트·곡 기반 VYBE 검색.
- 내장 가사, LRC 파일, 저장된 결과, LRCLIB에서 가져오는 동기화 가사와 오프라인 다운로드.
- 대기열 관리, A–B 반복, 북마크, 검토와 되돌리기가 있는 태그 자동 수정, 온라인 또는 로컬 아트워크.
- 저장하기 전에 현재 이미지와 비교하는 고해상도 아트워크와 아티스트 사진 업그레이드.
- 앨범, 플레이리스트, 곡 쌍별 크로스페이드와 편집 가능한 곡선을 갖춘 전환 스튜디오.
- 프리셋이 있는 8밴드 이퀄라이저와 시각화 도구.
- 아트워크 색상 테마, 라이트·다크·시스템 화면 모드, 가져온 테마 패키지.
- 지금 재생 중 화면에서 스와이프하는 아트워크 캐러셀과 떠 있는 유리 미니 플레이어.
- 앱과 잠금 화면의 움직이는 앨범 아트워크(움직이는 아트워크 빌드 전용).
- 컴퓨터 브라우저에서 하는 Wi-Fi 전송, 새 릴리스에 대한 수동 및 자동 업데이트 확인.

MP3, AAC, ALAC, FLAC, WAV, AIFF, CAF, Ogg Vorbis/Opus를 지원하며, 해당하는 경우 M4A, M4B, MP4 컨테이너도 지원합니다. 보호된(DRM) 파일은 지원하지 않습니다.

## 스크린샷

현재 빌드를 iPhone에서 촬영했습니다. 실제 화면입니다.

### 홈과 발견

<p>
  <a href="images/showcase/home.png#gh-dark-mode-only"><img src="images/showcase/home.png#gh-dark-mode-only" alt="스마트 플레이리스트와 추천 라디오 스테이션이 있는 erhu 홈(iPhone)" width="270"></a><a href="images/showcase/light/home.png#gh-light-mode-only"><img src="images/showcase/light/home.png#gh-light-mode-only" alt="스마트 플레이리스트와 추천 라디오 스테이션이 있는 erhu 홈(iPhone)" width="270"></a>
  <a href="images/showcase/radio.png#gh-dark-mode-only"><img src="images/showcase/radio.png#gh-dark-mode-only" alt="A Sky Full of Stars로 만든 erhu 라디오 스테이션(iPhone)" width="270"></a><a href="images/showcase/light/radio.png#gh-light-mode-only"><img src="images/showcase/light/radio.png#gh-light-mode-only" alt="A Sky Full of Stars로 만든 erhu 라디오 스테이션(iPhone)" width="270"></a>
  <a href="images/showcase/vybe.png#gh-dark-mode-only"><img src="images/showcase/vybe.png#gh-dark-mode-only" alt="erhu VYBE로 늦은 밤 분위기를 검색해 보관함에서 가장 가까운 곡을 보여 주는 화면(iPhone)" width="270"></a><a href="images/showcase/light/vybe.png#gh-light-mode-only"><img src="images/showcase/light/vybe.png#gh-light-mode-only" alt="erhu VYBE로 늦은 밤 분위기를 검색해 보관함에서 가장 가까운 곡을 보여 주는 화면(iPhone)" width="270"></a>
</p>

### 감상 경험

<p>
  <a href="images/showcase/now-playing.png#gh-dark-mode-only"><img src="images/showcase/now-playing.png#gh-dark-mode-only" alt="erhu의 지금 재생 중 화면(iPhone)" width="270"></a><a href="images/showcase/light/now-playing.png#gh-light-mode-only"><img src="images/showcase/light/now-playing.png#gh-light-mode-only" alt="erhu의 지금 재생 중 화면(iPhone)" width="270"></a>
  <a href="images/showcase/lyrics.png#gh-dark-mode-only"><img src="images/showcase/lyrics.png#gh-dark-mode-only" alt="erhu의 동기화 가사(iPhone)" width="270"></a><a href="images/showcase/light/lyrics.png#gh-light-mode-only"><img src="images/showcase/light/lyrics.png#gh-light-mode-only" alt="erhu의 동기화 가사(iPhone)" width="270"></a>
  <a href="images/showcase/queue.png#gh-dark-mode-only"><img src="images/showcase/queue.png#gh-dark-mode-only" alt="erhu의 대기열(iPhone)" width="270"></a><a href="images/showcase/light/queue.png#gh-light-mode-only"><img src="images/showcase/light/queue.png#gh-light-mode-only" alt="erhu의 대기열(iPhone)" width="270"></a>
</p>

### 나의 보관함

<p>
  <a href="images/showcase/library.png#gh-dark-mode-only"><img src="images/showcase/library.png#gh-dark-mode-only" alt="플레이리스트, 즐겨찾기, 아티스트가 있는 erhu 보관함(iPhone)" width="270"></a><a href="images/showcase/light/library.png#gh-light-mode-only"><img src="images/showcase/light/library.png#gh-light-mode-only" alt="플레이리스트, 즐겨찾기, 아티스트가 있는 erhu 보관함(iPhone)" width="270"></a>
  <a href="images/showcase/albums.png#gh-dark-mode-only"><img src="images/showcase/albums.png#gh-dark-mode-only" alt="최근 재생순으로 정렬한 erhu 앨범(iPhone)" width="270"></a><a href="images/showcase/light/albums.png#gh-light-mode-only"><img src="images/showcase/light/albums.png#gh-light-mode-only" alt="최근 재생순으로 정렬한 erhu 앨범(iPhone)" width="270"></a>
  <a href="images/showcase/songs-moods.png#gh-dark-mode-only"><img src="images/showcase/songs-moods.png#gh-dark-mode-only" alt="늦은 밤 분위기로 거른 erhu의 곡(iPhone)" width="270"></a><a href="images/showcase/light/songs-moods.png#gh-light-mode-only"><img src="images/showcase/light/songs-moods.png#gh-light-mode-only" alt="늦은 밤 분위기로 거른 erhu의 곡(iPhone)" width="270"></a>
</p>

### 보관함 도구

<p>
  <a href="images/showcase/artwork-fetcher.png#gh-dark-mode-only"><img src="images/showcase/artwork-fetcher.png#gh-dark-mode-only" alt="iPhone의 아트워크 가져오기 화면. 앨범과 아티스트 사진 확인" width="270"></a><a href="images/showcase/light/artwork-fetcher.png#gh-light-mode-only"><img src="images/showcase/light/artwork-fetcher.png#gh-light-mode-only" alt="iPhone의 아트워크 가져오기 화면. 앨범과 아티스트 사진 확인" width="270"></a>
  <a href="images/showcase/artwork-upgrade.png#gh-dark-mode-only"><img src="images/showcase/artwork-upgrade.png#gh-dark-mode-only" alt="iPhone의 앨범 아트워크 업그레이드 화면. 고해상도 후보와 표시된 움직이는 아트워크" width="270"></a><a href="images/showcase/light/artwork-upgrade.png#gh-light-mode-only"><img src="images/showcase/light/artwork-upgrade.png#gh-light-mode-only" alt="iPhone의 앨범 아트워크 업그레이드 화면. 고해상도 후보와 표시된 움직이는 아트워크" width="270"></a>
  <a href="images/showcase/auto-fix-tags.png#gh-dark-mode-only"><img src="images/showcase/auto-fix-tags.png#gh-dark-mode-only" alt="iPhone의 태그 자동 수정 화면. 일치한 곡과 검토가 필요한 불확실한 후보" width="270"></a><a href="images/showcase/light/auto-fix-tags.png#gh-light-mode-only"><img src="images/showcase/light/auto-fix-tags.png#gh-light-mode-only" alt="iPhone의 태그 자동 수정 화면. 일치한 곡과 검토가 필요한 불확실한 후보" width="270"></a>
</p>

### 오디오 설정과 전환 스튜디오

<p>
  <a href="images/showcase/audio-controls.png#gh-dark-mode-only"><img src="images/showcase/audio-controls.png#gh-dark-mode-only" alt="저음 강조 프리셋을 고른 erhu 이퀄라이저(iPhone)" width="270"></a><a href="images/showcase/light/audio-controls.png#gh-light-mode-only"><img src="images/showcase/light/audio-controls.png#gh-light-mode-only" alt="저음 강조 프리셋을 고른 erhu 이퀄라이저(iPhone)" width="270"></a>
  <a href="images/showcase/transition-studio.png#gh-dark-mode-only"><img src="images/showcase/transition-studio.png#gh-dark-mode-only" alt="iPhone의 전환 스튜디오. 앨범 설정과 곡 쌍" width="270"></a><a href="images/showcase/light/transition-studio.png#gh-light-mode-only"><img src="images/showcase/light/transition-studio.png#gh-light-mode-only" alt="iPhone의 전환 스튜디오. 앨범 설정과 곡 쌍" width="270"></a>
  <a href="images/showcase/transition-editor.png#gh-dark-mode-only"><img src="images/showcase/transition-editor.png#gh-dark-mode-only" alt="iPhone의 전환 편집기. 파형과 편집 가능한 곡선" width="270"></a><a href="images/showcase/light/transition-editor.png#gh-light-mode-only"><img src="images/showcase/light/transition-editor.png#gh-light-mode-only" alt="iPhone의 전환 편집기. 파형과 편집 가능한 곡선" width="270"></a>
</p>

[앨범 순서와 전환](docs/ALBUM_ORDER_AND_TRANSITIONS.md) · 앱 소개에서 이 도구들을 안내합니다. **설정 → Erhu 정보 → 소개**에서 다시 열 수 있습니다.

### 움직이는 아트워크

<a href="images/showcase/animated-artwork.webp#gh-dark-mode-only"><img src="images/showcase/animated-artwork.webp#gh-dark-mode-only" alt="앨범 페이지, 지금 재생 중 화면, 잠금 화면의 움직이는 앨범 아트워크(iPhone에서 녹화)" width="1200"></a><a href="images/showcase/light/animated-artwork.webp#gh-light-mode-only"><img src="images/showcase/light/animated-artwork.webp#gh-light-mode-only" alt="앨범 페이지, 지금 재생 중 화면, 잠금 화면의 움직이는 앨범 아트워크(iPhone에서 녹화)" width="1200"></a>

움직이는 아트워크는 움직이는 아트워크 빌드에만 있습니다. 잠금 화면에는 iOS 26 이상이 필요하며, 애니메이션을 언제 보여 줄지는 iOS가 정합니다.

[움직이는 아트워크 작동 방식](https://github.com/MeezaUni/Erhu-iOS/blob/with-animated-art/docs/ANIMATED_COVERS.md)

## VYBE

VYBE는 분위기, 아티스트, 좋아하는 곡처럼 듣고 싶은 것에 대한 설명으로 보관함을 검색합니다. 모든 처리는 iPhone에서만 이루어지며, 아무것도 업로드되지 않고 클라우드 추론도 없습니다.

iOS에서는 VYBE 모델이 앱에 내장되어 있으므로(압축된 Core ML MiniLM, 약 17MB) 따로 다운로드할 것이 없습니다. 소개 화면이나 설정에서 켠 다음, 보관함을 색인하는 동안 앱을 열어 두세요. [VYBE 자세히 보기](docs/VYBE.md)를 참고하세요.

## 빌드

iOS용 erhu는 Swift, SwiftUI, AVFoundation, Core Audio를 사용합니다.

Xcode와 [XcodeGen](https://github.com/yonaskolb/XcodeGen)이 설치된 Mac에서:

```sh
xcodegen generate
open Erhu.xcodeproj
```

[iOS 워크플로](.github/workflows/ios.yml)는 서명되지 않은 IPA를 빌드하고, 두 아트워크 브랜치 모두에 대해 시뮬레이터에서 네이티브 상호작용 검사와 단위 테스트를 실행합니다. [소스에서 빌드하기](docs/BUILDING.md)를 참고하세요.

`without-animated-art`는 기본 브랜치이며 정지 아트워크 버전을 빌드하고, `with-animated-art`는 움직이는 버전을 빌드합니다. 공통 변경 사항은 두 브랜치에 똑같이 유지됩니다.

## 피드백

버그는 [GitHub Issues](https://github.com/MeezaUni/Erhu-iOS/issues)로 알려 주세요. iPhone 모델, iOS 버전, 앱 버전, 아트워크 버전, 문제를 재현하는 단계를 함께 적어 주세요.

[기여하기](CONTRIBUTING.md) · [보안 정책](SECURITY.md) · [행동 강령](CODE_OF_CONDUCT.md)

## erhu 후원하기

erhu는 광고도 계정도 없는 무료 앱입니다. App Store에 올릴 수 있도록 Apple Developer 비용을 함께 부담해 주고 싶다면 [Ko-fi에서 커피 한 잔 사 주세요](https://ko-fi.com/kshitij38758). 같은 링크가 앱의 **설정 → Erhu 정보**에도 있습니다.

## 기여자

- [Azeem](https://github.com/MeezaUni) (iOS)
- [Lochan Thakke](https://github.com/lochanpthakke)와 [Kshitij Kekunnaya](https://github.com/Kshitij1425), [Android용 erhu](https://github.com/lochanpthakke/erhu) 제작자

## 라이선스

[GNU General Public License v3.0](LICENSE). Android에서 가져온 코드와 erhu 브랜드는 [라이선스 및 허가](docs/LICENSING-PERMISSIONS.md)에 정리되어 있습니다. 서드파티 구성 요소는 각자의 라이선스를 따릅니다([ThirdParty](ThirdParty/)).

<!-- Built by Tools/readme_i18n/build.py from template.md and strings/<language>.json. Edit those and run the script instead of editing this file. -->
