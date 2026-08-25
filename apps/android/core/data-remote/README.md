# Remote data Adapter

OpenAPI v1의 추천 요청을 수행하는 OkHttp 기반 Adapter다.

- `X-Kiosk-Key` 인증 헤더
- 요청/응답 schema version 검사
- HTTP 409 및 기타 4xx를 업무 거부로 분류
- 연결 실패, timeout, HTTP 5xx를 가용성 장애로 분류
- HYBRID 라우터는 가용성 장애에서만 LOCAL fallback

API 키와 base URL은 코드나 프로젝트 JSON에 저장하지 않고 런타임 설정으로 주입한다.
