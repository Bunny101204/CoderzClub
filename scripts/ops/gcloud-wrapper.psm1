Set-StrictMode -Version Latest

function Get-GcloudExecutable {
  $candidates = if ($env:OS -eq "Windows_NT") { @("gcloud.cmd", "gcloud") } else { @("gcloud") }
  foreach ($candidate in $candidates) {
    $command = Get-Command $candidate -CommandType Application -ErrorAction SilentlyContinue |
      Select-Object -First 1
    if ($null -ne $command) { return $command.Source }
  }
  throw "gcloud CLI executable not found; expected gcloud.cmd on Windows."
}

function Invoke-Gcloud {
  param([Parameter(Mandatory = $true, Position = 0)][string[]]$GcloudArgs)
  $executable = Get-GcloudExecutable
  $output = & $executable @GcloudArgs 2>&1
  if ($LASTEXITCODE -ne 0) {
    throw "gcloud failed: $($output -join [Environment]::NewLine)"
  }
  return ($output -join [Environment]::NewLine)
}

function Test-GcloudCommand {
  param([Parameter(Mandatory = $true, Position = 0)][string[]]$GcloudArgs)
  $executable = Get-GcloudExecutable
  & $executable @GcloudArgs 2>$null | Out-Null
  return $LASTEXITCODE -eq 0
}

Export-ModuleMember -Function Get-GcloudExecutable, Invoke-Gcloud, Test-GcloudCommand
