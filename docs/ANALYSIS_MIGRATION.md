# 분석 엔진 이식 계획

## 원칙

기존 `FaceAnalyzer`를 그대로 복사하지 않는다. 다음 책임을 독립 Adapter와 순수 계산부로 분리한다.

```text
CameraX frame source
  -> frame rotation / lifecycle
  -> MediaPipe face detector
  -> primary face selector
  -> cheek RGB sampler ---------> analysis-core legacy POS/FFT
  -> face crop preprocessor ----> ONNX emotion classifier
  -> measurement coordinator ---> emotion majority / stress
  -> UI state
```

## 완료된 특성화

`apps/android/core/analysis`는 Android에 의존하지 않는다.

- `legacy-pos-v1`: 300 RGB 샘플, 150샘플 초과 후 15샘플마다 계산, POS/FFT, 55~170 BPM, 10~35 RPM, 최근 유효값 10개 평균
- `LegacyEmotionAccumulator`: `Neutral`, `Error`, `Calibrating...` 제외 후 최빈값
- `legacy-stress-v1`: `(HR + RR * 2) / 2.5`, `Anger/Fear/Sad`에 15 가산, 0~100 제한

합성 1.2Hz 심박과 0.3Hz 호흡 신호, reset, 시간 순서, 감정 필터와 스트레스 경계값을 단위 테스트로 고정했다.

## 다음 이식 경계

1. ONNX Runtime Adapter
   - 입력 이름과 shape를 세션 메타데이터로 검증
   - grayscale 전처리를 독립 함수로 분리
   - 모델 출력은 label과 confidence로 반환
   - `Closeable`로 tensor/session 수명 관리
2. MediaPipe Adapter
   - 최대 얼굴 수와 가장 큰 얼굴 선택을 분리
   - 랜드마크 123 주변 ROI가 이미지 경계를 넘지 않게 검증
   - 결과 timestamp와 CameraX frame timestamp를 연결
3. 측정 coordinator
   - 얼굴 미검출, calibration, measuring, completed 상태를 명시
   - Activity와 WebView 콜백 제거
   - session reset 시 모든 버퍼와 모델 상태 초기화

## ONNX 계약 검증 결과

- Runtime: 1.16.0(기존 앱과 동일)
- input: `Input3`, float tensor `[1, 1, 64, 64]`
- output: 단일 float tensor `[1, 8]`
- 원본 SHA-256과 LFS 자산 해시 일치
- 상수 0~255 grayscale 입력으로 desktop inference 성공

ONNX Runtime 1.23.2의 Windows DLL은 현재 개발 PC에서 초기화되지 않아, 검증된 1.16.0을 재현성 기준으로 고정했다.

## MediaPipe/CameraX Adapter 결과

- MediaPipe `IMAGE` 모드에서 프레임과 랜드마크 결과를 동기화
- 최대 두 얼굴 중 normalized bounding box가 가장 큰 얼굴 선택
- 랜드마크 123 주변 10×10 ROI의 RGB 평균 추출
- ROI 경계 이탈 시 해당 샘플만 폐기
- 감정 추론은 30프레임마다 수행
- CameraX `ImageProxy`는 모든 성공/실패 경로에서 `finally`로 해제
- analyzer reset/close 시 신호 버퍼와 native detector/classifier 자원 해제

기존 `LIVE_STREAM` 구현처럼 이전 랜드마크와 현재 Bitmap이 섞이는 경쟁 조건을 피하기 위해 첫 버전은 동기 검출을 사용한다. 실기기 FPS가 부족할 때만 timestamp 기반 비동기 queue로 교체한다.

## 아직 검증되지 않은 항목

- FER+ 모델이 0~255 grayscale과 0~1 정규화 중 어느 입력을 기대하는지
- 운영 태블릿에서 MediaPipe/ONNX 지연, 발열과 메모리
- 실제 장비 대비 rPPG 정확도

위 항목이 확인되기 전에는 분석 값을 건강·의료 측정값으로 표현하지 않는다.
