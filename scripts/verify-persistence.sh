#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
fingerprint() {
  docker compose exec -T db sh -c 'psql -XqAt -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < scripts/database-fingerprint.sql
}
before=$(fingerprint)
test ${#before} -eq 32
docker compose restart
docker compose up -d --wait
after=$(fingerprint)
test "$before" = "$after" || { echo 'Database changed after restart' >&2; exit 1; }
echo 'PASS: all account, session, catalog, order, item, event and loyalty data retained after restart'
