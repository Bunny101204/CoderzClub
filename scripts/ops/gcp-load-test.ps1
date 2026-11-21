<##
Guarded GCP load-test workflow. Default action is Plan and performs no cloud call.

Preflight is read-only. This workflow deliberately uses only the five owner-
approved existing VMs. It never provisions or repurposes new VMs. Mutating
actions require explicit approval of an exclusive test window, no live users,
and verified dependency/Judge0 isolation.
##>
param(
  [ValidateSet("Plan", "Preflight", "Provision", "Start", "Test", "Inspect", "Stop")]
  [string]$Action = "Plan",
  [string]$Project = "coderzclub-load-test",
  [string]$Zone = "asia-south1-a",
  [string]$LoadgenName = "coderzclub-loadgen-1",
  [ValidateSet("ExistingFive")]
  [string]$Topology = "ExistingFive",
  [switch]$Approve,
  [switch]$ApproveReadOnly,
  [switch]$Apply,
  [switch]$ConfirmExclusiveTestWindow,
  [switch]$ConfirmNoLiveUsers,
  [switch]$ConfirmIsolationVerified,
  [switch]$ConfirmSecureJudge0,
  [ValidateSet("admission", "verdict")]
  [string]$TestStage = "admission",
  [ValidateSet(50, 100, 200)]
  [int]$Vus = 50
)

$ErrorActionPreference = "Stop"
$gcloudModule = Join-Path $PSScriptRoot "gcloud-wrapper.psm1"
Import-Module $gcloudModule -Force
$existingVmPlan = @(
  [pscustomobject]@{ Role = "api"; Name = "coderzclub-api-1" },
  [pscustomobject]@{ Role = "worker"; Name = "coderzclub-worker-1" },
  [pscustomobject]@{ Role = "infra"; Name = "coderzclub-infra-1" },
  [pscustomobject]@{ Role = "judge0"; Name = "coderzclub-judge0-1" },
  [pscustomobject]@{ Role = "loadgen"; Name = "coderzclub-loadgen-1" }
)
$roles = @("api", "worker", "infra", "judge0")
$approvedVmNames = @($existingVmPlan.Name)

function Require-Approval([bool]$ReadOnly = $false) {
  if ($ReadOnly) {
    if (-not $ApproveReadOnly -and -not $Approve) { throw "Read-only cloud action requires -ApproveReadOnly (or -Approve)." }
  } elseif (-not $Approve) { throw "Cloud mutation requires -Approve explicitly." }
}

function Assert-SafeNames {
  if ($LoadgenName -ne "coderzclub-loadgen-1") { throw "Only the approved dedicated loadgen VM may be used." }
}

function Require-ExistingVmApproval {
  if (-not $Approve -or -not $ConfirmExclusiveTestWindow -or -not $ConfirmNoLiveUsers -or -not $ConfirmIsolationVerified -or -not $ConfirmSecureJudge0) {
    throw "Existing-VM actions require -Approve, -ConfirmExclusiveTestWindow, -ConfirmNoLiveUsers, -ConfirmIsolationVerified, and -ConfirmSecureJudge0."
  }
}

function Require-ProvisionApproval {
  throw "Provision is disabled: this workflow never creates new GCP VMs. Use the five existing owner-approved VMs."
}

function Get-ExistingInventory {
  $inventory = @()
  foreach ($plan in $existingVmPlan) {
    $raw = Invoke-Gcloud @("compute", "instances", "describe", $plan.Name, "--project=$Project", "--zone=$Zone", "--format=json(name,status,labels,serviceAccounts[].email,networkInterfaces[].networkIP,tags.items)")
    $vm = $raw | ConvertFrom-Json
    $vm | Add-Member -NotePropertyName expectedRole -NotePropertyValue $plan.Role
    $inventory += $vm
  }
  return $inventory
}

function Get-LoadgenInventory {
  $raw = Invoke-Gcloud @("compute", "instances", "describe", $LoadgenName, "--project=$Project", "--zone=$Zone", "--format=json(name,status,labels,serviceAccounts[].email,networkInterfaces[].networkIP,tags.items)")
  return ($raw | ConvertFrom-Json)
}

function Assert-ExistingInventory($inventory) {
  if (@($inventory).Count -ne 5) { throw "Expected exactly five approved existing VMs." }
  foreach ($vm in $inventory) {
    if ($vm.name -notin $approvedVmNames) { throw "Unexpected VM in test inventory: $($vm.name)" }
    if ([string]::IsNullOrWhiteSpace($vm.status)) { throw "VM $($vm.name) returned no status." }
  }
}

function Assert-Loadgen($loadgen) {
  if ($loadgen.name -ne $LoadgenName) { throw "Loadgen identity mismatch." }
}

Assert-SafeNames
if ($Action -eq "Plan") {
  Write-Host "PLAN ONLY: project=$Project zone=$Zone; no new VMs"
  $existingVmPlan | Format-Table Role,Name
  Write-Host "API and worker remain separate existing VMs; infra and Judge0 are treated as production assets and require an exclusive test window."
  Write-Host "Preflight is read-only. Provision is disabled. Start/Stop/Test require all explicit isolation and owner confirmations."
  Write-Host "First cloud action: run Preflight with -ApproveReadOnly after owner review."
  exit 0
}

if ($Action -eq "Preflight") {
  Require-Approval $true
  $inventory = Get-ExistingInventory
  Assert-ExistingInventory $inventory
  $loadgen = Get-LoadgenInventory
  Assert-Loadgen $loadgen
  Write-Host "READ-ONLY PREFLIGHT PASSED for dedicated staging inventory:"
  $inventory | Select-Object name,status,@{n="role";e={$_.labels."coderzclub-role"}},@{n="serviceAccount";e={($_.serviceAccounts.email -join ",")}},@{n="privateIPs";e={($_.networkInterfaces.networkIP -join ",")}} | Format-Table
  $loadgen | Select-Object name,status,@{n="role";e={$_.labels."coderzclub-role"}},@{n="serviceAccount";e={($_.serviceAccounts.email -join ",")}},@{n="privateIPs";e={($_.networkInterfaces.networkIP -join ",")}} | Format-Table
  Write-Host "Next gates remain: separate staging secrets, Mongo database, Redis DB, Rabbit vhost/queues, private Judge0, firewall, and loadgen image/k6 installation."
  exit 0
}

if ($Action -eq "Provision") {
  Require-ProvisionApproval
}

if ($Action -eq "Inspect") { Require-Approval $true } else { Require-ExistingVmApproval }
$inventory = Get-ExistingInventory
Assert-ExistingInventory $inventory
$loadgen = Get-LoadgenInventory
Assert-Loadgen $loadgen
$names = @($inventory.name)
if ($Action -eq "Start") {
  foreach ($name in $names) { Invoke-Gcloud @("compute", "instances", "start", $name, "--project=$Project", "--zone=$Zone", "--quiet") | Out-Null }
  Write-Host "Started only the five owner-approved existing VMs during the confirmed exclusive window."
} elseif ($Action -eq "Stop") {
  foreach ($name in $names) { Invoke-Gcloud @("compute", "instances", "stop", $name, "--project=$Project", "--zone=$Zone", "--quiet") | Out-Null }
  Write-Host "Stopped only the five owner-approved existing VMs; no disks, queues, databases, or secrets were removed."
} elseif ($Action -eq "Inspect") {
  Write-Host "Approved inspection reports dedicated VM status only."
  $inventory | Select-Object name,status | Format-Table
} elseif ($Action -eq "Test") {
  if ($TestStage -eq "admission") {
    $command = "STAGING_LOADTEST_APPROVED=1 k6 run -e BASE_URL=http://<staging-private-api>:8080 -e VUS=$Vus -e DURATION=60s /opt/coderzclub/perf/k6/staging-admission.js"
  } else {
    $command = "STAGING_LOADTEST_APPROVED=1 REAL_VERDICT_TEST_APPROVED=1 k6 run -e BASE_URL=http://<staging-private-api>:8080 -e VUS=$([Math]::Min($Vus, 10)) -e DURATION=60s /opt/coderzclub/perf/k6/staging-verdict.js"
  }
  Write-Host "TEST COMMAND TO RUN ON DEDICATED LOADGEN $($loadgen.name):"
  Write-Host $command
  Write-Host "This wrapper does not execute k6; run only after dependency and queue gates are approved."
}
