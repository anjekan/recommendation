# Taean Flower Project

태안 꽃박람회를 범용 프로젝트 설정으로 변환하는 첫 번째 콘텐츠 세트다.

감정 상태별 꽃 추천 원본 83건을 `flower-recommendations.json`으로 정규화했다. 각 항목에는 감정 코드, 스트레스 점수 범위, 꽃 이름, 꽃말, 출력 메시지와 매칭 근거가 들어 있다.

현재 자료에는 꽃 이미지와 실제 전시장 위치가 없으므로 이를 임의로 생성하지 않았다. 운영 데이터가 도착하면 이미지 URL과 장소 ID를 연결한 뒤 `project-config.json`의 아이템 및 추천 규칙으로 승격한다.

원본 PDF를 다시 가져오려면 다음 명령을 실행한다.

```powershell
python scripts/import-flower-recommendations.py "감정상태별 꽃 추천리스트.pdf" projects/taean-flower/flower-recommendations.json
```
