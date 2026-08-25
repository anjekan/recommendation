# ADR-0002: Ports and Adapters

- 상태: Accepted
- 결정일: 2026-08-25

## 맥락

기존 앱은 UI, 분석, 통신과 상태 관리가 Activity와 FaceAnalyzer에 결합되어 있다. 서버 없이 실행하거나 웹으로 확장하려면 기술 구현을 교체할 수 있어야 한다.

## 결정

다음 의존 방향을 적용한다.

```text
UI → Application → Domain ← Adapters
```

Domain은 필요한 기능을 Port로 정의하고, Android·DB·네트워크 구현은 Adapter가 제공한다.

초기 핵심 Port:

- AnalysisPort
- RecommendationPort
- ProjectConfigPort
- EventLogPort
- RecommendationStatePort
- RuntimeConfigPort
- AssetPort
- ClockPort
- IdGeneratorPort

## 결과

- Local, Remote와 Hybrid 구현을 UI 변경 없이 교체할 수 있다.
- 가짜 Adapter로 서버와 카메라 없이 테스트할 수 있다.
- 계층 사이에는 명시적인 매핑 코드가 필요하다.

