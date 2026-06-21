# ADL2 CDR manual composition test against a running EhrBase + PostgreSQL stack.
# Prerequisites: docker compose up (ehrbase-db and adl2-cdr), EhrBase reachable at $BaseUrl

param(
    [string]$BaseUrl = "http://localhost:8080/ehrbase/rest/openehr/v1"
)

$ErrorActionPreference = "Stop"
$fixtures = Join-Path $PSScriptRoot "..\ehrbase\service\src\test\resources\adl2-fixtures"

function Invoke-EhrBase {
    param(
        [string]$Method,
        [string]$Path,
        [string]$ContentType = "application/json",
        [string]$Body = $null,
        [hashtable]$Headers = @{}
    )
    $uri = "$BaseUrl$Path"
    $params = @{
        Uri = $uri
        Method = $Method
        ContentType = $ContentType
        Headers = $Headers
    }
    if ($Body) { $params.Body = $Body }
    return Invoke-RestMethod @params
}

Write-Host "Creating EHR..."
$ehr = Invoke-EhrBase -Method POST -Path "/ehr"
$ehrId = $ehr.ehr_id.value
Write-Host "EHR: $ehrId"

Write-Host "Uploading prescription ADL2 template (sample-opt)..."
$prescriptionAdl = Get-Content (Join-Path $fixtures "sample-opt.adls") -Raw
Invoke-EhrBase -Method POST -Path "/definition/template/adl2" -ContentType "text/plain" -Body $prescriptionAdl -Headers @{ Prefer = "return=minimal" }

Write-Host "Uploading with_rules archetype as flattened OPT..."
$withRulesAdl = Get-Content (Join-Path $fixtures "openEHR-EHR-OBSERVATION.with_rules.v1.adls") -Raw
# EhrBase accepts operational_template ADL; flattening is done in integration tests via Archie Flattener.
# For manual REST testing, upload the specialized observation OPT which is already operational_template format.
$specializedOpt = Get-Content (Join-Path $fixtures "openEHR-EHR-OBSERVATION.specialized_template_observation.v1.0.0.opt2.adls") -Raw
Invoke-EhrBase -Method POST -Path "/definition/template/adl2" -ContentType "text/plain" -Body $specializedOpt -Headers @{ Prefer = "return=minimal" }

Write-Host "NOTE: with_rules template must be flattened to operational_template before upload."
Write-Host "      Run Adl2CompositionDataIntegrationIT for full constraint/rules/AQL coverage."

Write-Host "`nExample AQL queries (replace ehr id):"
@(
    "SELECT c/context/other_context/items[id3]/items[id5]/value FROM EHR e CONTAINS COMPOSITION c WHERE e/ehr_id/value = '$ehrId'",
    "SELECT o/data[id2]/events[id3]/data[id4]/items[id5]/value/magnitude FROM EHR e CONTAINS COMPOSITION c CONTAINS OBSERVATION o WHERE e/ehr_id/value = '$ehrId'"
) | ForEach-Object { Write-Host "  $_" }

Write-Host "`nRun integration tests for automated insert + validation:"
Write-Host "  cd ehrbase"
Write-Host "  mvn -pl service -am test -Dtest=Adl2CompositionDataIntegrationIT"
