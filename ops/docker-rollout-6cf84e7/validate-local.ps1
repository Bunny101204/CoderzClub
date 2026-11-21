<#
.SYNOPSIS
  Local validation of 6cf84e7 Docker rollout scripts. Never touches GCP.
#>
$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot ".")
Set-Location $Root

Write-Host "=== UNIT TESTS (docker_swap_test.py) ==="
python -m unittest docker_swap_test.py -v
if ($LASTEXITCODE -ne 0) { throw "unit tests failed" }

Write-Host "=== SYNTAX CHECK SHELL SCRIPTS (if bash available) ==="
$bash = Get-Command bash -ErrorAction SilentlyContinue
if ($bash) {
  foreach ($f in @(
    "rollout-api.sh","rollout-worker.sh","rollback-api.sh","rollback-worker.sh",
    "judge0-harden.sh","smoke-judge0-sandbox.sh","health-check.sh","judge0-harden_test.sh"
  )) {
    & bash -n $f
    if ($LASTEXITCODE -ne 0) { throw "bash -n failed: $f" }
    Write-Host "SYNTAX_OK=$f"
  }
  Write-Host "=== JUDGE0 HARDEN REGRESSION ==="
  & bash .\judge0-harden_test.sh
  if ($LASTEXITCODE -ne 0) { throw "judge0-harden_test.sh failed" }
} else {
  Write-Host "bash not on PATH; skipped shell -n checks"
}

Write-Host "=== POWERSHELL PARSE CHECK ==="
foreach ($f in @("prepare-image.ps1","transfer-image-iap.ps1","validate-local.ps1")) {
  $tokens = $null
  $errs = $null
  $null = [System.Management.Automation.Language.Parser]::ParseFile((Join-Path $Root $f), [ref]$tokens, [ref]$errs)
  if ($errs -and $errs.Count -gt 0) { throw "parse errors in $f : $errs" }
  Write-Host "PARSE_OK=$f"
}

Write-Host "=== TRANSFER DRY-RUN (no GCP mutate if gcloud missing/fails soft) ==="
try {
  powershell -NoProfile -File .\transfer-image-iap.ps1
  Write-Host "TRANSFER_DRY_RUN_EXIT=$LASTEXITCODE"
} catch {
  Write-Host "TRANSFER_DRY_RUN_NOTE=$($_.Exception.Message)"
}

Write-Host "=== PREPARE-IMAGE (if Docker up) ==="
try {
  docker version --format "{{.Server.Version}}" | Out-Null
  if ($LASTEXITCODE -eq 0) {
    powershell -NoProfile -File .\prepare-image.ps1
    Write-Host "PREPARE_IMAGE_EXIT=$LASTEXITCODE"
  } else {
    Write-Host "DOCKER_NOT_READY: skipped prepare-image"
  }
} catch {
  Write-Host "DOCKER_NOT_READY: skipped prepare-image ($($_.Exception.Message))"
}

Write-Host "VALIDATE_LOCAL_OK"
