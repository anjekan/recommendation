# 기술 및 아키텍처 결정

이 문서는 현재까지 합의된 결정을 요약한다. 새로운 결정은 ADR 형식으로 별도 기록하고 이 목록에 연결한다.

## D-001 Android 우선, 플랫폼 독립 설계

현재 구현과 납품은 Android에 집중한다. 그러나 공통 모델과 프로토콜은 향후 Web/PWA가 사용할 수 있도록 기술 독립적으로 정의한다.

## D-002 신규 프로젝트 우선

기존 앱을 대규모 수정하지 않는다. 새 프로젝트에 새 아키텍처를 만들고 기존 모델·알고리즘·콘텐츠를 선별 이식한다.

## D-003 Kotlin + Jetpack Compose

신규 Android 클라이언트는 Kotlin과 Jetpack Compose를 우선한다.

- UI 상태와 Activity 생명주기 분리
- Preview와 스크린샷 테스트
- 태블릿 해상도별 검증
- 디자인 토큰과 프로젝트 테마 적용

## D-004 Ports and Adapters

Domain이 Android, Retrofit, Room, PostgreSQL, Redis에 의존하지 않게 한다. UI도 저장소와 네트워크 구현체를 직접 호출하지 않는다.

## D-005 세 가지 실행 모드

- LOCAL: 앱 단독 실행
- REMOTE: 서버 추천
- HYBRID: 서버 우선, 장애 시 로컬 fallback

동일한 RecommendationPort 계약을 구현체만 바꿔 사용한다.

## D-006 콘텐츠 데이터화

꽃, 정원, 행사명, 이미지, 지도, 좌표, 문구와 추천 규칙을 코드에 하드코딩하지 않는다. ProjectConfig와 버전이 있는 설정 스키마로 관리한다.

## D-007 개인정보 미수집

이름, 전화번호, 성별, 생년월일을 기본 범위에서 제거한다. 익명 sessionId와 requestId를 UUID로 생성한다.

## D-008 Room/SQLite

Android의 프로젝트 설정, 추천 이력, 로컬 캐시와 동기화 대기열은 Room/SQLite에 저장한다.

## D-009 PostgreSQL

서버 영구 데이터의 원본은 PostgreSQL로 한다. 관계, 트랜잭션, 통계, 추천 이력과 확장성을 기준으로 선택한다.

## D-010 Redis

Redis는 대규모 트래픽의 실시간 상태 계층으로 사용한다.

- 설정 캐시
- 최근 추천 카운터
- 직전 장소
- idempotency
- heartbeat
- 원자적 추천 예약
- 상황판 이벤트
- Streams 기반 비동기 처리

Redis는 PostgreSQL을 대체하지 않으며 데이터 유실 시 재구성 가능해야 한다.

## D-011 이미지 저장

이미지와 지도는 DB BLOB으로 저장하지 않는다. 파일 또는 S3 호환 Object Storage에 두고 DB에는 URL과 메타데이터를 저장한다.

## D-012 Stateless API

서버 API는 인스턴스 메모리에 사용자 세션을 저장하지 않는다. Load Balancer 뒤에서 수평 확장 가능하게 설계한다.

## D-013 공통 계약 원본

JSON Schema 또는 OpenAPI를 계약 원본으로 사용한다. Kotlin, 서버 DTO, 향후 TypeScript 모델이 같은 계약을 따라야 한다.

## D-014 버전 관리

다음을 각각 버전 관리한다.

- API
- 설정 스키마
- 프로젝트 설정
- 추천 알고리즘
- 분석 모델
- Android 앱

## D-015 추천 결정 추적

모든 추천에는 다음을 기록한다.

- 후보와 제외 이유
- 선택된 콘텐츠와 장소
- 추천 출처 LOCAL/REMOTE/LOCAL_FALLBACK
- 정책 버전
- 설정 버전
- 생성 시각

## D-016 UI 완료 조건

UI는 코드 빌드만으로 완료 처리하지 않는다.

- Compose Preview
- 에뮬레이터 또는 렌더링 캡처
- 목표 태블릿 실기기
- 다국어와 긴 문구
- 이미지 실패와 오프라인 상태
- 스크린샷 회귀 테스트

## D-017 초기 구현 순서

분석 엔진보다 공통 계약, 범용 모델, 로컬 추천과 가짜 분석 기반 UI를 먼저 구현한다. 구조가 분석 코드에 끌려가지 않게 하기 위함이다.

## D-018 서버 기술 선택

Kotlin, Java 17, Spring Boot 4.1과 Spring Modulith를 서버 기준으로 사용한다. embedded server와 Docker image로 배포하며 별도 JBoss/WildFly는 사용하지 않는다. PostgreSQL, Redis, Docker 기반 운영 원칙과 Ports and Adapters 경계는 유지한다. 상세 근거는 ADR-0006에 기록한다.
