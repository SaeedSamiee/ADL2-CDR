# Start ADL2 CDR with Docker Compose (bundled or external PostgreSQL).
param(
    [switch]$ExternalDb,
    [string]$EhrbaseDir = (Join-Path $PSScriptRoot "..\ehrbase")
)

$ErrorActionPreference = "Stop"
Set-Location $EhrbaseDir

$envFile = Join-Path $EhrbaseDir ".env.ehrbase"
$example = Join-Path $EhrbaseDir ".env.ehrbase.example"
if (-not (Test-Path $envFile)) {
    Copy-Item $example $envFile
    Write-Host "Created .env.ehrbase from template — edit DB_URL and secrets before production use."
}

if ($ExternalDb) {
    Write-Host "Starting EhrBase with external PostgreSQL (see DB_URL in .env.ehrbase)..."
    docker compose -f docker-compose.external-db.yml up -d
} else {
    Write-Host "Starting EhrBase + bundled PostgreSQL + Keycloak..."
    docker compose up -d
}

Write-Host "Status: curl -u ehrbase-user:SuperSecretPassword http://localhost:8080/ehrbase/rest/status"
