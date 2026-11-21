<##
Start or validate the isolated local performance API/worker.

Default: validation only. -Apply is required to start a process.
Default apply starts API only. The worker is separately gated because it calls
Judge0 and local sandbox isolation has not been verified.

Required PERF_* environment variables are intentionally separate from .env and
the demo/staging variables. Secrets are validated but never printed.
##>
param(
  [switch]$Apply,
  [switch]$StartWorker,
  [switch]$AllowJudge0Worker,
  [string]$JarPath = "backend\target\coderzclub-backend-1.0.0.jar"
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
Set-Location $Root
$PidFile = Join-Path ([IO.Path]::GetTempPath()) "coderzclub-perf-local-pids.json"

if ($StartWorker -and -not $Apply) { throw "-StartWorker requires -Apply." }
if ($AllowJudge0Worker -and -not $StartWorker) { throw "-AllowJudge0Worker requires -StartWorker." }

function Require-Env([string]$Name) {
  $value = [Environment]::GetEnvironmentVariable($Name)
  if ([string]::IsNullOrWhiteSpace($value)) { throw "Missing required environment variable: $Name" }
  return $value.Trim()
}

function Assert-LocalPort([string]$Name, [string]$Host, [string]$Port) {
  if ($Host -notin @("127.0.0.1", "localhost")) { throw "$Name host must be loopback; got $Host" }
  if ($Port -notmatch '^\d+$') { throw "$Name port must be numeric." }
}

function Assert-NotProduction([string]$Name, [string]$Value) {
  if ($Value -match '(?i)(atlas|gcp|production|prod[-_.]|staging|coderzclub\.site|10\.20\.)') {
    throw "$Name appears to reference production, staging, or a cloud service."
  }
}

$mongoUri = Require-Env "PERF_MONGO_URI"
$mongoDb = Require-Env "PERF_MONGO_DB"
$redisHost = Require-Env "PERF_REDIS_HOST"
$redisPort = Require-Env "PERF_REDIS_PORT"
$redisDb = Require-Env "PERF_REDIS_DATABASE"
$rabbitHost = Require-Env "PERF_RABBIT_HOST"
$rabbitPort = Require-Env "PERF_RABBIT_PORT"
$rabbitManagementPort = Require-Env "PERF_RABBIT_MANAGEMENT_PORT"
$rabbitVhost = Require-Env "PERF_RABBIT_VHOST"
$rabbitUser = Require-Env "PERF_RABBIT_USERNAME"
$rabbitPassword = Require-Env "PERF_RABBIT_PASSWORD"
$judge0Url = Require-Env "PERF_JUDGE0_API_URL"
$jwtSecret = Require-Env "PERF_JWT_SECRET"

Assert-NotProduction "PERF_MONGO_URI" $mongoUri
Assert-NotProduction "PERF_JUDGE0_API_URL" $judge0Url
Assert-NotProduction "PERF_RABBIT_VHOST" $rabbitVhost
Assert-LocalPort "Mongo" ([uri]$mongoUri).Host ([uri]$mongoUri).Port
if ($mongoUri -notmatch '^mongodb://(127\.0\.0\.1|localhost):27027/?$') {
  throw "PERF_MONGO_URI must be exactly mongodb://127.0.0.1:27027 (without credentials or a database path)."
}
if ($mongoDb -ne "coderzclub_perf") { throw "PERF_MONGO_DB must be coderzclub_perf." }
Assert-LocalPort "Redis" $redisHost $redisPort
if ($redisPort -ne "6389") { throw "PERF_REDIS_PORT must be 6389." }
if ($redisDb -notmatch '^\d+$') { throw "PERF_REDIS_DATABASE must be numeric." }
Assert-LocalPort "RabbitMQ" $rabbitHost $rabbitPort
if ($rabbitPort -ne "5682") { throw "PERF_RABBIT_PORT must be 5682." }
if ($rabbitManagementPort -ne "15682") { throw "PERF_RABBIT_MANAGEMENT_PORT must be 15682." }
if ($rabbitVhost -ne "/coderzclub-perf") { throw "PERF_RABBIT_VHOST must be /coderzclub-perf, not the shared root vhost." }
if ($rabbitUser -match '(?i)(prod|staging|atlas|gcp)' -or $rabbitPassword -match '(?i)(prod|staging|atlas|gcp)') {
  throw "RabbitMQ credentials look environment-specific; use dedicated local perf credentials."
}
if ($judge0Url -notmatch '^http://(127\.0\.0\.1|localhost):2358(/submissions)?/?$') {
  throw "PERF_JUDGE0_API_URL must target local Judge0 at port 2358."
}
if ($jwtSecret.Length -lt 32 -or $jwtSecret -match '(?i)^(local|change|your|<)') {
  throw "PERF_JWT_SECRET must be a non-placeholder local secret of at least 32 characters."
}

try {
  $encodedVhost = [uri]::EscapeDataString($rabbitVhost)
  $basicBytes = [Text.Encoding]::ASCII.GetBytes("$rabbitUser`:$rabbitPassword")
  $basic = [Convert]::ToBase64String($basicBytes)
  $rabbitResponse = Invoke-RestMethod -UseBasicParsing -Headers @{ Authorization = "Basic $basic" } -Uri "http://$rabbitHost`:$rabbitManagementPort/api/vhosts/$encodedVhost" -TimeoutSec 5
  if ($rabbitResponse.name -ne $rabbitVhost) { throw "RabbitMQ vhost response did not match the isolated perf vhost." }
} catch {
  throw "Cannot read isolated RabbitMQ vhost /coderzclub-perf on local management port 15682: $($_.Exception.Message)"
}

$judge0SandboxVerified = [Environment]::GetEnvironmentVariable("PERF_JUDGE0_SANDBOX_VERIFIED")
if ($StartWorker -and (-not $AllowJudge0Worker -or $judge0SandboxVerified -ne "1")) {
  throw "Worker start requires -AllowJudge0Worker and PERF_JUDGE0_SANDBOX_VERIFIED=1 after sandbox review."
}

$jar = Join-Path $Root $JarPath
if ($Apply -and -not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw "Backend jar not found: $jar" }

$queue = @{
  SUBMISSION_QUEUE_NAME = "coderzclub.perf.submissions"
  SUBMISSION_QUEUE_EXCHANGE = "coderzclub.perf.submissions.exchange"
  SUBMISSION_QUEUE_RETRY_NAME = "coderzclub.perf.submissions.retry"
  SUBMISSION_QUEUE_DLQ_NAME = "coderzclub.perf.submissions.dlq"
  CONTEST_RUN_QUEUE_NAME = "coderzclub.perf.contest-runs"
  CONTEST_RUN_QUEUE_EXCHANGE = "coderzclub.perf.contest-runs.exchange"
}
$judge0BaseUrl = $judge0Url.TrimEnd('/')
if ($judge0BaseUrl -notmatch '/submissions$') { $judge0BaseUrl += '/submissions' }

Write-Host "Validation passed (secrets omitted)."
Write-Host "Mongo: 127.0.0.1:27027 / $mongoDb"
Write-Host "Redis: $redisHost`:$redisPort / database $redisDb"
Write-Host "RabbitMQ: $rabbitHost`:$rabbitPort / vhost $rabbitVhost"
Write-Host "Judge0: local loopback / port 2358"
Write-Host "API: 127.0.0.1:8082; worker: 127.0.0.1:8092 (only when explicitly enabled)"
if (-not $Apply) { Write-Host "Dry run only. Pass -Apply to start API; add -StartWorker only after Judge0 sandbox approval."; exit 0 }

$TargetFile = Join-Path ([IO.Path]::GetTempPath()) "coderzclub-perf-local-targets.json"
@{
  mongoHost = "127.0.0.1"; mongoPort = 27027; mongoDatabase = $mongoDb
  redisHost = $redisHost; redisPort = [int]$redisPort; redisDatabase = [int]$redisDb
  rabbitHost = $rabbitHost; rabbitPort = [int]$rabbitPort; rabbitManagementPort = [int]$rabbitManagementPort; rabbitVhost = $rabbitVhost
  judge0Host = ([uri]$judge0Url).Host; judge0Port = ([uri]$judge0Url).Port
  apiPort = 8082; apiManagementPort = 8084; workerPort = 8092; workerManagementPort = 8093
} | ConvertTo-Json | Set-Content -LiteralPath $TargetFile

function Set-PerfEnvironment([string]$Profile, [string]$ServerPort, [string]$ManagementPort) {
  $env:SPRING_PROFILES_ACTIVE = $Profile
  $env:SERVER_ADDRESS = "127.0.0.1"
  $env:SERVER_PORT = $ServerPort
  $env:MANAGEMENT_SERVER_ADDRESS = "127.0.0.1"
  $env:MANAGEMENT_SERVER_PORT = $ManagementPort
  $env:MONGODB_URI = $mongoUri
  $env:SPRING_DATA_MONGODB_URI = "$($mongoUri.TrimEnd('/'))/$mongoDb"
  $env:SPRING_DATA_MONGODB_DATABASE = $mongoDb
  $env:JWT_SECRET = $jwtSecret
  $env:SPRING_DATA_REDIS_HOST = $redisHost
  $env:SPRING_DATA_REDIS_PORT = $redisPort
  $env:SPRING_DATA_REDIS_DATABASE = $redisDb
  $env:SPRING_REDIS_HOST = $redisHost
  $env:SPRING_REDIS_PORT = $redisPort
  $env:SPRING_REDIS_DATABASE = $redisDb
  $env:RABBITMQ_HOST = $rabbitHost
  $env:RABBITMQ_PORT = $rabbitPort
  $env:RABBITMQ_USERNAME = $rabbitUser
  $env:RABBITMQ_PASSWORD = $rabbitPassword
  $env:RABBITMQ_VHOST = $rabbitVhost
  $env:JUDGE0_API_URL = $judge0BaseUrl
  $env:JUDGE0_AUTH_MODE = "SELF_HOSTED"
  $env:JUDGE0_WAIT = "true"
  $env:SUBMISSION_LIMIT_REDIS_FAIL_OPEN = "false"
  $env:SPRING_MAIL_ENABLED = "false"
  $env:MANAGEMENT_HEALTH_MAIL_ENABLED = "false"
  foreach ($entry in $queue.GetEnumerator()) { Set-Item -Path "Env:$($entry.Key)" -Value $entry.Value }
}

$processes = @()
Set-PerfEnvironment "api" "8082" "8084"
$processes += Start-Process -FilePath "java" -ArgumentList @("-jar", $jar) -WorkingDirectory $Root -PassThru
if ($StartWorker) {
  Set-PerfEnvironment "worker" "8092" "8093"
  $processes += Start-Process -FilePath "java" -ArgumentList @("-jar", $jar) -WorkingDirectory $Root -PassThru
}
$processes | Select-Object Id, ProcessName | ConvertTo-Json | Set-Content -LiteralPath $PidFile
Write-Host "Started perf-local process IDs in $PidFile (no secrets recorded)."
