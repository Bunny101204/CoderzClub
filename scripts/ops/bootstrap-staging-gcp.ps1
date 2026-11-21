<## Idempotent remote bootstrap/service control for the owner-approved existing
five-VM test window. Default is dry-run. It never creates VMs or removes data.
##>
param(
  [ValidateSet("Plan", "Bootstrap", "Health", "InspectQueues", "Start", "Stop")]
  [string]$Action = "Plan",
  [string]$Project = "coderzclub-load-test",
  [string]$Zone = "asia-south1-a",
  [string]$JarPath = "backend\target\coderzclub-backend-1.0.0.jar",
  [switch]$Apply,
  [switch]$Approve,
  [switch]$ConfirmExclusiveTestWindow,
  [switch]$ConfirmNoLiveUsers
)
$ErrorActionPreference = "Stop"
$gcloudModule = Join-Path $PSScriptRoot "gcloud-wrapper.psm1"
Import-Module $gcloudModule -Force
$app = "coderzclub-api-1"; $worker = "coderzclub-worker-1"; $infra = "coderzclub-infra-1"; $judge = "coderzclub-judge0-1"; $loadgen = "coderzclub-loadgen-1"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
Set-Location $Root
if ($Apply -and (-not $Approve -or -not $ConfirmExclusiveTestWindow -or -not $ConfirmNoLiveUsers)) { throw "Existing-VM bootstrap requires -Apply -Approve -ConfirmExclusiveTestWindow -ConfirmNoLiveUsers." }
function Ip([string]$Name) { return (Invoke-Gcloud @("compute","instances","describe",$Name,"--project=$Project","--zone=$Zone","--format=value(networkInterfaces[0].networkIP)")).Trim() }
function CopyRemote([string]$Local, [string]$Vm, [string]$Remote) { Invoke-Gcloud @("compute","scp",$Local,"$Vm`:$Remote","--project=$Project","--zone=$Zone","--quiet") | Out-Null }
function RunRemote([string]$Vm, [string]$Command) { Invoke-Gcloud @("compute","ssh",$Vm,"--project=$Project","--zone=$Zone","--command=$Command","--quiet") | Out-Null }
if ($Action -eq "Plan") {
  Write-Host "PLAN ONLY: bootstrap existing infra=$infra, judge0=$judge, api=$app, worker=$worker, loadgen=$loadgen"
  Write-Host "Bootstrap uses Secret Manager on each VM, Docker Compose for infra/Judge0, and systemd for API/worker."
  Write-Host "No command is sent without -Apply -Approve -ConfirmExclusiveTestWindow -ConfirmNoLiveUsers."
  exit 0
}
if (-not $Apply -or -not $Approve -or -not $ConfirmExclusiveTestWindow -or -not $ConfirmNoLiveUsers) { throw "This action requires -Apply -Approve -ConfirmExclusiveTestWindow -ConfirmNoLiveUsers." }
$infraIp = Ip $infra; $judgeIp = Ip $judge
if ($Action -eq "Bootstrap") {
  CopyRemote (Join-Path $Root "docker-compose.staging-infra.yml") $infra "/tmp/docker-compose.staging-infra.yml"
  CopyRemote (Join-Path $Root "scripts\ops\remote\bootstrap-infra.sh") $infra "/tmp/bootstrap-infra.sh"
  RunRemote $infra "chmod 700 /tmp/bootstrap-infra.sh; sudo /tmp/bootstrap-infra.sh"
  CopyRemote (Join-Path $Root "docker-compose.judge0-staging.yml") $judge "/tmp/docker-compose.judge0-staging.yml"
  CopyRemote (Join-Path $Root "scripts\ops\remote\bootstrap-judge0.sh") $judge "/tmp/bootstrap-judge0.sh"
  RunRemote $judge "chmod 700 /tmp/bootstrap-judge0.sh; sudo /tmp/bootstrap-judge0.sh"
  if (-not (Test-Path -LiteralPath $JarPath)) { throw "Reviewed backend jar missing: $JarPath" }
  CopyRemote (Resolve-Path $JarPath).Path $app "/tmp/coderzclub-backend.jar"
  CopyRemote (Join-Path $Root "scripts\ops\remote\bootstrap-app.sh") $app "/tmp/bootstrap-app.sh"
  RunRemote $app "chmod 700 /tmp/bootstrap-app.sh; sudo env SERVICE_ROLE=api INFRA_IP=$infraIp JUDGE0_IP=$judgeIp /tmp/bootstrap-app.sh"
  CopyRemote (Resolve-Path $JarPath).Path $worker "/tmp/coderzclub-backend.jar"
  CopyRemote (Join-Path $Root "scripts\ops\remote\bootstrap-app.sh") $worker "/tmp/bootstrap-app.sh"
  RunRemote $worker "chmod 700 /tmp/bootstrap-app.sh; sudo env SERVICE_ROLE=worker INFRA_IP=$infraIp JUDGE0_IP=$judgeIp /tmp/bootstrap-app.sh"
  exit 0
}
if ($Action -eq "Health") {
  RunRemote $infra "docker compose -f /opt/coderzclub-staging/infra/docker-compose.yml ps"
  RunRemote $judge "docker compose -f /opt/coderzclub-staging/judge0/docker-compose.yml ps; curl --fail --silent --show-error http://127.0.0.1:2358/about >/dev/null"
  RunRemote $app "curl --fail --silent --show-error http://127.0.0.1:8081/actuator/health/readiness"
  RunRemote $worker "curl --fail --silent --show-error http://127.0.0.1:8083/actuator/health/readiness"
  exit 0
}
if ($Action -eq "InspectQueues") {
  CopyRemote (Join-Path $Root "scripts\ops\remote\inspect-queues.sh") $infra "/tmp/inspect-queues.sh"
  RunRemote $infra "chmod 700 /tmp/inspect-queues.sh; sudo /tmp/inspect-queues.sh"
  exit 0
}
if ($Action -eq "Start") {
  RunRemote $infra "sudo docker compose -f /opt/coderzclub-staging/infra/docker-compose.yml up -d --wait"
  RunRemote $judge "sudo docker compose -f /opt/coderzclub-staging/judge0/docker-compose.yml up -d --wait"
  RunRemote $app "sudo systemctl start coderzclub-staging-api.service"
  RunRemote $worker "sudo systemctl start coderzclub-staging-worker.service"
} elseif ($Action -eq "Stop") {
  RunRemote $app "sudo systemctl stop coderzclub-staging-api.service"
  RunRemote $worker "sudo systemctl stop coderzclub-staging-worker.service"
  RunRemote $judge "sudo docker compose -f /opt/coderzclub-staging/judge0/docker-compose.yml stop"
  RunRemote $infra "sudo docker compose -f /opt/coderzclub-staging/infra/docker-compose.yml stop"
}
