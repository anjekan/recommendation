# 전체 시스템 아키텍처

## 1. 시스템 목표

이 시스템은 특정 꽃이나 정원을 추천하는 앱이 아니다. 분석 결과에 따라 범용 콘텐츠와 연결 장소를 추천하고, 운영자가 전체 추천 현황과 장소 상태를 제어하는 플랫폼이다.

```text
분석 입력
→ 감정 프로필
→ 추천 콘텐츠
→ 연결 장소
→ 이미지·문구·지도 표시
→ 추천 이력 및 운영 통계
```

태안 꽃박람회는 플랫폼에 등록되는 첫 번째 프로젝트 설정이다.

## 2. 시스템 컨텍스트

```text
Android · 향후 Web/PWA
          │
          ▼
Application / Use Cases
          │
  ┌───────┼───────────┬────────────┐
  ▼       ▼           ▼            ▼
Analysis  Recommendation  Config   Event Log
Port      Port            Port     Port
  │       │                │        │
  │  ┌────┴─────┐          │        │
  │  ▼          ▼          ▼        ▼
Native Local   Remote   Room/JSON  Sync Queue
Analysis Adapter Adapter
                  │
                  ▼
            API / Load Balancer
                  │
         ┌────────┼──────────┐
         ▼        ▼          ▼
     PostgreSQL  Redis   Admin Web
```

## 3. 아키텍처 스타일

Ports and Adapters 또는 Hexagonal Architecture를 적용한다.

```text
UI → Application → Domain ← Adapters
```

### Domain

- 감정 프로필
- 콘텐츠
- 장소
- 추천 규칙
- 추천 정책
- 추천 결정
- 추천 이력

Domain은 Android SDK, Room, Retrofit, PostgreSQL, Redis를 알지 못한다.

### Application

- 분석 세션 시작 및 종료
- 추천 요청
- 프로젝트 설정 로딩
- 실행 모드 선택
- 이벤트 기록 및 동기화

### Adapters

- CameraX
- MediaPipe
- ONNX Runtime
- Room/SQLite
- Retrofit
- PostgreSQL
- Redis
- 관리자 웹

## 4. 핵심 도메인 모델

```text
Project
├─ ProjectTheme
├─ EmotionProfile[]
├─ RecommendationItem[]
├─ Location[]
├─ RecommendationRule[]
└─ DisplayConfig

RecommendationRequest
├─ projectCode
├─ kioskId
├─ sessionId
├─ requestId
├─ emotionCode
├─ stressScore
├─ language
└─ previousLocationId

RecommendationDecision
├─ recommendationId
├─ emotionProfile
├─ item
├─ location
├─ display
├─ source
├─ policyVersion
└─ reasons[]
```

도메인 내부에서는 다음과 같은 전용 용어를 사용하지 않는다.

```text
Flower  → RecommendationItem
Garden  → Location
Festival → Project
FlowerMapping → RecommendationRule
```

## 5. 주요 포트

```kotlin
interface AnalysisPort {
    suspend fun analyze(input: AnalysisInput): AnalysisResult
}

interface RecommendationPort {
    suspend fun recommend(request: RecommendationRequest): RecommendationResult
}

interface ProjectConfigPort {
    suspend fun getProject(projectCode: String): ProjectConfig
    suspend fun refresh(projectCode: String): ConfigRefreshResult
}

interface EventLogPort {
    suspend fun append(event: RecommendationEvent)
    suspend fun pending(): List<RecommendationEvent>
    suspend fun markSynced(eventIds: List<String>)
}

interface RecommendationStatePort {
    suspend fun getLastLocation(scopeId: String): String?
    suspend fun getRecentCounts(locationIds: List<String>): Map<String, Long>
    suspend fun record(decision: RecommendationDecision)
}
```

실제 인터페이스는 플랫폼 독립 도메인 모듈에 둔다. 위 코드는 개념 예시다.

## 6. 실행 모드

### LOCAL

- 서버 호출 없음
- 패키징된 설정 또는 Room 캐시 사용
- 해당 태블릿 이력만 기준으로 추천 분산
- 이벤트를 로컬에 저장

### REMOTE

- 서버가 추천 결정
- 전체 태블릿의 최근 추천량과 장소 상태 반영
- 서버 실패를 오류로 반환

### HYBRID

- 서버를 우선 사용
- timeout 또는 장애 시 로컬 추천
- 로컬 fallback 이벤트를 동기화 대기열에 저장
- 네트워크 복구 시 서버로 전송

현장 운영 기본값은 HYBRID, 개발과 독립 시연 기본값은 LOCAL을 권장한다.

## 7. 분석 계층

분석도 하나의 교체 가능한 공급자로 취급한다.

```text
AnalysisPort
├─ NativeAnalysisAdapter
│  ├─ CameraFrameSource
│  ├─ FaceLandmarkDetector
│  ├─ PrimaryFaceSelector
│  ├─ FaceRegionExtractor
│  ├─ VitalSignalAnalyzer
│  ├─ EmotionAnalyzer
│  └─ FacialActionAnalyzer
├─ FakeAnalysisAdapter
└─ 향후 WebAnalysisAdapter
```

가짜 분석기로 전체 UI·추천 흐름을 먼저 완성한 후 네이티브 분석기를 연결한다.

## 8. 추천 정책

추천은 하나의 거대한 함수가 아니라 조합 가능한 정책으로 구성한다.

```text
ActiveCandidatePolicy
PreviousLocationExclusionPolicy
RecentRecommendationPenaltyPolicy
CongestionPolicy
WeightedSelectionPolicy
SafeFallbackPolicy
```

기본 순서:

1. 감정에 연결된 후보 조회
2. 비활성·추천 중지 장소 제외
3. 직전 추천 장소 제외
4. 최근 추천량과 혼잡 상태에 따라 감점
5. 프로젝트 가중치 적용
6. 최종 후보 선택
7. 정책 버전과 판단 근거 저장

로컬 모드는 태블릿 단위로만 분산할 수 있다. 서버 모드는 전체 사용자와 태블릿을 기준으로 분산한다.

## 9. 프로젝트 설정

코드에 행사 데이터와 이미지를 하드코딩하지 않는다.

```text
assets/projects/taean/
├─ project.json
├─ emotions.json
├─ items.json
├─ locations.json
├─ rules.json
├─ map.webp
└─ images/
```

서버 모드에서는 동일한 스키마로 설정을 내려받는다. Android는 마지막 정상 설정을 Room에 저장한다.

설정 공통 필드:

```json
{
  "schema_version": 1,
  "config_version": 1,
  "minimum_app_version": 1,
  "project_code": "TAEAN_FLOWER_2026"
}
```

## 10. 저장소

### Android

Room/SQLite:

- 프로젝트 설정
- 감정 프로필
- 추천 콘텐츠
- 장소
- 추천 규칙
- 최근 추천 이력
- 동기화 대기 이벤트
- 런타임 설정

### 서버

PostgreSQL:

- 모든 영구 데이터의 원본
- 프로젝트와 설정 버전
- 추천 콘텐츠와 장소
- 추천 규칙
- 키오스크
- 추천 이력
- 관리자 계정
- 감사 로그

이미지는 DB BLOB이 아니라 파일 또는 Object Storage에 저장하고 DB에는 URL과 메타데이터만 둔다.

## 11. Redis

대규모 웹·앱 트래픽을 위한 실시간 계층으로 설계한다.

주요 역할:

- 프로젝트 설정 캐시
- 장소별 최근 추천 카운터
- 태블릿·세션별 직전 장소
- idempotency 키
- 원자적 추천 후보 예약
- heartbeat
- 관리자 상황판 실시간 이벤트
- Redis Streams 기반 비동기 이벤트

PostgreSQL이 항상 영구 데이터의 원본이다. Redis 데이터가 사라져도 시스템이 복구 가능해야 한다.

초기에는 포트와 키 정책을 확정하고 필요한 기능부터 활성화한다. 부하 테스트 결과에 따라 Redis Cluster, 전용 메시지 큐, 읽기 Replica를 확장한다.

## 12. 서버 확장 구조

```text
Client
→ CDN / WAF / Load Balancer
→ Stateless API Servers
→ Redis + PostgreSQL
→ Background Workers
→ Admin Web / Analytics
```

API 서버는 세션을 메모리에 보관하지 않는다. 수평 확장 가능한 stateless 구조를 유지한다.

## 13. 공통 API

초기 API:

```text
GET  /api/v1/projects/{code}/config
POST /api/v1/recommendations
POST /api/v1/events/sync
POST /api/v1/kiosks/heartbeat
GET  /api/v1/health
```

모든 변경 요청에는 `request_id`를 포함하고 서버는 중복 요청을 한 번만 처리한다.

## 14. 관리자 웹

초기 기능:

- 프로젝트 관리
- 감정 프로필 관리
- 추천 콘텐츠와 이미지 관리
- 장소와 지도 좌표 관리
- 감정별 후보 및 가중치 관리
- 장소 정상·혼잡·추천 중지 설정
- 태블릿 온라인 상태
- 장소·감정·시간대별 추천 현황
- 관리자 변경 감사 로그

## 15. 개인정보와 보안

- 이름, 전화번호, 성별, 생년월일을 기본적으로 수집하지 않는다.
- 방문자는 익명 UUID 세션으로 식별한다.
- 태블릿은 기기별 키로 인증한다.
- 관리자 인증과 권한을 분리한다.
- PostgreSQL과 Redis 포트를 외부에 직접 공개하지 않는다.
- 모든 운영 변경을 감사 로그로 남긴다.

## 16. 버전 정책

별도로 관리할 버전:

- `api_version`
- `config_schema_version`
- `project_config_version`
- `algorithm_version`
- `app_version`
- `analysis_model_version`

구버전 클라이언트가 해석할 수 없는 설정은 서버가 내려주지 않거나 명확한 호환 오류를 반환한다.

## 17. UI 원칙

- 신규 Android UI는 Jetpack Compose를 우선한다.
- Activity는 탐색과 시스템 연결만 담당한다.
- UI는 Domain 모델과 화면 상태만 소비한다.
- Retrofit, Room, JSON 객체를 UI에 직접 전달하지 않는다.
- 프로젝트 로고·색상·문구·이미지·지도는 설정으로 주입한다.
- 화면은 Preview, 에뮬레이터 캡처, 실기기 확인과 스크린샷 테스트를 거친다.
- 실제 렌더링 확인 없이 UI를 완료 처리하지 않는다.
