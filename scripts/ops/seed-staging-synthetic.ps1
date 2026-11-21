<## Synthetic-only staging seed/verification. Default is dry-run. ##>
param(
  [ValidateSet("Plan", "Seed", "Verify")][string]$Action = "Plan",
  [string]$Project = "coderzclub-load-test", [string]$Zone = "asia-south1-a",
  [string]$InfraVm = "coderzclub-staging-infra-1", [switch]$Apply, [switch]$Approve,
  [switch]$ConfirmSyntheticOnly
)
$ErrorActionPreference="Stop"; $Root=(Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path; Set-Location $Root
$gcloudModule = Join-Path $PSScriptRoot "gcloud-wrapper.psm1"
Import-Module $gcloudModule -Force
if ($Action -eq "Plan") { Write-Host "PLAN ONLY: would seed 20,000 synthetic users into coderzclub_staging via the isolated infra VM; no production URI is accepted."; exit 0 }
if (-not $Apply -or -not $Approve -or -not $ConfirmSyntheticOnly) { throw "$Action requires -Apply -Approve -ConfirmSyntheticOnly." }
if ($InfraVm -ne "coderzclub-staging-infra-1") { throw "Refusing non-dedicated infra VM." }
Invoke-Gcloud @("compute", "scp", (Join-Path $Root "scripts\perf\seed-institutional.mjs"), "$InfraVm`:/tmp/seed-institutional.mjs", "--project=$Project", "--zone=$Zone", "--quiet") | Out-Null
$remote = if ($Action -eq "Seed") { "/tmp/seed-staging.sh" } else { "/tmp/verify-staging-synthetic.sh" }
$local = if ($Action -eq "Seed") { Join-Path $Root "scripts\ops\remote\seed-staging.sh" } else { Join-Path $Root "scripts\ops\remote\verify-staging-synthetic.sh" }
Invoke-Gcloud @("compute", "scp", $local, "$InfraVm`:$remote", "--project=$Project", "--zone=$Zone", "--quiet") | Out-Null
Invoke-Gcloud @("compute", "ssh", $InfraVm, "--project=$Project", "--zone=$Zone", "--command=chmod 700 $remote; sudo $remote", "--quiet") | Out-Null
