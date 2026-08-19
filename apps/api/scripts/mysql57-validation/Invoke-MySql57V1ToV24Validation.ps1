[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[a-z0-9]{8,32}$')]
    [string]$RunId,

    [Parameter(Mandatory = $true)]
    [ValidateSet('EMPTY', 'V14', 'V21')]
    [string]$Scenario,

    [ValidateSet('DryRun', 'Preflight', 'Execute', 'Diagnose')]
    [string]$Action = 'DryRun',

    [string]$MysqlCommand = 'mysql',
    [string]$FlywayCommand = 'flyway',
    [string]$MysqlDefaultsFile,
    [string]$FlywayConfigFile,
    [string]$MysqlHost = '127.0.0.1',
    [ValidateRange(1, 65535)]
    [int]$MysqlPort = 3306,
    [string]$JdbcBaseUrl,
    [string]$MigrationDirectory = (Join-Path $PSScriptRoot '..\..\src\main\resources\db\migration'),
    [string]$EvidenceDirectory = (Join-Path $PSScriptRoot 'evidence'),
    [string]$ConfirmationToken
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$databaseName = "hz_verify_$RunId"
$businessNamePattern = '(?i)(huarenzaimeng|prod|stage|test|dev|it_vnext)'
if ($databaseName -notmatch '^hz_verify_[a-z0-9]{8,32}$' -or $databaseName -match $businessNamePattern) {
    throw 'VERIFY_DATABASE_NAME_REJECTED'
}

$migrationPath = [System.IO.Path]::GetFullPath($MigrationDirectory)
$scriptPath = $MyInvocation.MyCommand.Path
$scriptHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $scriptPath).Hash
$migrationFiles = @(Get-ChildItem -LiteralPath $migrationPath -File -Filter 'V*__*.sql' |
    Where-Object { $_.Name -match '^V(\d+)__.+\.sql$' } |
    Sort-Object { [int]([regex]::Match($_.Name, '^V(\d+)__').Groups[1].Value) })
$versions = @($migrationFiles | ForEach-Object { [int]([regex]::Match($_.Name, '^V(\d+)__').Groups[1].Value) })
if (($versions -join ',') -ne ((1..24) -join ',')) {
    throw 'MIGRATION_MANIFEST_MUST_BE_CONTINUOUS_V1_TO_V24'
}
$migrationHashes = [ordered]@{}
foreach ($migrationFile in $migrationFiles) {
    $migrationHashes[$migrationFile.Name] = (Get-FileHash -Algorithm SHA256 -LiteralPath $migrationFile.FullName).Hash
}
$manifestPayload = (($migrationHashes.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join "`n") + "`n"
$sha256 = [System.Security.Cryptography.SHA256]::Create()
try {
    $manifestBytes = [System.Text.Encoding]::UTF8.GetBytes($manifestPayload)
    $migrationManifestHash = ([System.BitConverter]::ToString($sha256.ComputeHash($manifestBytes))).Replace('-', '')
} finally {
    $sha256.Dispose()
}

function Require-File([string]$Path, [string]$Code) {
    if ([string]::IsNullOrWhiteSpace($Path) -or -not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw $Code
    }
}

function Assert-SecureCredentialFile([string]$Path, [ValidateSet('MYSQL', 'FLYWAY')][string]$Kind) {
    Require-File $Path "${Kind}_CREDENTIAL_FILE_REQUIRED"
    $content = Get-Content -LiteralPath $Path -Raw
    if ([string]::IsNullOrWhiteSpace($content) -or $content -match '(?i)(CHANGE_ME|REPLACE_ME|REDACTED_SECRET|<[^>]+>|\{\{[^}]+\}\}|\$\{[^}]+\})') {
        throw "${Kind}_CREDENTIAL_FILE_PLACEHOLDER_REJECTED"
    }
    $userPattern = if ($Kind -eq 'MYSQL') { '(?im)^\s*user\s*=\s*(.+?)\s*$' } else { '(?im)^\s*flyway\.user\s*=\s*(.+?)\s*$' }
    $passwordPattern = if ($Kind -eq 'MYSQL') { '(?im)^\s*password\s*=\s*(.+?)\s*$' } else { '(?im)^\s*flyway\.password\s*=\s*(.+?)\s*$' }
    $userMatches = [regex]::Matches($content, $userPattern)
    $passwordMatches = [regex]::Matches($content, $passwordPattern)
    if ($userMatches.Count -ne 1 -or $passwordMatches.Count -ne 1) {
        throw "${Kind}_CREDENTIAL_FILE_KEYS_REQUIRED"
    }
    $normalizedUser = $userMatches[0].Groups[1].Value.Trim()
    if (($normalizedUser.StartsWith('"') -and $normalizedUser.EndsWith('"')) -or
        ($normalizedUser.StartsWith("'") -and $normalizedUser.EndsWith("'"))) {
        $normalizedUser = $normalizedUser.Substring(1, $normalizedUser.Length - 2).Trim()
    }
    if ($normalizedUser -notmatch '^[A-Za-z0-9_.-]{1,32}$') {
        throw "${Kind}_CREDENTIAL_USER_INVALID"
    }
    $broadSids = @('S-1-1-0', 'S-1-5-11', 'S-1-5-32-545', 'S-1-5-32-546')
    $acl = Get-Acl -LiteralPath $Path
    foreach ($rule in $acl.Access) {
        if ($rule.AccessControlType -ne [System.Security.AccessControl.AccessControlType]::Allow) { continue }
        $sid = $null
        try { $sid = $rule.IdentityReference.Translate([System.Security.Principal.SecurityIdentifier]).Value } catch { }
        $isBroadName = $rule.IdentityReference.Value -match '(?i)(Everyone|Authenticated Users|BUILTIN\\Users|BUILTIN\\Guests)'
        $canReadOrWrite = ($rule.FileSystemRights -band (
            [System.Security.AccessControl.FileSystemRights]::ReadData -bor
            [System.Security.AccessControl.FileSystemRights]::ReadAndExecute -bor
            [System.Security.AccessControl.FileSystemRights]::WriteData -bor
            [System.Security.AccessControl.FileSystemRights]::Modify -bor
            [System.Security.AccessControl.FileSystemRights]::FullControl
        )) -ne 0
        if ($canReadOrWrite -and (($null -ne $sid -and $broadSids -contains $sid) -or $isBroadName)) {
            throw "${Kind}_CREDENTIAL_FILE_PERMISSIONS_TOO_BROAD"
        }
    }
    return $normalizedUser
}

function Assert-CommandAvailable([string]$Command, [string]$Code) {
    if ($null -eq (Get-Command $Command -ErrorAction SilentlyContinue)) { throw $Code }
}

function Invoke-Checked([string]$Command, [string[]]$Arguments, [string]$FailureCode) {
    $output = @(& $Command @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "$FailureCode exit=$LASTEXITCODE"
    }
    return @($output | ForEach-Object { [string]$_ })
}

function Invoke-MysqlRead([string]$Sql, [switch]$UseDatabase) {
    $args = @(
        "--defaults-extra-file=$MysqlDefaultsFile",
        "--host=$MysqlHost",
        "--port=$MysqlPort",
        '--batch', '--raw', '--skip-column-names'
    )
    if ($UseDatabase) { $args += "--database=$databaseName" }
    $args += @('--execute', $Sql)
    return Invoke-Checked $MysqlCommand $args 'MYSQL_READ_FAILED'
}

function Invoke-Flyway([string[]]$CommandArguments) {
    $url = "$JdbcBaseUrl/$databaseName"
    $args = @(
        "-configFiles=$FlywayConfigFile",
        "-url=$url",
        "-schemas=$databaseName",
        "-defaultSchema=$databaseName",
        "-locations=filesystem:$migrationPath",
        '-cleanDisabled=true',
        '-baselineOnMigrate=false',
        '-outOfOrder=false',
        '-validateMigrationNaming=true'
    ) + $CommandArguments
    return Invoke-Checked $FlywayCommand $args 'FLYWAY_COMMAND_FAILED'
}

function Get-GitCommit {
    try {
        $value = @(& git rev-parse HEAD 2>$null)
        if ($LASTEXITCODE -eq 0 -and $value.Count -eq 1) { return [string]$value[0] }
    } catch { }
    return 'UNKNOWN'
}

function Assert-LeastPrivilegeGrants([string[]]$GrantLines) {
    $required = @('ALTER', 'CREATE', 'DELETE', 'INDEX', 'INSERT', 'REFERENCES', 'SELECT', 'UPDATE')
    $observed = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    $targetPattern = "^GRANT\s+(.+)\s+ON\s+``?$([regex]::Escape($databaseName))``?\.\*\s+TO\s+"
    foreach ($line in $GrantLines) {
        $normalized = $line.Trim()
        if ($normalized -match '(?i)(WITH\s+GRANT\s+OPTION|\bSUPER\b|ALL\s+PRIVILEGES)') {
            throw 'MIGRATION_ACCOUNT_HIGH_PRIVILEGE_REJECTED'
        }
        if ($normalized -match '(?i)^GRANT\s+USAGE\s+ON\s+\*\.\*\s+TO\s+') { continue }
        $match = [regex]::Match($normalized, $targetPattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
        if (-not $match.Success) { throw 'MIGRATION_ACCOUNT_NON_TARGET_GRANT_REJECTED' }
        foreach ($privilege in ($match.Groups[1].Value -split ',')) {
            [void]$observed.Add($privilege.Trim().ToUpperInvariant())
        }
    }
    $actual = @($observed | Sort-Object)
    if (($actual -join ',') -ne ($required -join ',')) {
        throw "MIGRATION_ACCOUNT_EXACT_PRIVILEGES_REQUIRED expected=$($required -join ',')"
    }
}

$plan = [ordered]@{
    status = 'PLANNED'
    action = $Action
    scenario = $Scenario
    runId = $RunId
    databaseName = $databaseName
    migrationCount = $migrationFiles.Count
    highestVersion = 24
    scriptSha256 = $scriptHash
    migrationManifestSha256 = $migrationManifestHash
    zeroConnection = ($Action -eq 'DryRun')
    destructiveCleanup = $false
}

if ($Action -eq 'DryRun') {
    $plan | ConvertTo-Json -Depth 4
    exit 0
}

if ($Action -eq 'Execute') {
    $mysqlCredentialUser = Assert-SecureCredentialFile $MysqlDefaultsFile 'MYSQL'
    $flywayCredentialUser = Assert-SecureCredentialFile $FlywayConfigFile 'FLYWAY'
    if ($mysqlCredentialUser -cne $flywayCredentialUser) {
        throw 'MYSQL_AND_FLYWAY_MIGRATION_USER_MUST_MATCH'
    }
    Assert-CommandAvailable $MysqlCommand 'MYSQL_COMMAND_REQUIRED'
    Assert-CommandAvailable $FlywayCommand 'FLYWAY_COMMAND_REQUIRED'
    if ([string]::IsNullOrWhiteSpace($JdbcBaseUrl)) { throw 'JDBC_BASE_URL_REQUIRED' }
    $jdbcMatch = [regex]::Match($JdbcBaseUrl, '^jdbc:mysql://([^/:?]+)(?::(\d+))?$')
    if (-not $jdbcMatch.Success) { throw 'JDBC_BASE_URL_MUST_NOT_CONTAIN_DATABASE_QUERY_OR_CREDENTIALS' }
    $jdbcPort = if ($jdbcMatch.Groups[2].Success) { [int]$jdbcMatch.Groups[2].Value } else { 3306 }
    if ($jdbcMatch.Groups[1].Value -cne $MysqlHost -or $jdbcPort -ne $MysqlPort) {
        throw 'JDBC_AND_MYSQL_ENDPOINT_MUST_MATCH'
    }
    if ($ConfirmationToken -cne "EXECUTE_MYSQL57_VERIFY:${databaseName}:$Scenario") {
        throw 'EXECUTE_CONFIRMATION_TOKEN_MISMATCH'
    }
} else {
    [void](Assert-SecureCredentialFile $MysqlDefaultsFile 'MYSQL')
    Assert-CommandAvailable $MysqlCommand 'MYSQL_COMMAND_REQUIRED'
}

$serverIdentitySql = "SELECT COALESCE(@@server_uuid,''), VERSION();"
$serverIdentity = @(Invoke-MysqlRead $serverIdentitySql)
if ($serverIdentity.Count -ne 1) { throw 'MYSQL_SERVER_IDENTITY_AMBIGUOUS' }
$identityParts = $serverIdentity[0] -split "`t", 2
if ($identityParts.Count -ne 2 -or $identityParts[1] -notmatch '^5\.7\.') {
    throw 'MYSQL_5_7_REQUIRED'
}
$serverUuid = $identityParts[0]
$mysqlVersion = $identityParts[1]

$databaseExistsSql = "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = '$databaseName';"
$databaseExists = [int]((Invoke-MysqlRead $databaseExistsSql)[0])

if ($Action -eq 'Preflight') {
    if ($databaseExists -ne 0) { throw 'VERIFY_DATABASE_ALREADY_EXISTS' }
    $plan.status = 'PREFLIGHT_OK'
    $plan.serverUuid = $serverUuid
    $plan.mysqlVersion = $mysqlVersion
    $plan | ConvertTo-Json -Depth 4
    exit 0
}

if ($Action -eq 'Diagnose') {
    if ($databaseExists -ne 1) { throw 'VERIFY_DATABASE_NOT_FOUND' }
    $databaseIdentity = @(Invoke-MysqlRead "SELECT DATABASE(), COALESCE(@@server_uuid,''), VERSION();" -UseDatabase)
    $historyTableCount = [int]((Invoke-MysqlRead "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='flyway_schema_history';" -UseDatabase)[0])
    $historySummary = @('ABSENT')
    $failedMigrations = @()
    if ($historyTableCount -eq 1) {
        $historySummary = @(Invoke-MysqlRead "SELECT COUNT(*), COALESCE(MAX(CAST(version AS UNSIGNED)),0), SUM(CASE WHEN success=1 THEN 1 ELSE 0 END), SUM(CASE WHEN success=0 THEN 1 ELSE 0 END) FROM flyway_schema_history;" -UseDatabase)
        $failedMigrations = @(Invoke-MysqlRead "SELECT installed_rank, COALESCE(version,''), description, script FROM flyway_schema_history WHERE success=0 ORDER BY installed_rank;" -UseDatabase)
    }
    $tableCount = [int]((Invoke-MysqlRead "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE';" -UseDatabase)[0])
    $columnCount = [int]((Invoke-MysqlRead "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE();" -UseDatabase)[0])
    [ordered]@{
        status = 'DIAGNOSIS_ONLY'
        scenario = $Scenario
        runId = $RunId
        databaseName = $databaseName
        identity = $databaseIdentity
        flywayHistoryTableCount = $historyTableCount
        flywaySummary = $historySummary
        failedMigrations = $failedMigrations
        tableCount = $tableCount
        columnCount = $columnCount
        instruction = 'PRESERVE_DATABASE_STOP_NO_RETRY_USE_RUNBOOK'
        scriptSha256 = $scriptHash
        migrationManifestSha256 = $migrationManifestHash
    } | ConvertTo-Json -Depth 4
    exit 0
}

if ($databaseExists -ne 1) { throw 'PRECREATED_VERIFY_DATABASE_REQUIRED' }

$startedAt = [DateTimeOffset]::UtcNow
try {
    $boundIdentity = @(Invoke-MysqlRead "SELECT DATABASE(), COALESCE(@@server_uuid,''), VERSION();" -UseDatabase)
    if ($boundIdentity.Count -ne 1 -or $boundIdentity[0] -notmatch "^$([regex]::Escape($databaseName))`t$([regex]::Escape($serverUuid))`t5\.7\.") {
        throw 'DATABASE_SERVER_VERSION_IDENTITY_BINDING_FAILED'
    }
    $authenticatedUser = @((Invoke-MysqlRead 'SELECT CURRENT_USER();' -UseDatabase))[0]
    $authenticatedUserMatch = [regex]::Match($authenticatedUser, '^([^@]+)@[^@]+$')
    if (-not $authenticatedUserMatch.Success -or $authenticatedUserMatch.Groups[1].Value -cne $mysqlCredentialUser) {
        throw 'MYSQL_AUTHENTICATED_USER_MUST_MATCH_MIGRATION_USER'
    }
    $grantLines = @(Invoke-MysqlRead 'SHOW GRANTS FOR CURRENT_USER();')
    Assert-LeastPrivilegeGrants $grantLines

    if ($Scenario -eq 'V14') {
        [void](Invoke-Flyway @('-target=14', 'migrate'))
    } elseif ($Scenario -eq 'V21') {
        [void](Invoke-Flyway @('-target=21', 'migrate'))
    }

    if ($Scenario -ne 'EMPTY') {
        $expected = if ($Scenario -eq 'V14') { 14 } else { 21 }
        $actual = [int]((Invoke-MysqlRead "SELECT COALESCE(MAX(CAST(version AS UNSIGNED)),0) FROM flyway_schema_history WHERE success=1;" -UseDatabase)[0])
        if ($actual -ne $expected) { throw 'START_STATE_TERMINAL_VERSION_MISMATCH' }
    }

    [void](Invoke-Flyway @('-target=24', 'migrate'))
    [void](Invoke-Flyway @('validate'))
    # This is an explicit idempotency check inside the same authorized validation run, not a failure retry.
    [void](Invoke-Flyway @('-target=24', 'migrate'))

    $terminal = @(Invoke-MysqlRead "SELECT COUNT(*), COUNT(DISTINCT version), COALESCE(MAX(CAST(version AS UNSIGNED)),0), SUM(CASE WHEN success=1 THEN 1 ELSE 0 END), SUM(CASE WHEN success=0 THEN 1 ELSE 0 END) FROM flyway_schema_history;" -UseDatabase)
    $tableCount = [int]((Invoke-MysqlRead "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE';" -UseDatabase)[0])
    $v22TableCount = [int]((Invoke-MysqlRead "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('buyer_consent_state','buyer_consent_acceptance','buyer_account_closure_request','buyer_pii_cleanup_task');" -UseDatabase)[0])
    $v22AuditColumnCount = [int]((Invoke-MysqlRead "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND ((table_name='hz_content_review_task' AND column_name IN ('self_approved','exception_policy_version')) OR (table_name='hz_v1_admin_audit' AND column_name IN ('self_approved','exception_policy_version')) OR (table_name='hz_content_version_history' AND column_name IN ('self_approved','exception_policy_version')));" -UseDatabase)[0])
    $v23TableCount = [int]((Invoke-MysqlRead "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('hz_quote_recipient_pending','hz_order_recipient_fulfillment');" -UseDatabase)[0])
    $v24TableCount = [int]((Invoke-MysqlRead "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='buyer_wechat_payment_identity';" -UseDatabase)[0])
    $v24PrepayColumnCount = [int]((Invoke-MysqlRead "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='hz_payment_coordination' AND column_name IN ('prepay_timestamp','prepay_nonce','prepay_package','prepay_sign_type','prepay_pay_sign','prepay_expires_at');" -UseDatabase)[0])
    if ($terminal.Count -ne 1 -or $terminal[0] -ne "24`t24`t24`t24`t0") {
        throw 'V24_TERMINAL_HISTORY_MISMATCH'
    }
    if ($v22TableCount -ne 4 -or $v22AuditColumnCount -ne 6) {
        throw 'V22_TERMINAL_OBJECT_MISMATCH'
    }
    if ($v23TableCount -ne 2) {
        throw 'V23_TERMINAL_OBJECT_MISMATCH'
    }
    if ($v24TableCount -ne 1 -or $v24PrepayColumnCount -ne 6) {
        throw 'V24_TERMINAL_OBJECT_MISMATCH'
    }

    $evidence = [ordered]@{
        status = 'PASS'
        scenario = $Scenario
        runId = $RunId
        databaseName = $databaseName
        serverUuid = $serverUuid
        mysqlVersion = $mysqlVersion
        gitCommit = Get-GitCommit
        scriptSha256 = $scriptHash
        migrationManifestSha256 = $migrationManifestHash
        migrationCount = 24
        terminalHistory = $terminal[0]
        tableCount = $tableCount
        v22TableCount = $v22TableCount
        v22AuditColumnCount = $v22AuditColumnCount
        v23TableCount = $v23TableCount
        v24TableCount = $v24TableCount
        v24PrepayColumnCount = $v24PrepayColumnCount
        startedAtUtc = $startedAt.ToString('O')
        finishedAtUtc = [DateTimeOffset]::UtcNow.ToString('O')
        evidenceBoundary = 'TEMPORARY_MYSQL57_VALIDATION_ONLY_NOT_PRODUCTION_EVIDENCE'
    }
    New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null
    $evidencePath = Join-Path $EvidenceDirectory "$RunId-$($Scenario.ToLowerInvariant()).json"
    $evidence | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $evidencePath -Encoding UTF8
    $evidence | ConvertTo-Json -Depth 4
} catch {
    [ordered]@{
        status = 'FAILED_STOP_NO_RETRY'
        scenario = $Scenario
        runId = $RunId
        databaseName = $databaseName
        serverUuid = $serverUuid
        mysqlVersion = $mysqlVersion
        scriptSha256 = $scriptHash
        migrationManifestSha256 = $migrationManifestHash
        failure = $_.Exception.Message
        instruction = 'PRESERVE_TEMP_DATABASE_RUN_DIAGNOSE_DO_NOT_MUTATE_OR_RETRY'
        failedAtUtc = [DateTimeOffset]::UtcNow.ToString('O')
    } | ConvertTo-Json -Depth 4
    exit 1
}
