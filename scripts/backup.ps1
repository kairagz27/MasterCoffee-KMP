param([string]$OutputDirectory = (Join-Path $PSScriptRoot '..\backups'))
$ErrorActionPreference = 'Stop'
Push-Location (Join-Path $PSScriptRoot '..')
try {
    New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
    $destination = (Resolve-Path -LiteralPath $OutputDirectory).Path
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $databaseFile = Join-Path $destination "mastercoffee-$stamp.dump"
    docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc -f /tmp/mastercoffee-backup.dump'
    if ($LASTEXITCODE -ne 0) { throw 'pg_dump failed' }
    docker compose cp db:/tmp/mastercoffee-backup.dump $databaseFile
    if ($LASTEXITCODE -ne 0) { throw 'Copying database backup failed' }
    docker compose exec -T api tar -czf /tmp/mastercoffee-uploads.tar.gz -C /data uploads
    if ($LASTEXITCODE -ne 0) { throw 'Uploads archive failed' }
    $uploadsFile = Join-Path $destination "mastercoffee-$stamp-uploads.tar.gz"
    docker compose cp api:/tmp/mastercoffee-uploads.tar.gz $uploadsFile
    if ($LASTEXITCODE -ne 0) { throw 'Copying uploads archive failed' }
    Write-Output "Database: $databaseFile"
    Write-Output "Uploads: $uploadsFile"
} finally { Pop-Location }
