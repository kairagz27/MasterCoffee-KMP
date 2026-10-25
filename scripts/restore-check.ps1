param(
    [Parameter(Mandatory = $true)][string]$BackupFile,
    [string]$DatabaseName = 'mastercoffee_restore_check'
)
$ErrorActionPreference = 'Stop'
if ($DatabaseName -notmatch '^mastercoffee_restore_[a-z0-9_]+$') {
    throw 'Use a fresh database name beginning mastercoffee_restore_ (lowercase, digits, underscore).'
}
$source = (Resolve-Path -LiteralPath $BackupFile).Path
$baseline = (Get-Content -LiteralPath "$source.fingerprint" -Raw).Trim()
if ($baseline -notmatch '^[0-9a-f]{32}$') { throw 'Backup snapshot .dump.fingerprint baseline is required.' }
Push-Location (Join-Path $PSScriptRoot '..')
try {
    $remote = '/tmp/mastercoffee-restore-' + [guid]::NewGuid().ToString('N') + '.dump'
    docker compose cp $source "db:$remote"
    if ($LASTEXITCODE -ne 0) { throw 'Copy failed' }
    # createdb refuses an existing destination. No database is dropped or overwritten.
    docker compose exec -T db sh -c 'createdb -U "$POSTGRES_USER" "$1"' -- $DatabaseName
    if ($LASTEXITCODE -ne 0) { throw 'Destination exists or could not be created; choose a new name.' }
    docker compose exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" -d "$1" --no-owner --exit-on-error "$2"' -- $DatabaseName $remote
    if ($LASTEXITCODE -ne 0) { throw 'pg_restore failed; inspect the separate destination database.' }
    docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$1" -c "SELECT count(*) AS orders FROM orders; SELECT count(*) AS users FROM users; SELECT count(*) AS migrations FROM schema_migrations;"' -- $DatabaseName
    if ($LASTEXITCODE -ne 0) { throw 'Restored schema check failed' }
    $fingerprintSql = Get-Content -LiteralPath scripts/database-fingerprint.sql -Raw
    $restoredFingerprint = $fingerprintSql | docker compose exec -T db sh -c 'psql -XqAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$1"' -- $DatabaseName
    if ($LASTEXITCODE -ne 0) { throw 'Restored fingerprint failed' }
    if ($baseline -ne ($restoredFingerprint -join '').Trim()) { throw 'Restored data differs from the frozen backup snapshot baseline.' }
    Get-Content -LiteralPath scripts/verify-sequences.sql -Raw | docker compose exec -T db sh -c 'psql -XqAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$1"' -- $DatabaseName
    if ($LASTEXITCODE -ne 0) { throw 'Restored sequence safety check failed' }
    Write-Output "Restored into separate database $DatabaseName; the live database was not changed."
} finally { Pop-Location }
