# Android Client

## 서버 연동 빌드

기본 서버 주소는 `http://183.96.45.33:18080`이다. 다른 서버를 사용할 때는 소스를 수정하지 않고 Gradle 속성이나 환경변수로 덮어쓴다.

```powershell
.\gradlew.bat assembleDebug -PrecommendationBaseUrl=http://192.168.0.62:8080 -PkioskKey=LOCAL-DEVELOPMENT
```

환경변수 `RECOMMENDATION_BASE_URL`, `KIOSK_KEY`도 같은 용도로 사용할 수 있다.

현재 우선 구현 대상인 범용 Android 키오스크 클라이언트다.

## 예정 기술

- Kotlin
- Jetpack Compose
- Coroutines / Flow
- Room / SQLite
- CameraX
- MediaPipe
- ONNX Runtime
- Retrofit 또는 동등한 HTTP Adapter

## 실행 모드

- LOCAL
- REMOTE
- HYBRID

## 로컬 빌드

Android SDK 경로는 Git에 포함하지 않는 `local.properties` 또는 `ANDROID_HOME`으로 제공한다.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat build
```

## 현장 배포 APK

공개 GitHub Releases에는 전용 서명키로 만든 APK만 올린다. 디버그 APK는 현장 배포에 사용하지 않는다.
이 PC의 PowerShell 터미널에서 `build-distribution.ps1`을 실행하면, 최초 실행 시
전용 키를 대화형으로 만들고 암호를 화면에 표시하거나 파일에 저장하지 않은 채
`app/build/outputs/apk/release/app-release.apk`를 빌드한다. 키 파일과 암호는
각각 안전하게 백업해야 하며, 키를 잃으면 설치된 앱을 업데이트할 수 없다.

```powershell
.\build-distribution.ps1
```

GitHub Release의 첨부 파일명은 매 버전 `recommendation-android.apk`로 고정한다.
공개 저장소의 최신 정식 릴리즈 다운로드 주소를 QR 코드에 넣으면 인쇄한 QR을
재사용할 수 있다.

`https://github.com/anjekan/recommendation/releases/latest/download/recommendation-android.apk`

새 릴리즈는 이전 버전보다 높은 `versionCode`와 동일한 서명키로 빌드해야 한다.
기존 디버그 서명 앱은 전용 키 APK로 덮어쓸 수 없으므로 삭제 후 설치한다.

현재 골격의 임시 application ID는 `kr.co.ninetyseconds.recommendation`이다. 출시 식별자가 달라져야 한다면 배포 전에 ADR로 변경한다.

빌드는 Compose 애플리케이션과 플랫폼 독립적인 [`core/domain`](core/domain/) 순수 Kotlin 모듈로 나눈다. 데이터베이스와 통신 구현은 도메인 포트 뒤에 별도 Adapter 모듈로 추가한다.

기존 태안 Android 프로젝트를 이 디렉터리에 그대로 복사하지 않는다. 분석 모델과 알고리즘은 특성화 테스트 후 Adapter로 선별 이식한다.

현재 앱은 저장소의 `contracts/examples/project-config.json`을 기본 Asset으로 패키징한다. 최초 실행 시 설정을 검증한 뒤 Room 카탈로그를 교체하고 `LOCAL` 추천 테스트를 수행할 수 있으므로 서버 설정 없이 기동된다.
