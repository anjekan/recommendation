# Local data Adapter

Room/SQLite 기반의 Android 로컬 저장 Adapter다.

- 프로젝트 카탈로그 전체 교체를 하나의 트랜잭션으로 처리
- 프로젝트, 장소, 추천 항목, 감정 매핑을 외래키로 검증
- 추천 이벤트의 `requestId`를 고유키로 사용해 중복 기록 방지
- 최근 추천 장소 조회를 제공해 연속 추천 방지 정책에서 사용
- Room schema JSON을 Git에 보관해 향후 migration을 검증

이 모듈은 도메인 포트를 구현하지만 추천 정책 자체는 포함하지 않는다.
