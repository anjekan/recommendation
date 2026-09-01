# Infrastructure

현재 로컬·사무실 서버 기준 구성은 PostgreSQL과 Spring Boot API다. Redis는 아직 실행 의존성에 포함하지 않는다.

## 실행

```powershell
docker compose -f infrastructure/compose.yaml up --build -d
```

기본 주소:

```text
API       http://localhost:8080/api/v1
Health    http://localhost:8080/api/v1/health
Actuator  http://localhost:8080/actuator/health
Admin     http://localhost:8080/admin/index.html
Postgres  127.0.0.1:5432
```

관리자 초기 계정은 `admin / admin`이다. 외부에 공개하기 전 `.env` 또는 시스템 환경변수의 `ADMIN_USERNAME`, `ADMIN_PASSWORD`를 변경한다.

서버는 `projects/taean-flower/taean-flower-project-config.json`을 읽고 데이터베이스에 프로젝트가 없거나 더 높은 설정 버전일 때 반영한다.

## 검증 기준

2026-08-27 Docker Desktop 환경에서 다음 항목을 실제 컨테이너로 확인했다.

- PostgreSQL 17 health check 통과 및 Flyway V1~V3 적용
- Spring Boot API 시작 및 `/api/v1/health`의 `UP` 응답
- 예제 프로젝트 설정 조회와 `If-None-Match` 요청의 `304 Not Modified` 응답
- 동일 `event_id`를 두 번 동기화해도 `recommendation_events`에는 한 행만 저장
- 추천 요청의 동의·미동의 상태 저장 및 관리자 대시보드 집계 확인
- 모바일 반응형 관리자 페이지 HTTP 200 확인
- Java 17 기준 `clean test bootJar` 성공

Windows 터미널이 Java 8을 기본으로 사용하면 Gradle 실행 전에 `JAVA_HOME`을 Java 17 이상으로 지정한다. Docker 자체 빌드는 Temurin 17 이미지를 사용하므로 호스트 Java 설정과 무관하다.

## 사무실 배포

- 서버 PC 내부 IP를 고정한다.
- 공유기에서 필요한 외부 포트 하나만 서버로 전달한다.
- PostgreSQL 5432는 외부에 공개하지 않는다.
- 관리자 초기 비밀번호를 변경하기 전에는 8080을 인터넷에 공개하지 않는다.
- 개발 비밀번호를 운영에서 사용하지 않고 환경변수나 별도 `.env`로 교체한다.
- 도메인이 준비되면 Caddy를 앞에 추가해 HTTPS를 종료한다.

운영 데이터는 Docker volume `recommendation-postgres`에 저장된다. 배포 전에 별도 백업 경로와 복구 절차를 구성해야 한다.
