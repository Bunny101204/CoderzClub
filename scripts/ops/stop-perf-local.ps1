<## Stop only processes recorded by start-perf-local.ps1. Never clears queues or data. ##>
$ErrorActionPreference = "Stop"
$PidFile = Join-Path ([IO.Path]::GetTempPath()) "coderzclub-perf-local-pids.json"
$TargetFile = Join-Path ([IO.Path]::GetTempPath()) "coderzclub-perf-local-targets.json"
if (-not (Test-Path -LiteralPath $PidFile)) { Write-Host "No perf-local PID file found; nothing stopped."; exit 0 }
$records = @(Get-Content -LiteralPath $PidFile -Raw | ConvertFrom-Json)
foreach ($record in $records) {
  $process = Get-Process -Id ([int]$record.Id) -ErrorAction SilentlyContinue
  if ($null -ne $process -and $process.ProcessName -eq "java") {
    Stop-Process -Id $process.Id
    Write-Host "Stopped recorded Java process $($process.Id)."
  }
}
Remove-Item -LiteralPath $PidFile -Force
if (Test-Path -LiteralPath $TargetFile) { Remove-Item -LiteralPath $TargetFile -Force }
Write-Host "Processes stopped only; RabbitMQ messages, MongoDB data, and Redis keys were preserved."
