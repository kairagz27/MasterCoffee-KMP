param([string]$OutputDirectory = (Join-Path $PSScriptRoot '..\backups'))
$ErrorActionPreference = 'Stop'
Push-Location (Join-Path $PSScriptRoot '..')
try {
    New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
    $destination = (Resolve-Path -LiteralPath $OutputDirectory).Path
    $stamp = (Get-Date).ToUniversalTime().ToString('yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0,8)
    $databaseFile = Join-Path $destination "mastercoffee-$stamp.dump"
    $remote = (docker compose exec -T db mktemp -d /tmp/mastercoffee-backup.XXXXXX | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $remote -notmatch '^/tmp/mastercoffee-backup\.[a-zA-Z0-9]+$') { throw 'Backup workspace creation failed' }
    docker compose cp scripts/database-fingerprint.sql "db:$remote/database-fingerprint.sql"
    if ($LASTEXITCODE -ne 0) { throw 'Fingerprint SQL copy failed' }
    docker compose cp scripts/backup-snapshot.psql "db:$remote/backup-snapshot.psql"
    if ($LASTEXITCODE -ne 0) { throw 'Snapshot SQL copy failed' }
    docker compose exec -T -e "COFFEE_BACKUP_DIR=$remote" db sh -c 'psql -XqAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" --set=fingerprint_sql="$COFFEE_BACKUP_DIR/database-fingerprint.sql" --set=fingerprint_file="$COFFEE_BACKUP_DIR/database.fingerprint" -f "$COFFEE_BACKUP_DIR/backup-snapshot.psql"'
    if ($LASTEXITCODE -ne 0) { throw 'Snapshot pg_dump failed' }
    docker compose cp "db:$remote/database.dump" $databaseFile
    if ($LASTEXITCODE -ne 0) { throw 'Copying database backup failed' }
    docker compose cp "db:$remote/database.fingerprint" "$databaseFile.fingerprint"
    if ($LASTEXITCODE -ne 0) { throw 'Copying snapshot baseline failed' }
    $archive = "/tmp/mastercoffee-uploads-$stamp.tar.gz"
    docker compose exec -T api tar -czf $archive -C /data uploads
    if ($LASTEXITCODE -ne 0) { throw 'Uploads archive failed' }
    $uploadsFile = Join-Path $destination "mastercoffee-$stamp-uploads.tar.gz"
    docker compose cp "api:$archive" $uploadsFile
    if ($LASTEXITCODE -ne 0) { throw 'Copying uploads archive failed' }
    Write-Output "Database: $databaseFile"
    Write-Output "Baseline: $databaseFile.fingerprint"
    (Get-FileHash -Algorithm SHA256 -LiteralPath $uploadsFile).Hash.ToLowerInvariant() | Set-Content -LiteralPath "$uploadsFile.sha256" -Encoding ascii
    Write-Output "Uploads: $uploadsFile"
} finally { Pop-Location }
