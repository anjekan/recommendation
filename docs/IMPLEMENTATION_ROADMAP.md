# 구현 로드맵

## 기본 전략

기존 Android 앱을 직접 대규모 수정하지 않는다. 새 프로젝트와 새 아키텍처를 만들고 검증된 분석 모델·알고리즘·콘텐츠 자산을 선별 이식한다.

각 단계는 앞 단계의 완료 기준을 충족한 후 진행한다.

## 0단계: 기준선 보존

### 작업

- 기존 앱 전체 빌드
- 시작 → 분석 → 결과 → 지도 → 초기화 흐름 기록
- 화면별 캡처 또는 영상 확보
- 기존 API 요청·응답 샘플 저장
- 감정·심박·호흡·스트레스 샘플 확보
- 분석 모델 체크섬과 버전 기록
- 목표 태블릿 모델과 해상도 기록

### 완료 기준

- 신규 앱과 비교할 수 있는 재현 가능한 기준이 있다.
- 기존 분석 결과를 검증할 입력 데이터가 있다.

## 1단계: 결정과 계약

### 작업

- ADR 작성
- 시스템 컨텍스트 확정
- 도메인 용어 확정
- LOCAL·REMOTE·HYBRID 동작 정의
- JSON Schema/OpenAPI 초안
- 장애 및 버전 호환 정책 정의

### 완료 기준

- UI, 도메인, 저장소, 통신의 책임 경계가 문서화됐다.
- Android와 서버가 사용할 공통 요청·응답 계약이 있다.

## 2단계: 신규 Android 골격

### 작업

- 새 Android 프로젝트 생성
- Kotlin과 Jetpack Compose 적용
- 모듈 또는 패키지 의존 방향 설정
- 코루틴과 Flow 기반 상태 관리
- 기본 CI 빌드와 정적 분석
- 테스트 구조 구성

권장 초기 경계:

```text
:app
:core:model
:core:domain
:core:config
:core:database
:analysis:contract
:analysis:native
:recommendation:domain
:recommendation:local
:recommendation:remote
:feature:start
:feature:analysis
:feature:result
:feature:settings
```

초기에는 과도한 멀티 모듈화를 피하고 의존 방향을 강제할 만큼만 분리한다.

### 완료 기준

- 빈 앱이 빌드되고 테스트가 실행된다.
- Domain 모듈은 Android SDK에 의존하지 않는다.

## 3단계: 범용 모델과 포트

### 작업

- Project, EmotionProfile, RecommendationItem, Location 정의
- 추천 요청·결정·이벤트 정의
- AnalysisPort와 RecommendationPort 정의
- ConfigPort, EventLogPort, RuntimeConfigPort 정의
- 공통 오류 모델 정의
- 가짜 구현체 작성

### 완료 기준

- 꽃과 정원 용어 없이 태안 프로젝트를 표현할 수 있다.
- 가짜 분석·추천 구현으로 Use Case 단위 테스트가 가능하다.

## 4단계: Room과 프로젝트 설정

### 작업

- Room 스키마 작성
- 프로젝트 설정 JSON importer 작성
- config schema/version 검사
- 마지막 정상 설정 보관
- 설정 업데이트·롤백 규칙 작성
- 태안 데이터를 범용 프로젝트 설정으로 변환

### 완료 기준

- 네트워크 없이 프로젝트 설정을 로딩한다.
- 앱 재시작 후에도 설정과 추천 이력이 유지된다.
- 다른 테스트 프로젝트도 같은 importer로 로딩된다.

## 5단계: 로컬 추천 엔진

### 작업

- 후보 필터 정책
- 직전 장소 제외 정책
- 최근 추천 감점 정책
- 가중치 정책
- 안전 fallback 정책
- 추천 이유와 정책 버전 기록
- 경계 조건 테스트

### 완료 기준

- 동일 장소가 연속 추천되지 않는다.
- 후보 0개·1개·모두 중지 상태를 안전하게 처리한다.
- 추천 정책 단위 테스트가 모두 통과한다.

## 6단계: 범용 Android UI

### 작업

- StartScreen
- AnalysisScreen
- ResultScreen
- SettingsScreen
- 프로젝트 테마 적용
- 로딩·오류·오프라인 상태
- 다국어 및 긴 문구 대응
- 원격·로컬 이미지 fallback
- 화면 전환 및 키오스크 모드

이 단계에서는 FakeAnalysisAdapter를 사용한다.

### UI 검증

- Compose Preview
- 목표 해상도별 캡처
- 작은·큰 글자와 긴 번역 문구
- 이미지 비율과 실패 상태
- 버튼 터치 영역
- 실제 태블릿 캡처
- 승인 화면 스크린샷 테스트

### 완료 기준

- 분석기 없이 전체 사용자 흐름이 동작한다.
- 태안과 테스트 프로젝트가 같은 UI에서 표시된다.
- UI 코드에 행사 전용 텍스트가 없다.

## 7단계: 분석 엔진 이식

### 순서

1. SignalProcessor에서 Android Log 의존 제거
2. 고정 입력 기반 특성화 테스트
3. EmotionClassifier 계약 분리
4. 모델 전처리와 confidence 검증
5. FaceAnalyzer 책임 분리
6. CameraX FrameSource 구현
7. MediaPipe Landmark Adapter 구현
8. ONNX Emotion Adapter 구현
9. AnalysisSessionCoordinator 구성
10. 실기기 성능과 메모리 검증

### 완료 기준

- 분석 계층이 UI와 서버를 알지 못한다.
- 세션 초기화와 자원 해제가 보장된다.
- 기존 기준 입력에 대한 결과가 허용 범위 안에 있다.
- 장시간 실행 시 메모리 누수가 없다.

## 8단계: 실행 모드

### 작업

- LOCAL·REMOTE·HYBRID 선택
- 숨겨진 관리자 설정 화면
- 서버 URL과 프로젝트 코드
- 키오스크 ID와 기기 인증 정보
- 연결 테스트
- timeout과 fallback 정책

### 완료 기준

- APK 재빌드 없이 모드를 변경한다.
- LOCAL 모드는 서버 없이 완전 동작한다.
- HYBRID 모드는 서버 실패 시 사용자 흐름을 중단하지 않는다.

## 9단계: 서버와 PostgreSQL

### 작업

- Stateless API 서버
- PostgreSQL 마이그레이션
- 프로젝트·콘텐츠·장소·규칙 관리
- 추천 이력과 idempotency
- 기기 인증
- 이미지 저장
- Health check
- Docker Compose 개발 환경

### 완료 기준

- Android와 계약 테스트가 통과한다.
- 여러 API 인스턴스로 수평 확장 가능하다.
- 중복 requestId가 한 번만 처리된다.

## 10단계: Redis 실시간 계층

### 초기 적용

- 설정 cache-aside
- 직전 추천 장소
- 최근 추천 카운터
- heartbeat
- 중복 요청 단기 캐시

### 대규모 확장

- 원자적 후보 선택·예약
- Redis Streams
- 실시간 상황판 이벤트
- Redis Sentinel 또는 Cluster
- 분산 rate limit

### 완료 기준

- 동시 요청이 같은 장소로 과도하게 쏠리지 않는다.
- Redis 장애 시 PostgreSQL 기반으로 저하 운영한다.
- Redis 데이터는 PostgreSQL 또는 이벤트 로그로 재구성할 수 있다.

## 11단계: 오프라인 동기화

### 작업

- PENDING/SYNCING/SYNCED/FAILED 상태
- 지수 백오프
- eventId 중복 방지
- 배치 동기화
- 부분 성공 처리
- 서버와 기기 시간 차이 처리

### 완료 기준

- 네트워크 단절 중 추천이 계속된다.
- 복구 후 이벤트가 정확히 한 번 통계에 반영된다.

## 12단계: 관리자 웹

### 작업

- 프로젝트와 콘텐츠 관리
- 장소와 지도 좌표 관리
- 추천 규칙과 가중치 관리
- 장소 정상·혼잡·추천 중지
- 태블릿 온라인 상태
- 감정·장소·시간대 통계
- 관리자 계정과 감사 로그

### 완료 기준

- 집행부가 DB 직접 접근 없이 운영한다.
- 장소 상태 변경이 새 추천에 즉시 반영된다.

## 13단계: 부하·장애·보안 검증

### 작업

- 목표 RPS와 피크 정의
- API 부하 테스트
- Redis/PostgreSQL 장애 주입
- 네트워크 지연과 패킷 손실
- 설정 손상과 롤백
- 백업·복구 리허설
- WAF와 rate limit
- 관리자 권한 테스트

### 완료 기준

- 목표 부하와 장애 시나리오를 수치로 통과한다.
- 백업에서 실제 복구할 수 있다.

## 14단계: Web/PWA 확장

Android 구조가 안정된 이후 진행한다.

- 동일한 JSON Schema/OpenAPI 사용
- WebAnalysisProvider
- IndexedDB 캐시
- LOCAL·REMOTE·HYBRID 웹 구현
- 브라우저 카메라·모델 성능 검증

## 구현 중 금지 사항

- UI에서 Retrofit 직접 호출
- UI에서 Room DAO 직접 호출
- 꽃·정원·태안 데이터 하드코딩
- 운영 DB 직접 수정
- 마이그레이션 없는 스키마 변경
- Redis를 유일한 영구 저장소로 사용
- 실제 렌더링 없이 UI 완료 처리
- 테스트 없는 추천 정책 변경
- 서버 장애가 사용자 흐름을 중단하게 설계
