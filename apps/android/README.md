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

Android 프로젝트 생성 전 `contracts/`와 `docs/`의 계약·아키텍처를 먼저 확정한다. 기존 태안 Android 프로젝트를 이 디렉터리에 그대로 복사하지 않는다.
