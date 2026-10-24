#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p backups
stamp=$(date -u +%Y%m%d-%H%M%S)
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "backups/mastercoffee-$stamp.dump"
docker compose exec -T api tar -czf - -C /data uploads > "backups/mastercoffee-$stamp-uploads.tar.gz"
echo "Created backups/mastercoffee-$stamp.dump and matching uploads archive"
