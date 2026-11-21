<## Read-only staging telemetry and queue/app health collection. ##>
param([string]$Project="coderzclub-load-test",[string]$Zone="asia-south1-a",[switch]$ApproveReadOnly)
$ErrorActionPreference="Stop"
$gcloudModule = Join-Path $PSScriptRoot "gcloud-wrapper.psm1"
Import-Module $gcloudModule -Force
if (-not $ApproveReadOnly) { throw "Telemetry collection requires -ApproveReadOnly." }
$targets=@("coderzclub-api-1","coderzclub-worker-1","coderzclub-infra-1","coderzclub-judge0-1","coderzclub-loadgen-1")
foreach($vm in $targets){ Invoke-Gcloud @("compute", "ssh", $vm, "--project=$Project", "--zone=$Zone", "--command=date -Is; hostname; vmstat 1 2; free -h; df -h /; docker ps --format '{{.Names}} {{.Status}}' 2>/dev/null || true", "--quiet") }
Invoke-Gcloud @("compute", "ssh", "coderzclub-api-1", "--project=$Project", "--zone=$Zone", "--command=curl --fail --silent http://127.0.0.1:8081/actuator/metrics/http.server.requests; curl --fail --silent http://127.0.0.1:8081/actuator/health", "--quiet")
