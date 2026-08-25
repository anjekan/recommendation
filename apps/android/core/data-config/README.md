# Project config importer

계약 JSON을 플랫폼 독립적으로 파싱하고 검증해 도메인 `ProjectConfiguration`으로 변환한다.

- schema/app version 호환성 검사
- 기본 언어 fallback
- 장소·항목·추천 규칙 참조 무결성 검사
- 비활성 장소·항목·규칙 제거
- UI 테마, 이미지 참조, 지도, 감정 표시 문구 변환

파일·Asset·HTTP에서 문자열을 읽는 책임은 각 플랫폼 Adapter에 둔다.
