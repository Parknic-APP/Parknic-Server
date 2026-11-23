# Parknic-Server

## 로컬 실행

```bash
docker compose up -d          # 로컬 DB
./gradlew bootRun             # local 프로필, application-local.yaml 필요 (Git 제외)
```

## 운영 구성

운영은 `prod` 프로필과 환경변수로 설정하고 컨테이너 세 개로 구성한다. 개발용 로그인은 꺼져 있다.

```
인터넷 -> caddy(80/443, HTTPS) -> app(8080, 내부) -> db(5432, 내부)
```

```bash
cp .env.example .env          # DOMAIN, DB_NAME, DB_USERNAME, DB_PASSWORD, JWT_SECRET 채우기
docker compose -f docker-compose.prod.yaml --env-file .env up -d --build
curl https://<DOMAIN>/actuator/health   # {"status":"UP"...}
```

- `JWT_SECRET`은 32자 이상이어야 하고, 비어 있으면 서버가 시작되지 않는다.
- `app`과 `db`는 호스트에 포트를 열지 않는다. 외부에서는 `caddy`(HTTPS)로만 접근한다.
- 스키마는 앱 시작 시 Flyway가 적용한다.
- 백업과 복구: `scripts/backup-db.sh`, `scripts/restore-db.sh <덤프> --yes`
- GCP VM 생성, 배포, 업데이트, 롤백, 장애 점검: [docs/gcp-deploy.md](docs/gcp-deploy.md)
- 앱 심사 기간의 심사용 공원 노출: [docs/review-mode.md](docs/review-mode.md)
- 서버 전환 계획: [docs/migration-plan.md](docs/migration-plan.md)
