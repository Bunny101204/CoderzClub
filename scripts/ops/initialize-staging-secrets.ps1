<## Create staging-only Secret Manager entries and first versions.
Default is dry-run. Values are generated in memory and are never printed.
Existing secrets are not rotated unless -Rotate is explicitly supplied. ##>
param(
  [string]$Project = "coderzclub-load-test",
  [string]$InfraIp,
  [string]$Judge0Ip,
  [switch]$Apply,
  [switch]$Approve,
  [switch]$Rotate
)
$ErrorActionPreference = "Stop"
$gcloudModule = Join-Path $PSScriptRoot "gcloud-wrapper.psm1"
Import-Module $gcloudModule -Force
$names = @(
  "coderzclub-staging-mongodb-uri", "coderzclub-staging-jwt-secret",
  "coderzclub-staging-redis-password", "coderzclub-staging-rabbitmq-username",
  "coderzclub-staging-rabbitmq-password", "coderzclub-staging-judge0-api-key",
  "coderzclub-staging-judge0-db-password", "coderzclub-staging-judge0-secret-key-base"
)
function Assert-PrivateIp([string]$Name, [string]$Value) {
  if ($Value -notmatch '^(10\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[0-1])\.)') { throw "$Name must be an RFC1918 private address." }
}
function New-RandomSecret([int]$Length = 48) {
  $bytes = New-Object byte[] $Length
  [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
  return ([Convert]::ToBase64String($bytes)).Replace('+','-').Replace('/','_').TrimEnd('=')
}
function SecretExists([string]$Name) {
  return Test-GcloudCommand @("secrets", "describe", $Name, "--project=$Project", "--format=value(name)")
}
function Put-Secret([string]$Name, [string]$Value) {
  if (-not (SecretExists $Name)) { Invoke-Gcloud @("secrets", "create", $Name, "--project=$Project", "--replication-policy=user-managed", "--locations=asia-south1") | Out-Null }
  if ($Rotate -or -not (Invoke-Gcloud @("secrets", "versions", "list", $Name, "--project=$Project", "--filter=state:ENABLED", "--format=value(name)")).Trim()) {
    $executable = Get-GcloudExecutable
    $Value | & $executable secrets versions add $Name "--project=$Project" "--data-file=-" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Failed to add secret version for $Name." }
  }
}

if (-not $Apply) {
  Write-Host "DRY RUN: would initialize only these staging secret names: $($names -join ', ')"
  Write-Host "No secret values are generated in dry-run. InfraIp and Judge0Ip must be private RFC1918 addresses."
  exit 0
}
if (-not $Approve) { throw "Secret initialization requires -Approve." }
if ([string]::IsNullOrWhiteSpace($InfraIp) -or [string]::IsNullOrWhiteSpace($Judge0Ip)) { throw "-InfraIp and -Judge0Ip are required." }
Assert-PrivateIp "InfraIp" $InfraIp; Assert-PrivateIp "Judge0Ip" $Judge0Ip
$mongoPassword = New-RandomSecret
$redisPassword = New-RandomSecret
$rabbitUser = "staging_$(New-RandomSecret 12)"
$rabbitPassword = New-RandomSecret
$jwt = New-RandomSecret 64
$judgeDb = New-RandomSecret
$judgeBase = New-RandomSecret 64
Put-Secret "coderzclub-staging-mongodb-uri" "mongodb://root:$mongoPassword@$InfraIp`:27017/coderzclub_staging?authSource=admin&replicaSet=rs0"
Put-Secret "coderzclub-staging-jwt-secret" $jwt
Put-Secret "coderzclub-staging-redis-password" $redisPassword
Put-Secret "coderzclub-staging-rabbitmq-username" $rabbitUser
Put-Secret "coderzclub-staging-rabbitmq-password" $rabbitPassword
Put-Secret "coderzclub-staging-judge0-api-key" "not-used-self-hosted"
Put-Secret "coderzclub-staging-judge0-db-password" $judgeDb
Put-Secret "coderzclub-staging-judge0-secret-key-base" $judgeBase
$appSa = "serviceAccount:coderzclub-staging-app@$Project.iam.gserviceaccount.com"
$infraSa = "serviceAccount:coderzclub-staging-infra@$Project.iam.gserviceaccount.com"
$judgeSa = "serviceAccount:coderzclub-staging-judge0@$Project.iam.gserviceaccount.com"
foreach ($secret in @("coderzclub-staging-mongodb-uri","coderzclub-staging-jwt-secret","coderzclub-staging-redis-password","coderzclub-staging-rabbitmq-username","coderzclub-staging-rabbitmq-password","coderzclub-staging-judge0-api-key")) { Invoke-Gcloud @("secrets","add-iam-policy-binding",$secret,"--project=$Project","--member=$appSa","--role=roles/secretmanager.secretAccessor","--quiet") | Out-Null }
foreach ($secret in @("coderzclub-staging-mongodb-uri","coderzclub-staging-redis-password","coderzclub-staging-rabbitmq-username","coderzclub-staging-rabbitmq-password")) { Invoke-Gcloud @("secrets","add-iam-policy-binding",$secret,"--project=$Project","--member=$infraSa","--role=roles/secretmanager.secretAccessor","--quiet") | Out-Null }
foreach ($secret in @("coderzclub-staging-judge0-db-password","coderzclub-staging-judge0-secret-key-base")) { Invoke-Gcloud @("secrets","add-iam-policy-binding",$secret,"--project=$Project","--member=$judgeSa","--role=roles/secretmanager.secretAccessor","--quiet") | Out-Null }
Write-Host "Staging secret initialization completed for $Project. Values were not printed."
