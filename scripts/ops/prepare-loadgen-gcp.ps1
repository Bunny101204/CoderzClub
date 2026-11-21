<## Prepare a dedicated loadgen. Default is dry-run; install requires approval. ##>
param([string]$Project="coderzclub-load-test",[string]$Zone="asia-south1-a",[string]$LoadgenVm="coderzclub-loadgen-1",[string]$K6Version="",[switch]$InstallK6,[switch]$Apply,[switch]$Approve)
$ErrorActionPreference="Stop"
$gcloudModule = Join-Path $PSScriptRoot "gcloud-wrapper.psm1"
Import-Module $gcloudModule -Force
if ($LoadgenVm -ne "coderzclub-loadgen-1") { throw "Refusing non-dedicated loadgen." }
if (-not $InstallK6) { Write-Host "DRY RUN: would verify k6, curl, vmstat, free, and nproc on $LoadgenVm."; exit 0 }
if (-not $Apply -or -not $Approve -or [string]::IsNullOrWhiteSpace($K6Version)) { throw "k6 installation requires -InstallK6 -Apply -Approve and a reviewed -K6Version." }
$cmd = "set -e; test -n '$K6Version'; curl -fsSL https://dl.k6.io/key.gpg | gpg --dearmor | sudo tee /usr/share/keyrings/k6-archive-keyring.gpg >/dev/null; echo 'deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main' | sudo tee /etc/apt/sources.list.d/k6.list >/dev/null; sudo apt-get update; sudo apt-get install -y k6=$K6Version; k6 version; free -h; nproc"
Invoke-Gcloud @("compute", "ssh", $LoadgenVm, "--project=$Project", "--zone=$Zone", "--command=$cmd", "--quiet") | Out-Null
