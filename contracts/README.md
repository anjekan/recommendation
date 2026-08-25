# Contracts

Android, 서버, 관리자 웹과 향후 Web/PWA가 공유하는 기술 독립 계약의 원본이다.

```text
contracts/
├─ openapi/
├─ schemas/
└─ examples/
```

계약 변경 시:

1. schema/API 버전을 검토한다.
2. 예제 요청·응답을 갱신한다.
3. 하위 호환성을 확인한다.
4. Android·서버 계약 테스트를 함께 갱신한다.
