#!/bin/sh
set -eu
umask 077
cd "$(dirname "$0")/.."
mkdir -p backups
remote=$(docker compose exec -T db mktemp -d /tmp/mastercoffee-backup.XXXXXX | tr -d '\r')
case "$remote" in /tmp/mastercoffee-backup.??????) ;; *) echo 'Backup workspace creation failed' >&2; exit 1 ;; esac
stamp="$(date -u +%Y%m%d-%H%M%S)-${remote##*.}"
docker compose cp scripts/database-fingerprint.sql "db:$remote/database-fingerprint.sql"
docker compose cp scripts/backup-snapshot.psql "db:$remote/backup-snapshot.psql"
docker compose exec -T -e "COFFEE_BACKUP_DIR=$remote" db sh -c 'psql -XqAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" --set=fingerprint_sql="$COFFEE_BACKUP_DIR/database-fingerprint.sql" --set=fingerprint_file="$COFFEE_BACKUP_DIR/database.fingerprint" -f "$COFFEE_BACKUP_DIR/backup-snapshot.psql"'
docker compose cp "db:$remote/database.dump" "backups/mastercoffee-$stamp.dump"
docker compose cp "db:$remote/database.fingerprint" "backups/mastercoffee-$stamp.dump.fingerprint"
docker compose exec -T api tar -czf - -C /data uploads > "backups/mastercoffee-$stamp-uploads.tar.gz"
sha256sum "backups/mastercoffee-$stamp-uploads.tar.gz" > "backups/mastercoffee-$stamp-uploads.tar.gz.sha256"
echo "Created backups/mastercoffee-$stamp.dump, frozen fingerprint and matching uploads archive"
