@echo off
setlocal EnableExtensions DisableDelayedExpansion

if "%~1"=="" (
  echo AUTHORIZATION_FILE_REQUIRED 1>&2
  exit /b 64
)

set "P021_IT_AUTHORIZATION_FILE=%~f1"
set "P021_IT_WRAPPER=%~dp0Invoke-P021TestReadonlyIntegrationFinalRun.ps1"
set "P021_IT_SELF_TEST=false"
if /I "%~2"=="--self-test" set "P021_IT_SELF_TEST=true"

if not defined P021_IT_AUTHORIZATION_FILE (
  echo AUTHORIZATION_PATH_NOT_BOUND 1>&2
  exit /b 65
)
if not defined P021_IT_WRAPPER (
  echo WRAPPER_PATH_NOT_BOUND 1>&2
  exit /b 66
)

powershell.exe -NoLogo -NoProfile -NonInteractive -Command "$ErrorActionPreference='Stop'; try { $source=[IO.File]::ReadAllText($env:P021_IT_WRAPPER,[Text.Encoding]::UTF8); $entry=[ScriptBlock]::Create($source); if($env:P021_IT_SELF_TEST -ceq 'true'){ & $entry -AuthorizationFile $env:P021_IT_AUTHORIZATION_FILE -SelfTest } else { & $entry -AuthorizationFile $env:P021_IT_AUTHORIZATION_FILE }; exit $LASTEXITCODE } catch { Write-Error ($_.Exception.Message + [Environment]::NewLine + $_.ScriptStackTrace); exit 1 }"
exit /b %ERRORLEVEL%
