<#
Start or validate the isolated staging Compose project.
Default is validation-only. Pass -Apply only after staging infrastructure and
secrets have been separately approved. Never pass a production env file.
#>
param(
  [string]$EnvFile = ".env.staging",
  [switch]$Apply
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $Root
$ComposeFile = Join-Path $Root "docker-compose.staging.yml"
$EnvPath = (Resolve-Path (Join-Path $Root $EnvFile)).Path

if ([IO.Path]::GetFileName($EnvPath) -ne ".env.staging") { throw "Refuse: staging requires a file named .env.staging." }
if (-not (Test-Path $ComposeFile)) { throw "Missing staging Compose file." }

$values = @{}
Get-Content $EnvPath | ForEach-Object {
  if ($_ -match '^\s*#' -or $_ -match '^\s*$') { return }
  $parts = $_.Split('=', 2)
  if ($parts.Length -ne 2) { throw "Invalid environment line in .env.staging." }
  $values[$parts[0].Trim()] = $parts[1].Trim()
}

if ($values.STAGING_ENVIRONMENT -ne "staging" -or $values.COMPOSE_PROJECT_NAME -ne "coderzclub-staging") { throw "Refuse: staging selectors are missing or incorrect." }
if ($values.SPRING_DATA_MONGODB_DATABASE -ne "coderzclub_staging") { throw "Refuse: Mongo database must be coderzclub_staging." }
if ($values.RABBITMQ_VHOST -ne "/coderzclub-staging") { throw "Refuse: RabbitMQ vhost must be /coderzclub-staging." }

$joined = ($values.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join "`n"
if ($joined -match '(?i)10\.20\.0\.|api\.coderzclub\.site|^APP_FRONTEND_URL=https://(www\.)?coderzclub\.site$|^APP_BACKEND_URL=https://api\.coderzclub\.site$|prod(?:uction)?[-_.]|\.prod\.|coderzclub-api-before|MONGODB_ATLAS_URI|patch-judge0-no-cgroup|fix-judge0-init') { throw "Refuse: production or insecure Judge0 reference found in staging env." }
if ($values.JWT_SECRET -match '^<|^your-|^change-|^local') { throw "Refuse: replace the staging JWT placeholder with a unique secret." }
if ($values.MONGODB_URI -match '<|coderzclub(?:\?|$|/)(?!_staging)' ) { throw "Refuse: Mongo URI appears to be a placeholder or production database." }
if ($values.JUDGE0_API_URL -match '(?i)localhost|127\.0\.0\.1|patch-judge0|no-cgroup') { throw "Refuse: staging Judge0 must be a private secure-host endpoint." }
if ($values.APP_FRONTEND_URL -match '(?i)^https://(www\.)?coderzclub\.site/?$') { throw "Refuse: staging frontend cannot use the production origin." }

$composeArgs = @("compose", "--env-file", $EnvPath, "-p", "coderzclub-staging", "-f", $ComposeFile, "config")
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw "Docker CLI is required for Compose validation." }
& docker @composeArgs | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Staging Compose validation failed." }

if ($Apply) {
  Write-Host "Starting ONLY Compose project coderzclub-staging..."
  & docker compose --env-file $EnvPath -p coderzclub-staging -f $ComposeFile up -d --build
  if ($LASTEXITCODE -ne 0) { throw "Staging Compose startup failed." }
} else {
  Write-Host "Validation passed. No containers started. Pass -Apply to start staging explicitly."
}
