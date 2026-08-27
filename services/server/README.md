# Recommendation Server

범용 감정 분석·콘텐츠 추천 플랫폼의 stateless API 서버다.

## 기술 기준

- Kotlin 2.3
- Java 17
- Spring Boot 4.1
- Spring Modulith
- PostgreSQL 17
- Flyway

## 패키지 모듈

```text
health   서버 상태
project  프로젝트 설정 조회와 초기 시드
event    오프라인 추천 이벤트 멱등 동기화
```

## 빌드

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat test bootJar
```

Docker 없이 단위·모듈 테스트와 JAR 빌드가 가능하다. PostgreSQL 통합 실행은 `infrastructure/compose.yaml`을 사용한다.

## 초기 API

```text
GET  /api/v1/health
GET  /api/v1/projects/{projectCode}/config
POST /api/v1/events/sync
```

프로젝트 설정 조회는 config version 기반 ETag를 제공한다. 이벤트 동기화는 `event_id`를 PostgreSQL primary key로 사용하며, 재전송된 이벤트도 성공으로 응답해 Android 동기화 대기열이 안전하게 종료되게 한다.
