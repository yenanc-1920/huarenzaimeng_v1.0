[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'Invoke-TestReleaseGate.ps1') -EnvironmentName prod -ServerPort 18083
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
