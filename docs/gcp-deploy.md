# GCP 배포 가이드 (VM + docker compose)

`docs/migration-plan.md`의 구성 A(VM 한 대에 컨테이너 세 개)를 기준으로 한다.

```
인터넷 -> caddy(80/443, HTTPS) -> app(8080, 내부) -> db(5432, 내부)
```

> **검증 범위**: `docker-compose.prod.yaml`, `Caddyfile`, `scripts/backup-db.sh`, `scripts/restore-db.sh`는 로컬에서 실제로 실행해 확인했다(HTTPS는 `DOMAIN=localhost`의 자체 서명 인증서, 백업과 복구는 데이터를 지운 뒤 되돌아오는지 확인).
> **확인되지 않음**: 아래 `gcloud`/콘솔 단계(VM 생성, 방화벽, 고정 IP, 업타임 체크)와 실제 도메인에서의 Let's Encrypt 발급은 GCP 계정이 있어야 해서 실행해 보지 못했다. 처음 실행할 때 출력과 오류를 보며 확인한다.

## 0. 준비물

- GCP 프로젝트와 결제 설정, `gcloud` CLI 로그인
- 도메인(또는 DuckDNS 등 DNS) 하나. VM의 고정 IP를 가리키게 한다
- Apple 로그인 설정: `APPLE_TEAM_ID`, `APPLE_KEY_ID`, `APPLE_PRIVATE_KEY`(.p8 내용)
- `JWT_SECRET`: `openssl rand -base64 48`로 만든 값

## 1. VM 만들기

리전은 서울(`asia-northeast3`)을 예로 든다. 사양은 작게 시작해서 필요하면 키운다.

```bash
PROJECT=<프로젝트 ID>
ZONE=asia-northeast3-a

# 고정 외부 IP (VM을 재시작해도 IP가 바뀌지 않도록)
gcloud compute addresses create parknic-ip --project=$PROJECT --region=asia-northeast3

# VM (Ubuntu 24.04, 디스크 30GB SSD)
gcloud compute instances create parknic --project=$PROJECT --zone=$ZONE \
  --machine-type=e2-small \
  --image-family=ubuntu-2404-lts-amd64 --image-project=ubuntu-os-cloud \
  --boot-disk-size=30GB --boot-disk-type=pd-ssd \
  --address=parknic-ip --tags=parknic

# 방화벽: 80/443은 모두에게, SSH(22)는 본인 IP만. 5432와 8080은 열지 않는다
gcloud compute firewall-rules create parknic-web --project=$PROJECT \
  --allow=tcp:80,tcp:443 --target-tags=parknic
gcloud compute firewall-rules create parknic-ssh --project=$PROJECT \
  --allow=tcp:22 --source-ranges=<내 공인 IP>/32 --target-tags=parknic
```

기본 네트워크의 `default-allow-ssh` 규칙이 모든 IP에 22를 열어 두므로, 위 규칙을 만든 뒤 지우거나 범위를 줄인다.

## 2. DNS 연결

도메인의 A 레코드를 고정 IP(`gcloud compute addresses describe parknic-ip --region=asia-northeast3 --format='value(address)'`)로 설정한다.
DuckDNS를 쓰면 `https://www.duckdns.org/update?domains=<이름>&token=<토큰>&ip=<고정 IP>`로 IP를 바꾼다(토큰은 비밀).

**인증서는 DNS가 새 IP를 가리킨 뒤에** 발급된다. 먼저 서버를 시작하면 발급이 실패하고 Caddy가 재시도한다(문제는 없지만 실패 횟수가 쌓이면 일정 시간 막힐 수 있다).

## 3. 서버 준비 (VM 안에서)

```bash
gcloud compute ssh parknic --zone=asia-northeast3-a

# 도커 설치 (공식 설치 스크립트)
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER   # 다시 로그인해야 적용

git clone https://github.com/Parknic-APP/Parknic-Server.git ~/parknic
cd ~/parknic
cp .env.example .env
chmod 600 .env                  # 비밀값이 있으므로 소유자만 읽게
nano .env
```

`.env`에 채울 값

| 변수 | 값 |
|---|---|
| `DOMAIN` | 위에서 연결한 도메인 |
| `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | 새로 정한 값(비밀번호는 충분히 길게) |
| `JWT_SECRET` | 32자 이상 랜덤 문자열 |
| `APPLE_CLIENT_ID` | 앱 Bundle ID (생략하면 `com.hwangsanghwan.parknic`) |
| `APPLE_TEAM_ID`, `APPLE_KEY_ID`, `APPLE_PRIVATE_KEY` | Apple 개발자 계정 값. `.p8`은 줄바꿈을 `\n`으로 바꿔 한 줄로 |
| `REVIEW_MODE` | 평소 비움(false). 앱 심사 기간만 true (`docs/review-mode.md`) |

`HTTP_PORT`, `HTTPS_PORT`, `COMPOSE_PROJECT_NAME`은 비워 둔다(로컬 확인용).

## 4. 첫 시작

```bash
docker compose -f docker-compose.prod.yaml --env-file .env up -d --build
docker compose -f docker-compose.prod.yaml --env-file .env ps        # db, app, caddy가 모두 Up/healthy
docker compose -f docker-compose.prod.yaml --env-file .env logs -f app   # Flyway V1~V3 적용, Started ParknicApplication
curl https://<DOMAIN>/actuator/health                                   # {"status":"UP",...}
```

새 DB에는 공원 데이터가 없다. 한 번 적재한다(공원 CSV `parks.csv`가 필요).

```bash
# CSV를 VM에 복사한 뒤, 일회성 컨테이너로 적재 (앱과 같은 DB를 사용). 이미지의 실행 명령(java -jar app.jar)에 인자만 이어 붙인다
docker compose -f docker-compose.prod.yaml --env-file .env run -d --name parknic-import --no-deps \
  -v ~/parks.csv:/data/parks.csv:ro \
  app --parknic.park-import.file=/data/parks.csv --server.port=0
docker logs -f parknic-import      # "park import: 읽음 ... 최종 ..." 줄이 나오면 끝난 것
docker rm -f parknic-import        # 적재가 끝나도 서버는 계속 떠 있으므로 직접 정리한다
```

적재 후 `select count(*) from parks`가 17,012건 안팎인지 본다. (로컬에서 같은 방식으로 적재가 되는 것은 확인했다. VM에서는 확인되지 않음)

## 5. 백업

```bash
cd ~/parknic
scripts/backup-db.sh                         # ./backups/parknic-YYYYmmdd-HHMMSS.dump 생성, 14일 지난 파일 삭제
GCS_BUCKET=<버킷 이름> scripts/backup-db.sh   # Cloud Storage에도 업로드 (VM과 다른 곳에 보관)
```

매일 새벽 3시에 실행하려면 `crontab -e`에 추가한다.

```
0 3 * * * cd /home/<사용자>/parknic && GCS_BUCKET=<버킷 이름> scripts/backup-db.sh >> backups/backup.log 2>&1
```

- 같은 디스크에만 두면 VM이 사라질 때 백업도 사라진다. Cloud Storage 버킷(수명 주기 규칙으로 보관 기간 지정)에 올리는 것을 권한다. 업로드에는 VM의 서비스 계정에 해당 버킷의 쓰기 권한이 있어야 한다.
- 스크립트가 실패하면(`pg_dump` 오류, 읽을 수 없는 덤프) 0이 아닌 코드로 끝나서 로그(`backup.log`)에 남는다. 실패를 알림으로 받으려면 Cloud Monitoring 로그 기반 알림 등을 따로 설정한다.
- **복구를 연습한다.** 백업은 복구해 봐야 믿을 수 있다. 분기에 한 번은 별도 환경에서 `restore-db.sh`로 되살려 본다.

## 6. 복구

```bash
scripts/restore-db.sh backups/parknic-20261123-030000.dump          # --yes가 없으면 아무것도 바꾸지 않고 안내만 출력
scripts/restore-db.sh backups/parknic-20261123-030000.dump --yes    # 앱과 프록시를 멈추고, 한 트랜잭션으로 복구한 뒤 다시 시작
```

복구는 현재 데이터를 덮어쓴다. 복구 직전에 현재 상태를 `scripts/backup-db.sh`로 한 번 더 남겨 두면 되돌릴 수 있다.

## 7. 업데이트와 롤백

```bash
cd ~/parknic
git pull
scripts/backup-db.sh                                                 # 업데이트 전 백업
docker compose -f docker-compose.prod.yaml --env-file .env up -d --build
docker compose -f docker-compose.prod.yaml --env-file .env logs --tail=100 app
curl https://<DOMAIN>/actuator/health
```

- 컨테이너가 교체되는 동안 몇 초간 요청이 실패할 수 있다(무중단 배포는 아니다).
- 새 버전이 Flyway 마이그레이션을 포함하면 시작 시 자동 적용된다. 마이그레이션은 되돌려지지 않으므로 업데이트 전 백업이 롤백 수단이다.
- 롤백: `git log`로 이전 커밋을 찾아 `git checkout <커밋>` 후 `up -d --build`. 마이그레이션이 이미 적용된 DB라면 코드만 되돌리면 호환되지 않을 수 있어서, 이때는 업데이트 전 백업으로 `restore-db.sh --yes`를 함께 한다.

## 8. 모니터링

- **업타임 체크**: Cloud Monitoring > Uptime checks에서 `https://<DOMAIN>/actuator/health`를 1~5분 간격으로 설정하고, 실패 시 이메일 알림 정책을 연결한다.
- 로그: `docker compose ... logs -f app`. `unexpected error`, `APPLICATION FAILED TO START`를 먼저 본다. 앱이 계속 재시작되면(`restart: unless-stopped`) `ps`의 재시작 횟수와 `logs`를 확인한다.
- 디스크: `df -h`. 백업 폴더(`backups/`)와 도커 이미지가 쌓이면 가득 찬다. 가끔 `docker image prune`.

## 9. 장애 점검 순서

1. `docker compose ... ps`로 세 컨테이너가 모두 Up인지, `app`이 healthy인지
2. `docker compose ... logs --tail=200 app` (시작 실패 원인: `JWT_SECRET` 비어 있음, DB 접속 실패, 마이그레이션 실패)
3. `curl http://localhost`(VM 안에서)와 `https://<DOMAIN>`의 차이로 프록시, 인증서, DNS 문제를 구분
4. 인증서: `docker compose ... logs caddy`에서 발급 실패(DNS가 다른 IP, 80/443이 막힘)를 확인
5. DB: `docker compose ... exec db psql -U <DB_USERNAME> -d <DB_NAME> -c 'select 1'`

## 10. 비밀값 관리

- `.env`는 VM에서 `chmod 600`, Git에 올리지 않는다(`.gitignore`에 포함).
- Apple `.p8`, `JWT_SECRET`, DB 비밀번호는 GCP Secret Manager에 원본을 보관하고 필요할 때 `.env`에 넣는 방식을 권한다.
- 환경변수는 `docker inspect`로 보이므로 VM 접근 권한을 최소로 유지한다.
