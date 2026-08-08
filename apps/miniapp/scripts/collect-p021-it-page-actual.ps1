[CmdletBinding()]
param(
  [Parameter(Mandatory=$true)][ValidateSet('IT01','IT02','IT03','IT04','IT05','IT06')][string]$ScenarioId,
  [Parameter(Mandatory=$true)][string]$SubcaseId,
  [Parameter(Mandatory=$true)][ValidateSet('MINIAPP_P021','ADMIN_A140')][string]$PageKind,
  [Parameter(Mandatory=$true)][ValidateSet('BUYER','CS','FIN','CONTENT')][string]$Role,
  [Parameter(Mandatory=$true)][string]$BaseUrl,
  [Parameter(Mandatory=$true)][string]$OrderRef,
  [Parameter(Mandatory=$true)][ValidateSet('READY','READ_ERROR','INFORMATION_UPDATED','NOT_AVAILABLE','ACCESS_DENIED','UNAVAILABLE')][string]$ExpectedViewState,
  [Parameter(Mandatory=$true)][string]$OutputDirectory,
  [Parameter(Mandatory=$true)][string]$BrowserExecutable,
  [Parameter(Mandatory=$true)][string]$BrowserSha256,
  [Parameter(Mandatory=$true)][int]$ViewportWidth,
  [Parameter(Mandatory=$true)][int]$ViewportHeight,
  [Parameter(Mandatory=$true)][ValidateSet('PROJECTION_LOW_VERSION','ORDER_VERSION_CONFLICT','QUOTE_DIGEST_CONFLICT','LATE_LOWER_VERSION_AFTER_READY','NONE')][string]$DelayPlan
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$node=(Get-Command node.exe -ErrorAction Stop).Source
$entry=Join-Path $PSScriptRoot 'collect-p021-it-page-actual.mjs'
$arguments=@($entry,'--scenario-id',$ScenarioId,'--subcase-id',$SubcaseId,'--page-kind',$PageKind,'--role',$Role,'--base-url',$BaseUrl,'--order-ref',$OrderRef,'--expected-view-state',$ExpectedViewState,'--output-directory',$OutputDirectory,'--browser-executable',$BrowserExecutable,'--browser-sha256',$BrowserSha256,'--viewport-width',[string]$ViewportWidth,'--viewport-height',[string]$ViewportHeight,'--delay-plan',$DelayPlan)
& $node @arguments
exit $LASTEXITCODE
