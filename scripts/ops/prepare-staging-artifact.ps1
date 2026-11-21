<## Prepare or directly transfer a reviewed backend artifact. Default is dry-run.
This script never pushes to GitHub or a container registry and never copies env
files or secrets. Remote startup remains a separate reviewed host bootstrap. ##>
param(
  [string]$JarPath = "backend\target\coderzclub-backend-1.0.0.jar",
  [string]$Project = "coderzclub-load-test",
  [string]$Zone = "asia-south1-a",
  [ValidateSet("coderzclub-api-1", "coderzclub-worker-1")]
  [string]$TargetVm = "coderzclub-api-1",
  [switch]$Build,
  [switch]$Apply,
  [switch]$Approve
)
$ErrorActionPreference = "Stop"
$gcloudModule = Join-Path $PSScriptRoot "gcloud-wrapper.psm1"
Import-Module $gcloudModule -Force
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
Set-Location $Root
if ($Apply -and (-not $Approve)) { throw "Artifact transfer requires -Approve explicitly." }
$jar = (Resolve-Path $JarPath -ErrorAction SilentlyContinue)
if ($Build) {
  if (-not $Apply) { Write-Host "DRY RUN: would run mvn test package in backend."; exit 0 }
  Push-Location (Join-Path $Root "backend")
  & mvn test package
  if ($LASTEXITCODE -ne 0) { throw "Maven package failed." }
  Pop-Location
  $jar = (Resolve-Path $JarPath)
}
if ($null -eq $jar) { throw "Jar not found. Use -Build -Apply or provide a reviewed local jar." }
$hash = (Get-FileHash -LiteralPath $jar.Path -Algorithm SHA256).Hash
$hash = $hash.ToLowerInvariant()
Write-Host "Reviewed artifact: $($jar.Path)"
Write-Host "SHA256: $hash"
if (-not $Apply) {
  Write-Host "DRY RUN: would transfer only this jar directly to $TargetVm at a versioned /tmp path; no env, secrets, source tree, or GitHub push."
  exit 0
}
$remote = "/tmp/coderzclub-backend-$hash.jar"
Invoke-Gcloud @("compute", "scp", $jar.Path, "$TargetVm`:$remote", "--project=$Project", "--zone=$Zone", "--quiet") | Out-Null
$remoteHashOutput = [string](Invoke-Gcloud @("compute", "ssh", $TargetVm, "--project=$Project", "--zone=$Zone", "--command=sha256sum '$remote'", "--quiet"))
if ($remoteHashOutput -notmatch [regex]::Escape($hash)) { throw "Remote SHA-256 mismatch for $remote on $TargetVm." }
Write-Host "Artifact transferred to $TargetVm`:$remote and remotely verified. No service was restarted or started; the versioned artifact was not replaced or deleted."
