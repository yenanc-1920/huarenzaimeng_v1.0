[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [switch]$NonFinalSelfCheck,

    [Parameter(Mandatory = $true)]
    [ValidateSet('NONFINAL_ONLY')]
    [string]$Confirm,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^M1-WRAPPER-NONFINAL-[A-Za-z0-9._-]{8,80}$')]
    [string]$SelfCheckId
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ExpectedWrapperSha256 = '698B0591E18AFAC77640EC4BB8A955215ABAD097043AB77CD5A7512CB8986843'
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
        return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($Text)))).Replace('-', '')
    }
    finally {
        $sha.Dispose()
    }
}

function Write-Json {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)]$Value
    )
    [IO.File]::WriteAllText($Path, (($Value | ConvertTo-Json -Depth 30) + "`n"), $Utf8NoBom)
}

function Get-EntrySnapshot {
    param([Parameter(Mandatory = $true)][string]$Root)
    $result = [ordered]@{}
    foreach ($name in @('runs', 'staging', 'authorizations')) {
        $directory = Join-Path $Root $name
        $entries = if (Test-Path -LiteralPath $directory -PathType Container) {
            @(Get-ChildItem -LiteralPath $directory -Force | Sort-Object Name | ForEach-Object {
                [ordered]@{
                    name = $_.Name
                    isDirectory = $_.PSIsContainer
                    length = if ($_.PSIsContainer) { $null } else { $_.Length }
                    lastWriteTimeUtc = $_.LastWriteTimeUtc.ToString('o')
                }
            })
        }
        else {
            @()
        }
        $result[$name] = $entries
    }
    return $result
}

function Get-CanonicalJson {
    param([Parameter(Mandatory = $true)]$Value)
    return ($Value | ConvertTo-Json -Depth 30 -Compress)
}

if (-not $NonFinalSelfCheck -or $Confirm -ne 'NONFINAL_ONLY') {
    throw 'NONFINAL_SELF_CHECK_REQUIRES_EXPLICIT_CONFIRMATION'
}

$ScriptPath = $MyInvocation.MyCommand.Path
$ApiRoot = Split-Path -Parent $ScriptPath
$RepositoryRoot = [IO.Path]::GetFullPath((Join-Path $ApiRoot '../..'))
$WrapperPath = Join-Path $ApiRoot 'Invoke-M1OrderEvidenceFinalRun.ps1'
$FormalEvidenceRoot = Join-Path $ApiRoot 'target/m1-subcase-evidence'
$SelfCheckRoot = Join-Path $ApiRoot 'target/m1-process-capture-nonfinal'
$OutputDirectory = Join-Path $SelfCheckRoot $SelfCheckId

if (-not (Test-Path -LiteralPath $WrapperPath -PathType Leaf)) {
    throw 'NONFINAL_WRAPPER_MISSING'
}
if ((Get-Sha256 -Path $WrapperPath) -ne $ExpectedWrapperSha256) {
    throw 'NONFINAL_WRAPPER_SHA_MISMATCH'
}
if (Test-Path -LiteralPath $OutputDirectory) {
    throw 'NONFINAL_SELF_CHECK_ID_ALREADY_USED'
}
if ([IO.Path]::GetFullPath($OutputDirectory).StartsWith([IO.Path]::GetFullPath($FormalEvidenceRoot),
        [StringComparison]::OrdinalIgnoreCase)) {
    throw 'NONFINAL_OUTPUT_OVERLAPS_FORMAL_EVIDENCE'
}

$wrapperSource = [IO.File]::ReadAllText($WrapperPath, [Text.Encoding]::UTF8)
if ([regex]::IsMatch($wrapperSource, '(?im)\bStart-Process\b')) {
    throw 'NONFINAL_FORMAL_PATH_STILL_USES_START_PROCESS'
}
$tokens = $null
$parseErrors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile(
    $WrapperPath, [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count -ne 0) {
    throw 'NONFINAL_WRAPPER_PARSE_FAILED'
}
$captureFunction = $ast.Find({
        param($node)
        $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and
            $node.Name -eq 'Invoke-NativeProcessCapture'
    }, $true)
if ($null -eq $captureFunction) {
    throw 'NONFINAL_CAPTURE_FUNCTION_MISSING'
}
Invoke-Expression $captureFunction.Extent.Text
$repositoryFunction = $ast.Find({
        param($node)
        $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and
            $node.Name -eq 'Assert-ApprovedOfflineRepository'
    }, $true)
if ($null -eq $repositoryFunction) {
    throw 'NONFINAL_OFFLINE_REPOSITORY_FUNCTION_MISSING'
}
Invoke-Expression $repositoryFunction.Extent.Text

$offlineFlagPresent = $wrapperSource.IndexOf("'-o'", [StringComparison]::Ordinal) -ge 0
$localRepositoryBindingPresent = $wrapperSource.IndexOf('-Dmaven.repo.local=', [StringComparison]::Ordinal) -ge 0
$repositoryGateBeforeEvidenceCreation =
    $wrapperSource.IndexOf('Assert-ApprovedOfflineRepository -RepositoryPath', [StringComparison]::Ordinal) -ge 0 -and
    $wrapperSource.IndexOf('Assert-ApprovedOfflineRepository -RepositoryPath', [StringComparison]::Ordinal) -lt
        $wrapperSource.IndexOf('[IO.Directory]::CreateDirectory($RunsRoot)', [StringComparison]::Ordinal)
if (-not $offlineFlagPresent -or -not $localRepositoryBindingPresent -or
        -not $repositoryGateBeforeEvidenceCreation) {
    throw 'NONFINAL_FORMAL_OFFLINE_GATE_MISSING'
}

$machineKeys = @([Environment]::GetEnvironmentVariables('Machine').Keys | ForEach-Object { [string]$_ })
$userKeys = @([Environment]::GetEnvironmentVariables('User').Keys | ForEach-Object { [string]$_ })
$pathScopeCollisionPresent =
    @($machineKeys | Where-Object { $_.Equals('PATH', [StringComparison]::OrdinalIgnoreCase) }).Count -gt 0 -and
    @($userKeys | Where-Object { $_.Equals('PATH', [StringComparison]::OrdinalIgnoreCase) }).Count -gt 0
if (-not $pathScopeCollisionPresent) {
    throw 'NONFINAL_PATH_SCOPE_COLLISION_NOT_PRESENT'
}

$before = Get-EntrySnapshot -Root $FormalEvidenceRoot
[IO.Directory]::CreateDirectory($OutputDirectory) | Out-Null
$ProbeWorkingDirectory = Join-Path $OutputDirectory '探针 工作目录'
[IO.Directory]::CreateDirectory($ProbeWorkingDirectory) | Out-Null

$FakeRepository = Join-Path $OutputDirectory 'fake-offline-repository'
$FakeParentDirectory = Join-Path $FakeRepository 'org/springframework/boot/spring-boot-starter-parent/3.5.16'
$FakeJarDirectory = Join-Path $FakeRepository 'synthetic/dependency/1.0'
[IO.Directory]::CreateDirectory($FakeParentDirectory) | Out-Null
[IO.Directory]::CreateDirectory($FakeJarDirectory) | Out-Null
$FakeParent = Join-Path $FakeParentDirectory 'spring-boot-starter-parent-3.5.16.pom'
$FakeJar = Join-Path $FakeJarDirectory 'dependency-1.0.jar'
[IO.File]::WriteAllText($FakeParent, 'SYNTHETIC_PARENT_NONFINAL', $Utf8NoBom)
[IO.File]::WriteAllText($FakeJar, 'SYNTHETIC_JAR_NONFINAL', $Utf8NoBom)
$fakeRelativePaths = @(
    'org/springframework/boot/spring-boot-starter-parent/3.5.16/spring-boot-starter-parent-3.5.16.pom',
    'synthetic/dependency/1.0/dependency-1.0.jar'
)
[Array]::Sort($fakeRelativePaths, [StringComparer]::Ordinal)
$fakeLines = @($fakeRelativePaths | ForEach-Object {
    $absolute = Join-Path $FakeRepository $_.Replace('/', [IO.Path]::DirectorySeparatorChar)
    "$_|$(Get-Sha256 -Path $absolute)"
})
$fakeAggregate = Get-TextSha256 -Text ($fakeLines -join "`n")
$fakeParentSha = Get-Sha256 -Path $FakeParent
$targetProcessLaunchCount = 0
$completeRepositoryResult = Assert-ApprovedOfflineRepository -RepositoryPath $FakeRepository `
    -ExpectedArtifactCount 2 -ExpectedAggregate $fakeAggregate -ExpectedParentSha $fakeParentSha
$ApprovedOfflineRepository = Join-Path $RepositoryRoot '.m2-local'
$approvedRepositoryResult = Assert-ApprovedOfflineRepository -RepositoryPath $ApprovedOfflineRepository `
    -ExpectedArtifactCount $ExpectedOfflineRepositoryArtifactCount `
    -ExpectedAggregate $ExpectedOfflineRepositoryAggregate `
    -ExpectedParentSha $ExpectedSpringBootParentSha
$missingRepositoryBlocked = $false
try {
    Assert-ApprovedOfflineRepository -RepositoryPath (Join-Path $OutputDirectory 'missing-repository') `
        -ExpectedArtifactCount 2 -ExpectedAggregate $fakeAggregate -ExpectedParentSha $fakeParentSha
}
catch {
    $missingRepositoryBlocked = $_.Exception.Message -eq 'HOST_PRELAUNCH_BLOCKED|OFFLINE_REPOSITORY_MISSING'
}
if (-not $missingRepositoryBlocked -or $targetProcessLaunchCount -ne 0) {
    throw 'NONFINAL_MISSING_REPOSITORY_DID_NOT_BLOCK_PRELAUNCH'
}

$PowerShell = (Get-Command powershell.exe -ErrorAction Stop).Source
$probeEncodingPrefix = "`$encoding=New-Object System.Text.UTF8Encoding(`$false);[Console]::OutputEncoding=`$encoding;"
$probes = @(
    [ordered]@{
        id = 'STDOUT_EXIT0'
        script = $probeEncodingPrefix + "`$ProgressPreference='SilentlyContinue';[Console]::Out.WriteLine('M1_NONFINAL_STDOUT');exit 0"
        expectedExit = 0
        stdoutMarker = 'M1_NONFINAL_STDOUT'
        stderrMarker = $null
        expectedStatus = 'PASS_NONFINAL'
    },
    [ordered]@{
        id = 'STDERR_EXIT0'
        script = $probeEncodingPrefix + "`$ProgressPreference='SilentlyContinue';[Console]::Error.WriteLine('M1_NONFINAL_STDERR');exit 0"
        expectedExit = 0
        stdoutMarker = $null
        stderrMarker = 'M1_NONFINAL_STDERR'
        expectedStatus = 'PASS_NONFINAL'
    },
    [ordered]@{
        id = 'NONZERO_EXIT7'
        script = $probeEncodingPrefix + "`$ProgressPreference='SilentlyContinue';[Console]::Out.WriteLine('M1_NONFINAL_BEFORE_FAILURE');[Console]::Error.WriteLine('M1_NONFINAL_EXPECTED_FAILURE');exit 7"
        expectedExit = 7
        stdoutMarker = 'M1_NONFINAL_BEFORE_FAILURE'
        stderrMarker = 'M1_NONFINAL_EXPECTED_FAILURE'
        expectedStatus = 'BLOCKED_NONFINAL'
    },
    [ordered]@{
        id = 'CHINESE_SPACE_WORKING_DIRECTORY'
        script = $probeEncodingPrefix + "`$ProgressPreference='SilentlyContinue';[Console]::Out.WriteLine((Get-Location).Path);exit 0"
        expectedExit = 0
        stdoutMarker = $ProbeWorkingDirectory
        stderrMarker = $null
        expectedStatus = 'PASS_NONFINAL'
    }
)

$results = @()
try {
    foreach ($probe in $probes) {
        $probeDirectory = Join-Path $OutputDirectory $probe.id
        [IO.Directory]::CreateDirectory($probeDirectory) | Out-Null
        $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($probe.script))
        $startedAt = [DateTimeOffset]::UtcNow
        $targetProcessLaunchCount++
        $capture = Invoke-NativeProcessCapture -FilePath $PowerShell `
            -ArgumentList @('-NoProfile', '-NonInteractive', '-EncodedCommand', $encoded) `
            -WorkingDirectory $ProbeWorkingDirectory
        $endedAt = [DateTimeOffset]::UtcNow
        $stdoutPath = Join-Path $probeDirectory 'stdout.log'
        $stderrPath = Join-Path $probeDirectory 'stderr.log'
        [IO.File]::WriteAllText($stdoutPath, $capture.stdout, $Utf8NoBom)
        [IO.File]::WriteAllText($stderrPath, $capture.stderr, $Utf8NoBom)
        $stdoutMatch = if ($null -eq $probe.stdoutMarker) {
            $true
        }
        else {
            $capture.stdout.Contains($probe.stdoutMarker)
        }
        $stderrMatch = if ($null -eq $probe.stderrMarker) {
            $true
        }
        else {
            $capture.stderr.Contains($probe.stderrMarker)
        }
        $streamsSeparated =
            ($null -eq $probe.stderrMarker -or -not $capture.stdout.Contains($probe.stderrMarker)) -and
            ($null -eq $probe.stdoutMarker -or -not $capture.stderr.Contains($probe.stdoutMarker))
        $status = if ($capture.exitCode -eq 0) { 'PASS_NONFINAL' } else { 'BLOCKED_NONFINAL' }
        $result = [ordered]@{
            probeId = $probe.id
            processId = $capture.processId
            processOwnerSid = $capture.processOwnerSid
            processOwnerName = $capture.processOwnerName
            startedAt = $startedAt.ToString('o')
            endedAt = $endedAt.ToString('o')
            exitCode = $capture.exitCode
            stdoutPath = "$($probe.id)/stdout.log"
            stdoutSha256 = Get-Sha256 -Path $stdoutPath
            stdoutLength = $capture.stdout.Length
            stderrPath = "$($probe.id)/stderr.log"
            stderrSha256 = Get-Sha256 -Path $stderrPath
            stderrLength = $capture.stderr.Length
            expectedExitCode = $probe.expectedExit
            stdoutMatched = $stdoutMatch
            stderrMatched = $stderrMatch
            streamsSeparated = $streamsSeparated
            executionStatus = $status
            consumable = $false
            readyWritten = $false
        }
        Write-Json -Path (Join-Path $probeDirectory 'result.json') -Value $result
        if ($capture.exitCode -ne $probe.expectedExit -or -not $stdoutMatch -or -not $stderrMatch -or
                -not $streamsSeparated -or
                $capture.processId -le 0 -or [string]::IsNullOrWhiteSpace($capture.processOwnerSid) -or
                [string]::IsNullOrWhiteSpace($capture.processOwnerName) -or
                $status -ne $probe.expectedStatus) {
            throw "NONFINAL_PROBE_FAILED|$($probe.id)"
        }
        $results += $result
    }

    $after = Get-EntrySnapshot -Root $FormalEvidenceRoot
    if ((Get-CanonicalJson -Value $before) -ne (Get-CanonicalJson -Value $after)) {
        throw 'NONFINAL_FORMAL_EVIDENCE_STATE_CHANGED'
    }
    if (@(Get-ChildItem -LiteralPath $OutputDirectory -Recurse -Force -Filter 'READY').Count -ne 0) {
        throw 'NONFINAL_READY_FILE_FORBIDDEN'
    }
    $evidence = [ordered]@{
        schema = 'M1_PROCESS_CAPTURE_NONFINAL_SELF_CHECK_V1'
        selfCheckId = $SelfCheckId
        publicationState = 'NONFINAL_SELF_CHECK_ONLY'
        scenarioExecutionStatus = 'NOT_RUN'
        consumable = $false
        wrapperSha256 = Get-Sha256 -Path $WrapperPath
        selfCheckScriptSha256 = Get-Sha256 -Path $ScriptPath
        powershellVersion = $PSVersionTable.PSVersion.ToString()
        pathScopeCollisionPresent = $pathScopeCollisionPresent
        formalPathUsesStartProcess = $false
        formalOfflineFlagPresent = $offlineFlagPresent
        formalLocalRepositoryBindingPresent = $localRepositoryBindingPresent
        repositoryGateBeforeEvidenceCreation = $repositoryGateBeforeEvidenceCreation
        completeRepositoryPreflightPassed = $completeRepositoryResult.networkMode -eq 'MAVEN_OFFLINE'
        approvedRepositoryPreflightPassed =
            $approvedRepositoryResult.aggregateSha256 -eq $ExpectedOfflineRepositoryAggregate
        approvedRepositoryArtifactCount = $approvedRepositoryResult.artifactCount
        approvedRepositoryAggregateSha256 = $approvedRepositoryResult.aggregateSha256
        approvedSpringBootParentSha256 = $approvedRepositoryResult.springBootParentSha256
        missingRepositoryPrelaunchBlocked = $missingRepositoryBlocked
        missingRepositoryTargetProcessLaunchCount = 0
        networkUnavailableSimulatedWithoutNetworkAccess = $offlineFlagPresent -and $localRepositoryBindingPresent
        targetProcessLaunchCount = $targetProcessLaunchCount
        formalEvidenceStateUnchanged = $true
        probeCount = $results.Count
        passNonFinalCount = @($results | Where-Object { $_.executionStatus -eq 'PASS_NONFINAL' }).Count
        blockedNonFinalCount = @($results | Where-Object { $_.executionStatus -eq 'BLOCKED_NONFINAL' }).Count
        results = $results
    }
    $evidencePath = Join-Path $OutputDirectory 'SelfCheckEvidence.json'
    Write-Json -Path $evidencePath -Value $evidence
    Write-Output "M1_PROCESS_CAPTURE_NONFINAL_PASS|$SelfCheckId|$(Get-Sha256 -Path $evidencePath)|$OutputDirectory"
}
catch {
    $blocked = [ordered]@{
        schema = 'M1_PROCESS_CAPTURE_NONFINAL_SELF_CHECK_V1'
        selfCheckId = $SelfCheckId
        publicationState = 'BLOCKED_NONFINAL'
        scenarioExecutionStatus = 'NOT_RUN'
        consumable = $false
        readyWritten = $false
        reason = $_.Exception.Message
    }
    if (Test-Path -LiteralPath $OutputDirectory -PathType Container) {
        Write-Json -Path (Join-Path $OutputDirectory 'BLOCKED.json') -Value $blocked
    }
    throw
}
