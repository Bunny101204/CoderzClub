<#
.SYNOPSIS
  Transfer coderzclub-backend:6cf84e7 tar to API/worker VMs via gcloud IAP SCP, then docker load.
.NOTES
  - Uses --tunnel-through-iap (compatible with Windows when direct SSH fails).
  - Never restarts containers.
  - Requires -Apply -Approve for any remote mutation (SCP/load).
  - Default is dry-run.
#>
param(
  [string]$Project = "coderzclub-load-test",
  [string]$Zone = "asia-south1-a",
  [string[]]$TargetVms = @("coderzclub-api-1", "coderzclub-worker-1"),
  [string]$TarPath = "E:\Hustle\CoderzClub\.local\phase8\artifacts\coderzclub-backend-6cf84e7.tar",
  [string]$ExpectedImageId = "sha256:b30086c75baa8cc68f14d5c183bf09d521ed0eb2363aedfed5ad2f5f29c6107f",
  [string]$RemoteDir = "/tmp/coderzclub-rollout-6cf84e7",
  [switch]$Apply,
  [switch]$Approve,
  [switch]$AllowSshKeyRefresh
)
$ErrorActionPreference = "Stop"
$gcloudModule = Join-Path $PSScriptRoot "..\..\scripts\ops\gcloud-wrapper.psm1"
Import-Module $gcloudModule -Force

if ($Apply -and -not $Approve) {
  throw "Transfer/load requires both -Apply and -Approve."
}

Write-Host "EXPECTED_IMAGE_ID=$ExpectedImageId"
Write-Host "TARGETS=$($TargetVms -join ',')"
Write-Host "METHOD=gcloud compute scp --tunnel-through-iap (+ optional --ssh-key-expire-after)"
Write-Host "LOCAL_TAR=$TarPath"

if (-not $Apply) {
  Write-Host "DRY_RUN: would create $RemoteDir on each VM, scp tar via IAP, sha256sum verify, docker load, verify image id."
  Write-Host "DRY_RUN: would NOT restart/stop/rename any container."
  if (-not (Test-Path -LiteralPath $TarPath)) {
    Write-Host "DRY_RUN_NOTE: tar not built yet (run prepare-image.ps1 -Save before -Apply)."
  }
  exit 0
}

if (-not (Test-Path -LiteralPath $TarPath)) {
  throw "Tar not found: $TarPath (run prepare-image.ps1 -Save first)."
}

$tarHash = (Get-FileHash -LiteralPath $TarPath -Algorithm SHA256).Hash.ToLowerInvariant()
$tarName = Split-Path $TarPath -Leaf
$remoteTar = "$RemoteDir/$tarName"
Write-Host "LOCAL_TAR_SHA256=$tarHash"

$iapArgs = @(
  "--project=$Project",
  "--zone=$Zone",
  "--tunnel-through-iap",
  "--quiet"
)
if ($AllowSshKeyRefresh) {
  # Explicit owner approval required: refreshes short-lived instance SSH metadata.
  $iapArgs += "--ssh-key-expire-after=60m"
}

foreach ($vm in $TargetVms) {
  Write-Host "=== TRANSFER $vm ==="
  Invoke-Gcloud (@("compute", "ssh", $vm) + $iapArgs + @(
    "--command=mkdir -p '$RemoteDir' && chmod 700 '$RemoteDir'"
  )) | Out-Null

  Invoke-Gcloud (@("compute", "scp") + $iapArgs + @(
    $TarPath,
    "${vm}:${remoteTar}"
  )) | Out-Null

  $remoteHashOut = [string](Invoke-Gcloud (@("compute", "ssh", $vm) + $iapArgs + @(
    "--command=sha256sum '$remoteTar'"
  )))
  if ($remoteHashOut -notmatch [regex]::Escape($tarHash)) {
    throw "Remote tar SHA-256 mismatch on $vm : $remoteHashOut"
  }
  Write-Host "REMOTE_TAR_SHA256_OK=$vm"

  $loadCmd = @"
set -euo pipefail
docker load -i '$remoteTar'
got=`$(docker image inspect --format '{{.Id}}' coderzclub-backend:6cf84e7)
echo LOADED_IMAGE_ID=`$got
test "`$got" = '$ExpectedImageId'
"@
  $loadOut = [string](Invoke-Gcloud (@("compute", "ssh", $vm) + $iapArgs + @(
    "--command=$loadCmd"
  )))
  Write-Host $loadOut
  if ($loadOut -notmatch [regex]::Escape($ExpectedImageId)) {
    throw "Loaded image id mismatch on $vm"
  }
  Write-Host "LOAD_OK=$vm (no container restart)"
}

Write-Host "ALL_TRANSFERS_COMPLETE"
