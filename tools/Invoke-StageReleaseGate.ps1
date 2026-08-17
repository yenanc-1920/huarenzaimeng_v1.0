[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'Invoke-TestReleaseGate.ps1') -EnvironmentName stage -ServerPort 18082
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
