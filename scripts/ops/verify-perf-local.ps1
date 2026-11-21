<## Read-only runtime/config verification for the isolated perf API and worker. ##>
param(
  [int]$ApiPort = 8082,
  [int]$ApiManagementPort = 8084,
  [int]$WorkerManagementPort = 8093
)
$ErrorActionPreference = "Stop"
function Require-Env([string]$Name) {
  $value = [Environment]::GetEnvironmentVariable($Name)
  if ([string]::IsNullOrWhiteSpace($value)) { throw "Missing $Name" }
  return $value.Trim()
}
$mongo = Require-Env PERF_MONGO_URI
$db = Require-Env PERF_MONGO_DB
$redisHost = Require-Env PERF_REDIS_HOST
$redisPort = Require-Env PERF_REDIS_PORT
$rabbitHost = Require-Env PERF_RABBIT_HOST
$rabbitPort = Require-Env PERF_RABBIT_PORT
$rabbitManagementPort = Require-Env PERF_RABBIT_MANAGEMENT_PORT
$vhost = Require-Env PERF_RABBIT_VHOST
$judge0 = Require-Env PERF_JUDGE0_API_URL
if ($mongo -notmatch '^mongodb://(127\.0\.0\.1|localhost):27027/?$' -or $db -ne "coderzclub_perf") { throw "Mongo target is not isolated perf Mongo." }
if ($redisHost -notin @("127.0.0.1", "localhost") -or $redisPort -ne "6389") { throw "Redis target is not isolated perf Redis." }
if ($rabbitHost -notin @("127.0.0.1", "localhost") -or $rabbitPort -ne "5682" -or $rabbitManagementPort -ne "15682" -or $vhost -ne "/coderzclub-perf") { throw "RabbitMQ target/vhost is not isolated perf RabbitMQ." }
if ($judge0 -notmatch '^http://(127\.0\.0\.1|localhost):2358') { throw "Judge0 target is not local." }
Write-Host "Configured targets: Mongo 127.0.0.1:27027/$db; Redis $redisHost`:$redisPort; RabbitMQ $rabbitHost`:$rabbitPort/$vhost; Judge0 loopback:2358"
$targetFile = Join-Path ([IO.Path]::GetTempPath()) "coderzclub-perf-local-targets.json"
if (Test-Path -LiteralPath $targetFile) {
  $effective = Get-Content -LiteralPath $targetFile -Raw | ConvertFrom-Json
  if ($effective.mongoPort -ne 27027 -or $effective.mongoDatabase -ne "coderzclub_perf" -or $effective.redisPort -ne 6389 -or $effective.rabbitPort -ne 5682 -or $effective.rabbitVhost -ne "/coderzclub-perf" -or $effective.judge0Port -ne 2358) { throw "Effective launcher target manifest is not isolated perf configuration." }
  Write-Host "PASS effective target manifest from launcher (secret-free)"
}

foreach ($url in @(
  "http://127.0.0.1:$ApiPort/api/test",
  "http://127.0.0.1:$ApiManagementPort/actuator/health/readiness"
)) {
  $response = Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 10
  if ($response.StatusCode -ne 200) { throw "Health check failed: $url" }
  Write-Host "PASS $url"
}
try {
  $response = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$WorkerManagementPort/actuator/health/readiness" -TimeoutSec 10
  if ($response.StatusCode -eq 200) { Write-Host "PASS worker readiness" }
} catch {
  Write-Host "Worker not running; API-only admission mode remains non-executing."
}
Write-Host "No credentials, queue contents, or secret-bearing URIs were printed."
