# 기여 및 작업 규칙

## 브랜치

- 기본 브랜치: `main`
- 작업 브랜치: `codex/<short-topic>` 또는 `feature/<short-topic>`
- 긴 작업은 계약·도메인·어댑터·UI 단위로 나눈다.

## 변경 순서

1. 계약 또는 아키텍처에 영향이 있는지 확인한다.
2. 필요한 ADR과 Schema/OpenAPI를 먼저 변경한다.
3. Domain과 테스트를 변경한다.
4. Local/Remote Adapter를 변경한다.
5. UI를 변경한다.
6. 빌드·테스트·렌더링 검증 결과를 기록한다.

## 커밋

권장 형식:

```text
docs: document recommendation architecture
contract: add project configuration schema
android: add local recommendation adapter
server: implement idempotent recommendation endpoint
admin: add location status control
infra: add postgres and redis compose services
```

한 커밋에 서로 무관한 변경을 섞지 않는다.

## 완료 기준

- 관련 테스트가 통과한다.
- 계약 변경은 예제와 버전이 함께 갱신됐다.
- DB 변경은 마이그레이션으로 기록됐다.
- UI 변경은 실제 렌더링을 확인했다.
- 새로운 운영 결정은 문서 또는 ADR에 남겼다.
- 비밀키, 로컬 DB, 빌드 산출물이 포함되지 않았다.
