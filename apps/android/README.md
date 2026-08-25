# Android Client

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

현재 골격의 임시 application ID는 `kr.co.ninetyseconds.recommendation`이다. 출시 식별자가 달라져야 한다면 배포 전에 ADR로 변경한다.

기존 태안 Android 프로젝트를 이 디렉터리에 그대로 복사하지 않는다. 분석 모델과 알고리즘은 특성화 테스트 후 Adapter로 선별 이식한다.
