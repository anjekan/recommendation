# Infrastructure

로컬 개발, 테스트, 운영 배포 구성을 관리한다.

예정 구조:

```text
infrastructure/
├─ docker/
├─ postgres/
├─ redis/
├─ nginx/
└─ monitoring/
```

서버 프레임워크와 이미지 정책을 확정한 뒤 Docker Compose를 추가한다. 비밀번호와 인증키는 저장소에 커밋하지 않는다.
