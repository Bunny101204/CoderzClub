<#
.SYNOPSIS
  Verify or rebuild local coderzclub-backend:6cf84e7 and optionally docker-save a tar.
.NOTES
  Local only. Never transfers to GCP. Never restarts services.
#>
param(
  [string]$ExpectedImageId = "sha256:b30086c75baa8cc68f14d5c183bf09d521ed0eb2363aedfed5ad2f5f29c6107f",
  [string]$Tag = "coderzclub-backend:6cf84e7",
  [string]$JarPath = "",
  [string]$OutDir = "E:\Hustle\CoderzClub\.local\phase8\artifacts",
  [switch]$Rebuild,
  [switch]$Save
)
$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path

function Assert-Docker {
  docker version --format "{{.Server.Version}}" | Out-Null
  if ($LASTEXITCODE -ne 0) { throw "Docker daemon is not available." }
}

Assert-Docker
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$existing = docker image inspect --format "{{.Id}}" $Tag 2>$null
if ($LASTEXITCODE -ne 0) { $existing = $null }

if ($existing -and -not $Rebuild) {
  Write-Host "FOUND_IMAGE=$Tag"
  Write-Host "IMAGE_ID=$existing"
  if ($existing -ne $ExpectedImageId) {
    Write-Host "WARNING: image id differs from previously recorded expected id."
    Write-Host "RECORDED_EXPECTED=$ExpectedImageId"
    Write-Host "ACTUAL=$existing"
    Write-Host "Rebuild with -Rebuild to recreate from reviewed JAR, or update expected id after review."
  } else {
    Write-Host "IMAGE_ID_MATCH=True"
  }
} else {
  if (-not $JarPath) {
    $candidate = Join-Path $Root ".local\worktrees\main-deploy-prep\backend\target\coderzclub-backend-1.0.0.jar"
    if (Test-Path $candidate) { $JarPath = $candidate }
  }
  if (-not $JarPath -or -not (Test-Path $JarPath)) {
    throw "JAR not found. Pass -JarPath to a reviewed 6cf84e7 jar."
  }
  $jarHash = (Get-FileHash -LiteralPath $JarPath -Algorithm SHA256).Hash.ToLowerInvariant()
  Write-Host "JAR=$JarPath"
  Write-Host "JAR_SHA256=$jarHash"
  $buildDir = Join-Path $OutDir "image-build-6cf84e7"
  New-Item -ItemType Directory -Force -Path $buildDir | Out-Null
  Copy-Item -LiteralPath $JarPath -Destination (Join-Path $buildDir "coderzclub-backend-1.0.0.jar") -Force
  Copy-Item -LiteralPath (Join-Path $Root ".codex\local-image-build\Dockerfile.runtime") -Destination (Join-Path $buildDir "Dockerfile") -Force
  docker build --platform linux/amd64 -t $Tag $buildDir
  if ($LASTEXITCODE -ne 0) { throw "docker build failed" }
  $existing = docker image inspect --format "{{.Id}}" $Tag
  Write-Host "BUILT_IMAGE_ID=$existing"
  if ($ExpectedImageId -and $existing -ne $ExpectedImageId) {
    Write-Host "NOTE: rebuilt image id differs from prior recorded id (base layer churn is possible)."
    Write-Host "Use BUILT_IMAGE_ID as EXPECTED_NEW_IMAGE_ID after review."
  }
}

$arch = docker image inspect --format "{{.Architecture}}" $Tag
$user = docker image inspect --format "{{index .Config.User}}" $Tag
$ep = docker image inspect --format "{{json .Config.Entrypoint}}" $Tag
Write-Host "ARCHITECTURE=$arch"
Write-Host "USER=$user"
Write-Host "ENTRYPOINT=$ep"
if ($arch -ne "amd64") { throw "Image architecture must be amd64 for GCP VMs." }
if ($user -ne "10001") { throw "Image user must be 10001." }

if ($Save) {
  $tar = Join-Path $OutDir "coderzclub-backend-6cf84e7.tar"
  if (Test-Path $tar) { throw "Refuse overwrite existing tar: $tar" }
  docker save -o $tar $Tag
  if ($LASTEXITCODE -ne 0) { throw "docker save failed" }
  $tarHash = (Get-FileHash -LiteralPath $tar -Algorithm SHA256).Hash.ToLowerInvariant()
  $meta = Join-Path $OutDir "coderzclub-backend-6cf84e7.tar.sha256"
  Set-Content -Path $meta -Value "$tarHash  coderzclub-backend-6cf84e7.tar" -Encoding ascii
  Write-Host "TAR=$tar"
  Write-Host "TAR_SHA256=$tarHash"
  Write-Host "IMAGE_ID=$(docker image inspect --format '{{.Id}}' $Tag)"
}

Write-Host "DONE"
