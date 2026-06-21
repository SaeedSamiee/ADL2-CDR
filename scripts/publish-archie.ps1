$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Archie = Join-Path $Root "archie"

Write-Host "Publishing Archie 3.18.0 to Maven local repository..."
Push-Location $Archie
try {
    if (Test-Path ".\gradlew.bat") {
        .\gradlew.bat publishToMavenLocal -x test
    } else {
        gradle publishToMavenLocal -x test
    }
} finally {
    Pop-Location
}
Write-Host "Archie published to ~/.m2/repository/com/nedap/healthcare/archie/"
