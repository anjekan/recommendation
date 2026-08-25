# Recommendation Server

추천 API와 프로젝트 설정, 추천 이력, 동선 분산을 담당하는 stateless 서버다.

## 확정된 기반 기술

- PostgreSQL: 영구 데이터 원본
- Redis: 실시간 추천 상태와 대규모 트래픽 계층
- Docker: 개발 및 운영 환경 일관성
- OpenAPI: 클라이언트·서버 계약

API 프레임워크와 언어는 계약, 팀 숙련도, 운영 환경을 검토한 뒤 ADR로 확정한다.
