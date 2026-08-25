# 도메인 용어 사전

이 문서는 Android, 서버, 관리자 웹과 향후 Web/PWA에서 같은 의미로 사용해야 할 핵심 용어를 정의한다.

## 핵심 용어

| 영문 | 한글 | 정의 |
|---|---|---|
| Project | 프로젝트 | 하나의 행사·시설·서비스에 적용되는 독립 콘텐츠와 운영 설정 단위 |
| ProjectConfig | 프로젝트 설정 | 프로젝트의 테마, 감정, 콘텐츠, 장소와 추천 규칙을 포함한 버전 데이터 |
| Kiosk | 키오스크 | 분석과 추천을 실행하는 관리 대상 클라이언트 기기 |
| Session | 세션 | 개인정보 없이 한 번의 사용자 흐름을 연결하는 익명 식별 범위 |
| Analysis | 분석 | 카메라 등의 입력에서 감정과 생체 신호를 계산하는 과정 |
| AnalysisResult | 분석 결과 | 감정 코드, 스트레스 점수, 심박·호흡과 분석 신뢰도 |
| EmotionProfile | 감정 프로필 | 분석 결과를 사용자에게 표현하고 추천 규칙에 연결하는 도메인 상태 |
| RecommendationItem | 추천 콘텐츠 | 사용자에게 제시할 꽃, 프로그램, 전시물, 상품 또는 장소 등의 범용 대상 |
| Location | 장소 | 추천 콘텐츠가 연결되는 실제 또는 논리적 위치 |
| RecommendationRule | 추천 규칙 | 특정 감정과 추천 콘텐츠의 후보·가중치·우선순위 연결 |
| RecommendationPolicy | 추천 정책 | 후보 제외, 감점, 가중치와 최종 선택을 수행하는 알고리즘 |
| RecommendationDecision | 추천 결정 | 정책이 선택한 콘텐츠·장소와 선택 근거를 포함한 결과 |
| RecommendationEvent | 추천 이벤트 | 추천 발생 사실을 영구 기록하거나 서버로 동기화하는 불변 이벤트 |
| Candidate | 후보 | 현재 요청에서 정책 평가 대상이 되는 콘텐츠·장소 조합 |
| ProjectTheme | 프로젝트 테마 | 이름, 로고, 색상, 배경과 지도 등 화면 표현 설정 |
| RuntimeConfig | 런타임 설정 | 실행 모드, 서버 URL, 프로젝트 코드, 키오스크 ID 등 기기 설정 |
| ConfigVersion | 설정 버전 | 프로젝트 콘텐츠 설정 변경을 식별하는 증가 값 |
| PolicyVersion | 정책 버전 | 추천 판단 알고리즘의 변경을 식별하는 값 |
| Source | 추천 출처 | `LOCAL`, `REMOTE`, `LOCAL_FALLBACK` 중 추천을 만든 위치 |
| Idempotency | 멱등성 | 같은 요청을 재전송해도 추천과 이벤트가 중복 생성되지 않는 성질 |

## 실행 모드

### LOCAL

클라이언트의 Room 캐시와 로컬 정책만 사용한다. 해당 키오스크 내부 이력만 고려할 수 있다.

### REMOTE

서버가 추천을 결정한다. 전체 키오스크와 웹 사용자의 최근 추천 상태를 고려할 수 있다.

### HYBRID

서버를 우선 사용하고 실패 시 로컬로 전환한다. 로컬 fallback 이벤트는 네트워크 복구 후 동기화한다.

## 사용하지 않을 도메인 용어

다음은 태안 프로젝트 콘텐츠에서는 사용할 수 있지만 공통 Domain, API와 DB 핵심 명칭으로 사용하지 않는다.

| 전용 용어 | 공통 용어 |
|---|---|
| Flower | RecommendationItem |
| Garden | Location |
| Festival | Project |
| FlowerMapping | RecommendationRule |
| Visit/User | Session |

## 식별자 규칙

- 내부 엔터티와 이벤트 ID는 UUID를 사용한다.
- 사람이 입력하거나 URL에서 쓰는 값은 별도 `code`를 사용한다.
- `code`는 프로젝트 범위에서 안정적으로 유지한다.
- 외부 요청은 `request_id`, 이벤트는 `event_id`를 사용한다.
- DB 자동 증가 정수 값을 외부 계약의 영구 식별자로 노출하지 않는다.

## 시간 규칙

- API와 이벤트 시간은 ISO 8601과 timezone offset을 사용한다.
- 서버 저장 기준은 UTC다.
- 화면 표시만 프로젝트 또는 기기 timezone으로 변환한다.
- 추천 판단에는 서버의 신뢰 가능한 ClockPort를 사용한다.

