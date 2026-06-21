$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot

& (Join-Path $Root "scripts\publish-archie.ps1")

Write-Host "Building EhrBase ADL2 CDR fork..."
Push-Location (Join-Path $Root "ehrbase")
try {
    $mvn = Join-Path $Root "tools\apache-maven-3.9.6\bin\mvn.cmd"
    if (-not (Test-Path $mvn)) { $mvn = "mvn" }
    $jdk = Get-ChildItem (Join-Path $Root "tools") -Directory -Filter "jdk-*" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
    & $mvn clean install -DskipTests -DskipITs
} finally {
    Pop-Location
}
