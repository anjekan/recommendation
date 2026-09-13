# 2026 의령 리치리치 페스티벌

프로젝트 코드: `UIRYEONG_RICHRICH_2026`

`uiryeong-richrich-project-config.json`은 RICH FLOW 태블릿 기획서 v1.4를 기준으로 만든 schema version 2 초안이다.

현재 포함된 확정 요구사항:

- 10초 측정, 결과 5초 표시, 미검출 5초 후 복귀
- 맥박 55~170 BPM, 호흡 10~35 RPM
- 스트레스 4단계와 얼굴 긴장도 3구간
- 12상태와 6감각
- 상태별 3정거장 감각 순서
- 장소별 40·60·90분 추천 유효시간
- 혼잡 구간과 우천·식사·폐장 규칙
- 총 30대 태블릿 배치 가안

확정되지 않은 AU 경계값, 과열 기준, 지도 좌표, 수용인원과 운영 일정은 `null` 또는 `PENDING_CONFIRMATION`으로 표시했다. 해당 장소와 키오스크는 값이 확정될 때까지 `active: false`로 유지한다.

기획서 007의 2026 지도 이미지를 `richrich_2026_map.jpg`로 추출했다. 좌표 기준과 주요 기준점은 `MAP_COORDINATES.md`를 참조한다.
