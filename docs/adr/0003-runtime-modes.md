# ADR-0003: LOCAL, REMOTE, HYBRID 실행 모드

- 상태: Accepted
- 결정일: 2026-08-25

## 맥락

행사장 통신은 불안정할 수 있고 서버 개발 전에도 Android 단독으로 전체 기능을 개발·시연해야 한다. 서버가 연결되면 전체 태블릿 추천량을 반영해야 한다.

## 결정

하나의 RecommendationPort 계약에 세 실행 구현을 제공한다.

- LOCAL: Room 설정과 키오스크 내부 이력으로 추천
- REMOTE: 서버가 전체 실시간 상태로 추천
- HYBRID: 서버 우선, timeout 또는 장애 시 LOCAL fallback

HYBRID fallback 결과는 `LOCAL_FALLBACK` 출처로 기록하고 동기화 큐에 저장한다.

## 결과

- 서버 장애가 사용자 흐름을 중단하지 않는다.
- LOCAL 모드에서는 전체 행사장의 정확한 분산 상태를 알 수 없다.
- fallback과 재동기화의 멱등성 처리가 필수다.

