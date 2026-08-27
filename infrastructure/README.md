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
Postgres  127.0.0.1:5432
```

서버는 `contracts/examples/project-config.json`을 읽고 데이터베이스에 프로젝트가 없거나 더 높은 설정 버전일 때만 반영한다.

## 사무실 배포

- 서버 PC 내부 IP를 고정한다.
- 공유기에서 필요한 외부 포트 하나만 서버로 전달한다.
- PostgreSQL 5432는 외부에 공개하지 않는다.
- 개발 비밀번호를 운영에서 사용하지 않고 환경변수나 별도 `.env`로 교체한다.
- 도메인이 준비되면 Caddy를 앞에 추가해 HTTPS를 종료한다.

운영 데이터는 Docker volume `recommendation-postgres`에 저장된다. 배포 전에 별도 백업 경로와 복구 절차를 구성해야 한다.
