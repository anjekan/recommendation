# ADR-0005: OpenAPI와 JSON Schema를 계약 원본으로 사용

- 상태: Accepted
- 결정일: 2026-08-25

## 맥락

Android, 서버, 관리자 웹과 향후 Web/PWA가 각각 DTO를 수동 정의하면 필드와 의미가 달라질 수 있다. 프로젝트 설정도 앱 버전과 독립적으로 변경돼야 한다.

## 결정

- HTTP API의 원본은 OpenAPI 3.1 문서다.
- 프로젝트 콘텐츠 설정의 원본은 JSON Schema 2020-12 문서다.
- Kotlin, 서버와 TypeScript 모델은 계약에서 생성하거나 계약 테스트로 검증한다.
- 계약에 꽃·정원·태안 전용 필드를 사용하지 않는다.
- `schema_version`, `config_version`, `minimum_app_version`, `policy_version`을 분리한다.

호환 정책:

- 필드 추가는 기본값 또는 optional로 제공한다.
- 필드 의미 변경·삭제는 새 API 또는 schema major version에서 수행한다.
- 구버전 앱이 해석할 수 없는 설정은 내려주지 않는다.
- 요청·이벤트 ID로 재시도를 멱등 처리한다.

## 결과

- 계약 변경이 코드 변경보다 먼저 검토된다.
- 계약 검증 자동화가 빌드 파이프라인의 필수 단계가 된다.
- 계약 버전과 생성 도구 운영 비용이 추가된다.

