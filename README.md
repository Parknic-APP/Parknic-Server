# Parknic-Server

## 로컬 실행

```bash
docker compose up -d          # 로컬 DB
./gradlew bootRun             # local 프로필, application-local.yaml 필요 (Git 제외)
```

## 운영 이미지 빌드와 실행

운영은 `prod` 프로필과 환경변수로 설정한다. 개발용 로그인은 꺼져 있다.

```bash
cp .env.example .env          # DB_NAME, DB_USERNAME, DB_PASSWORD, JWT_SECRET 채우기
docker compose -f docker-compose.prod.yaml --env-file .env up -d --build
curl localhost:8080/actuator/health   # {"status":"UP"}
```

- `JWT_SECRET`은 32자 이상이어야 하고, 비어 있으면 서버가 시작되지 않는다.
- DB 포트는 호스트에 열리지 않고 `app` 컨테이너만 접속한다.
- 스키마는 앱 시작 시 Flyway가 적용한다.
