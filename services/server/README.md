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
admin    추천·동의 상태 집계 조회
security 관리자 화면과 API 인증
```

## 빌드

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat test bootJar
```

Docker 없이 단위·모듈 테스트와 JAR 빌드가 가능하다. PostgreSQL 통합 실행은 `infrastructure/compose.yaml`을 사용한다.

로컬 연동 시험은 H2 인메모리 DB와 태안 꽃 설정을 사용하는 `local` 프로필로 실행한다.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

## 초기 API

```text
GET  /api/v1/health
GET  /api/v1/projects/{projectCode}/config
POST /api/v1/events/sync
POST /api/v1/recommendations
GET  /api/v1/admin/dashboard?projectCode={projectCode}
GET  /admin/index.html
```

프로젝트 설정 조회는 config version 기반 ETag를 제공한다. 이벤트 동기화는 `event_id`를 PostgreSQL primary key로 사용하며, 재전송된 이벤트도 성공으로 응답해 Android 동기화 대기열이 안전하게 종료되게 한다.

추천 요청의 `consent_status`는 `CONSENTED`, `DECLINED`, `NOT_ASKED` 중 하나다. 원격 추천이 성공하면 감정, 스트레스 점수, 동의 상태와 추천 결과를 익명 이벤트로 저장한다. 개인정보 내용은 저장하지 않는다.

관리자 API와 화면은 Spring Security 로그인이 필요하다. 개발 초기 계정은 `admin / admin`이며 운영에서는 반드시 `ADMIN_USERNAME`, `ADMIN_PASSWORD` 환경변수로 변경한다. 관리자 API는 HTTP Basic 인증도 지원한다.
