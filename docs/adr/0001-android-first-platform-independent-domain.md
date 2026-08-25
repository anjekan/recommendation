# ADR-0001: Android 우선, 플랫폼 독립 도메인

- 상태: Accepted
- 결정일: 2026-08-25

## 맥락

현재 우선 납품 대상은 Android 키오스크다. 궁극적으로 사용자는 앱 설치 없이 웹페이지에서도 같은 분석·추천 서비스를 사용할 수 있어야 한다. 기존 코드는 Android Activity와 WebView, 꽃박람회 데이터에 결합되어 있다.

## 결정

Android를 첫 번째 클라이언트로 개발하되 Domain과 공통 계약에는 Android 타입이나 꽃박람회 전용 용어를 사용하지 않는다.

분석, 추천, 프로젝트 설정과 이벤트 계약은 향후 Web/PWA가 재사용할 수 있게 정의한다. Android 전용 CameraX, MediaPipe와 ONNX Runtime 구현은 Adapter에 둔다.

## 결과

- Android 구현 속도만을 위해 Domain에 `Context`, `Bitmap`, Room Entity 또는 Retrofit DTO를 노출할 수 없다.
- 웹 분석 공급자가 추가돼도 추천과 콘텐츠 계약은 유지된다.
- 초기 설계 비용은 증가하지만 다음 프로젝트의 재작성 범위는 감소한다.

