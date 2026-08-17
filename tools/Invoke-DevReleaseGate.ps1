[CmdletBinding()]
param(
    [switch]$SkipCodeGate
)

$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$runtimeRoot = 'E:\workspace\huarenzaimeng\.runtime\mysql-dev'
$credentialFile = Join-Path $runtimeRoot 'credentials.clixml'
$mysql = 'E:\workspace\huarenzaimeng\.runtime\mysql-5.7.44-winx64\bin\mysql.exe'
$mavenRepository = 'E:\workspace\huarenzaimeng\.runtime\maven-repository'
$gateId = 'gate_' + (Get-Date -Format 'yyyyMMdd_HHmmss') + '_' + ([guid]::NewGuid().ToString('N').Substring(0, 8))
$database = 'huarenzaimeng_' + $gateId
$gateOutput = Join-Path $runtimeRoot (Join-Path 'gates' $gateId)
$appProcess = $null

function Get-PlainPassword([System.Security.SecureString]$secure) {
    return [System.Net.NetworkCredential]::new('', $secure).Password
}

function Invoke-RootSql([string]$sql) {
    $env:MYSQL_PWD = Get-PlainPassword $credentials.Root.Password
    try {
        $sql | & $mysql --protocol=TCP --host=$($credentials.Host) --port=$($credentials.Port) `
            --user=$($credentials.Root.UserName) --batch --silent
        if ($LASTEXITCODE -ne 0) { throw "MYSQL_ROOT_SQL_FAILED exit=$LASTEXITCODE" }
    } finally {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    }
}

function Wait-HttpReady([string]$url, [int]$seconds) {
    $deadline = (Get-Date).AddSeconds($seconds)
    do {
        if ($appProcess -and $appProcess.HasExited) {
            throw "DEV_APPLICATION_EXITED code=$($appProcess.ExitCode)"
        }
        try {
            $response = Invoke-RestMethod -Uri $url -TimeoutSec 3
            if ($response.status -eq 'UP') { return }
        } catch {
            Start-Sleep -Seconds 1
        }
    } while ((Get-Date) -lt $deadline)
    throw 'DEV_APPLICATION_HEALTH_TIMEOUT'
}

function Wait-FlywayHistory([int]$seconds, [string]$appPassword) {
    $deadline = (Get-Date).AddSeconds($seconds)
    $lastHistory = 'NOT_READ'
    do {
        if ($appProcess -and $appProcess.HasExited) {
            throw "DEV_APPLICATION_EXITED_DURING_MIGRATION code=$($appProcess.ExitCode)"
        }
        $env:MYSQL_PWD = $appPassword
        try {
            $historyTableCount = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$database' AND table_name='flyway_schema_history';" |
                & $mysql --protocol=TCP --host=127.0.0.1 --port=3307 --user=$($credentials.App.UserName) `
                    --batch --skip-column-names --silent
            if ($LASTEXITCODE -eq 0 -and $historyTableCount -eq '1') {
                $lastHistory = "SELECT COALESCE(SUM(version IS NOT NULL),0), COALESCE(SUM(success=0),0), COALESCE(MAX(CAST(version AS UNSIGNED)),0) FROM $database.flyway_schema_history;" |
                    & $mysql --protocol=TCP --host=127.0.0.1 --port=3307 --user=$($credentials.App.UserName) `
                        --batch --skip-column-names --silent
                $parts = @($lastHistory -split "`t")
                if ($parts.Count -eq 3 -and $parts[0] -eq '14' -and $parts[1] -eq '0' -and $parts[2] -eq '14') {
                    return
                }
            }
        } finally {
            Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
        }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)
    throw "FLYWAY_HISTORY_TIMEOUT actual=$lastHistory"
}

if (-not (Test-Path -LiteralPath $credentialFile)) { throw 'LOCAL_MYSQL_CREDENTIALS_MISSING' }
if (-not (Test-Path -LiteralPath $mysql)) { throw 'LOCAL_MYSQL_CLIENT_MISSING' }
$credentials = Import-Clixml -LiteralPath $credentialFile
if ($credentials.Host -ne '127.0.0.1' -or [int]$credentials.Port -ne 3307) {
    throw 'LOCAL_MYSQL_IDENTITY_MISMATCH'
}

New-Item -ItemType Directory -Force -Path $gateOutput | Out-Null
New-Item -ItemType Directory -Force -Path $mavenRepository | Out-Null
$env:MAVEN_REPO_LOCAL = $mavenRepository

try {
    Invoke-RootSql @"
CREATE DATABASE $database CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT SELECT, INSERT, UPDATE, DELETE ON $database.* TO 'huaren_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, REFERENCES, INDEX, ALTER ON $database.* TO 'huaren_migrator'@'%';
FLUSH PRIVILEGES;
"@

    if (-not $SkipCodeGate) {
        & node (Join-Path $projectRoot 'tools\run-dev-deployment-gate.mjs')
        if ($LASTEXITCODE -ne 0) { throw "DEV_CODE_GATE_FAILED exit=$LASTEXITCODE" }
    }

    $jar = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'apps\api\target') -Filter 'api-*.jar' -File |
        Where-Object { $_.Name -notlike '*.original' })
    if ($jar.Count -ne 1) { throw "DEV_JAR_IDENTITY_INVALID count=$($jar.Count)" }

    $appPassword = Get-PlainPassword $credentials.App.Password
    $migratorPassword = Get-PlainPassword $credentials.Migrator.Password
    $jdbc = "jdbc:mysql://127.0.0.1:3307/${database}?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Dhaka"
    $env:SPRING_PROFILES_ACTIVE = 'release-mysql,local-mysql'
    $env:SERVER_PORT = '18080'
    $env:HZ_DATASOURCE_URL = $jdbc
    $env:SPRING_DATASOURCE_USERNAME = $credentials.App.UserName
    $env:SPRING_DATASOURCE_PASSWORD = $appPassword
    $env:SPRING_FLYWAY_USER = $credentials.Migrator.UserName
    $env:SPRING_FLYWAY_PASSWORD = $migratorPassword
    $env:SPRING_FLYWAY_CONNECT_RETRIES = '0'
    $env:HZ_DEV_DATABASE_NAME = $database
    $env:HZ_DEV_FUNCTION_RELEASE_ENABLED = 'true'
    $env:HZ_ADMIN_BOOTSTRAP_ENABLED = 'false'
    $env:HZ_ADMIN_SESSION_HOURS = '8'

    $stdout = Join-Path $gateOutput 'application.stdout.log'
    $stderr = Join-Path $gateOutput 'application.stderr.log'
    $appProcess = Start-Process -FilePath 'java' -ArgumentList '-jar', $jar[0].FullName `
        -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr

    Wait-HttpReady 'http://127.0.0.1:18080/actuator/health' 75
    Wait-FlywayHistory 75 $appPassword

    Write-Output "DEV_RELEASE_GATE_PASS gate=$gateId migrations=14 health=UP"
} finally {
    if ($appProcess -and -not $appProcess.HasExited) {
        Stop-Process -Id $appProcess.Id -ErrorAction SilentlyContinue
        $appProcess.WaitForExit(10000) | Out-Null
    }
    Remove-Item Env:MAVEN_REPO_LOCAL,Env:SPRING_PROFILES_ACTIVE,Env:SERVER_PORT,Env:HZ_DATASOURCE_URL,Env:SPRING_DATASOURCE_USERNAME,Env:SPRING_DATASOURCE_PASSWORD,Env:SPRING_FLYWAY_USER,Env:SPRING_FLYWAY_PASSWORD,Env:SPRING_FLYWAY_CONNECT_RETRIES,Env:HZ_DEV_DATABASE_NAME,Env:HZ_DEV_FUNCTION_RELEASE_ENABLED,Env:HZ_ADMIN_BOOTSTRAP_ENABLED,Env:HZ_ADMIN_SESSION_HOURS -ErrorAction SilentlyContinue
    if ($credentials) {
        try { Invoke-RootSql "DROP DATABASE IF EXISTS $database;" } catch { Write-Warning 'GATE_DATABASE_CLEANUP_FAILED' }
    }
}
