#!/usr/bin/env bash
# 덤프로 DB를 복구한다. 기존 데이터를 덮어쓰므로 --yes가 있어야 실행한다.
# 사용: scripts/restore-db.sh <덤프 파일> --yes
# 환경변수: ENV_FILE(기본 .env)
set -euo pipefail
cd "$(dirname "$0")/.."

ENV_FILE="${ENV_FILE:-.env}"
FILE="${1:-}"

dc() { docker compose -f docker-compose.prod.yaml --env-file "$ENV_FILE" "$@"; }
env_value() { grep -E "^$1=" "$ENV_FILE" | head -1 | cut -d= -f2-; }

[ -n "$FILE" ] && [ -f "$FILE" ] || { echo "사용: scripts/restore-db.sh <덤프 파일> --yes" >&2; exit 1; }

DB_NAME="$(env_value DB_NAME)"
DB_USERNAME="$(env_value DB_USERNAME)"

if [ "${2:-}" != "--yes" ]; then
  echo "복구 대상 DB: $DB_NAME (현재 데이터를 덮어씁니다)" >&2
  echo "복구 파일: $FILE" >&2
  echo "계속하려면 --yes를 붙여 다시 실행하세요. 아무것도 바꾸지 않았습니다." >&2
  exit 1
fi

# 앱이 복구 중에 쓰기를 하지 않도록 먼저 멈춘다
dc stop caddy app

# 한 트랜잭션으로 복구해서, 중간에 실패하면 원래 상태로 되돌아간다
dc exec -T db pg_restore -U "$DB_USERNAME" -d "$DB_NAME" \
  --single-transaction --clean --if-exists --no-owner < "$FILE"

dc start app caddy
echo "복구 완료: $FILE -> $DB_NAME"
