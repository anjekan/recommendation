# 기존 Android 기준선

## 목적

신규 Android 클라이언트가 기존 앱의 분석·화면 흐름을 의도치 않게 훼손하지 않도록 비교 기준을 고정한다. 기존 프로젝트는 Git 저장소가 아니므로 파일 체크섬, 빌드 설정, 데이터 흐름과 관찰된 동작을 기준으로 사용한다.

## 원본 위치

```text
C:\90\TaeAn\Kioskondevice_servering
```

원본은 신규 저장소에 복사하거나 직접 수정하지 않는다. 검증된 분석 자산을 이식할 때는 원본 체크섬과 출처를 기록한다.

## 빌드 기준

| 항목 | 값 |
|---|---|
| Application ID | `com.example.kioskondevice` |
| Version | `1.9` (`versionCode` 10) |
| Gradle | 9.1.0 |
| Android Gradle Plugin | 9.0.0 |
| Compile SDK | 36 |
| Target SDK | 36 |
| Minimum SDK | 24 |
| Gradle 실행 JDK | Android Studio Embedded JDK 17 |
| Java source/target | 11 |
| Kotlin 소스 | 10개 파일, 2,045줄 |
| HTML/JS | 7개 파일, 2,250줄 |

주요 라이브러리:

| 용도 | 라이브러리 |
|---|---|
| Camera | CameraX 1.3.0 |
| Face landmarks | MediaPipe Tasks Vision 0.10.0 |
| Emotion inference | ONNX Runtime Android 1.16.0 |
| Network | Retrofit 2.9.0 |
| Charts | MPAndroidChart 3.1.0 |

2026-08-21 기준 원본 설정과 동일한 API 36 구성의 Gradle `build`가 성공했다. 단위 테스트 1개는 Android Studio 기본 예제이며 기능 정확성을 검증하지 않는다. Lint는 오류 0개, 경고 95개였다.

## 자산 체크섬

SHA-256:

| 자산 | 체크섬 |
|---|---|
| `emotion-ferplus-8.onnx` | `A2A2BA6A335A3B29C21ACB6272F962BD3D47F84952AAFFA03B60986E04EFA61C` |
| `face_landmarker.task` | `64184E229B263107BC2B804C6625DB1341FF2BB731874B0BCC2FE6544E0BC9FF` |
| 기존 `app-debug.apk` | `3DA5351BC47FC351493C3BE8414F5476F1E69C19A5B99A223D7C736DFC8F118F` |

검증:

```powershell
.\scripts\verify-legacy-baseline.ps1 -LegacyRoot C:\90\TaeAn\Kioskondevice_servering
```

## 사용자 흐름

```text
StartActivity / Page_start.html
→ 개인정보 또는 익명 시작
→ POST /api/v1/user
→ MainActivity
→ CameraX + MediaPipe + ONNX + SignalProcessor
→ ResultActivity / result_combined.html
→ PATCH /api/v1/user
→ 서버 추천 또는 로컬 꽃 fallback
→ result_step3.html 지도
→ StartActivity 초기화
```

신규 앱에서는 개인정보·문자 발송·기존 사용자 생성 API를 제거하고 익명 세션을 사용한다.

## 분석 기준

### 감정

FER+ 출력 라벨:

```text
Neutral, Happy, Surprise, Sad, Anger, Disgust, Fear, Contempt
```

기존 구현은 30프레임마다 얼굴 crop을 ONNX 모델에 전달하고 최빈 감정을 최종 결과로 사용한다. `Neutral`, `Error`, `Calibrating...`은 최종 감정 후보 수집에서 제외된다.

### 심박·호흡

- 얼굴 랜드마크 123번 주변 10×10 픽셀의 RGB 평균을 사용한다.
- 최대 300샘플을 유지한다.
- 150샘플 이후 15샘플마다 계산한다.
- POS 기반 신호와 자체 FFT를 사용한다.
- 심박 허용 범위: 55~170 BPM
- 호흡 허용 범위: 10~35 RPM
- 최근 유효 결과 10개의 평균을 표시한다.

### 스트레스

```text
stress = ((heartRate + respiratoryRate × 2) / 2.5)
```

감정이 `Anger`, `Fear`, `Sad`이면 15를 더하고 결과를 0~100으로 제한한다.

이 계산은 의학적 검증 결과가 아니라 기존 앱 동작의 특성화 기준이다. 신규 앱에서 변경하려면 알고리즘 버전과 회귀 테스트를 함께 갱신한다.

## 기존 API

```text
POST  /api/v1/user
PATCH /api/v1/user
POST  /api/visit/cancel
POST  /api/visit/send-link
```

이 API는 개인정보, 꽃, 정원과 문자 발송에 결합되어 신규 시스템 계약으로 대체한다.

## 알려진 문제

- 서버 결과를 화면이 고정 1초 후 한 번만 조회한다.
- 서버 지연 시 무작위 로컬 꽃 추천으로 전환된다.
- `user_name`과 결과 화면의 `name` 키가 불일치한다.
- 취소·문자 API 규격이 확정되지 않았다.
- ONNX 입력의 0~255 범위가 모델 기대 전처리와 일치하는지 검증되지 않았다.
- 감정 confidence를 사용하지 않는다.
- rPPG 심박·호흡을 실제 측정 장비와 비교한 자료가 없다.
- 카메라·분석·UI·네트워크가 Activity와 Analyzer에 결합되어 있다.
- 장시간 메모리, 발열과 자원 해제 테스트가 없다.

## 아직 확보해야 할 기준 자료

- 실제 운영 태블릿 제조사·모델·해상도
- 목표 Android 버전
- 전면 카메라 해상도와 평균 FPS
- 기존 앱의 화면별 실기기 캡처 또는 영상
- 동일 피험자의 기준 심박·호흡 측정값
- 조명·거리·움직임별 분석 결과
- 목표 정확도와 허용 오차
- 분석 제한 시간과 자동 초기화 시간의 최종 운영값

위 항목이 없으면 신규 분석 엔진의 정확도 동등성을 확정할 수 없다. 확보 전까지는 기존 계산 결과를 기능적 특성화 기준으로만 사용한다.
