[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [switch]$FinalRun,

    [Parameter(Mandatory = $true)]
    [ValidateSet('FINAL_RUN')]
    [string]$Confirm,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^P001-BE-[A-Za-z0-9][A-Za-z0-9._-]{7,80}$')]
    [string]$RunId,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Fa-f0-9]{64}$')]
    [string]$ExpectedGeneratorSha256
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ExpectedBackendAggregate = '601E483E2A4C925EBE7BEE30385260019232496512769165721BC2FFE870ACA0'
$ExpectedFrontendAggregate = 'FAD7562EFAA9A44BC634FA3778A1C4D384DFDB9837F54DB7737CCEBA5AF840DC'
$ExpectedCrossStackAggregate = 'F8DA7206EFD2F8C6C8324384FB9D0769867963DEE4561C3095817350B66054C0'
$ExpectedD1Sha = 'F2CA50A6C8EAD6463753E33B50A819EE51DA69117D7EDDCE9A54EC4A8F785538'
$ExpectedD2Sha = 'A87E8D7BFE6D8AF421BF87421849D6065DE96EB160A37F2DCCD2AF2EA59A8E82'
$ExpectedD3RegistrySha = '492D687679228FFA2107C4BC407C156820EED05DF3A6E4950C7599E2AC0BAF87'
$ExpectedD303Sha = '4976103492A1E0B906BEDEB44B86C15BA8B5ED5586735389D237CC630CCD5339'
$ExpectedD304Sha = '1590FA192D2FA2B71B40AAF80794F5D6BED7E5CBE1A6B0497C816EBAFA384015'
$ExpectedD305Sha = '328F45D54215DA54C601CAA0F8FD1A24B3B1D63E16BA60D19A571FFF08DF850E'
$TestSelector = 'TemporalOverviewEvidenceFinalRunTest'
$ExpectedOfflineRepositoryArtifactCount = 990
$ExpectedOfflineRepositoryAggregate = 'E2E9A52D9CC046F9156547DFF613752D5AA38C596F6E408804E0C33977DAECE7'
$ExpectedSpringBootParentSha = 'D00E26B8F354697F9F134822AF44C8CC79B5F7B553F8BB1043220BBDC11462C2'
$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)

$BackendProductionPaths = @(
    'apps/api/src/main/java/com/huarenzaimeng/api/TemporalOverviewController.java',
    'apps/api/src/main/java/com/huarenzaimeng/api/TemporalOverviewDomain.java',
    'apps/api/src/main/java/com/huarenzaimeng/api/TemporalOverviewService.java',
    'apps/api/src/main/java/com/huarenzaimeng/api/TemporalOverviewSideEffectProbe.java',
    'apps/api/src/main/java/com/huarenzaimeng/api/config/TestAccessTokenFilter.java',
    'apps/api/src/main/resources/application-mock.yml',
    'apps/api/src/main/resources/application-release-mysql.yml',
    'apps/api/src/main/resources/application.yml'
)
$FrontendProductionPaths = @(
    'apps/miniapp/scripts/assert-frontend-contracts.mjs',
    'apps/miniapp/scripts/verify-mp-weixin-output.mjs',
    'apps/miniapp/src/api/client.ts',
    'apps/miniapp/src/api/mock.ts',
    'apps/miniapp/src/api/temporal-overview-contract.ts',
    'apps/miniapp/src/domain/temporal-overview-flow.ts',
    'apps/miniapp/src/pages/index/index.vue'
)
$FixedCaseIds = @(
    'P001-TEMP-001-NORMAL-DAY',
    'P001-TEMP-002-CROSS-DATE',
    'P001-TEMP-003-DEVICE-TZ-NON-IMPACT',
    'P001-TEMP-004-DHAKA-UNAVAILABLE',
    'P001-TEMP-005-BEIJING-UNAVAILABLE',
    'P001-TEMP-006-BOTH-UNAVAILABLE',
    'P001-TEMP-007-STALE',
    'P001-TEMP-008-RECOVERED-CLIENT-ONCE',
    'P001-TEMP-009-CN-NO-HOLIDAY',
    'P001-TEMP-010-BD-NO-HOLIDAY',
    'P001-TEMP-011-CN-CONFIRMED-HOLIDAY',
    'P001-TEMP-012-BD-CONFIRMED-HOLIDAY',
    'P001-TEMP-013-PENDING-CONFIRMATION',
    'P001-TEMP-014-READ-ERROR',
    'P001-TEMP-015-STALE-EXPIRED',
    'P001-TEMP-016-UNPUBLISHED',
    'P001-TEMP-017-CRITICAL-UNKNOWN-FAIL-CLOSED',
    'P001-TEMP-018-ANONYMOUS-MINIMAL-ZERO-SIDE-EFFECT'
)

function Get-Sha256 {
    param([Parameter(Mandatory = $true)][string]$Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToUpperInvariant()
}

function Get-TextSha256 {
    param([Parameter(Mandatory = $true)][string]$Text)
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        return ([BitConverter]::ToString($sha.ComputeHash($Utf8NoBom.GetBytes($Text)))).Replace('-', '')
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
    [IO.File]::WriteAllText($temporary, (($Value | ConvertTo-Json -Depth 40) + "`n"), $Utf8NoBom)
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

function Assert-UniqueFileBySha {
    param(
        [Parameter(Mandatory = $true)][string]$SearchRoot,
        [Parameter(Mandatory = $true)][string]$FilePattern,
        [Parameter(Mandatory = $true)][string]$Expected
    )
    $matches = @(Get-ChildItem -LiteralPath $SearchRoot -Recurse -File -Filter $FilePattern |
        Where-Object { (Get-Sha256 -Path $_.FullName) -eq $Expected.ToUpperInvariant() })
    if ($matches.Count -ne 1) {
        throw "BLOCKED_VERSION_DRIFT|BASELINE_LOOKUP|$FilePattern|$($matches.Count)"
    }
    return $matches[0].FullName
}

function Get-PathAggregate {
    param(
        [Parameter(Mandatory = $true)][string]$RepositoryRoot,
        [Parameter(Mandatory = $true)][string[]]$Paths,
        [string]$ManifestPathPrefixToTrim = ''
    )
    $orderedPaths = @($Paths)
    [Array]::Sort($orderedPaths, [StringComparer]::Ordinal)
    $lines = foreach ($relativePath in $orderedPaths) {
        $absolutePath = Join-Path $RepositoryRoot $relativePath
        if (-not (Test-Path -LiteralPath $absolutePath -PathType Leaf)) {
            throw "BLOCKED_VERSION_DRIFT|MISSING_IMPLEMENTATION_FILE|$relativePath"
        }
        $manifestPath = $relativePath
        if (-not [string]::IsNullOrEmpty($ManifestPathPrefixToTrim)) {
            if (-not $relativePath.StartsWith($ManifestPathPrefixToTrim, [StringComparison]::Ordinal)) {
                throw "BLOCKED_VERSION_DRIFT|MANIFEST_PATH_PREFIX|$relativePath"
            }
            $manifestPath = $relativePath.Substring($ManifestPathPrefixToTrim.Length)
        }
        "$manifestPath|$(Get-Sha256 -Path $absolutePath)"
    }
    return [pscustomobject]@{
        paths = $orderedPaths
        manifestLines = @($lines)
        aggregateSha256 = Get-TextSha256 -Text ($lines -join "`n")
        algorithm = '.NET StringComparer.Ordinal; relativePath|UPPERCASE_SHA256; UTF-8 no BOM; LF; no terminal LF'
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
        if ($process.ExitCode -ne 0) {
            throw "BLOCKED_TOOL_VERSION_EXIT|$ToolName|$($process.ExitCode)"
        }
        if ([string]::IsNullOrWhiteSpace($stdout) -and [string]::IsNullOrWhiteSpace($stderr)) {
            throw "BLOCKED_TOOL_VERSION_EMPTY|$ToolName"
        }
        return [pscustomobject]@{
            executable = $FilePath
            arguments = @('-version')
            exitCode = $process.ExitCode
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
        $processId = $process.Id
        $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        return [pscustomobject]@{
            processId = $processId
            processOwnerSid = $identity.User.Value
            processOwnerName = $identity.Name
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
    Assert-FileSha -Path $parentPath -Expected $ExpectedParentSha
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
        while (($offset = $Text.IndexOf($marker, $offset, [StringComparison]::OrdinalIgnoreCase)) -ge 0) {
            $hits++
            $offset += $marker.Length
        }
    }
    return $hits
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
    Write-AtomicUtf8Json -Path (Join-Path $StagingPath 'BLOCKED.json') -Value ([ordered]@{
        runId = $Run
        executionStatus = 'BLOCKED'
        consumable = $false
        readyWritten = $false
        reason = $Reason
        automaticRetryAllowed = $false
        startedAt = $StartedAt.ToString('o')
        endedAt = [DateTimeOffset]::UtcNow.ToString('o')
    })
}

if (-not $FinalRun.IsPresent -or $Confirm -ne 'FINAL_RUN') {
    throw 'FINAL_RUN_AUTHORIZATION_REQUIRED'
}

$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$GeneratorPath = Join-Path $RepositoryRoot 'apps/api/src/test/java/com/huarenzaimeng/api/TemporalOverviewEvidenceFinalRunTest.java'
$WrapperPath = $PSCommandPath
$ApprovedOfflineRepository = Join-Path $RepositoryRoot '.m2-local'
$EvidenceRoot = Join-Path $RepositoryRoot 'apps/api/target/p001-temporal-evidence'
$RunsRoot = Join-Path $EvidenceRoot 'runs'
$StagingRoot = Join-Path $EvidenceRoot 'staging'
$AuthorizationRoot = Join-Path $EvidenceRoot 'authorizations'
$FinalDirectory = Join-Path $RunsRoot $RunId
$AuthorizationFile = Join-Path $AuthorizationRoot "$RunId.json"
$StagingDirectory = $null
$RunStartedAt = [DateTimeOffset]::UtcNow

try {
    # Read-only preflight: no target or authorization bytes exist until every fixed input matches.
    Assert-FileSha -Path $GeneratorPath -Expected $ExpectedGeneratorSha256
    $generatorSource = Get-Content -LiteralPath $GeneratorPath -Raw -Encoding UTF8
    foreach ($caseId in $FixedCaseIds) {
        if ([regex]::Matches($generatorSource, [regex]::Escape('"' + $caseId + '"')).Count -lt 1) {
            throw "BLOCKED_VERSION_DRIFT|STABLE_CASE_ID|$caseId"
        }
    }
    $allFixedIdPattern = 'P001-TEMP-(?:00[1-9]|01[0-8])-[A-Z0-9-]+'
    $sourceIds = @([regex]::Matches($generatorSource, $allFixedIdPattern) |
        ForEach-Object { $_.Value } | Sort-Object -Unique)
    if ($sourceIds.Count -ne 18) {
        throw "BLOCKED_VERSION_DRIFT|STABLE_CASE_DENOMINATOR|$($sourceIds.Count)"
    }

    # Windows PowerShell 5.1 decodes UTF-8-without-BOM scripts using the local ANSI code page.
    # Resolve the governed Chinese-path baselines by stable ASCII filename prefixes and exact SHA,
    # so the wrapper remains byte-compatible and cannot silently select a different version.
    [void](Assert-UniqueFileBySha -SearchRoot $RepositoryRoot -FilePattern 'D1*.md' -Expected $ExpectedD1Sha)
    [void](Assert-UniqueFileBySha -SearchRoot $RepositoryRoot -FilePattern 'D2*.md' -Expected $ExpectedD2Sha)
    [void](Assert-UniqueFileBySha -SearchRoot $RepositoryRoot -FilePattern 'D3*.md' -Expected $ExpectedD3RegistrySha)
    [void](Assert-UniqueFileBySha -SearchRoot $RepositoryRoot -FilePattern 'D3-03-*.md' -Expected $ExpectedD303Sha)
    [void](Assert-UniqueFileBySha -SearchRoot $RepositoryRoot -FilePattern 'D3-04-*.md' -Expected $ExpectedD304Sha)
    [void](Assert-UniqueFileBySha -SearchRoot $RepositoryRoot -FilePattern 'D3-05-*.md' -Expected $ExpectedD305Sha)

    $backend = Get-PathAggregate -RepositoryRoot $RepositoryRoot -Paths $BackendProductionPaths
    $frontend = Get-PathAggregate -RepositoryRoot $RepositoryRoot -Paths $FrontendProductionPaths `
        -ManifestPathPrefixToTrim 'apps/miniapp/'
    $crossStack = Get-PathAggregate -RepositoryRoot $RepositoryRoot -Paths @($BackendProductionPaths + $FrontendProductionPaths)
    if ($backend.aggregateSha256 -ne $ExpectedBackendAggregate) {
        throw "BLOCKED_VERSION_DRIFT|BACKEND_AGGREGATE|$($backend.aggregateSha256)"
    }
    if ($frontend.aggregateSha256 -ne $ExpectedFrontendAggregate) {
        throw "BLOCKED_VERSION_DRIFT|FRONTEND_AGGREGATE|$($frontend.aggregateSha256)"
    }
    if ($crossStack.aggregateSha256 -ne $ExpectedCrossStackAggregate) {
        throw "BLOCKED_VERSION_DRIFT|CROSS_STACK_AGGREGATE|$($crossStack.aggregateSha256)"
    }

    $offlineRepository = Assert-ApprovedOfflineRepository -RepositoryPath $ApprovedOfflineRepository `
        -ExpectedArtifactCount $ExpectedOfflineRepositoryArtifactCount `
        -ExpectedAggregate $ExpectedOfflineRepositoryAggregate `
        -ExpectedParentSha $ExpectedSpringBootParentSha
    $maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($null -eq $maven) {
        $maven = Get-Command mvn -ErrorAction Stop
    }
    $java = Get-Command java -ErrorAction Stop
    $mavenVersion = Invoke-NativeVersionProbe -FilePath $maven.Source -ToolName 'maven'
    $javaVersion = Invoke-NativeVersionProbe -FilePath $java.Source -ToolName 'java'
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
        fixedCommandSelector = '-Dtest=TemporalOverviewEvidenceFinalRunTest'
        expectedGeneratorSha256 = $ExpectedGeneratorSha256.ToUpperInvariant()
        wrapperSha256 = $wrapperSha
        stagingDirectory = [IO.Path]::GetFullPath($StagingDirectory)
        automaticRetryAllowed = $false
    }
    $authorizationBytes = $Utf8NoBom.GetBytes((($authorization | ConvertTo-Json -Depth 10) + "`n"))
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

    $mavenArgs = @(
        '-o',
        "-Dmaven.repo.local=$([IO.Path]::GetFullPath($ApprovedOfflineRepository))",
        '-pl', 'apps/api', '-am',
        '-Dtest=TemporalOverviewEvidenceFinalRunTest',
        '-Dsurefire.failIfNoSpecifiedTests=false',
        '-Dp001.evidence.finalRun=true',
        "-Dp001.evidence.executionRunId=$RunId",
        "-Dp001.evidence.expectedGeneratorSha256=$($ExpectedGeneratorSha256.ToUpperInvariant())",
        "-Dp001.evidence.expectedFrontendAggregateSha256=$($frontend.aggregateSha256)",
        "-Dp001.evidence.expectedImplementationAggregateSha256=$($crossStack.aggregateSha256)",
        "-Dp001.evidence.stagingDirectory=$([IO.Path]::GetFullPath($StagingDirectory))",
        "-Dp001.evidence.authorizationFile=$([IO.Path]::GetFullPath($AuthorizationFile))",
        "-Dp001.evidence.authorizationFileSha256=$authorizationSha",
        "-Dp001.evidence.testSelector=$TestSelector",
        'test'
    )
    $commandTokens = @($maven.Source) + $mavenArgs
    $commandText = $commandTokens -join ' '
    Write-AtomicUtf8Json -Path (Join-Path $ProcessDirectory 'tool-versions.json') -Value ([ordered]@{
        powershell = $PSVersionTable.PSVersion.ToString()
        maven = $mavenVersion
        java = $javaVersion
        offlineRepository = $offlineRepository
    })

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
        $mavenVersion.exitCode -eq 0 -and $javaVersion.exitCode -eq 0
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
        stdoutSha256 = Get-Sha256 -Path $stdoutPath
        stderrPath = 'process/stderr.log'
        stderrSha256 = Get-Sha256 -Path $stderrPath
        toolVersionsPath = 'process/tool-versions.json'
        toolVersionsSha256 = Get-Sha256 -Path (Join-Path $ProcessDirectory 'tool-versions.json')
        wrapperSha256 = $wrapperSha
        generatorSha256 = $ExpectedGeneratorSha256.ToUpperInvariant()
        backendAggregateSha256 = $backend.aggregateSha256
        frontendAggregateSha256 = $frontend.aggregateSha256
        crossStackAggregateSha256 = $crossStack.aggregateSha256
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
    $generatorValidation = Get-Content -LiteralPath $generatorValidationPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($generatorValidation.executionStatus -ne 'PASS' -or
            $generatorValidation.candidateConsumable -ne $false -or
            $generatorValidation.actualEvidenceFileCount -ne 18 -or
            $generatorValidation.exact18NoExtras -ne $true -or
            $generatorValidation.allCasesPass -ne $true -or
            $generatorValidation.sixStateTotalEquals18 -ne $true -or
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
    if ($stateTotal -ne 18 -or [int]$generatorValidation.statusCounts.PASS -ne 18) {
        throw 'BLOCKED_SIX_STATE_INVARIANT'
    }
    $caseFiles = @(Get-ChildItem -LiteralPath (Join-Path $StagingDirectory 'cases') -File -Filter '*.json')
    if ($caseFiles.Count -ne 18) {
        throw "BLOCKED_CASE_DENOMINATOR|$($caseFiles.Count)"
    }

    $markers = @(
        'client_secret', 'access_token', 'refresh_token',
        'authorization:', 'cookie:', 'set-cookie:',
        'sourceRef', 'verifiedBy', 'authorizationRef', 'evidenceRef',
        'deviceId', 'openid', 'unionid', 'phoneNumber', 'mobileNumber'
    )
    $canary = "P001-WRAPPER-SCAN-CANARY-$([Guid]::NewGuid().ToString('N'))"
    if ((Get-ProhibitedHitCount -Text "prefix|$canary|suffix" -Markers @($canary)) -ne 1) {
        throw 'BLOCKED_PROHIBITED_SCAN_CANARY_INSENSITIVE'
    }
    $prohibitedHits = 0
    foreach ($file in @(Get-ChildItem -LiteralPath $StagingDirectory -Recurse -File)) {
        $prohibitedHits += Get-ProhibitedHitCount -Text ([IO.File]::ReadAllText($file.FullName)) -Markers $markers
    }
    if ($prohibitedHits -ne 0) {
        throw "BLOCKED_PROHIBITED_PAYLOAD|$prohibitedHits"
    }

    $crossStackManifestPath = Join-Path $StagingDirectory 'cross-stack-implementation-manifest.json'
    Write-AtomicUtf8Json -Path $crossStackManifestPath -Value ([ordered]@{
        algorithm = $crossStack.algorithm
        backendPathCount = $backend.paths.Count
        frontendPathCount = $frontend.paths.Count
        totalPathCount = $crossStack.paths.Count
        backendAggregateSha256 = $backend.aggregateSha256
        frontendAggregateSha256 = $frontend.aggregateSha256
        crossStackAggregateSha256 = $crossStack.aggregateSha256
        orderedPathSha256 = $crossStack.manifestLines
    })

    $excludedNames = @('evidence-package-index.json', 'evidence-package-manifest.json', 'READY')
    $relativePaths = @(Get-ChildItem -LiteralPath $StagingDirectory -Recurse -File |
        Where-Object { $excludedNames -notcontains $_.Name } |
        ForEach-Object { $_.FullName.Substring($StagingDirectory.Length + 1).Replace('\', '/') })
    [Array]::Sort($relativePaths, [StringComparer]::Ordinal)
    $manifestLines = foreach ($relativePath in $relativePaths) {
        $absolutePath = Join-Path $StagingDirectory $relativePath.Replace('/', [IO.Path]::DirectorySeparatorChar)
        "$relativePath|$(Get-Sha256 -Path $absolutePath)"
    }
    $packageSha = Get-TextSha256 -Text ($manifestLines -join "`n")
    $packageManifestPath = Join-Path $StagingDirectory 'evidence-package-manifest.json'
    Write-AtomicUtf8Json -Path $packageManifestPath -Value ([ordered]@{
        runId = $RunId
        algorithm = '.NET StringComparer.Ordinal; relativePath|UPPERCASE_SHA256; UTF-8 no BOM; LF; no terminal LF'
        payloadCount = $manifestLines.Count
        payload = $manifestLines
        packageSha256 = $packageSha
    })

    $indexPath = Join-Path $StagingDirectory 'evidence-package-index.json'
    Write-AtomicUtf8Json -Path $indexPath -Value ([ordered]@{
        evidencePackageId = 'P001-TEMPORAL-BE-LOCAL-SYNTHETIC'
        runId = $RunId
        executionStatus = 'PASS'
        consumable = $true
        ready = $true
        testSelector = $TestSelector
        generatorSha256 = $ExpectedGeneratorSha256.ToUpperInvariant()
        wrapperSha256 = $wrapperSha
        d1Sha256 = $ExpectedD1Sha
        d2Sha256 = $ExpectedD2Sha
        d3RegistrySha256 = $ExpectedD3RegistrySha
        d3_03Sha256 = $ExpectedD303Sha
        d3_04Sha256 = $ExpectedD304Sha
        d3_05Sha256 = $ExpectedD305Sha
        backendAggregateSha256 = $backend.aggregateSha256
        frontendAggregateSha256 = $frontend.aggregateSha256
        crossStackAggregateSha256 = $crossStack.aggregateSha256
        crossStackManifestSha256 = Get-Sha256 -Path $crossStackManifestPath
        stableEvidenceCount = 18
        statusCounts = $generatorValidation.statusCounts
        packageSha256 = $packageSha
        packageManifestSha256 = Get-Sha256 -Path $packageManifestPath
        processEvidenceSha256 = Get-Sha256 -Path (Join-Path $ProcessDirectory 'process-evidence.json')
        prohibitedPayloadHitCount = 0
        prohibitedScanCanarySensitive = $true
        prohibitedScanCanarySha256 = Get-TextSha256 -Text $canary
        notRunBoundaries = [ordered]@{
            realDatabase = 'NOT_RUN'
            externalNetwork = 'NOT_RUN'
            production = 'NOT_RUN'
            externalTimeSource = 'NOT_RUN'
            externalHolidaySource = 'NOT_RUN'
            identity = 'NOT_RUN'
            notification = 'NOT_RUN'
        }
    })
    foreach ($artifact in @($crossStackManifestPath, $packageManifestPath, $indexPath)) {
        if ((Get-ProhibitedHitCount -Text ([IO.File]::ReadAllText($artifact)) -Markers $markers) -ne 0) {
            throw 'BLOCKED_PROHIBITED_PAYLOAD_IN_PACKAGE_METADATA'
        }
    }

    $readyPath = Join-Path $StagingDirectory 'READY'
    Write-AtomicUtf8Json -Path $readyPath -Value ([ordered]@{
        runId = $RunId
        executionStatus = 'PASS'
        consumable = $true
        indexSha256 = Get-Sha256 -Path $indexPath
        packageSha256 = $packageSha
    })
    if ((Get-ProhibitedHitCount -Text ([IO.File]::ReadAllText($readyPath)) -Markers $markers) -ne 0) {
        throw 'BLOCKED_PROHIBITED_PAYLOAD_IN_READY'
    }
    if (Test-Path -LiteralPath $FinalDirectory) {
        throw 'FINAL_RUN_DIRECTORY_APPEARED_BEFORE_PUBLISH'
    }
    [IO.Directory]::Move($StagingDirectory, $FinalDirectory)
    $StagingDirectory = $null
    Write-Output "P001_TEMPORAL_FINAL_EVIDENCE_READY|$RunId|$packageSha|$FinalDirectory"
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
