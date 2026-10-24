param(
    [Parameter(Mandatory = $true)][string]$BackupFile,
    [string]$DatabaseName = 'mastercoffee_restore_check'
)
$ErrorActionPreference = 'Stop'
if ($DatabaseName -notmatch '^mastercoffee_restore_[a-z0-9_]+$') {
    throw 'Use a fresh database name beginning mastercoffee_restore_ (lowercase, digits, underscore).'
}
$source = (Resolve-Path -LiteralPath $BackupFile).Path
Push-Location (Join-Path $PSScriptRoot '..')
try {
    docker compose cp $source db:/tmp/mastercoffee-restore.dump
    if ($LASTEXITCODE -ne 0) { throw 'Copy failed' }
    # createdb refuses an existing destination. No database is dropped or overwritten.
    docker compose exec -T db sh -c 'createdb -U "$POSTGRES_USER" "$1"' -- $DatabaseName
    if ($LASTEXITCODE -ne 0) { throw 'Destination exists or could not be created; choose a new name.' }
    docker compose exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" -d "$1" --no-owner --exit-on-error /tmp/mastercoffee-restore.dump' -- $DatabaseName
    if ($LASTEXITCODE -ne 0) { throw 'pg_restore failed; inspect the separate destination database.' }
    docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$1" -c "SELECT count(*) AS orders FROM orders; SELECT count(*) AS users FROM users; SELECT count(*) AS migrations FROM schema_migrations;"' -- $DatabaseName
    if ($LASTEXITCODE -ne 0) { throw 'Restored schema check failed' }
    Write-Output "Restored into separate database $DatabaseName; the live database was not changed."
} finally { Pop-Location }
