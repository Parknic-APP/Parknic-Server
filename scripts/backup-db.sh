#!/usr/bin/env bash
# DB를 덤프해서 파일로 남긴다. 사용: scripts/backup-db.sh
# 환경변수: ENV_FILE(기본 .env), BACKUP_DIR(기본 ./backups), KEEP_DAYS(기본 14), GCS_BUCKET(있으면 Cloud Storage에도 업로드)
# 실패하면 0이 아닌 코드로 종료한다 (크론/알림에서 실패를 알아채기 위해)
set -euo pipefail
cd "$(dirname "$0")/.."

ENV_FILE="${ENV_FILE:-.env}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
KEEP_DAYS="${KEEP_DAYS:-14}"

dc() { docker compose -f docker-compose.prod.yaml --env-file "$ENV_FILE" "$@"; }
env_value() { grep -E "^$1=" "$ENV_FILE" | head -1 | cut -d= -f2-; }

DB_NAME="$(env_value DB_NAME)"
DB_USERNAME="$(env_value DB_USERNAME)"
[ -n "$DB_NAME" ] && [ -n "$DB_USERNAME" ] || { echo "$ENV_FILE에 DB_NAME, DB_USERNAME이 필요합니다" >&2; exit 1; }

mkdir -p "$BACKUP_DIR"
FILE="$BACKUP_DIR/parknic-$(date +%Y%m%d-%H%M%S).dump"
TMP="$FILE.tmp"
trap '[ -f "$TMP" ] && rm -f "$TMP"; true' EXIT

# custom 형식: pg_restore로 선택 복구와 검증이 가능하고 압축된다
dc exec -T db pg_dump -U "$DB_USERNAME" -d "$DB_NAME" --format=custom > "$TMP"

# 덤프가 읽히는지 확인한 뒤에만 정식 파일로 바꾼다 (깨진 파일이 백업처럼 남지 않도록)
dc exec -T db pg_restore --list < "$TMP" > /dev/null
mv "$TMP" "$FILE"
echo "백업 완료: $FILE ($(wc -c < "$FILE" | tr -d ' ') bytes)"

if [ -n "${GCS_BUCKET:-}" ]; then
  gcloud storage cp "$FILE" "gs://$GCS_BUCKET/db/"
  echo "업로드 완료: gs://$GCS_BUCKET/db/$(basename "$FILE")"
fi

# 보관 기간이 지난 백업 파일만 정리 (이름이 parknic-*.dump인 파일만)
find "$BACKUP_DIR" -maxdepth 1 -type f -name 'parknic-*.dump' -mtime +"$KEEP_DAYS" -print -delete | sed 's/^/오래된 백업 삭제: /'
