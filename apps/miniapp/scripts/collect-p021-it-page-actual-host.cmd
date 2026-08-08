@echo off
setlocal EnableExtensions DisableDelayedExpansion

if not defined P021_PAGE_COLLECTOR (
  echo PAGE_COLLECTOR_PATH_NOT_BOUND 1>&2
  exit /b 65
)
if not defined P021_PAGE_PARAMS (
  echo PAGE_COLLECTOR_PARAMS_NOT_BOUND 1>&2
  exit /b 66
)

powershell.exe -NoLogo -NoProfile -NonInteractive -Command "$ErrorActionPreference='Stop'; try { $params=$env:P021_PAGE_PARAMS|ConvertFrom-Json; $invoke=@{}; foreach($property in $params.PSObject.Properties){$invoke[$property.Name]=$property.Value}; $source=[IO.File]::ReadAllText($env:P021_PAGE_COLLECTOR,[Text.Encoding]::UTF8); $entry=[ScriptBlock]::Create($source); & $entry @invoke; exit $LASTEXITCODE } catch { Write-Error ($_.Exception.Message + [Environment]::NewLine + $_.ScriptStackTrace); exit 1 }"
exit /b %ERRORLEVEL%
