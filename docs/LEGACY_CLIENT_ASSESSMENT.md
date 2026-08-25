# 기존 Android 클라이언트 해체 분석

## 원본

```text
C:\90\TaeAn\Kioskondevice_servering
```

단일 `app` 모듈 Android 프로젝트이며 Gradle 전체 빌드는 성공했다.

## 규모

주요 Kotlin 파일:

| 파일 | 줄 수 | 역할 |
|---|---:|---|
| `MainActivity.kt` | 약 620 | 카메라, 분석 화면, 상태, 서버 취소, 화면 이동 |
| `StartActivity.kt` | 약 383 | WebView 시작 화면, 개인정보, 서버 사용자 생성, 업데이트 |
| `FaceAnalyzer.kt` | 약 296 | MediaPipe, 얼굴 선택, RGB, 감정, AU, 생체 신호 |
| `SignalProcessor.kt` | 약 220 | 심박·호흡 신호 처리 |
| `ResultActivity.kt` | 약 214 | 결과 WebView, 서버 추천, 문자, 화면 초기화 |
| `EmotionClassifier.kt` | 약 97 | ONNX 감정 추론 |

주요 Web 파일:

| 파일 | 줄 수 | 역할 |
|---|---:|---|
| `Page_start.html` | 약 698 | 시작 UI와 입력 |
| `result_combined.html` | 약 534 | 분석 결과와 추천 |
| `result_step3.html` | 약 404 | 태안 지도와 장소 마커 |
| `translations.js` | 약 145 | 감정·꽃·정원·다국어 데이터 |

실질적인 기능 테스트는 없고 Android Studio 기본 예제 테스트만 존재한다.

## 현재 서버 API

```text
POST  /api/v1/user
PATCH /api/v1/user
POST  /api/visit/cancel
POST  /api/visit/send-link
```

서버 모델은 개인정보, 꽃, 정원, 문자 발송에 고정되어 있으므로 새 공통 계약으로 교체한다.

## 재사용 판정

| 구성요소 | 판정 | 조치 |
|---|---|---|
| `emotion-ferplus-8.onnx` | 재사용 | 체크섬·전처리·정확도 검증 |
| `face_landmarker.task` | 재사용 | 모델 버전 기록 |
| `SignalProcessor` | 검증 후 재사용 | Android 의존 제거, 고정 입력 테스트 |
| `EmotionClassifier` | 감싸서 재사용 | 포트 분리, 전처리·confidence 개선 |
| `FaceAnalyzer` | 부분 재사용 | 여러 책임으로 분해 |
| `FaceOverlayView` | 참고 또는 재사용 | 새 Compose/Preview UI와 검토 |
| Activity 3종 | 재작성 | 새 Navigation과 상태 모델 사용 |
| Retrofit API 모델 | 폐기 | 범용 OpenAPI 계약으로 재정의 |
| WebView HTML | 디자인 참고 | 범용 Compose UI로 재구성 |
| `translations.js` | 데이터 변환 | 태안 프로젝트 설정 JSON으로 변환 |
| 태안 지도·이미지 | 콘텐츠 자산 | 프로젝트별 asset으로 이동 |

## 발견된 주요 문제

### 책임 결합

Activity와 FaceAnalyzer에 UI, 시스템, 분석, 서버 통신이 혼재한다.

### 도메인 하드코딩

- 꽃 38종
- 정원 15개
- 태안 지도
- 장소 좌표
- 꽃·정원 전용 서버 필드
- 행사 전용 문구

### 서버 연동 취약점

- 서버 응답을 결과 화면이 고정 1초 후 한 번만 읽음
- 서버 지연 시 임의 로컬 추천으로 전환
- 사용자 JSON의 `user_name`과 결과 화면의 `name` 불일치
- 문자 발송 성공·실패 무시
- 취소 API 경로가 추정 상태

### 분석 검증 부족

- SignalProcessor가 30fps와 특정 신호 범위를 가정
- ONNX 입력 정규화가 모델과 일치하는지 불명확
- 입력 노드 이름 고정
- 감정 confidence 없음
- 실측 장비 대비 생체 신호 정확도 테스트 없음
- 장시간 메모리와 자원 해제 테스트 없음

## 결론

권장 비율:

```text
신규 프로젝트·구조: 70~80%
기존 분석·콘텐츠 자산 재사용: 20~30%
```

기존 프로젝트는 직접 대규모 수정하지 않고 비교 기준과 자산 보관소로 유지한다. 신규 앱에서 가짜 분석기로 전체 흐름을 먼저 완성한 후 분석 모델과 알고리즘을 단계적으로 이식한다.
