[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [switch]$FinalRun,

    [Parameter(Mandatory = $true)]
    [ValidateSet('FINAL_RUN')]
    [string]$Confirm,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^M1-BE-[A-Za-z0-9][A-Za-z0-9._-]{7,80}$')]
    [string]$RunId,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Fa-f0-9]{64}$')]
    [string]$ExpectedGeneratorSha256
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ExpectedImplementationAggregate = 'DF0759508BD7E3588277BDCE7773A983C3389DBB0F17B602F0625BDC78058658'
$ExpectedMatrixSha = '4673D29642AB461B62AB73EDCF828644234C2D1FBAFBF62C4729D226C45726C5'
$ExpectedD1Sha = 'F2CA50A6C8EAD6463753E33B50A819EE51DA69117D7EDDCE9A54EC4A8F785538'
$ExpectedD2Sha = '2E9BC7D6F9075018CF5DB447571FD8FA9E23DA2E237EC0EEF13AA6D64628AA73'
$ExpectedD3RegistrySha = '358CBFCC3DEAEE618C8F478603367FA3001B84BE63AAF11ED9CCB9B3EA5BDB8C'
$ExpectedD303Sha = '2C3CE8BB4A872953CFAFAAEB8AC4983839BAD21A162FF6B40B09F6A20CCB6B5A'
$ExpectedD305Sha = 'BA21D617C9FBB96F5CBC7628ACFE53312B51735B5894D813C4725B7F12FB09A0'
$TestSelector = 'M1OrderSubcaseEvidenceTest'
$ExpectedOfflineRepositoryArtifactCount = 990
$ExpectedOfflineRepositoryAggregate = 'E2E9A52D9CC046F9156547DFF613752D5AA38C596F6E408804E0C33977DAECE7'
$ExpectedSpringBootParentSha = 'D00E26B8F354697F9F134822AF44C8CC79B5F7B553F8BB1043220BBDC11462C2'
$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Get-Sha256 {
    param([Parameter(Mandatory = $true)][string]$Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToUpperInvariant()
}

function Get-TextSha256 {
    param([Parameter(Mandatory = $true)][string]$Text)
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        $bytes = $Utf8NoBom.GetBytes($Text)
        return ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-', '')
    }
    finally {
        $sha.Dispose()
    }
}

function Write-AtomicUtf8Json {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)]$Value
    )
    $parent = Split-Path -Parent $Path
    if (-not (Test-Path -LiteralPath $parent -PathType Container)) {
        [IO.Directory]::CreateDirectory($parent) | Out-Null
    }
    $temporary = "$Path.tmp"
    [IO.File]::WriteAllText($temporary, (($Value | ConvertTo-Json -Depth 30) + "`n"), $Utf8NoBom)
    if (Test-Path -LiteralPath $Path) {
        [IO.File]::Delete($Path)
    }
    [IO.File]::Move($temporary, $Path)
}

function Assert-FileSha {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Expected
    )
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "BLOCKED_VERSION_DRIFT|MISSING_FILE|$Path"
    }
    $actual = Get-Sha256 -Path $Path
    if ($actual -ne $Expected.ToUpperInvariant()) {
        throw "BLOCKED_VERSION_DRIFT|SHA_MISMATCH|$Path|$actual"
    }
}

function Invoke-NativeVersionProbe {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string]$ToolName
    )
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $FilePath
    $startInfo.Arguments = '-version'
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $captureEncoding = New-Object System.Text.UTF8Encoding($false)
    $startInfo.StandardOutputEncoding = $captureEncoding
    $startInfo.StandardErrorEncoding = $captureEncoding
    $startInfo.CreateNoWindow = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    try {
        if (-not $process.Start()) {
            throw "BLOCKED_TOOL_VERSION_START|$ToolName"
        }
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        $stdout = $stdoutTask.GetAwaiter().GetResult()
        $stderr = $stderrTask.GetAwaiter().GetResult()
        $exitCode = $process.ExitCode
        if ($exitCode -ne 0) {
            throw "BLOCKED_TOOL_VERSION_EXIT|$ToolName|$exitCode"
        }
        if ([string]::IsNullOrWhiteSpace($stdout) -and [string]::IsNullOrWhiteSpace($stderr)) {
            throw "BLOCKED_TOOL_VERSION_EMPTY|$ToolName"
        }
        return [pscustomobject]@{
            executable = $FilePath
            arguments = @('-version')
            exitCode = $exitCode
            stdout = $stdout
            stderr = $stderr
        }
    }
    finally {
        $process.Dispose()
    }
}

function Invoke-NativeProcessCapture {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string[]]$ArgumentList,
        [Parameter(Mandatory = $true)][string]$WorkingDirectory
    )
    foreach ($argument in $ArgumentList) {
        if ([string]::IsNullOrWhiteSpace($argument) -or $argument.IndexOfAny([char[]]@(' ', "`t", '"')) -ge 0) {
            throw "BLOCKED_NATIVE_ARGUMENT_SERIALIZATION|$argument"
        }
    }
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $FilePath
    $startInfo.Arguments = $ArgumentList -join ' '
    $startInfo.WorkingDirectory = $WorkingDirectory
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $captureEncoding = New-Object System.Text.UTF8Encoding($false)
    $startInfo.StandardOutputEncoding = $captureEncoding
    $startInfo.StandardErrorEncoding = $captureEncoding
    $startInfo.CreateNoWindow = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    try {
        if (-not $process.Start()) {
            throw 'BLOCKED_TARGET_PROCESS_START'
        }
        $targetProcessId = $process.Id
        $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
        $ownerSid = $identity.User.Value
        $ownerName = $identity.Name
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        return [pscustomobject]@{
            processId = $targetProcessId
            processOwnerSid = $ownerSid
            processOwnerName = $ownerName
            exitCode = $process.ExitCode
            stdout = $stdoutTask.GetAwaiter().GetResult()
            stderr = $stderrTask.GetAwaiter().GetResult()
        }
    }
    finally {
        $process.Dispose()
    }
}

function Assert-ApprovedOfflineRepository {
    param(
        [Parameter(Mandatory = $true)][string]$RepositoryPath,
        [Parameter(Mandatory = $true)][int]$ExpectedArtifactCount,
        [Parameter(Mandatory = $true)][string]$ExpectedAggregate,
        [Parameter(Mandatory = $true)][string]$ExpectedParentSha
    )
    if (-not (Test-Path -LiteralPath $RepositoryPath -PathType Container)) {
        throw 'HOST_PRELAUNCH_BLOCKED|OFFLINE_REPOSITORY_MISSING'
    }
    $parentPath = Join-Path $RepositoryPath `
        'org/springframework/boot/spring-boot-starter-parent/3.5.16/spring-boot-starter-parent-3.5.16.pom'
    if (-not (Test-Path -LiteralPath $parentPath -PathType Leaf)) {
        throw 'HOST_PRELAUNCH_BLOCKED|SPRING_BOOT_PARENT_3_5_16_MISSING'
    }
    if ((Get-Sha256 -Path $parentPath) -ne $ExpectedParentSha.ToUpperInvariant()) {
        throw 'HOST_PRELAUNCH_BLOCKED|SPRING_BOOT_PARENT_3_5_16_DRIFT'
    }
    $relativePaths = @(Get-ChildItem -LiteralPath $RepositoryPath -Recurse -File |
        Where-Object { $_.Extension -eq '.pom' -or $_.Extension -eq '.jar' } |
        ForEach-Object { $_.FullName.Substring($RepositoryPath.Length + 1).Replace('\', '/') })
    [Array]::Sort($relativePaths, [StringComparer]::Ordinal)
    if ($relativePaths.Count -ne $ExpectedArtifactCount) {
        throw "HOST_PRELAUNCH_BLOCKED|OFFLINE_REPOSITORY_ARTIFACT_COUNT|$($relativePaths.Count)"
    }
    $lines = foreach ($relativePath in $relativePaths) {
        $absolutePath = Join-Path $RepositoryPath $relativePath.Replace('/', [IO.Path]::DirectorySeparatorChar)
        "$relativePath|$(Get-Sha256 -Path $absolutePath)"
    }
    $aggregate = Get-TextSha256 -Text ($lines -join "`n")
    if ($aggregate -ne $ExpectedAggregate.ToUpperInvariant()) {
        throw "HOST_PRELAUNCH_BLOCKED|OFFLINE_REPOSITORY_AGGREGATE|$aggregate"
    }
    return [pscustomobject]@{
        repositoryPath = [IO.Path]::GetFullPath($RepositoryPath)
        artifactCount = $relativePaths.Count
        aggregateSha256 = $aggregate
        springBootParentSha256 = Get-Sha256 -Path $parentPath
        networkMode = 'MAVEN_OFFLINE'
    }
}

function Get-ProhibitedHitCount {
    param(
        [Parameter(Mandatory = $true)][string]$Text,
        [Parameter(Mandatory = $true)][string[]]$Markers
    )
    $hits = 0
    foreach ($marker in $Markers) {
        $offset = 0
        while (($offset = $Text.IndexOf($marker, $offset, [StringComparison]::Ordinal)) -ge 0) {
            $hits++
            $offset += $marker.Length
        }
    }
    return $hits
}

function Get-ImplementationAggregate {
    param([Parameter(Mandatory = $true)][string]$RepositoryRoot)
    $paths = @(
        'apps/api/src/main/java/com/huarenzaimeng/api/FlowMapper.java',
        'apps/api/src/main/java/com/huarenzaimeng/api/FlowStore.java',
        'apps/api/src/main/java/com/huarenzaimeng/api/InMemoryFlowStore.java',
        'apps/api/src/main/java/com/huarenzaimeng/api/LocalSyntheticOrderRecoveryService.java',
        'apps/api/src/main/java/com/huarenzaimeng/api/MockFlowController.java',
        'apps/api/src/main/java/com/huarenzaimeng/api/MockFlowService.java',
        'apps/api/src/main/java/com/huarenzaimeng/api/MyBatisFlowStore.java',
        'apps/api/src/main/java/com/huarenzaimeng/api/OrderCreationDomain.java',
        'apps/api/src/main/resources/db/migration/V5__tighten_order_command_idempotency_scope.sql',
        'apps/api/src/main/resources/db/migration/V5_ORDER_COMMAND_ALIAS_FORWARD_RUNBOOK.md',
        'apps/api/src/test/java/com/huarenzaimeng/api/MockFlowApiContractTest.java',
        'apps/api/src/test/java/com/huarenzaimeng/api/MockFlowServiceTest.java',
        'apps/api/src/test/java/com/huarenzaimeng/api/OrderCreationApiContractTest.java',
        'apps/api/src/test/java/com/huarenzaimeng/api/OrderCreationMigrationContractTest.java',
        'apps/api/src/test/java/com/huarenzaimeng/api/OrderRecoveryApiContractTest.java'
    )
    $lines = foreach ($relativePath in $paths) {
        $absolutePath = Join-Path $RepositoryRoot $relativePath
        if (-not (Test-Path -LiteralPath $absolutePath -PathType Leaf)) {
            throw "BLOCKED_VERSION_DRIFT|MISSING_IMPLEMENTATION_FILE|$relativePath"
        }
        "$relativePath|$(Get-Sha256 -Path $absolutePath)"
    }
    return [pscustomobject]@{
        Paths = $paths
        Lines = $lines
        Aggregate = Get-TextSha256 -Text ($lines -join "`n")
    }
}

function Write-BlockedDiagnostic {
    param(
        [string]$StagingPath,
        [string]$Reason,
        [string]$Run,
        [datetimeoffset]$StartedAt
    )
    if ([string]::IsNullOrWhiteSpace($StagingPath) -or
            -not (Test-Path -LiteralPath $StagingPath -PathType Container)) {
        return
    }
    foreach ($name in @('READY', 'evidence-package-index.json', 'evidence-package-manifest.json')) {
        $candidate = Join-Path $StagingPath $name
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            Remove-Item -LiteralPath $candidate -Force
        }
    }
    $diagnostic = [ordered]@{
        runId = $Run
        executionStatus = 'BLOCKED'
        consumable = $false
        readyWritten = $false
        reason = $Reason
        startedAt = $StartedAt.ToString('o')
        endedAt = [DateTimeOffset]::UtcNow.ToString('o')
    }
    Write-AtomicUtf8Json -Path (Join-Path $StagingPath 'BLOCKED.json') -Value $diagnostic
}

if (-not $FinalRun.IsPresent -or $Confirm -ne 'FINAL_RUN') {
    throw 'FINAL_RUN_AUTHORIZATION_REQUIRED'
}

$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$GeneratorPath = Join-Path $RepositoryRoot 'apps/api/src/test/java/com/huarenzaimeng/api/M1OrderSubcaseEvidenceTest.java'
$WrapperPath = $PSCommandPath
$ApprovedOfflineRepository = Join-Path $RepositoryRoot '.m2-local'
$EvidenceRoot = Join-Path $RepositoryRoot 'apps/api/target/m1-subcase-evidence'
$RunsRoot = Join-Path $EvidenceRoot 'runs'
$StagingRoot = Join-Path $EvidenceRoot 'staging'
$AuthorizationRoot = Join-Path $EvidenceRoot 'authorizations'
$FinalDirectory = Join-Path $RunsRoot $RunId
$AuthorizationFile = Join-Path $AuthorizationRoot "$RunId.json"
$StagingDirectory = $null
$RunStartedAt = [DateTimeOffset]::UtcNow

try {
    # Read-only preflight. No evidence or authorization directory may be created before all fixed bytes match.
    Assert-FileSha -Path $GeneratorPath -Expected $ExpectedGeneratorSha256
    $generatorSource = Get-Content -LiteralPath $GeneratorPath -Raw -Encoding UTF8
    $fixedSubcaseIds = @(
        'M1-ORD-002-RECEIVER-REPLAY', 'M1-ORD-005-EXPIRED-QUOTE',
        'M1-ORD-006-LEGACY-V4-NULL', 'M1-ORD-007-SUPPORT-VERSION-DRIFT',
        'M1-ORD-008-CATALOG-VERSION-DRIFT', 'M1-ORD-009-CROSS-SUBJECT',
        'M1-ORD-010-MISSING-QUOTEREF', 'M1-ORD-011-MISSING-COMMANDID',
        'M1-ORD-012-MISSING-IDEMPOTENCYKEY', 'M1-ORD-013-INVALID-CREATION-PRECONDITION',
        'M1-ORD-014-MISSING-SNAPSHOT-FIELD', 'M1-ORD-014A-MISSING-SESSIONVERSION',
        'M1-ORD-014B-MISSING-AUTHSETREF', 'M1-ORD-014C-STALE-SESSIONVERSION',
        'M1-ORD-014D-WRONG-AUTHSETREF', 'M1-ORD-014E-REVOKED-OR-EVIDENCE-MISMATCH',
        'M1-ORD-014F-A-NONLOCAL-PROFILE', 'M1-ORD-014F-B-RELEASE-HIT',
        'M1-ORD-014F-C-NO-TEST-TOKEN', 'M1-ORD-015-CLIENT-AMOUNT-TAMPER',
        'M1-ORD-016-CLIENT-CURRENCY-TAMPER', 'M1-ORD-017-CLIENT-PRODUCT-TAMPER',
        'M1-ORD-018-CLIENT-DENOMINATION-TAMPER'
    )
    if ([regex]::Matches($generatorSource, '@Test\s+void\s+M1_ORD_').Count -ne 23 -or
            [regex]::Matches($generatorSource, 'register\(definitions').Count -ne 23) {
        throw 'BLOCKED_VERSION_DRIFT|STABLE_SUBCASE_DENOMINATOR'
    }
    foreach ($subcaseId in $fixedSubcaseIds) {
        if ([regex]::Matches($generatorSource, [regex]::Escape('"' + $subcaseId + '"')).Count -lt 2) {
            throw "BLOCKED_VERSION_DRIFT|STABLE_SUBCASE_ID|$subcaseId"
        }
    }
    $option = [Text.RegularExpressions.RegexOptions]::Singleline
    $parameterBindings = @(
        'M1-ORD-014F-A-NONLOCAL-PROFILE"\s*,\s*"NONLOCAL_PROFILE',
        'M1-ORD-014F-B-RELEASE-HIT"\s*,\s*"RELEASE_HIT',
        'M1-ORD-014F-C-NO-TEST-TOKEN"\s*,\s*"NO_TRUSTED_TEST_TOKEN'
    )
    foreach ($binding in $parameterBindings) {
        $pattern = 'register\(definitions,\s*"M1-S05",\s*"M1-ORD-014F-NONLOCAL-OR-RELEASE",\s*"' + $binding + '"\s*\)'
        if (-not [regex]::IsMatch($generatorSource, $pattern, $option)) {
            throw "BLOCKED_VERSION_DRIFT|014F_PARAMETER_BINDING|$binding"
        }
    }
    Assert-FileSha -Path (Join-Path $RepositoryRoot '项目管理/正式交付/D1-产品规划与需求/D1产品基线版本清单.md') -Expected $ExpectedD1Sha
    Assert-FileSha -Path (Join-Path $RepositoryRoot '项目管理/正式交付/D2-体验与UI设计/D2体验与UI设计版本清单.md') -Expected $ExpectedD2Sha
    Assert-FileSha -Path (Join-Path $RepositoryRoot '项目管理/正式交付/D3-技术实现基线/D3技术基线版本清单.md') -Expected $ExpectedD3RegistrySha
    Assert-FileSha -Path (Join-Path $RepositoryRoot '项目管理/正式交付/D3-技术实现基线/D3-03-领域状态与项目API协议.md') -Expected $ExpectedD303Sha
    Assert-FileSha -Path (Join-Path $RepositoryRoot '项目管理/正式交付/D3-技术实现基线/D3-05-安全可靠性与开发门禁.md') -Expected $ExpectedD305Sha
    Assert-FileSha -Path (Join-Path $RepositoryRoot '项目管理/正式交付/D4-开发计划与工程准备/D5-M1报价后稳定建单本地质量矩阵.md') -Expected $ExpectedMatrixSha
    $implementation = Get-ImplementationAggregate -RepositoryRoot $RepositoryRoot
    if ($implementation.Aggregate -ne $ExpectedImplementationAggregate) {
        throw "BLOCKED_VERSION_DRIFT|IMPLEMENTATION_AGGREGATE|$($implementation.Aggregate)"
    }
    $offlineRepository = Assert-ApprovedOfflineRepository -RepositoryPath $ApprovedOfflineRepository `
        -ExpectedArtifactCount $ExpectedOfflineRepositoryArtifactCount `
        -ExpectedAggregate $ExpectedOfflineRepositoryAggregate `
        -ExpectedParentSha $ExpectedSpringBootParentSha
    if ((Test-Path -LiteralPath $FinalDirectory) -or (Test-Path -LiteralPath $AuthorizationFile)) {
        throw 'FINAL_RUN_ALREADY_CONSUMED_OR_RUNID_EXISTS'
    }
    if (Test-Path -LiteralPath $StagingRoot -PathType Container) {
        $sameRun = @(Get-ChildItem -LiteralPath $StagingRoot -Directory |
            Where-Object { $_.Name.StartsWith("$RunId-", [StringComparison]::Ordinal) })
        if ($sameRun.Count -gt 0) {
            throw 'FINAL_RUN_ALREADY_CONSUMED_OR_STAGING_EXISTS'
        }
    }

    [IO.Directory]::CreateDirectory($RunsRoot) | Out-Null
    [IO.Directory]::CreateDirectory($StagingRoot) | Out-Null
    [IO.Directory]::CreateDirectory($AuthorizationRoot) | Out-Null
    $StagingDirectory = Join-Path $StagingRoot "$RunId-$([Guid]::NewGuid().ToString('N'))"
    [IO.Directory]::CreateDirectory($StagingDirectory) | Out-Null
    $ProcessDirectory = Join-Path $StagingDirectory 'process'
    [IO.Directory]::CreateDirectory($ProcessDirectory) | Out-Null

    $wrapperSha = Get-Sha256 -Path $WrapperPath
    $authorization = [ordered]@{
        runId = $RunId
        finalRunAuthorized = $true
        authorizationConsumed = $true
        consumedAt = [DateTimeOffset]::UtcNow.ToString('o')
        testSelector = $TestSelector
        fixedCommandSelector = '-Dtest=M1OrderSubcaseEvidenceTest'
        expectedGeneratorSha256 = $ExpectedGeneratorSha256.ToUpperInvariant()
        wrapperSha256 = $wrapperSha
        stagingDirectory = [IO.Path]::GetFullPath($StagingDirectory)
    }
    $authorizationJson = ($authorization | ConvertTo-Json -Depth 10) + "`n"
    $authorizationBytes = $Utf8NoBom.GetBytes($authorizationJson)
    $authorizationStream = [IO.File]::Open($AuthorizationFile, [IO.FileMode]::CreateNew,
        [IO.FileAccess]::Write, [IO.FileShare]::None)
    try {
        $authorizationStream.Write($authorizationBytes, 0, $authorizationBytes.Length)
        $authorizationStream.Flush($true)
    }
    finally {
        $authorizationStream.Dispose()
    }
    $authorizationSha = Get-Sha256 -Path $AuthorizationFile

    $maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($null -eq $maven) {
        $maven = Get-Command mvn -ErrorAction Stop
    }
    $java = Get-Command java -ErrorAction Stop
    $mavenArgs = @(
        '-o',
        "-Dmaven.repo.local=$([IO.Path]::GetFullPath($ApprovedOfflineRepository))",
        '-pl', 'apps/api', '-am',
        '-Dtest=M1OrderSubcaseEvidenceTest',
        '-Dsurefire.failIfNoSpecifiedTests=false',
        '-Dm1.evidence.finalRun=true',
        "-Dm1.evidence.executionRunId=$RunId",
        "-Dm1.evidence.expectedGeneratorSha256=$($ExpectedGeneratorSha256.ToUpperInvariant())",
        "-Dm1.evidence.stagingDirectory=$([IO.Path]::GetFullPath($StagingDirectory))",
        "-Dm1.evidence.authorizationFile=$([IO.Path]::GetFullPath($AuthorizationFile))",
        "-Dm1.evidence.authorizationFileSha256=$authorizationSha",
        "-Dm1.evidence.testSelector=$TestSelector",
        'test'
    )
    $commandTokens = @($maven.Source) + $mavenArgs
    $commandText = $commandTokens -join ' '
    $mavenVersion = Invoke-NativeVersionProbe -FilePath $maven.Source -ToolName 'maven'
    $javaVersion = Invoke-NativeVersionProbe -FilePath $java.Source -ToolName 'java'
    $toolVersions = [ordered]@{
        powershell = $PSVersionTable.PSVersion.ToString()
        maven = $mavenVersion
        java = $javaVersion
        offlineRepository = $offlineRepository
    }
    Write-AtomicUtf8Json -Path (Join-Path $ProcessDirectory 'tool-versions.json') -Value $toolVersions

    $stdoutPath = Join-Path $ProcessDirectory 'stdout.log'
    $stderrPath = Join-Path $ProcessDirectory 'stderr.log'
    $processStartedAt = [DateTimeOffset]::UtcNow
    $processResult = Invoke-NativeProcessCapture -FilePath $maven.Source -ArgumentList $mavenArgs `
        -WorkingDirectory $RepositoryRoot
    $processEndedAt = [DateTimeOffset]::UtcNow
    [IO.File]::WriteAllText($stdoutPath, $processResult.stdout, $Utf8NoBom)
    [IO.File]::WriteAllText($stderrPath, $processResult.stderr, $Utf8NoBom)

    $captureComplete = (Test-Path -LiteralPath $stdoutPath -PathType Leaf) -and
        (Test-Path -LiteralPath $stderrPath -PathType Leaf) -and
        -not [string]::IsNullOrWhiteSpace($commandText) -and
        $mavenVersion.exitCode -eq 0 -and
        (-not [string]::IsNullOrWhiteSpace($mavenVersion.stdout) -or -not [string]::IsNullOrWhiteSpace($mavenVersion.stderr)) -and
        $javaVersion.exitCode -eq 0 -and
        (-not [string]::IsNullOrWhiteSpace($javaVersion.stdout) -or -not [string]::IsNullOrWhiteSpace($javaVersion.stderr))
    $processEvidence = [ordered]@{
        runId = $RunId
        command = $commandText
        commandTokens = $commandTokens
        processId = $processResult.processId
        processOwnerSid = $processResult.processOwnerSid
        processOwnerName = $processResult.processOwnerName
        startedAt = $processStartedAt.ToString('o')
        endedAt = $processEndedAt.ToString('o')
        exitCode = $processResult.exitCode
        stdoutPath = 'process/stdout.log'
        stdoutSha256 = if (Test-Path -LiteralPath $stdoutPath) { Get-Sha256 -Path $stdoutPath } else { $null }
        stderrPath = 'process/stderr.log'
        stderrSha256 = if (Test-Path -LiteralPath $stderrPath) { Get-Sha256 -Path $stderrPath } else { $null }
        toolVersionsPath = 'process/tool-versions.json'
        toolVersionsSha256 = Get-Sha256 -Path (Join-Path $ProcessDirectory 'tool-versions.json')
        wrapperSha256 = $wrapperSha
        generatorSha256 = $ExpectedGeneratorSha256.ToUpperInvariant()
        implementationAggregateSha256 = $implementation.Aggregate
        authorizationFileSha256 = $authorizationSha
        outputCaptureComplete = $captureComplete
    }
    Write-AtomicUtf8Json -Path (Join-Path $ProcessDirectory 'process-evidence.json') -Value $processEvidence
    if (-not $captureComplete) {
        throw 'BLOCKED_OUTPUT_CAPTURE_LOST'
    }
    if ($processResult.exitCode -ne 0) {
        throw "BLOCKED_TARGET_PROCESS_EXIT|$($processResult.exitCode)"
    }

    $generatorValidationPath = Join-Path $StagingDirectory 'generator-validation.json'
    if (-not (Test-Path -LiteralPath $generatorValidationPath -PathType Leaf)) {
        throw 'BLOCKED_GENERATOR_VALIDATION_MISSING'
    }
    $generatorValidation = Get-Content -LiteralPath $generatorValidationPath -Raw -Encoding UTF8 |
        ConvertFrom-Json
    if ($generatorValidation.executionStatus -ne 'PASS' -or
            $generatorValidation.candidateConsumable -ne $false -or
            $generatorValidation.actualEvidenceFileCount -ne 23 -or
            $generatorValidation.exact23NoExtras -ne $true -or
            $generatorValidation.allCasesPass -ne $true -or
            $generatorValidation.sixStateTotalEquals23 -ne $true -or
            $generatorValidation.caseSchemaAndAssertionBindingValid -ne $true -or
            $generatorValidation.prohibitedPayloadHitCount -ne 0 -or
            $generatorValidation.prohibitedScanCanarySensitive -ne $true) {
        throw 'BLOCKED_GENERATOR_VALIDATION_FAILED'
    }
    $stateTotal = [int]$generatorValidation.statusCounts.PASS +
        [int]$generatorValidation.statusCounts.FAIL +
        [int]$generatorValidation.statusCounts.BLOCKED +
        [int]$generatorValidation.statusCounts.SKIPPED +
        [int]$generatorValidation.statusCounts.NA +
        [int]$generatorValidation.statusCounts.NOT_RUN
    if ($stateTotal -ne 23 -or [int]$generatorValidation.statusCounts.PASS -ne 23) {
        throw 'BLOCKED_SIX_STATE_INVARIANT'
    }

    $caseFiles = @(Get-ChildItem -LiteralPath (Join-Path $StagingDirectory 'cases') -File -Filter '*.json')
    if ($caseFiles.Count -ne 23) {
        throw "BLOCKED_CASE_DENOMINATOR|$($caseFiles.Count)"
    }

    $markers = @(
        'local-synthetic-order-creation-secret',
        'client_secret', 'access_token', 'refresh_token',
        '"openid"', '"unionid"', '"phoneNumber"', '"mobileNumber"'
    )
    $canary = "M1-WRAPPER-SCAN-CANARY-$([Guid]::NewGuid().ToString('N'))"
    $canarySensitive = (Get-ProhibitedHitCount -Text "prefix|$canary|suffix" -Markers @($canary)) -eq 1
    if (-not $canarySensitive) {
        throw 'BLOCKED_PROHIBITED_SCAN_CANARY_INSENSITIVE'
    }

    $scanFiles = @(Get-ChildItem -LiteralPath $StagingDirectory -Recurse -File)
    $prohibitedHits = 0
    foreach ($file in $scanFiles) {
        $content = [IO.File]::ReadAllText($file.FullName)
        $prohibitedHits += Get-ProhibitedHitCount -Text $content -Markers $markers
    }
    if ($prohibitedHits -ne 0) {
        throw "BLOCKED_PROHIBITED_PAYLOAD|$prohibitedHits"
    }

    $excludedNames = @('evidence-package-index.json', 'evidence-package-manifest.json', 'READY')
    $payloadFiles = @(Get-ChildItem -LiteralPath $StagingDirectory -Recurse -File |
        Where-Object { $excludedNames -notcontains $_.Name })
    $relativePaths = @($payloadFiles | ForEach-Object {
        $_.FullName.Substring($StagingDirectory.Length + 1).Replace('\', '/')
    })
    [Array]::Sort($relativePaths, [StringComparer]::Ordinal)
    $manifestLines = foreach ($relativePath in $relativePaths) {
        $absolutePath = Join-Path $StagingDirectory $relativePath.Replace('/', [IO.Path]::DirectorySeparatorChar)
        "$relativePath|$(Get-Sha256 -Path $absolutePath)"
    }
    $packageSha = Get-TextSha256 -Text ($manifestLines -join "`n")
    $packageManifest = [ordered]@{
        runId = $RunId
        algorithm = '.NET StringComparer.Ordinal; relativePath|UPPERCASE_SHA256; UTF-8 no BOM; LF; no terminal LF'
        payloadCount = $manifestLines.Count
        payload = $manifestLines
        packageSha256 = $packageSha
    }
    $packageManifestPath = Join-Path $StagingDirectory 'evidence-package-manifest.json'
    Write-AtomicUtf8Json -Path $packageManifestPath -Value $packageManifest

    $index = [ordered]@{
        evidencePackageId = 'D5-M1-BE-LOCAL-SYNTHETIC'
        runId = $RunId
        executionStatus = 'PASS'
        consumable = $true
        ready = $true
        testSelector = $TestSelector
        generatorSha256 = $ExpectedGeneratorSha256.ToUpperInvariant()
        wrapperSha256 = $wrapperSha
        matrixInputSha256 = $ExpectedMatrixSha
        d1Sha256 = $ExpectedD1Sha
        d2Sha256 = $ExpectedD2Sha
        d3RegistrySha256 = $ExpectedD3RegistrySha
        d3_03Sha256 = $ExpectedD303Sha
        d3_05Sha256 = $ExpectedD305Sha
        implementationAggregateSha256 = $implementation.Aggregate
        stableEvidenceCount = 23
        statusCounts = $generatorValidation.statusCounts
        packageSha256 = $packageSha
        packageManifestSha256 = Get-Sha256 -Path $packageManifestPath
        processEvidenceSha256 = Get-Sha256 -Path (Join-Path $ProcessDirectory 'process-evidence.json')
        prohibitedPayloadHitCount = 0
        prohibitedScanCanarySensitive = $true
        prohibitedScanCanarySha256 = Get-TextSha256 -Text $canary
        notRunBoundaries = [ordered]@{
            realMySql = 'NOT_RUN'
            crossProcessConcurrency = 'NOT_RUN'
            externalNetwork = 'NOT_RUN'
            wechat = 'NOT_RUN'
            realIdentity = 'NOT_RUN'
            realFunds = 'NOT_RUN'
        }
    }
    $indexPath = Join-Path $StagingDirectory 'evidence-package-index.json'
    Write-AtomicUtf8Json -Path $indexPath -Value $index

    foreach ($artifact in @($packageManifestPath, $indexPath)) {
        $artifactText = [IO.File]::ReadAllText($artifact)
        if ((Get-ProhibitedHitCount -Text $artifactText -Markers $markers) -ne 0) {
            throw 'BLOCKED_PROHIBITED_PAYLOAD_IN_PACKAGE_METADATA'
        }
    }
    $ready = [ordered]@{
        runId = $RunId
        executionStatus = 'PASS'
        consumable = $true
        indexSha256 = Get-Sha256 -Path $indexPath
        packageSha256 = $packageSha
    }
    $readyPath = Join-Path $StagingDirectory 'READY'
    Write-AtomicUtf8Json -Path $readyPath -Value $ready
    if ((Get-ProhibitedHitCount -Text ([IO.File]::ReadAllText($readyPath)) -Markers $markers) -ne 0) {
        throw 'BLOCKED_PROHIBITED_PAYLOAD_IN_READY'
    }

    if (Test-Path -LiteralPath $FinalDirectory) {
        throw 'FINAL_RUN_DIRECTORY_APPEARED_BEFORE_PUBLISH'
    }
    [IO.Directory]::Move($StagingDirectory, $FinalDirectory)
    $StagingDirectory = $null
    Write-Output "M1_FINAL_EVIDENCE_READY|$RunId|$packageSha|$FinalDirectory"
    exit 0
}
catch {
    $reason = $_.Exception.Message
    if (-not [string]::IsNullOrWhiteSpace($StagingDirectory)) {
        $processEvidenceCandidate = Join-Path $StagingDirectory 'process/process-evidence.json'
        $stdoutCandidate = Join-Path $StagingDirectory 'process/stdout.log'
        $stderrCandidate = Join-Path $StagingDirectory 'process/stderr.log'
        if (-not (Test-Path -LiteralPath $processEvidenceCandidate -PathType Leaf) -or
                -not (Test-Path -LiteralPath $stdoutCandidate -PathType Leaf) -or
                -not (Test-Path -LiteralPath $stderrCandidate -PathType Leaf)) {
            $reason = "BLOCKED_OUTPUT_CAPTURE_LOST|$reason"
        }
    }
    Write-BlockedDiagnostic -StagingPath $StagingDirectory -Reason $reason -Run $RunId -StartedAt $RunStartedAt
    [Console]::Error.WriteLine($reason)
    exit 1
}
