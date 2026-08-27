# ADR-0006: Spring Boot 모듈형 서버와 단계적 인프라

- 상태: Accepted
- 결정일: 2026-08-27

## 맥락

초기 배포는 사무실 고정 IP의 단일 서버지만, 최종 범위에는 Android·웹 공통 API, 프로젝트 설정, 추천, 이벤트 수집, 실시간 상황판, 관리자 기능과 통계가 포함된다. 초기 규모만 보고 임시 서버를 만들거나, 반대로 JBoss/WildFly와 분산 시스템을 먼저 도입하면 유지보수 비용이 커진다.

## 결정

- Kotlin, Java 17, Spring Boot 4.1을 API 서버 기준으로 사용한다.
- Spring Modulith로 도메인별 모듈 경계와 의존 방향을 검증한다.
- 실행 산출물은 embedded server를 포함한 단일 executable JAR와 Docker image다.
- PostgreSQL을 영구 데이터 원본으로 사용하고 Flyway로만 스키마를 변경한다.
- Redis는 포트와 키 정책을 먼저 설계하고 부하·실시간 요구가 확인될 때 활성화한다.
- API 서버는 인스턴스 메모리에 사용자 세션을 저장하지 않는 stateless 구조를 유지한다.
- 초기 배포는 Docker Compose 단일 호스트로 하되 동일 이미지를 클라우드나 다중 인스턴스로 이전할 수 있게 한다.
- JBoss/WildFly는 조직 표준이나 Jakarta EE 컨테이너 기능이 새로 요구될 때만 재검토한다.

## 초기 모듈

- `project`: 프로젝트 설정과 버전 조회
- `event`: 오프라인 추천 이벤트의 멱등 수집
- `health`: 서버 상태 확인

이후 `recommendation`, `kiosk`, `dashboard`, `admin`, `audit` 모듈을 같은 원칙으로 추가한다.

## 결과

- 사무실 한 대에서 단순하게 시작하면서 전체 시스템 경계를 유지한다.
- embedded server를 사용하므로 별도 애플리케이션 서버 운영이 필요 없다.
- PostgreSQL과 Docker가 없는 개발 PC에서도 순수 단위·모듈 테스트와 JAR 빌드는 가능하다.
- 실제 PostgreSQL 통합 검증과 운영 보안 구성은 배포 환경에서 추가로 수행해야 한다.
