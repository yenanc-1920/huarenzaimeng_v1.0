[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$AuthorizationFile,
    [switch]$SelfTest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:EntryScriptPath = if ([string]::IsNullOrWhiteSpace($PSCommandPath)) { $env:P021_IT_WRAPPER } else { $PSCommandPath }
if ([string]::IsNullOrWhiteSpace($script:EntryScriptPath)) { throw 'ENTRY_SCRIPT_PATH_UNAVAILABLE' }
$script:EntryScriptRoot = Split-Path -Parent $script:EntryScriptPath

$script:Scope = 'P021_TEST_READONLY_INTEGRATION_7_SCENARIO'
$script:BaseUrl = 'https://huaren-api-it-284852-10-1456291159.sh.run.tcloudbase.com'
$script:Database = 'huarenzaimeng_it_vnext'
$script:OrderRefs = @('IT-P021-AWAITING','IT-P021-PAYMENT','IT-P021-TOPUP','IT-P021-UNKNOWN','IT-P021-DELIVERED','IT-P021-REFUNDED','IT-P021-REVOKED')
$script:EvidenceRoot = Join-Path $script:EntryScriptRoot '..\..\项目管理\正式交付\D4-开发计划与工程准备\证据\P021-IT'
$script:PageCollector = Join-Path $script:EntryScriptRoot '..\miniapp\scripts\collect-p021-it-page-actual.ps1'
$script:PageCollectorNode = Join-Path $script:EntryScriptRoot '..\miniapp\scripts\collect-p021-it-page-actual.mjs'
$script:DiagnosticController = Join-Path $script:EntryScriptRoot 'src\main\java\com\huarenzaimeng\api\P021TestReadonlyDiagnosticController.java'
$script:FixtureFile = Join-Path $script:EntryScriptRoot 'src\test\resources\db\fixture\VnextP021OrderDetailCloudBaseConsoleFixture.sql'
$script:V7File = Join-Path $script:EntryScriptRoot 'src\main\resources\db\migration\V7__add_order_detail_read_projection.sql'
$script:ManifestFile = Join-Path $script:EntryScriptRoot 'manifests\P021-IT最终执行准备固定清单.txt'
$script:ObservedRequests = [Collections.Generic.List[object]]::new()

function Get-Sha256Hex([string]$Path) { (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash }

function ConvertFrom-SecureStringPlain([Security.SecureString]$Value) {
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Value)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}

function Assert-Authorization($auth) {
    $required = @('RunId','Scope','BaseUrl','Database','ValidFrom','ValidUntil','SingleUse','AutomaticRetryAllowed',
        'WrapperSha256','PageCollectorSha256','PageCollectorNodeSha256','PageCollectorAggregateSha256','DiagnosticControllerSha256','FixtureSha256','V7Sha256','ManifestSha256','ServiceVersion','DatabaseInstanceIdentity','BrowserExecutable','BrowserSha256')
    foreach ($key in $required) { if ($null -eq $auth.$key) { throw "AUTHORIZATION_MISSING_$key" } }
    if ($auth.RunId -notmatch '^P021-IT-20260808-FINAL-[0-9]{3}$') { throw 'RUN_ID_INVALID' }
    if ($auth.Scope -cne $script:Scope -or $auth.BaseUrl -cne $script:BaseUrl -or $auth.Database -cne $script:Database) { throw 'AUTHORIZATION_SCOPE_MISMATCH' }
    if ($auth.SingleUse -ne $true -or $auth.AutomaticRetryAllowed -ne $false) { throw 'AUTHORIZATION_RETRY_POLICY_INVALID' }
    if ($auth.WrapperSha256 -cne (Get-Sha256Hex $script:EntryScriptPath) -or
        $auth.PageCollectorSha256 -cne (Get-Sha256Hex $script:PageCollector) -or
        $auth.PageCollectorNodeSha256 -cne (Get-Sha256Hex $script:PageCollectorNode) -or
        $auth.PageCollectorAggregateSha256 -cne '50A157DBDEBC61EB4FA682F865E0E2799BFE1D65A7A5BA3015DB5277AA2B5281' -or
        $auth.DiagnosticControllerSha256 -cne (Get-Sha256Hex $script:DiagnosticController) -or
        $auth.FixtureSha256 -cne (Get-Sha256Hex $script:FixtureFile) -or
        $auth.V7Sha256 -cne (Get-Sha256Hex $script:V7File) -or
        $auth.ManifestSha256 -cne (Get-Sha256Hex $script:ManifestFile)) { throw 'AUTHORIZATION_FILE_IDENTITY_MISMATCH' }
    $now = [DateTimeOffset]::UtcNow
    if ($now -lt [DateTimeOffset]::Parse($auth.ValidFrom) -or $now -gt [DateTimeOffset]::Parse($auth.ValidUntil)) { throw 'AUTHORIZATION_EXPIRED_OR_NOT_YET_VALID' }
}

function Write-JsonAtomic([string]$Path, $Value) {
    $tmp = "$Path.tmp"
    $json = $Value | ConvertTo-Json -Depth 30
    [IO.File]::WriteAllText($tmp, $json, [Text.UTF8Encoding]::new($false))
    [IO.File]::Move($tmp, $Path)
}

function Invoke-HttpJson([string]$Path, [string]$Token, [string]$Method='GET') {
    $uri = $script:BaseUrl + $Path
    try { $response = Invoke-WebRequest -UseBasicParsing -Uri $uri -Method $Method -Headers @{ Cookie = "HZM_IT_SESSION=$Token"; Accept = 'application/json' } }
    catch { if ($null -eq $_.Exception.Response) { throw }; $response=$_.Exception.Response }
    $headerNames=@($response.Headers.Keys|ForEach-Object{[string]$_}|Sort-Object)
    $script:ObservedRequests.Add([ordered]@{Boundary='WRAPPER_HTTP';Method=$Method;Uri=$uri;Host=([Uri]$uri).Host;Status=[int]$response.StatusCode;RequestHeaderNames=@('Accept','Cookie');ResponseHeaderNames=$headerNames})
    [pscustomobject]@{ Status = [int]$response.StatusCode; CacheControl = [string]$response.Headers.'Cache-Control'; Body = ($response.Content | ConvertFrom-Json); Length = $response.RawContentLength; Uri = $uri; RequestHeaderNames=@('Accept','Cookie'); ResponseHeaderNames=$headerNames }
}

function Assert-ExactKeys($Object,[string[]]$Keys,[string]$Label) {
    $actual=@($Object.PSObject.Properties.Name|Sort-Object);$expected=@($Keys|Sort-Object)
    if((Compare-Object $actual $expected).Count -ne 0){throw "${Label}_KEYS_MISMATCH"}
}
function ConvertTo-CanonicalObject($Value) {
    if($null -eq $Value){return $null}
    if($Value -is [Collections.IDictionary]){$o=[ordered]@{};foreach($k in @($Value.Keys|Sort-Object)){$o[$k]=ConvertTo-CanonicalObject $Value[$k]};return $o}
    if($Value -is [Management.Automation.PSCustomObject]){$o=[ordered]@{};foreach($k in @($Value.PSObject.Properties.Name|Sort-Object)){$o[$k]=ConvertTo-CanonicalObject $Value.$k};return $o}
    if($Value -is [Collections.IEnumerable] -and $Value -isnot [string]){return @($Value|ForEach-Object{ConvertTo-CanonicalObject $_})}
    $Value
}
function ConvertTo-CanonicalJson($Value){ConvertTo-CanonicalObject $Value|ConvertTo-Json -Depth 40 -Compress}
function Assert-StrictOrderResponse($Response,[string]$ProjectCode) {
    Assert-ExactKeys $Response.Body @('requestRef','outcome','projectCode','resourceRef','aggregateVersion','currentProjection','retryClass','nextPollAt') 'RESPONSE'
    if($Response.Body.projectCode -cne $ProjectCode){throw 'PROJECT_CODE_MISMATCH'}
    if($ProjectCode -eq 'ORDER_DETAIL_READ'){
        Assert-ExactKeys $Response.Body.currentProjection @('orderRef','aggregateVersion','projectionVersion','stateCode','priceSnapshotSummary','confirmedItems','unknownItems','responsibilityCode','updatedAt','nextReviewPoint','timeline','allowedActions','supportRef') 'PROJECTION'
        Assert-ExactKeys $Response.Body.currentProjection.priceSnapshotSummary @('priceSnapshotRef','totalMinor','currency','displayVersion','maskedTarget','brandDisplayName','productDisplayName','targetValueDisplay','targetCurrency','validUntil') 'PRICE'
        foreach($item in @($Response.Body.currentProjection.timeline)){Assert-ExactKeys $item @('timelineItemRef','sequence','projectionVersion','stateCode','occurredAt','userMessageCode') 'TIMELINE'}
        foreach($item in @($Response.Body.currentProjection.allowedActions)){Assert-ExactKeys $item @('actionCode','enabled','actionBindingVersion','supportRef') 'ACTION'}
    }
}

function Assert-RuntimeIdentity($auth) {
    $nonce=([Guid]::NewGuid().ToString('N')).ToUpperInvariant()
    $challenge=Invoke-HttpJson "/internal/test-readonly/p021/challenge?nonce=$nonce" $script:BuyerForChallenge
    Assert-ExactKeys $challenge.Body @('nonce','serviceVersion','mode','databaseIdentity') 'RUNTIME_CHALLENGE'
    if($challenge.Body.nonce -cne $nonce -or $challenge.Body.serviceVersion -cne $auth.ServiceVersion -or
       $challenge.Body.mode -cne 'test-readonly' -or $challenge.Body.databaseIdentity -cne $auth.DatabaseInstanceIdentity){
        throw 'RUNTIME_IDENTITY_CHALLENGE_MISMATCH'
    }
}

function Get-DatabaseSnapshot {
    $response=Invoke-HttpJson '/internal/test-readonly/p021/snapshot' $script:BuyerForChallenge
    Assert-ExactKeys $response.Body @('rowCount','rows','canonicalSha256') 'DATABASE_SNAPSHOT'
    $rows=@($response.Body.rows)
    if([int]$response.Body.rowCount -ne 7 -or $rows.Count -ne 7 -or
       $response.Body.canonicalSha256 -cne (Get-StringSha256 ($rows -join "`n"))){throw 'DATABASE_SNAPSHOT_INVALID'}
    [ordered]@{RowCount=7;Rows=$rows;CanonicalSha256=$response.Body.canonicalSha256}
}

function Get-StringSha256([string]$Value){$sha=[Security.Cryptography.SHA256]::Create();try{([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($Value)))).Replace('-','')}finally{$sha.Dispose()}}

function Get-ProjectionJson {
    (Invoke-HttpJson '/internal/test-readonly/p021/projection' $script:BuyerForChallenge).Body
}

function Invoke-It03Rollback([string]$Buyer) {
    $before=Get-DatabaseSnapshot;$actual=@()
    try {
        $expected=@{PROJECTION_LOW_VERSION='ORDER_DETAIL_READ_ERROR';ORDER_VERSION_CONFLICT='ORDER_DETAIL_READ_ERROR';QUOTE_DIGEST_CONFLICT='ORDER_DETAIL_READ_ERROR'}
        foreach($kind in @('PROJECTION_LOW_VERSION','ORDER_VERSION_CONFLICT','QUOTE_DIGEST_CONFLICT')){
            $response=Invoke-HttpJson "/internal/test-readonly/p021/conflict/$kind" $Buyer 'POST'
            Assert-StrictOrderResponse $response $expected[$kind]
            $actual += [ordered]@{Conflict=$kind;Http=$response;ExpectedProjectCode=$expected[$kind];ActualProjectCode=$response.Body.projectCode}
        }
    } finally {
        $after=Get-DatabaseSnapshot
        if((ConvertTo-CanonicalJson $before) -cne (ConvertTo-CanonicalJson $after)){throw 'IT03_ROLLBACK_NOT_PROVEN'}
    }
    $actual
}

function Invoke-PageCollector($auth,[string]$ScenarioId,[string]$SubcaseId,[string]$PageKind,[string]$Role,[string]$OrderRef,[string]$ExpectedViewState,[string]$DelayPlan,[string]$Token) {
    $output=Join-Path $stagingRoot ("page\$ScenarioId\$SubcaseId")
    [IO.Directory]::CreateDirectory($output)|Out-Null
    $args=@('-NoLogo','-NoProfile','-NonInteractive','-File',$script:PageCollector,'-ScenarioId',$ScenarioId,'-SubcaseId',$SubcaseId,'-PageKind',$PageKind,'-Role',$Role,'-BaseUrl',$script:BaseUrl,'-OrderRef',$OrderRef,'-ExpectedViewState',$ExpectedViewState,'-OutputDirectory',$output,'-BrowserExecutable',$auth.BrowserExecutable,'-BrowserSha256',$auth.BrowserSha256,'-ViewportWidth',$(if($PageKind -eq 'MINIAPP_P021'){'375'}else{'1280'}),'-ViewportHeight',$(if($PageKind -eq 'MINIAPP_P021'){'812'}else{'790'}),'-DelayPlan',$DelayPlan)
    $psi=[Diagnostics.ProcessStartInfo]::new();$psi.FileName='powershell.exe';$psi.Arguments=($args|ForEach-Object{'"'+($_ -replace '"','\"')+'"'}) -join ' '
    $psi.UseShellExecute=$false;$psi.RedirectStandardInput=$true;$psi.RedirectStandardOutput=$true;$psi.RedirectStandardError=$true;$psi.CreateNoWindow=$true
    $p=[Diagnostics.Process]::new();$p.StartInfo=$psi;[void]$p.Start();$p.StandardInput.WriteLine(([ordered]@{BUYER=$(if($Role -eq 'BUYER'){$Token}else{$null});CS=$(if($Role -eq 'CS'){$Token}else{$null});FIN=$(if($Role -eq 'FIN'){$Token}else{$null})}|ConvertTo-Json -Compress));$p.StandardInput.Close()
    $out=$p.StandardOutput.ReadToEnd();$err=$p.StandardError.ReadToEnd();$p.WaitForExit()
    if($p.ExitCode -ne 0){throw "PAGE_COLLECTOR_FAILED_${ScenarioId}_${SubcaseId}:$err"}
    $actual=$out|ConvertFrom-Json
    if($actual.ExpectedMatched -ne $true -or $actual.ExitCode -ne 0 -or $actual.ExternalHosts.Count -ne 0 -or $actual.WriteActionCount -ne 0){throw "PAGE_ACTUAL_MISMATCH_${ScenarioId}_${SubcaseId}"}
    if(@($actual.ForbiddenFieldFindings.PSObject.Properties|Where-Object{$_.Value.count -ne 0}).Count -ne 0 -or
       @($actual.ForbiddenIdentityHeaderFindings.PSObject.Properties|Where-Object{$_.Value.count -ne 0}).Count -ne 0){throw "PAGE_FORBIDDEN_FINDING_${ScenarioId}_${SubcaseId}"}
    $allowed=@('127.0.0.1',([Uri]$script:BaseUrl).Host)
    foreach($entry in @($actual.BrowserNetwork)+@($actual.ProxyNetwork)){$host=([Uri]$entry.url).Host;if($host -notin $allowed){throw "PAGE_NETWORK_DOMAIN_VIOLATION_${ScenarioId}_${SubcaseId}"}}
    $actual
}

function Assert-ProjectCode($response, [string]$expected) {
    if ($response.Status -lt 200 -or $response.Status -ge 300 -or $response.CacheControl -notmatch 'no-store' -or $response.Body.projectCode -cne $expected) { throw "UNEXPECTED_HTTP_RESPONSE_$expected" }
}

function Invoke-SevenScenarios([string]$Buyer, [string]$Cs, [string]$Fin) {
    $results = [ordered]@{}
    $before = Get-DatabaseSnapshot
    $counterBefore=Invoke-HttpJson '/internal/test-readonly/p021/counters' $Buyer
    $it01Api = Invoke-HttpJson '/api/v1/orders/IT-P021-AWAITING' $Buyer
    Assert-ProjectCode $it01Api 'ORDER_DETAIL_READ'
    Assert-StrictOrderResponse $it01Api 'ORDER_DETAIL_READ'
    $dbProjection=Get-ProjectionJson
    if((ConvertTo-CanonicalJson $dbProjection) -cne (ConvertTo-CanonicalJson $it01Api.Body.currentProjection)){throw 'IT01_DATABASE_PROJECTION_MISMATCH'}
    $results.'P021-IT-01'=[ordered]@{Api=$it01Api;Page=(Invoke-PageCollector $auth 'IT01' 'BUYER_READY' 'MINIAPP_P021' 'BUYER' 'IT-P021-AWAITING' 'READY' 'NONE' $Buyer)}

    $it02 = @(
        Invoke-HttpJson '/api/v1/orders/IT-P021-AWAITING' '',
        Invoke-HttpJson '/api/v1/orders/IT-P021-CROSS-SUBJECT' $Buyer,
        Invoke-HttpJson '/api/v1/orders/IT-P021-NOT-FOUND' $Buyer,
        Invoke-HttpJson '/api/v1/orders/IT-P021-REVOKED' $Buyer)
    foreach ($item in $it02) { Assert-ProjectCode $item 'ORDER_DETAIL_NOT_AVAILABLE';Assert-StrictOrderResponse $item 'ORDER_DETAIL_NOT_AVAILABLE' }
    if ((($it02 | ForEach-Object { $_.Body | ConvertTo-Json -Compress }) | Sort-Object -Unique).Count -ne 1) { throw 'IT02_NOT_SAME_SHAPE' }
    if((($it02|ForEach-Object{$_.Status}|Sort-Object -Unique).Count)-ne 1 -or (($it02|ForEach-Object{$_.Length}|Sort-Object -Unique).Count)-ne 1){throw 'IT02_STATUS_OR_LENGTH_NOT_SAME'}
    $it02Names=@('UNAUTHENTICATED','CROSS_SUBJECT','NOT_FOUND','REVOKED');$it02Pages=@()
    for($i=0;$i -lt 4;$i++){$it02Pages+=Invoke-PageCollector $auth 'IT02' $it02Names[$i] 'MINIAPP_P021' 'BUYER' @('IT-P021-AWAITING','IT-P021-CROSS-SUBJECT','IT-P021-NOT-FOUND','IT-P021-REVOKED')[$i] 'NOT_AVAILABLE' 'NONE' $(if($i -eq 0){''}else{$Buyer})}
    $results.'P021-IT-02' = [ordered]@{Api=@($it02|ForEach-Object{[ordered]@{Expected=[ordered]@{ProjectCode='ORDER_DETAIL_NOT_AVAILABLE';SameShape=$true};Actual=$_}});Page=$it02Pages}

    $results.'P021-IT-03' = [ordered]@{Api=Invoke-It03Rollback $Buyer;Page=@(
        Invoke-PageCollector $auth 'IT03' 'PROJECTION_LOW_VERSION' 'MINIAPP_P021' 'BUYER' 'IT-P021-AWAITING' 'INFORMATION_UPDATED' 'PROJECTION_LOW_VERSION' $Buyer
        Invoke-PageCollector $auth 'IT03' 'ORDER_VERSION_CONFLICT' 'MINIAPP_P021' 'BUYER' 'IT-P021-AWAITING' 'READ_ERROR' 'ORDER_VERSION_CONFLICT' $Buyer
        Invoke-PageCollector $auth 'IT03' 'QUOTE_DIGEST_CONFLICT' 'MINIAPP_P021' 'BUYER' 'IT-P021-AWAITING' 'INFORMATION_UPDATED' 'QUOTE_DIGEST_CONFLICT' $Buyer)}
    $results.'P021-IT-04' = Invoke-PageCollector $auth 'IT04' 'LATE_LOWER_VERSION_AFTER_READY' 'MINIAPP_P021' 'BUYER' 'IT-P021-AWAITING' 'READY' 'LATE_LOWER_VERSION_AFTER_READY' $Buyer

    $csResponse = Invoke-HttpJson '/admin-read/v1/orders/IT-P021-AWAITING' $Cs
    Assert-ProjectCode $csResponse 'ADMIN_ORDER_DETAIL_READ'
    $csJson = $csResponse.Body | ConvertTo-Json -Depth 20
    if ($csJson -match 'totalMinor|targetCurrency|sessionRef|authorization') { throw 'IT05_CROSS_ROLE_FIELD_VISIBLE' }
    $results.'P021-IT-05'=[ordered]@{Api=$csResponse;Page=(Invoke-PageCollector $auth 'IT05' 'CS_READY' 'ADMIN_A140' 'CS' 'IT-P021-AWAITING' 'READY' 'NONE' $Cs)}

    $finResponse = Invoke-HttpJson '/admin-read/v1/orders/IT-P021-AWAITING' $Fin
    Assert-ProjectCode $finResponse 'ADMIN_ORDER_DETAIL_READ'
    $denied = Invoke-HttpJson '/admin-read/v1/orders/IT-P021-AWAITING' ''
    Assert-ProjectCode $denied 'ADMIN_ORDER_DETAIL_NOT_AVAILABLE'
    $finJson = $finResponse.Body | ConvertTo-Json -Depth 20
    if ($finJson -match 'maskedTarget|sessionRef|authorization|projectSubjectRef') { throw 'IT06_CROSS_ROLE_FIELD_VISIBLE' }
    $results.'P021-IT-06' = [ordered]@{Api=@($finResponse,$denied);Page=@(
        Invoke-PageCollector $auth 'IT06' 'FIN_READY' 'ADMIN_A140' 'FIN' 'IT-P021-AWAITING' 'READY' 'NONE' $Fin
        Invoke-PageCollector $auth 'IT06' 'CONTENT_DENIED' 'ADMIN_A140' 'CONTENT' 'IT-P021-AWAITING' 'ACCESS_DENIED' 'NONE' '')}

    $counterAfter=Invoke-HttpJson '/internal/test-readonly/p021/counters' $Buyer
    $after = Get-DatabaseSnapshot
    if ((ConvertTo-CanonicalJson $before) -cne (ConvertTo-CanonicalJson $after)) { throw 'IT07_DATABASE_CHANGED' }
    $nonQuery=@($counterAfter.Body.PSObject.Properties|Where-Object{$_.Name -ne 'QueryCall'})
    foreach($entry in $nonQuery){$beforeValue=[long]$counterBefore.Body.($entry.Name);if([long]$entry.Value -ne $beforeValue){throw "IT07_NON_QUERY_DELTA_$($entry.Name)"}}
    $hosts=@($script:ObservedRequests|ForEach-Object{$_.Host}|Sort-Object -Unique)
    if($hosts.Count -ne 1 -or $hosts[0] -cne ([Uri]$script:BaseUrl).Host){throw 'IT07_NETWORK_EGRESS_VIOLATION'}
    $pageNetwork=@();foreach($scenario in $results.Values){$json=$scenario|ConvertTo-Json -Depth 40;if($json -match 'BrowserNetwork|ProxyNetwork'){$pageNetwork+=$scenario}}
    $results.'P021-IT-07' = [ordered]@{Expected=[ordered]@{DatabaseUnchanged=$true;SevenCanonicalRows=$true;NonQueryDelta=0;AllowedHosts=@('127.0.0.1',([Uri]$script:BaseUrl).Host)};Actual=[ordered]@{Before=$before;After=$after;CounterBefore=$counterBefore.Body;CounterAfter=$counterAfter.Body;WrapperNetwork=$script:ObservedRequests;PageNetwork=$pageNetwork;ExternalHosts=$hosts}}
    $results
}

function Invoke-SelfTest {
    if ($script:BaseUrl -notmatch '^https://huaren-api-it-' -or $script:Database -cne 'huarenzaimeng_it_vnext' -or $script:OrderRefs.Count -ne 7) { throw 'FIXED_BOUNDARY_INVALID' }
    $source = Get-Content -Raw -LiteralPath $script:EntryScriptPath
    foreach ($token in @('AutomaticRetryAllowed','ROLLBACK','P021-IT-01','P021-IT-07','Read-Host -AsSecureString','READY.json','BLOCKED.json')) {
        if (-not $source.Contains($token)) { throw "SELFTEST_MISSING_$token" }
    }
    'P021 IT wrapper offline self-test: PASS'
}

if ($SelfTest) { Invoke-SelfTest; exit 0 }

$auth = Get-Content -Raw -LiteralPath $AuthorizationFile | ConvertFrom-Json
Assert-Authorization $auth
$authorizationRecordSha=Get-Sha256Hex $AuthorizationFile
$runRoot = Join-Path $script:EvidenceRoot $auth.RunId
$stagingRoot = Join-Path $script:EvidenceRoot ('.staging-' + $auth.RunId)
$blockedRoot = Join-Path $script:EvidenceRoot ('.blocked-' + $auth.RunId)
if ((Test-Path $runRoot) -or (Test-Path $stagingRoot) -or (Test-Path $blockedRoot)) { throw 'RUN_ID_ALREADY_EXISTS' }
[IO.Directory]::CreateDirectory($script:EvidenceRoot) | Out-Null
$consumePath = $AuthorizationFile + '.consumed'
try {
    $consumeStream = [IO.File]::Open($consumePath,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
    try {
        $bytes=[Text.Encoding]::UTF8.GetBytes(([ordered]@{RunId=$auth.RunId;ConsumedAt=[DateTimeOffset]::UtcNow.ToString('O');AutomaticRetryAllowed=$false}|ConvertTo-Json -Compress))
        $consumeStream.Write($bytes,0,$bytes.Length);$consumeStream.Flush($true)
    } finally { $consumeStream.Dispose() }
} catch { throw 'AUTHORIZATION_ALREADY_CONSUMED_OR_NOT_WRITABLE' }
$authorizationConsumptionSha=Get-Sha256Hex $consumePath
[IO.Directory]::CreateDirectory($stagingRoot) | Out-Null
$blocked = $false
$startedAt=[DateTimeOffset]::UtcNow
$stdout=[Collections.Generic.List[string]]::new();$stderr=[Collections.Generic.List[string]]::new()
$buyerSecure=$null;$csSecure=$null;$finSecure=$null;$buyer=$null;$cs=$null;$fin=$null
try {
    $buyerSecure = Read-Host '请输入 BUYER 测试会话 token' -AsSecureString
    $csSecure = Read-Host '请输入 CS 测试会话 token' -AsSecureString
    $finSecure = Read-Host '请输入 FIN 测试会话 token' -AsSecureString
    $buyer = ConvertFrom-SecureStringPlain $buyerSecure; $cs = ConvertFrom-SecureStringPlain $csSecure; $fin = ConvertFrom-SecureStringPlain $finSecure
    try {
        if ([string]::IsNullOrWhiteSpace($buyer) -or [string]::IsNullOrWhiteSpace($cs) -or [string]::IsNullOrWhiteSpace($fin) -or @($buyer,$cs,$fin | Sort-Object -Unique).Count -ne 3) { throw 'SESSION_TOKENS_INVALID' }
        $script:BuyerForChallenge=$buyer
        Assert-RuntimeIdentity $auth
        $results = Invoke-SevenScenarios $buyer $cs $fin
        1..7|ForEach-Object{$stdout.Add("P021-IT-0$_ PASS")}
        Write-JsonAtomic (Join-Path $stagingRoot 'index.json') ([ordered]@{ RunId=$auth.RunId; Scope=$script:Scope; ExecutionStatus='PASS'; ScenarioCount=7; ServiceVersion=$auth.ServiceVersion; WrapperSha256=$auth.WrapperSha256; PageCollectorSha256=$auth.PageCollectorSha256;PageCollectorNodeSha256=$auth.PageCollectorNodeSha256;PageCollectorAggregateSha256=$auth.PageCollectorAggregateSha256;DiagnosticControllerSha256=$auth.DiagnosticControllerSha256; FixtureSha256=$auth.FixtureSha256; V7Sha256=$auth.V7Sha256; ManifestSha256=$auth.ManifestSha256; DatabaseInstanceIdentity=$auth.DatabaseInstanceIdentity; AuthorizationRecordSha256=$authorizationRecordSha;AuthorizationConsumptionSha256=$authorizationConsumptionSha;Results=$results; Consumable=$false })
    } finally { $buyer=$null; $cs=$null; $fin=$null; if($buyerSecure){$buyerSecure.Dispose()};if($csSecure){$csSecure.Dispose()};if($finSecure){$finSecure.Dispose()} }
} catch {
    $blocked = $true
    $stderr.Add($_.Exception.Message)
    Write-JsonAtomic (Join-Path $stagingRoot 'BLOCKED.json') ([ordered]@{ RunId=$auth.RunId; ExecutionStatus='BLOCKED'; Consumable=$false; Error=$_.Exception.Message; AutomaticRetryAllowed=$false })
    throw
} finally {
    try {
        [IO.File]::WriteAllLines((Join-Path $stagingRoot 'process.stdout.txt'),$stdout,[Text.UTF8Encoding]::new($false))
        [IO.File]::WriteAllLines((Join-Path $stagingRoot 'process.stderr.txt'),$stderr,[Text.UTF8Encoding]::new($false))
        $stdoutSha=Get-Sha256Hex (Join-Path $stagingRoot 'process.stdout.txt');$stderrSha=Get-Sha256Hex (Join-Path $stagingRoot 'process.stderr.txt')
        $indexSha=$(if(Test-Path (Join-Path $stagingRoot 'index.json')){Get-Sha256Hex (Join-Path $stagingRoot 'index.json')}else{$null})
        Write-JsonAtomic (Join-Path $stagingRoot 'process-evidence.json') ([ordered]@{ RunId=$auth.RunId; StartedAt=$startedAt.ToString('O'); EndedAt=[DateTimeOffset]::UtcNow.ToString('O'); OsExit=($(if($blocked){1}else{0})); Blocked=$blocked; BaseUrl=$script:BaseUrl; Database=$script:Database; AuthorizationRecordSha256=$authorizationRecordSha;AuthorizationConsumptionRef=[IO.Path]::GetFileName($consumePath);AuthorizationConsumptionSha256=$authorizationConsumptionSha;ManifestSha256=$auth.ManifestSha256;SecretsPersisted=$false;StdoutRef='process.stdout.txt';StdoutSha256=$stdoutSha;StderrRef='process.stderr.txt';StderrSha256=$stderrSha;IndexSha256=$indexSha })
        if($blocked){[IO.Directory]::Move($stagingRoot,$blockedRoot)}else{
            $processSha=Get-Sha256Hex (Join-Path $stagingRoot 'process-evidence.json')
            Write-JsonAtomic (Join-Path $stagingRoot 'package.json') ([ordered]@{RunId=$auth.RunId;IndexSha256=$indexSha;ProcessEvidenceSha256=$processSha;ManifestSha256=$auth.ManifestSha256;AuthorizationRecordSha256=$authorizationRecordSha;AuthorizationConsumptionSha256=$authorizationConsumptionSha;Consumable=$false})
            $packageSha=Get-Sha256Hex (Join-Path $stagingRoot 'package.json')
            [IO.Directory]::Move($stagingRoot,$runRoot)
            Write-JsonAtomic (Join-Path $runRoot 'READY.json') ([ordered]@{ RunId=$auth.RunId; ExecutionStatus='PASS'; IndexRef='index.json';IndexSha256=$indexSha;PackageRef='package.json';PackageSha256=$packageSha;ProcessEvidenceSha256=$processSha; Consumable=$true })
        }
    } catch {
        if(Test-Path $runRoot){Get-ChildItem $runRoot -Recurse -Filter READY.json|Remove-Item -Force;[IO.Directory]::Move($runRoot,$blockedRoot)}
        elseif(Test-Path $stagingRoot){Get-ChildItem $stagingRoot -Recurse -Filter READY.json|Remove-Item -Force;[IO.Directory]::Move($stagingRoot,$blockedRoot)}
        if(Test-Path $blockedRoot){Write-JsonAtomic (Join-Path $blockedRoot 'BLOCKED.json') ([ordered]@{RunId=$auth.RunId;ExecutionStatus='BLOCKED';Consumable=$false;Error=$_.Exception.Message;AutomaticRetryAllowed=$false;ReadyCount=0})}
        throw
    }
}
