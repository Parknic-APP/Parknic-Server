# GCP 배포 가이드 (VM + docker compose)

`docs/migration-plan.md`의 구성 A(VM 한 대에 컨테이너 세 개)를 기준으로 한다.

```
인터넷 -> caddy(80/443, HTTPS) -> app(8080, 내부) -> db(5432, 내부)
```

> **실제 GCP에서 확인됨 (2026-11-23)**: VM 생성(콘솔), 고정 IP, 80/443 방화벽, 도커 설치, 저장소 클론, `.env` 작성, `docker compose up`(Flyway V1~V3 적용), Let's Encrypt 인증서 발급, HTTPS와 HTTP 리다이렉트, 공개 페이지(`/terms` 등), 공원 CSV 적재(17,012건), 백업과 복구 연습(데이터를 지운 뒤 `restore-db.sh --yes`로 복원), 일일 백업 크론.
> 이어서 확인됨: SSH(22)를 내 IP로 제한한 뒤에도 `ssh parknic` 접속, Cloud Storage 버킷에 백업 업로드(서비스 계정은 생성과 조회만 가능하고 덮어쓰기와 삭제는 거절됨), 크론과 같은 최소 환경에서의 업로드.
> **확인되지 않음**: 버킷의 수명 주기 규칙(14일)이 실제로 객체를 지우는지, 업타임 체크 알림, 실기기에서의 Apple 로그인, `REVIEW_MODE` 켜기. 해당 절에 표시했다.

## 0. 준비물

- GCP 프로젝트와 결제 설정, `gcloud` CLI 로그인
- 도메인(또는 DuckDNS 등 DNS) 하나. VM의 고정 IP를 가리키게 한다
- Apple 로그인 설정: `APPLE_TEAM_ID`, `APPLE_KEY_ID`, `APPLE_PRIVATE_KEY`(.p8 내용)
- `JWT_SECRET`: `openssl rand -base64 48`로 만든 값

## 1. VM 만들기

리전은 서울(`asia-northeast3`)이고, 실제로는 콘솔에서 만들었다(아래 `gcloud` 명령과 같은 결과). **머신 유형은 `e2-medium`(2 vCPU, 4GB)을 권한다.** 이미지를 VM 안에서 빌드하는데 Gradle 빌드가 메모리를 1GB 이상 쓰고, 앱과 PostgreSQL이 같이 돌기 때문에 2GB(`e2-small`)는 빌드 중에 모자랄 수 있다. 나중에 사용량을 보고 VM을 멈춘 뒤 유형을 줄일 수 있다.

콘솔에서 만들 때의 설정: 리전 `asia-northeast3`(서울), 영역은 하나로 고정, OS `Ubuntu 24.04 LTS`, 부팅 디스크 30GB, 네트워킹의 **"HTTP/HTTPS 트래픽 허용" 체크**(80/443 규칙이 자동 생성됨), **외부 IPv4 주소는 "고정 IP 주소 예약"**(이름 `parknic-ip`), 보안의 액세스 범위에서 **Storage를 "읽기/쓰기"**(백업을 Cloud Storage에 올리기 위해).

```bash
PROJECT=<프로젝트 ID>
ZONE=asia-northeast3-a   # 실제 VM은 콘솔에서 -c 영역에 만들어졌다. 영역은 어디든 되지만 이후 명령에서 같은 값을 쓴다

# 고정 외부 IP (VM을 재시작해도 IP가 바뀌지 않도록)
gcloud compute addresses create parknic-ip --project=$PROJECT --region=asia-northeast3

# VM (Ubuntu 24.04, 디스크 30GB SSD)
gcloud compute instances create parknic --project=$PROJECT --zone=$ZONE \
  --machine-type=e2-medium \
  --image-family=ubuntu-2404-lts-amd64 --image-project=ubuntu-os-cloud \
  --boot-disk-size=30GB --boot-disk-type=pd-balanced \
  --scopes=storage-rw,logging-write,monitoring-write \
  --address=parknic-ip --tags=parknic

# 방화벽: 80/443은 모두에게, SSH(22)는 본인 IP만. 5432와 8080은 열지 않는다
gcloud compute firewall-rules create parknic-web --project=$PROJECT \
  --allow=tcp:80,tcp:443 --target-tags=parknic
gcloud compute firewall-rules create parknic-ssh --project=$PROJECT \
  --allow=tcp:22 --source-ranges=<내 공인 IP>/32 --target-tags=parknic
```

기본 네트워크의 `default-allow-ssh` 규칙이 모든 IP에 22를 열어 두므로, 위 규칙을 만든 뒤 지우거나 범위를 줄인다(**확인되지 않음**: 콘솔에서 `default-allow-ssh`의 소스 범위를 `<내 공인 IP>/32`로 바꾸고, 콘솔 브라우저 SSH의 탈출구로 `35.235.240.0/20`도 함께 둔다).

**내 컴퓨터에서 직접 `ssh parknic`으로 접속하려면** 이 VM 전용 키를 만들고 `~/.ssh/config`에 항목을 둔다.

```bash
ssh-keygen -t ed25519 -f ~/.ssh/parknic_gcp -N "" -C "<사용자>@parknic-gcp"   # 키 끝의 주석이 VM의 사용자 이름이 된다
cat ~/.ssh/parknic_gcp.pub                                                  # 이 한 줄을 콘솔 VM 수정 > SSH 키에 등록
cat >> ~/.ssh/config <<'EOF'
Host parknic
    HostName <고정 IP>
    User <사용자>
    IdentityFile ~/.ssh/parknic_gcp
    IdentitiesOnly yes
    StrictHostKeyChecking accept-new
EOF
```

콘솔에서 키 저장 시 `Supplied fingerprint does not match current metadata fingerprint`가 나면 수정 화면을 새로 고치고 바로 저장하거나, **Compute Engine > 설정 > 메타데이터 > SSH 키**(프로젝트 전체)에 등록한다.

## 2. DNS 연결

도메인의 A 레코드를 고정 IP(`gcloud compute addresses describe parknic-ip --region=asia-northeast3 --format='value(address)'`)로 설정한다.
DuckDNS를 쓰면 duckdns.org에서 이름을 만들고 `current ip`를 고정 IP로 설정한다(또는 `https://www.duckdns.org/update?domains=<이름>&token=<토큰>&ip=<고정 IP>`, 토큰은 비밀). 이 서버는 검증용으로 `parknic-gcp.duckdns.org`를 만들었고, 기존 임시 서버의 `parknic-dev`는 그대로 두었다. 서버에서 `getent hosts <도메인>`이 고정 IP를 돌려주는지 확인한 뒤 시작한다.

**인증서는 DNS가 새 IP를 가리킨 뒤에** 발급된다. 먼저 서버를 시작하면 발급이 실패하고 Caddy가 재시도한다(문제는 없지만 실패 횟수가 쌓이면 일정 시간 막힐 수 있다).

## 3. 서버 준비 (VM 안에서)

```bash
ssh parknic                     # 위에서 만든 ~/.ssh/config 항목 (또는 gcloud compute ssh 또는 콘솔 SSH 버튼)

# 보안 업데이트와 도커 설치 (공식 설치 스크립트). 재부팅하면 도커 그룹 권한과 커널 업데이트가 적용된다
sudo apt-get update && sudo DEBIAN_FRONTEND=noninteractive apt-get -y upgrade
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
sudo reboot
# 1분 뒤 다시 ssh parknic 으로 접속해서 확인: docker --version, docker compose version, docker run --rm hello-world
```

### 저장소 가져오기 (비공개 저장소)
조직(`Parknic-APP`)의 정책이 **Deploy keys를 막아 둘 수 있다**(저장소 Settings > Deploy keys에 "Disabled by Parknic-APP"이 보이고 `Add deploy key` 버튼이 없음). 이 경우 **fine-grained 개인 액세스 토큰**을 읽기 전용으로 만든다.

1. GitHub 프로필 사진 > Settings > Developer settings > Personal access tokens > Fine-grained tokens > Generate new token
2. Resource owner: **`Parknic-APP`**(개인 계정을 고르면 조직 저장소에 접근할 수 없어서 404가 난다), Repository access: `Only select repositories` > `Parknic-Server`, Permissions: **Contents: Read-only**, 만료일 설정
3. 토큰(`github_pat_...`)은 한 번만 보이므로 복사해 둔다. **채팅이나 쉘 기록에 남기지 않는다.**

```bash
# 토큰이 유효하고 저장소 접근이 되는지 먼저 확인 (200이면 정상, 401은 토큰 오류, 404는 권한 없음)
read -rs TOKEN; echo; echo "토큰 길이: ${#TOKEN}"          # fine-grained 토큰은 93자 안팎. 0이면 붙여 넣기가 안 된 것
curl -s -o /dev/null -w '저장소 접근: HTTP %{http_code}\n' -H "Authorization: Bearer $TOKEN" https://api.github.com/repos/Parknic-APP/Parknic-Server
unset TOKEN

git clone https://github.com/Parknic-APP/Parknic-Server.git ~/parknic     # Username: GitHub 사용자 이름, Password: 위 토큰
cd ~/parknic
git config credential.helper 'cache --timeout=3600'                        # 1시간만 메모리에 보관(디스크에 저장하지 않음)
```

### `.env` 만들기 (비밀값은 서버에서 생성)
```bash
cp .env.example .env
chmod 600 .env                  # 비밀값이 있으므로 소유자만 읽게
set_env() { sed -i "s|^$1=.*|$1=$2|" .env; }
set_env DOMAIN <도메인>
set_env DB_NAME parknic
set_env DB_USERNAME parknic
set_env DB_PASSWORD "$(openssl rand -hex 24)"
set_env JWT_SECRET "$(openssl rand -hex 48)"
awk -F= '/^[A-Z_]+=/ {print $1, "(길이", length($2) ")"}' .env    # 값은 출력하지 않고 길이만 확인
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

새 DB에는 공원 데이터가 없다. 한 번 적재한다(공원 CSV `parks.csv`가 필요). 내 컴퓨터에서 `scp parks.csv parknic:~/parks.csv`로 복사한 뒤 서버에서 실행한다.

```bash
# CSV를 VM에 복사한 뒤, 일회성 컨테이너로 적재 (앱과 같은 DB를 사용). 이미지의 실행 명령(java -jar app.jar)에 인자만 이어 붙인다
docker compose -f docker-compose.prod.yaml --env-file .env run -d --name parknic-import --no-deps \
  -v ~/parks.csv:/data/parks.csv:ro \
  app --parknic.park-import.file=/data/parks.csv --server.port=0
docker logs -f parknic-import      # "park import: 읽음 ... 최종 ..." 줄이 나오면 끝난 것
docker rm -f parknic-import        # 적재가 끝나도 서버는 계속 떠 있으므로 직접 정리한다
```

적재 후 `select count(*) from parks`가 17,012건인지 본다(실제 VM에서 확인됨). 적재용 컨테이너는 앱과 같은 환경변수(DB 비밀번호 포함)를 쓰므로 끝나면 `docker rm -f parknic-import`로 지우고, 서버의 `~/parks.csv`도 지운다.

## 5. 백업

```bash
cd ~/parknic
scripts/backup-db.sh                         # ./backups/parknic-YYYYmmdd-HHMMSS.dump 생성, 14일 지난 파일 삭제
GCS_BUCKET=<버킷 이름> scripts/backup-db.sh   # Cloud Storage에도 업로드 (VM과 다른 곳에 보관)
```

Cloud Storage 버킷은 한 번만 만든다. **보관 기간은 버킷의 수명 주기 규칙이 지킨다**(스크립트의 `KEEP_DAYS`는 VM 안의 `backups/` 폴더만 정리한다). 개인정보 처리방침에 적은 백업 보관 기간과 같은 값을 쓴다(`scripts/gcs-lifecycle.json`은 14일).

```bash
BUCKET=<버킷 이름>
# 서울 리전에 만들고, 객체를 지우거나 덮어쓰지 못하는 VM 서비스 계정 권한(생성 + 조회)만 준다
gcloud storage buckets create gs://$BUCKET --location=asia-northeast3 --uniform-bucket-level-access
gcloud storage buckets update gs://$BUCKET --lifecycle-file=scripts/gcs-lifecycle.json
gcloud storage buckets describe gs://$BUCKET --format='value(lifecycle_config)'   # 규칙이 적용됐는지 확인

# VM이 쓰는 서비스 계정에 업로드 권한 부여 (계정 이름은 `gcloud compute instances describe parknic --zone=... --format='value(serviceAccounts[0].email)'`)
gcloud storage buckets add-iam-policy-binding gs://$BUCKET \
  --member=serviceAccount:<서비스 계정> --role=roles/storage.objectCreator
gcloud storage buckets add-iam-policy-binding gs://$BUCKET \
  --member=serviceAccount:<서비스 계정> --role=roles/storage.objectViewer
```

**두 역할이 모두 필요하다.** `objectCreator`만 있으면 `gcloud storage cp`가 업로드 전에 같은 이름의 객체가 있는지 조회(`storage.objects.get`)하다가 403으로 실패한다. 반대로 `objectViewer`만 있으면 `storage.objects.create`가 거절된다. 두 역할을 합치면 생성과 조회는 되고, **덮어쓰기와 삭제(`storage.objects.delete`)는 되지 않아서** 서버가 뚫려도 공격자가 기존 백업을 지우거나 덮어쓰지 못한다(실제 VM에서 확인됨: 업로드는 성공하고 덮어쓰기와 삭제는 거절). 콘솔에서는 역할 이름이 "저장소 개체 만들기(생성자)"와 "저장소 개체 뷰어"로 보인다.

(콘솔로 같은 설정을 해서 업로드가 되는 것을 확인했다. 위 `gcloud` 명령 자체를 실행해 본 것은 아니다.) VM에서 업로드하려면 VM이 Cloud Storage 접근 범위로 만들어져 있어야 한다(`gcloud compute instances create`의 `--scopes=storage-rw`, 또는 이후 `gcloud compute instances set-service-account`). 범위가 없으면 `backup-db.sh`의 `gcloud storage cp`가 권한 오류로 실패한다.

매일 새벽 3시에 실행하려면 크론에 등록한다. **크론의 기본 `PATH`에는 `/snap/bin`이 없는데 `gcloud`가 snap으로 설치되어 있으므로, 크론 파일 맨 위에 `PATH`를 지정해야 한다.** 그렇지 않으면 크론에서만 `gcloud`를 못 찾아 업로드가 실패한다. 실제 VM에서 아래처럼 등록했고, 크론과 같은 최소 환경(`env -i`)에서 업로드까지 성공하는 것을 확인했다.

```
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/snap/bin
0 3 * * * cd /home/<사용자>/parknic && GCS_BUCKET=<버킷 이름> scripts/backup-db.sh >> backups/backup.log 2>&1
```

(`crontab -e`로 직접 편집해도 되고, 위 두 줄을 파일에 저장해 `crontab <파일>`로 등록해도 된다.)

- 같은 디스크에만 두면 VM이 사라질 때 백업도 사라진다. Cloud Storage 버킷(수명 주기 규칙으로 보관 기간 지정)에 올리는 것을 권한다. 업로드에는 VM의 서비스 계정에 해당 버킷의 **생성과 조회 권한**이 있어야 한다(아래 설명).
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
- 로그 보관: 컨테이너 로그는 서비스마다 **10MB x 3개**까지만 보관하고 오래된 것부터 지운다(`docker-compose.prod.yaml`의 `x-logging`). 디스크가 가득 차는 것을 막는 용량 기준이고, 로그가 적은 서비스에서는 이 용량이 오래 걸쳐 쌓일 수 있다. 개인정보 처리방침에도 "기간"이 아니라 "일정 용량이 차면 오래된 것부터 삭제"라고 적는다. 기간으로 보장해야 한다면 Cloud Logging(`_Default` 버킷의 보관 기간을 설정)으로 보내는 방식으로 바꾼다.
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
