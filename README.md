# noltalk
## 놀톡 — 수다 떨다가, 바로 한판!
채팅방에서 대화하며 가위바위보·끝말잇기·오목·빙고를 하는 메신저

- 웹: https://thkim3589-web.github.io/noltalk/
- 안드로이드 앱: [Releases](../../releases/latest)에서 `noltalk.apk` 다운로드

### 구조
- `index.html` 등 루트 파일: 웹앱 (GitHub Pages로 서비스, Firebase 사용)
- `android/`: 웹앱을 띄우는 안드로이드 앱 — `android/**`가 바뀌면 GitHub Actions가 APK를 자동 빌드
- 웹 파일만 고치면 앱도 바로 반영됨 (APK 재설치 필요 없음)
