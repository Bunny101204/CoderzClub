param([string]$EnvFile = ".env.staging")
$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $Root
if ([IO.Path]::GetFileName((Resolve-Path $EnvFile).Path) -ne ".env.staging") { throw "Refuse: only .env.staging is accepted." }
$api = $env:STAGING_API_URL
if ([string]::IsNullOrWhiteSpace($api)) { $api = "http://127.0.0.1:8080" }
if ($api -match 'coderzclub\.site|10\.20\.0\.') { throw "Refuse: production API target." }
foreach ($url in @("$api/api/test", "$api/api/problems/test", "http://127.0.0.1:8081/actuator/health/readiness")) {
  $response = Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 15
  if ($response.StatusCode -ne 200) { throw "Smoke check failed: $url" }
  Write-Host "PASS $url"
}
Write-Host "Application smoke checks passed. Secure Judge0, tenant, email, and workflow checks remain required."
