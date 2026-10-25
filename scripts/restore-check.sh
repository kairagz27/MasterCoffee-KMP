#!/bin/sh
set -eu
backup=${1:?Usage: sh scripts/restore-check.sh path.dump mastercoffee_restore_name}
destination=${2:-mastercoffee_restore_check}
case "$destination" in mastercoffee_restore_*) ;; *) echo 'Use a fresh mastercoffee_restore_* database name' >&2; exit 1 ;; esac
case "$destination" in *[!a-z0-9_]*) echo 'Only lowercase letters, digits and underscores are permitted' >&2; exit 1 ;; esac
cd "$(dirname "$0")/.."
test -r "$backup.fingerprint" || { echo 'Backup baseline .dump.fingerprint is required' >&2; exit 1; }
expected=$(tr -d '\r\n' < "$backup.fingerprint")
test ${#expected} -eq 32 || { echo 'Invalid backup fingerprint' >&2; exit 1; }
# createdb deliberately refuses an existing destination.
docker compose exec -T db sh -c 'createdb -U "$POSTGRES_USER" "$1"' -- "$destination"
docker compose exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" -d "$1" --no-owner --exit-on-error' -- "$destination" < "$backup"
docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$1" -c "SELECT count(*) FROM orders; SELECT count(*) FROM users; SELECT count(*) FROM schema_migrations;"' -- "$destination"
restored_fingerprint=$(docker compose exec -T db sh -c 'psql -XqAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$1"' -- "$destination" < scripts/database-fingerprint.sql)
test "$expected" = "$restored_fingerprint" || { echo 'Restored data differs from the backup snapshot baseline' >&2; exit 1; }
docker compose exec -T db sh -c 'psql -XqAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$1"' -- "$destination" < scripts/verify-sequences.sql
echo 'PASS: restored business data matches the frozen backup snapshot'
