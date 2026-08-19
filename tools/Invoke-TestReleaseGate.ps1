[CmdletBinding()]
param(
    [ValidateSet('test', 'stage', 'prod')]
    [string]$EnvironmentName = 'test',
    [ValidateRange(1024, 65535)]
    [int]$ServerPort = 18081
)

$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$runtimeRoot = 'E:\workspace\huarenzaimeng\.runtime\mysql-dev'
$credentialFile = Join-Path $runtimeRoot 'credentials.clixml'
$mysql = 'E:\workspace\huarenzaimeng\.runtime\mysql-5.7.44-winx64\bin\mysql.exe'
$gateId = $EnvironmentName + '_gate_' + (Get-Date -Format 'yyyyMMdd_HHmmss') + '_' + ([guid]::NewGuid().ToString('N').Substring(0, 8))
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

function Invoke-AppScalar([string]$sql, [string]$password) {
    $env:MYSQL_PWD = $password
    try {
        $value = $sql | & $mysql --protocol=TCP --host=$($credentials.Host) --port=$($credentials.Port) `
            --user=$($credentials.App.UserName) --batch --skip-column-names --silent
        if ($LASTEXITCODE -ne 0) { throw "MYSQL_APP_SQL_FAILED exit=$LASTEXITCODE" }
        return "$value".Trim()
    } finally {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    }
}

function Wait-Health([int]$seconds) {
    $deadline = (Get-Date).AddSeconds($seconds)
    do {
        if ($appProcess.HasExited) { throw "TEST_APPLICATION_EXITED code=$($appProcess.ExitCode)" }
        try {
            $response = Invoke-RestMethod -Uri "http://127.0.0.1:$ServerPort/actuator/health" -TimeoutSec 3
            if ($response.status -eq 'UP') { return }
        } catch { Start-Sleep -Seconds 1 }
    } while ((Get-Date) -lt $deadline)
    throw 'TEST_APPLICATION_HEALTH_TIMEOUT'
}

function Wait-MigrationHistory([int]$seconds, [string]$password) {
    $deadline = (Get-Date).AddSeconds($seconds)
    do {
        if ($appProcess.HasExited) { throw "TEST_APPLICATION_EXITED_DURING_MIGRATION code=$($appProcess.ExitCode)" }
        $tableCount = Invoke-AppScalar "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$database' AND table_name='flyway_schema_history';" $password
        if ($tableCount -eq '1') {
            $history = Invoke-AppScalar "SELECT COUNT(*), SUM(success=0), MAX(CAST(version AS UNSIGNED)) FROM $database.flyway_schema_history WHERE version IS NOT NULL;" $password
            if ($history -eq "14`t0`t14") { return }
        }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)
    throw 'TEST_MIGRATION_HISTORY_TIMEOUT'
}

if (-not (Test-Path -LiteralPath $credentialFile)) { throw 'LOCAL_MYSQL_CREDENTIALS_MISSING' }
if (-not (Test-Path -LiteralPath $mysql)) { throw 'LOCAL_MYSQL_CLIENT_MISSING' }
$credentials = Import-Clixml -LiteralPath $credentialFile
if ($credentials.Host -ne '127.0.0.1' -or [int]$credentials.Port -ne 3307) {
    throw 'LOCAL_MYSQL_IDENTITY_MISMATCH'
}

New-Item -ItemType Directory -Force -Path $gateOutput | Out-Null

try {
    Invoke-RootSql @"
CREATE DATABASE $database CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT SELECT, INSERT, UPDATE, DELETE ON $database.* TO 'huaren_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, REFERENCES, INDEX, ALTER ON $database.* TO 'huaren_migrator'@'%';
FLUSH PRIVILEGES;
"@

    $jar = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'apps\api\target') -Filter 'api-*.jar' -File |
        Where-Object { $_.Name -notlike '*.original' })
    if ($jar.Count -ne 1) { throw "TEST_JAR_IDENTITY_INVALID count=$($jar.Count)" }

    $appPassword = Get-PlainPassword $credentials.App.Password
    $jdbc = "jdbc:mysql://127.0.0.1:3307/${database}?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Dhaka"
    $env:SPRING_PROFILES_ACTIVE = "release-mysql,$EnvironmentName-mysql"
    $env:SERVER_PORT = "$ServerPort"
    $env:HZ_DATASOURCE_URL = $jdbc
    $env:SPRING_DATASOURCE_USERNAME = $credentials.App.UserName
    $env:SPRING_DATASOURCE_PASSWORD = $appPassword
    $env:SPRING_FLYWAY_USER = $credentials.Migrator.UserName
    $env:SPRING_FLYWAY_PASSWORD = Get-PlainPassword $credentials.Migrator.Password
    $env:SPRING_FLYWAY_CONNECT_RETRIES = '0'
    $env:HZ_ENV_DATABASE_NAME = $database
    if ($EnvironmentName -eq 'prod') {
        $env:HZ_ENV_INITIALIZE_EMPTY_DATABASE = 'true'
    } else {
        $env:HZ_ENV_MIGRATION_ENABLED = 'true'
    }
    $env:HZ_ENV_FUNCTION_RELEASE_ENABLED = 'true'
    $env:HZ_ADMIN_BOOTSTRAP_ENABLED = 'false'

    $stdout = Join-Path $gateOutput 'application.stdout.log'
    $stderr = Join-Path $gateOutput 'application.stderr.log'
    $appProcess = Start-Process -FilePath 'java' -ArgumentList '-jar', $jar[0].FullName `
        -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr

    Wait-MigrationHistory 75 $appPassword
    Wait-Health 75
    $seedCount = Invoke-AppScalar "SELECT (SELECT COUNT(*) FROM $database.hz_city) + (SELECT COUNT(*) FROM $database.hz_platform_product);" $appPassword
    if ($seedCount -ne '0') { throw "TEST_DEVELOPMENT_DATA_PRESENT count=$seedCount" }

    Write-Output "$($EnvironmentName.ToUpperInvariant())_RELEASE_GATE_PASS gate=$gateId migrations=14 devSeedRows=0 health=UP"
} finally {
    if ($appProcess -and -not $appProcess.HasExited) {
        Stop-Process -Id $appProcess.Id -ErrorAction SilentlyContinue
        $appProcess.WaitForExit(10000) | Out-Null
    }
    Remove-Item Env:SPRING_PROFILES_ACTIVE,Env:SERVER_PORT,Env:HZ_DATASOURCE_URL,Env:SPRING_DATASOURCE_USERNAME,Env:SPRING_DATASOURCE_PASSWORD,Env:SPRING_FLYWAY_USER,Env:SPRING_FLYWAY_PASSWORD,Env:SPRING_FLYWAY_CONNECT_RETRIES,Env:HZ_ENV_DATABASE_NAME,Env:HZ_ENV_MIGRATION_ENABLED,Env:HZ_ENV_INITIALIZE_EMPTY_DATABASE,Env:HZ_ENV_FUNCTION_RELEASE_ENABLED,Env:HZ_ADMIN_BOOTSTRAP_ENABLED -ErrorAction SilentlyContinue
    if ($credentials) {
        try { Invoke-RootSql "DROP DATABASE IF EXISTS $database;" } catch { Write-Warning 'TEST_GATE_DATABASE_CLEANUP_FAILED' }
    }
}
