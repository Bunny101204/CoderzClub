param(
  [string]$EnvFile = ".env.staging",
  [switch]$ConfirmStagingRollback
)
$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $Root
if (-not $ConfirmStagingRollback) { throw "Refuse: pass -ConfirmStagingRollback explicitly." }
if ([IO.Path]::GetFileName((Resolve-Path $EnvFile).Path) -ne ".env.staging") { throw "Refuse: only .env.staging is accepted." }
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw "Docker CLI is required." }
Write-Host "Stopping API and worker in ONLY coderzclub-staging; volumes are preserved."
& docker compose --env-file (Resolve-Path $EnvFile) -p coderzclub-staging -f docker-compose.staging.yml stop api worker
if ($LASTEXITCODE -ne 0) { throw "Staging rollback stop failed." }
Write-Host "Staging containers stopped. Restore the approved previous staging image/configuration, then start with start-staging.ps1 -Apply."
