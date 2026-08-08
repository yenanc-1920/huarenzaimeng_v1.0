param(
    [Parameter(Mandatory=$true)][string]$RunId,
    [Parameter(Mandatory=$true)][ValidateSet('FINAL_RUN')][string]$Confirm,
    [Parameter(Mandatory=$true)][ValidatePattern('^[A-F0-9]{64}$')][string]$ExpectedRunnerAggregate,
    [Parameter(Mandatory=$true)][string]$AuthorizationRecord,
    [Parameter(Mandatory=$true)][ValidatePattern('^[A-F0-9]{64}$')][string]$ExpectedAuthorizationRecordSha
)
$ErrorActionPreference='Stop'; Set-StrictMode -Version Latest
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$runner='apps/api/src/test/java/com/huarenzaimeng/api/P021OrderDetailEvidenceFinalRunTest.java'
$wrapper='apps/api/Invoke-P021OrderDetailEvidenceFinalRun.ps1'
$evidenceRoot=Join-Path $repo '项目管理/正式交付/D4-开发计划与工程准备/证据/D5-ORD-03-P021后端技术证据'
$finalDir=Join-Path $evidenceRoot $RunId; $staging=Join-Path $evidenceRoot ('.staging-'+$RunId)
$publishing=Join-Path $evidenceRoot ('.publishing-'+$RunId); $blocked=Join-Path $evidenceRoot ('.blocked-'+$RunId)
$processTemp=Join-Path $evidenceRoot ('.process-'+$RunId)
$fixed=@{
 '项目管理/正式交付/D1-产品规划与需求/D1产品基线版本清单.md'='364F73B28E2BC1C4D3827D5B9332C1CEB6D93C928859A226CD8842FF283D9BD8'
 '项目管理/正式交付/D2-体验与UI设计/D2体验与UI设计版本清单.md'='516EA3ACA003BEF18A49D5961F13DCCE313CAD4C1B60C44964519CA793CE52BF'
 '项目管理/正式交付/D3-技术实现基线/D3-03-领域状态与项目API协议.md'='33CCC6BB94B2D2A95E798C7CAD44C839CC35D8B577727492C64F10776E6FDA95'
 '项目管理/正式交付/D3-技术实现基线/D3-04-数据账务与外部适配方案.md'='B12E780F19AB333458018C908324EADA6F84E1ECD35349FB739A7DC5AE22E4DB'
 '项目管理/正式交付/D3-技术实现基线/D3-05-安全可靠性与开发门禁.md'='CC27E94902FF2BCE86E02EE553F2E03F7E7752603AD247D826E619F929856282'
 '项目管理/正式交付/D3-技术实现基线/D3技术基线版本清单.md'='6321D16CAC17D426801F26F5AD643BAB418D08FF72FD299BCEA7FB20C9522A0C'
 '项目管理/正式交付/D4-开发计划与工程准备/D4-05-CI质量门禁与开发就绪检查表.md'='4054C8A4DA3C4D32BB7AF10F562827D399CB24380A474B765383791902617618'
 '项目管理/正式交付/D4-开发计划与工程准备/D4-08-当前开发波次测试与质量门禁清单.md'='13DE50B5306F6CA2AEDA735484772DFCDEC00CDC11E0BE082B43787CD2D1250B'
 '项目管理/正式交付/D4-开发计划与工程准备/评审记录/D5-ORD-03-P021-D4矩阵重绑-测试与质量工程师记录.md'='65F59B4F6F4A5A7A6757D65862D09492217A236859D02B3A026242FDEDA62468'
}
$fixedFieldNames=@{
 '项目管理/正式交付/D1-产品规划与需求/D1产品基线版本清单.md'='D1RegistrySha';'项目管理/正式交付/D2-体验与UI设计/D2体验与UI设计版本清单.md'='D2RegistrySha';'项目管理/正式交付/D3-技术实现基线/D3-03-领域状态与项目API协议.md'='D3_03Sha';'项目管理/正式交付/D3-技术实现基线/D3-04-数据账务与外部适配方案.md'='D3_04Sha';'项目管理/正式交付/D3-技术实现基线/D3-05-安全可靠性与开发门禁.md'='D3_05Sha';'项目管理/正式交付/D3-技术实现基线/D3技术基线版本清单.md'='D3RegistrySha';'项目管理/正式交付/D4-开发计划与工程准备/D4-05-CI质量门禁与开发就绪检查表.md'='D4_05Sha';'项目管理/正式交付/D4-开发计划与工程准备/D4-08-当前开发波次测试与质量门禁清单.md'='D4_08Sha';'项目管理/正式交付/D4-开发计划与工程准备/评审记录/D5-ORD-03-P021-D4矩阵重绑-测试与质量工程师记录.md'='D4RebindRecordSha'
}
$implementation='C4A8D77A5422354C546473DAF013893952349A447349B3E8CC9F89A884CF0ADE'
$identitySha='A0134787E0484705ADE5E32383D778D7851BC2DAD72FE9F6873752CBE639A8DE'
$authorizationScope='P021_TECHNICAL_EVIDENCE_18_SCENARIO_38_PARAMETER'
$authorizationRoot=(Join-Path $repo '项目管理/正式交付/D4-开发计划与工程准备/授权记录')
$writeKeys=@('Command','CommandAlias','TopupBusinessKey','TopupSemanticAction','TopupIntent','DispatchSemanticAction','DispatchIntent','OrderVersion','ProjectionVersion','SyntheticObservation','PaymentAttempt','SendAttempt','RemoteAcceptance','WechatPrepay','RequestPayment','Notification','ExternalFact','W','U','D','L','LedgerEntry','ExternalCall')
$allCountKeys=@($writeKeys)+@('QueryCall','FileWrite','QueueWrite','NotificationSend')
$implementationFiles=@(
'apps/api/src/main/java/com/huarenzaimeng/api/LocalSyntheticOrderRecoveryService.java','apps/api/src/main/java/com/huarenzaimeng/api/MockFlowController.java','apps/api/src/main/java/com/huarenzaimeng/api/P021OrderDetailBoundaryAdapters.java','apps/api/src/main/java/com/huarenzaimeng/api/P021OrderDetailController.java','apps/api/src/main/java/com/huarenzaimeng/api/P021OrderDetailDomain.java','apps/api/src/main/java/com/huarenzaimeng/api/P021OrderDetailFixtureLoader.java','apps/api/src/main/java/com/huarenzaimeng/api/P021OrderDetailService.java','apps/api/src/main/java/com/huarenzaimeng/api/P021OrderDetailSideEffectProbe.java','apps/api/src/main/java/com/huarenzaimeng/api/config/TestAccessTokenFilter.java','apps/api/src/test/java/com/huarenzaimeng/api/MockFlowApiContractTest.java','apps/api/src/test/java/com/huarenzaimeng/api/P021OrderDetailApiContractTest.java')
$ids=@(
'ORD03-P021-001-AWAITING-PAYMENT|ORD03-P021-001-AWAITING-PAYMENT|P001-STATE','ORD03-P021-002-PAYMENT-PROCESSING|ORD03-P021-002-PAYMENT-PROCESSING|P001-STATE','ORD03-P021-003-PAID-AWAITING-TOPUP|ORD03-P021-003-PAID-AWAITING-TOPUP|P001-STATE','ORD03-P021-004-TOPUP-PROCESSING|ORD03-P021-004-TOPUP-PROCESSING|P001-STATE','ORD03-P021-005-TOPUP-UNKNOWN|ORD03-P021-005-TOPUP-UNKNOWN|P001-SUPPORT','ORD03-P021-005-TOPUP-UNKNOWN|ORD03-P021-005-TOPUP-UNKNOWN|P002-NO-AUTO','ORD03-P021-006-DELIVERED|ORD03-P021-006-DELIVERED|P001-STATE','ORD03-P021-007-CONFIRMED-NOT-DELIVERED|ORD03-P021-007-CONFIRMED-NOT-DELIVERED|P001-SUPPORT','ORD03-P021-008-REFUND-PROCESSING|ORD03-P021-008-REFUND-PROCESSING|P001-STATE','ORD03-P021-009-REFUNDED|ORD03-P021-009-REFUNDED|P001-STATE','ORD03-P021-010-DELIVERY-REFUND-CONFLICT|ORD03-P021-010-DELIVERY-REFUND-CONFLICT|P001-SUPPORT','ORD03-P021-011-SUPPORT-REVIEW|ORD03-P021-011-SUPPORT-REVIEW|P001-SUPPORT',
'ORD03-P021-012-EXISTENCE-SAME-SHAPE|ORD03-P021-012-EXISTENCE-SAME-SHAPE|P001-UNAUTH','ORD03-P021-012-EXISTENCE-SAME-SHAPE|ORD03-P021-012-EXISTENCE-SAME-SHAPE|P002-CROSS-SUBJECT','ORD03-P021-012-EXISTENCE-SAME-SHAPE|ORD03-P021-012-EXISTENCE-SAME-SHAPE|P003-NOT-FOUND','ORD03-P021-012-EXISTENCE-SAME-SHAPE|ORD03-P021-012-EXISTENCE-SAME-SHAPE|P004-REVOKED','ORD03-P021-013-SESSION-VERSION-DRIFT|ORD03-P021-013-SESSION-VERSION-DRIFT|P001-EXPIRED','ORD03-P021-013-SESSION-VERSION-DRIFT|ORD03-P021-013-SESSION-VERSION-DRIFT|P002-MISMATCH','ORD03-P021-014-AUTHORIZATION-SET-DRIFT|ORD03-P021-014-AUTHORIZATION-SET-DRIFT|P001-SET','ORD03-P021-014-AUTHORIZATION-SET-DRIFT|ORD03-P021-014-AUTHORIZATION-SET-DRIFT|P002-EVIDENCE','ORD03-P021-014-AUTHORIZATION-SET-DRIFT|ORD03-P021-014-AUTHORIZATION-SET-DRIFT|P003-ORDERREF','ORD03-P021-015-MONOTONIC-TIMELINE|ORD03-P021-015-MONOTONIC-TIMELINE|P001-HIGHER','ORD03-P021-015-MONOTONIC-TIMELINE|ORD03-P021-015-MONOTONIC-TIMELINE|P002-LATE-LOWER','ORD03-P021-015-MONOTONIC-TIMELINE|ORD03-P021-015-MONOTONIC-TIMELINE|P003-ROLLBACK','ORD03-P021-015-MONOTONIC-TIMELINE|ORD03-P021-015-MONOTONIC-TIMELINE|P004-CONFLICT',
'ORD03-P021-016-STRICT-DTO|ORD03-P021-016-STRICT-DTO|P001-MISSING','ORD03-P021-016-STRICT-DTO|ORD03-P021-016-STRICT-DTO|P002-ADDITIONAL','ORD03-P021-016-STRICT-DTO|ORD03-P021-016-STRICT-DTO|P003-UNKNOWN-ENUM','ORD03-P021-016-STRICT-DTO|ORD03-P021-016-STRICT-DTO|P004-NULL','ORD03-P021-016-STRICT-DTO|ORD03-P021-016-STRICT-DTO|P005-TYPE','ORD03-P021-016-STRICT-DTO|ORD03-P021-016-STRICT-DTO|P006-CROSS-FIELD','ORD03-P021-017-PRICE-SNAPSHOT-IDENTITY|ORD03-P021-017-PRICE-SNAPSHOT-IDENTITY|P001-SAME','ORD03-P021-017-PRICE-SNAPSHOT-IDENTITY|ORD03-P021-017-PRICE-SNAPSHOT-IDENTITY|P002-DIGEST-DRIFT','ORD03-P021-017-PRICE-SNAPSHOT-IDENTITY|ORD03-P021-017-PRICE-SNAPSHOT-IDENTITY|P003-UNMASKED','ORD03-P021-017-PRICE-SNAPSHOT-IDENTITY|ORD03-P021-017-PRICE-SNAPSHOT-IDENTITY|P004-MISSING','ORD03-P021-018-READ-ZERO-SIDE-EFFECT|ORD03-P021-018-READ-ZERO-SIDE-EFFECT|P001-SUCCESS','ORD03-P021-018-READ-ZERO-SIDE-EFFECT|ORD03-P021-018-READ-ZERO-SIDE-EFFECT|P002-REJECTED','ORD03-P021-018-READ-ZERO-SIDE-EFFECT|ORD03-P021-018-READ-ZERO-SIDE-EFFECT|P003-ERROR')
function Sha([string]$p){(Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $repo $p)).Hash.ToUpperInvariant()}
function Aggregate([string[]]$paths){$sorted=[string[]]$paths.Clone();[Array]::Sort($sorted,[StringComparer]::Ordinal);$lines=@();foreach($p in $sorted){$lines+=($p+'|'+(Sha $p))};$bytes=[Text.UTF8Encoding]::new($false).GetBytes([string]::Join("`n",$lines));$a=[Security.Cryptography.SHA256]::Create();try{([BitConverter]::ToString($a.ComputeHash($bytes))).Replace('-','')}finally{$a.Dispose()}}
function TextSha([string[]]$lines){$sorted=[string[]]$lines.Clone();[Array]::Sort($sorted,[StringComparer]::Ordinal);$bytes=[Text.UTF8Encoding]::new($false).GetBytes([string]::Join("`n",$sorted));$a=[Security.Cryptography.SHA256]::Create();try{([BitConverter]::ToString($a.ComputeHash($bytes))).Replace('-','')}finally{$a.Dispose()}}
function ExactKeys($object,[string[]]$expected){if($null-eq$object){return $false};$actual=[string[]]@($object.PSObject.Properties.Name);[Array]::Sort($actual,[StringComparer]::Ordinal);$copy=[string[]]$expected.Clone();[Array]::Sort($copy,[StringComparer]::Ordinal);return ([string]::Join("`n",$actual)-ceq[string]::Join("`n",$copy))}
function IsInteger($value){return $value-is[byte]-or$value-is[int16]-or$value-is[int32]-or$value-is[int64]-or$value-is[uint16]-or$value-is[uint32]-or$value-is[uint64]}
function PropertyValue($object,[string]$name){$property=@($object.PSObject.Properties|Where-Object{$_.Name-ceq$name});if($property.Count-ne 1){return $null};return $property[0].Value}
function ConsumeAuthorization([string]$marker,[hashtable]$payload){$bytes=[Text.UTF8Encoding]::new($false).GetBytes(($payload|ConvertTo-Json -Compress));$stream=[IO.File]::Open($marker,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None);try{$stream.Write($bytes,0,$bytes.Length);$stream.Flush($true)}finally{$stream.Dispose()}}
function Block([string]$reason,[hashtable]$extra){$exception=[InvalidOperationException]::new($reason);$exception.Data['BlockExtra']=$extra;throw $exception}
function AssertBlockedJsonTree([string]$root){
 if(-not(Test-Path -LiteralPath $root)){return}
 if(@(Get-ChildItem -LiteralPath $root -Recurse -File -Filter 'READY').Count-ne 0){throw ('BLOCKED_READY_REVOCATION_FAILED|'+$root)}
 foreach($file in @(Get-ChildItem -LiteralPath $root -Recurse -File -Filter '*.json')){
  $json=Get-Content -Raw -LiteralPath $file.FullName|ConvertFrom-Json -ErrorAction Stop
  if($json.Consumable-ne $false-or$json.ExecutionStatus-ne'BLOCKED'){throw ('BLOCKED_JSON_REVOCATION_VERIFY_FAILED|'+$file.FullName)}
 }
}
function RevokeJsonTree([string]$root,[string]$isolationRoot){
 if(-not(Test-Path -LiteralPath $root)){return}
 try {
  Get-ChildItem -LiteralPath $root -Recurse -File -Filter 'READY'|Remove-Item -Force -ErrorAction Stop
  foreach($file in @(Get-ChildItem -LiteralPath $root -Recurse -File -Filter '*.json')){
   $json=Get-Content -Raw -LiteralPath $file.FullName|ConvertFrom-Json -ErrorAction Stop
   if($json.PSObject.Properties.Name-contains'Consumable'){$json.Consumable=$false}else{$json|Add-Member -NotePropertyName Consumable -NotePropertyValue $false}
   if($json.PSObject.Properties.Name-contains'ExecutionStatus'){$json.ExecutionStatus='BLOCKED'}else{$json|Add-Member -NotePropertyName ExecutionStatus -NotePropertyValue 'BLOCKED'}
   $json|ConvertTo-Json -Depth 100|Set-Content -LiteralPath $file.FullName -Encoding utf8 -ErrorAction Stop
  }
  AssertBlockedJsonTree $root
 } catch {
  $revocationFailure=$_
  New-Item -ItemType Directory -Force -Path $isolationRoot|Out-Null
  $quarantine=Join-Path $isolationRoot ('NONCONSUMABLE-REVOCATION-FAILED-'+[IO.Path]::GetFileName($root)+'-'+[Guid]::NewGuid().ToString('N'))
  Move-Item -LiteralPath $root -Destination $quarantine -ErrorAction Stop
  if(Test-Path -LiteralPath $root){throw ('BLOCKED_NAMESPACE_ISOLATION_FAILED|'+$root)}
  try {
   Get-ChildItem -LiteralPath $quarantine -Recurse -File -Filter 'READY'|Remove-Item -Force -ErrorAction Stop
   foreach($file in @(Get-ChildItem -LiteralPath $quarantine -Recurse -File -Filter '*.json')){
    $json=Get-Content -Raw -LiteralPath $file.FullName|ConvertFrom-Json -ErrorAction Stop
    if($json.PSObject.Properties.Name-contains'Consumable'){$json.Consumable=$false}else{$json|Add-Member -NotePropertyName Consumable -NotePropertyValue $false}
    if($json.PSObject.Properties.Name-contains'ExecutionStatus'){$json.ExecutionStatus='BLOCKED'}else{$json|Add-Member -NotePropertyName ExecutionStatus -NotePropertyValue 'BLOCKED'}
    $json|ConvertTo-Json -Depth 100|Set-Content -LiteralPath $file.FullName -Encoding utf8 -ErrorAction Stop
   }
   AssertBlockedJsonTree $quarantine
  } catch {
   throw ('BLOCKED_REVOCATION_FAILED_NONCONSUMABLE_PATH_GATE|'+$quarantine+'|'+$revocationFailure.Exception.Message+'|'+$_.Exception.Message)
  }
  throw ('BLOCKED_JSON_REVOCATION_FAILED_ISOLATED|'+$quarantine+'|'+$revocationFailure.Exception.Message)
 }
}
function AssertRevokedNamespace([string]$root){if(Test-Path -LiteralPath $root){AssertBlockedJsonTree $root}}
try {
$implementationFileShas=@{};foreach($path in $implementationFiles){$implementationFileShas[$path]=Sha $path}
if($RunId -notmatch '^P021-BE-[A-Za-z0-9._-]{8,100}$'){throw 'invalid RunId'}
New-Item -ItemType Directory -Force -Path $evidenceRoot|Out-Null
if((Test-Path $finalDir)-or(Test-Path $staging)-or(Test-Path $publishing)-or(Test-Path $blocked)-or(Test-Path $processTemp)){throw 'RunId already used'}
foreach($entry in $fixed.GetEnumerator()){if((Sha $entry.Key)-ne $entry.Value){Block 'BLOCKED_VERSION_DRIFT' @{Path=$entry.Key}}}
if((Aggregate $implementationFiles)-ne $implementation){Block 'BLOCKED_IMPLEMENTATION_DRIFT' @{}}
if((TextSha $ids)-ne $identitySha){Block 'BLOCKED_IDENTITY_DRIFT' @{}}
if((Aggregate @($runner,$wrapper))-ne $ExpectedRunnerAggregate){Block 'BLOCKED_RUNNER_DRIFT' @{}}
$authorizationPath=(Resolve-Path -LiteralPath $AuthorizationRecord).Path
if(-not $authorizationPath.StartsWith($authorizationRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)-or[IO.Path]::GetExtension($authorizationPath)-ne'.json'){Block 'BLOCKED_AUTHORIZATION_LOCATION' @{}}
$authorizationSha=(Get-FileHash -Algorithm SHA256 -LiteralPath $authorizationPath).Hash.ToUpperInvariant();if($authorizationSha-ne$ExpectedAuthorizationRecordSha){Block 'BLOCKED_AUTHORIZATION_SHA' @{}}
$authorization=Get-Content -Raw -LiteralPath $authorizationPath|ConvertFrom-Json
$requiredAuth=@('AuthorizationRef','ExecutionScope','RunId','ValidFrom','ValidUntil','ImplementationAggregateSha','MatrixIdentitySha','RunnerAggregateSha','SingleUse','Status')
if(-not(ExactKeys $authorization $requiredAuth)){Block 'BLOCKED_AUTHORIZATION_SCHEMA' @{}}
$now=[DateTimeOffset]::UtcNow;$validFrom=[DateTimeOffset]::ParseExact($authorization.ValidFrom,'o',[Globalization.CultureInfo]::InvariantCulture);$validUntil=[DateTimeOffset]::ParseExact($authorization.ValidUntil,'o',[Globalization.CultureInfo]::InvariantCulture)
if($authorization.ExecutionScope-ne$authorizationScope-or$authorization.RunId-ne$RunId-or$authorization.ImplementationAggregateSha-ne$implementation-or$authorization.MatrixIdentitySha-ne$identitySha-or$authorization.RunnerAggregateSha-ne$ExpectedRunnerAggregate-or$authorization.SingleUse-ne$true-or$authorization.Status-ne'APPROVED'-or$now-lt$validFrom-or$now-gt$validUntil){Block 'BLOCKED_AUTHORIZATION_SCOPE_OR_EXPIRY' @{AuthorizationRef=$authorization.AuthorizationRef}}
$authorizationMarker=$authorizationPath+'.consumed.'+$RunId+'.json'
if(Test-Path $authorizationMarker){Block 'BLOCKED_AUTHORIZATION_ALREADY_CONSUMED' @{AuthorizationRef=$authorization.AuthorizationRef}}
try{ConsumeAuthorization $authorizationMarker @{AuthorizationRef=$authorization.AuthorizationRef;RunId=$RunId;ConsumedAt=$now.ToString('o');AuthorizationRecordSha=$authorizationSha;Scope=$authorizationScope}}catch{Block 'BLOCKED_AUTHORIZATION_ATOMIC_CONSUME' @{AuthorizationRef=$authorization.AuthorizationRef;Error=$_.Exception.Message}}
New-Item -ItemType Directory -Path $staging|Out-Null
New-Item -ItemType Directory -Path $processTemp|Out-Null
$stdout=Join-Path $processTemp 'process.stdout.txt';$stderr=Join-Path $processTemp 'process.stderr.txt'
$args=@('-pl','apps/api','-am','-Dtest=P021OrderDetailEvidenceFinalRunTest','-Dsurefire.failIfNoSpecifiedTests=false','-Dp021.evidence.confirm=FINAL_RUN',('-Dp021.evidence.runId='+$RunId),('-Dp021.evidence.authorizationRef='+$authorization.AuthorizationRef),('-Dp021.evidence.authorizationRecordSha='+$authorizationSha),('-Dp021.evidence.staging='+$staging),'test')
$started=[DateTimeOffset]::UtcNow;$p=Start-Process -FilePath 'mvn' -ArgumentList $args -WorkingDirectory $repo -NoNewWindow -Wait -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr;$ended=[DateTimeOffset]::UtcNow
$process=@{Command='mvn '+($args -join ' ');StartedAt=$started.ToString('o');EndedAt=$ended.ToString('o');OsExitCode=$p.ExitCode;StdoutSha=(Get-FileHash $stdout -Algorithm SHA256).Hash;StderrSha=(Get-FileHash $stderr -Algorithm SHA256).Hash}
if($p.ExitCode-ne 0){Block 'BLOCKED_TEST_PROCESS_FAILED' $process}
$caseFiles=@(Get-ChildItem -LiteralPath $staging -Filter '*.json');if($caseFiles.Count-ne 38){Block 'BLOCKED_EVIDENCE_COUNT' @{Actual=$caseFiles.Count}}
$actualIds=@();foreach($f in $caseFiles){$j=Get-Content -Raw -LiteralPath $f.FullName|ConvertFrom-Json;if($j.Consumable-ne $false-or$j.ExecutionStatus-ne'PASS'-or$j.ExecutionRunId-ne$RunId-or$j.AuthorizationRecordRef-ne$authorization.AuthorizationRef-or$j.AuthorizationRecordSha-ne$authorizationSha-or$j.ProcessEvidenceRef-ne'process-evidence.json'){Block 'BLOCKED_CASE_STATE' @{File=$f.Name}};if($null-eq$j.Input-or$null-eq$j.Expected-or$null-eq$j.Actual-or$null-eq$j.CompleteResponse-or$null-eq$j.Before-or$null-eq$j.After-or$null-eq$j.Delta-or$null-eq$j.Input.FixtureDigest-or$null-eq$j.Input.CompleteRequest-or$null-eq$j.Input.FixedInputs-or$null-eq$j.Input.ImplementationFiles-or$j.Input.AuthorizationRef-ne$authorization.AuthorizationRef){Block 'BLOCKED_CASE_INCOMPLETE' @{File=$f.Name}};if(-not(ExactKeys $j.Input.FixedInputs @('D1RegistrySha','D2RegistrySha','D3_03Sha','D3_04Sha','D3_05Sha','D3RegistrySha','D4_05Sha','D4_08Sha','D4RebindRecordSha'))-or-not(ExactKeys $j.Input.ImplementationFiles $implementationFiles)){Block 'BLOCKED_CASE_VERSION_KEYS' @{File=$f.Name}};foreach($entry in $fixed.GetEnumerator()){$name=$fixedFieldNames[$entry.Key];if((PropertyValue $j.Input.FixedInputs $name)-ne$entry.Value){Block 'BLOCKED_CASE_VERSION_VALUE' @{File=$f.Name;Key=$name}}};foreach($path in $implementationFiles){if((PropertyValue $j.Input.ImplementationFiles $path)-ne$implementationFileShas[$path]){Block 'BLOCKED_CASE_IMPLEMENTATION_SHA' @{File=$f.Name;Path=$path}}};if(-not(ExactKeys $j.WriteDelta23 $writeKeys)-or-not(ExactKeys $j.Before $allCountKeys)-or-not(ExactKeys $j.After $allCountKeys)-or-not(ExactKeys $j.Delta $allCountKeys)){Block 'BLOCKED_COUNTER_KEYS' @{File=$f.Name}};foreach($key in $allCountKeys){$b=PropertyValue $j.Before $key;$a=PropertyValue $j.After $key;$d=PropertyValue $j.Delta $key;if(-not(IsInteger $b)-or-not(IsInteger $a)-or-not(IsInteger $d)-or($a-$b)-ne$d){Block 'BLOCKED_COUNTER_ARITHMETIC' @{File=$f.Name;Key=$key}}};foreach($key in $writeKeys){if((PropertyValue $j.WriteDelta23 $key)-ne(PropertyValue $j.Delta $key)-or(PropertyValue $j.Delta $key)-ne 0){Block 'BLOCKED_WRITE_DELTA' @{File=$f.Name;Key=$key}}};if(-not(IsInteger $j.QueryCallDelta)-or$j.QueryCallDelta-ne(PropertyValue $j.Delta 'QueryCall')-or$j.QueryCallDelta-ne 1-or(PropertyValue $j.Delta 'FileWrite')-ne 0-or(PropertyValue $j.Delta 'QueueWrite')-ne 0-or(PropertyValue $j.Delta 'NotificationSend')-ne 0){Block 'BLOCKED_QUERY_OR_AUX_DELTA' @{File=$f.Name}};$actualIds+=($j.ScenarioId+'|'+$j.SubcaseId+'|'+$j.ParameterId)}
if((TextSha $actualIds)-ne $identitySha){Block 'BLOCKED_CASE_IDENTITY' @{}}
try {
 New-Item -ItemType Directory -Path $publishing|Out-Null
 $index=@();foreach($f in $caseFiles){$j=Get-Content -Raw -LiteralPath $f.FullName|ConvertFrom-Json;$j.Consumable=$true;$target=Join-Path $publishing $f.Name;$j|ConvertTo-Json -Depth 100|Set-Content -LiteralPath $target -Encoding utf8;$index+=@{File=$f.Name;Sha256=(Get-FileHash $target -Algorithm SHA256).Hash;Identity=$j.ScenarioId+'|'+$j.SubcaseId+'|'+$j.ParameterId}}
 $process|ConvertTo-Json -Depth 20|Set-Content -LiteralPath (Join-Path $publishing 'process-evidence.json') -Encoding utf8
 Copy-Item -LiteralPath $stdout -Destination (Join-Path $publishing 'process.stdout.txt')
 Copy-Item -LiteralPath $stderr -Destination (Join-Path $publishing 'process.stderr.txt')
 if(-not(Test-Path (Join-Path $publishing 'process-evidence.json'))){Block 'BLOCKED_PROCESS_EVIDENCE_REF' @{}}
 $index|ConvertTo-Json -Depth 20|Set-Content -LiteralPath (Join-Path $publishing 'index.json') -Encoding utf8
 $manifest=@{Consumable=$true;ExecutionStatus='PASS';RunId=$RunId;ScenarioCount=18;ParameterCount=38;IdentitySetSha=$identitySha;ImplementationAggregateSha=$implementation;D3RegistrySha=$fixed['项目管理/正式交付/D3-技术实现基线/D3技术基线版本清单.md'];RunnerAggregateSha=$ExpectedRunnerAggregate;AuthorizationRecordRef=$authorization.AuthorizationRef;AuthorizationRecordSha=$authorizationSha;AuthorizationConsumptionRef=[IO.Path]::GetFileName($authorizationMarker);ProcessEvidenceRef='process-evidence.json'}
 $manifest|ConvertTo-Json -Depth 20|Set-Content -LiteralPath (Join-Path $publishing 'manifest.json') -Encoding utf8
 $packageFiles=@(Get-ChildItem -LiteralPath $publishing -File|Sort-Object Name);$packageLines=@($packageFiles|ForEach-Object{$_.Name+'|'+(Get-FileHash $_.FullName -Algorithm SHA256).Hash});$packageSha=TextSha $packageLines
 @{PackageSha256=$packageSha;FileCount=$packageFiles.Count}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $publishing 'package.json') -Encoding utf8
 Remove-Item -LiteralPath $staging -Recurse -Force
 Remove-Item -LiteralPath $processTemp -Recurse -Force
 'READY'|Set-Content -LiteralPath (Join-Path $publishing 'READY') -NoNewline -Encoding ascii
 Move-Item -LiteralPath $publishing -Destination $finalDir
 $publishedResult=('PUBLISHED|'+$RunId+'|'+$packageSha)
} catch {
 RevokeJsonTree $publishing $blocked
 Block 'BLOCKED_ATOMIC_PUBLISH' @{Error=$_.Exception.Message}
}
} catch {
 $failure=$_
 New-Item -ItemType Directory -Force -Path $blocked|Out-Null
 foreach($candidate in @($publishing,$finalDir)){RevokeJsonTree $candidate $blocked}
 AssertRevokedNamespace $publishing;AssertRevokedNamespace $finalDir
 foreach($source in @($processTemp,$publishing)){if(Test-Path $source){Get-ChildItem -LiteralPath $source -File|Where-Object{$_.Extension-ne'.json'}|Copy-Item -Destination $blocked -Force -ErrorAction SilentlyContinue}}
 $blockedRecord=@{Consumable=$false;ExecutionStatus='BLOCKED';Reason=$failure.Exception.Message;RunId=$RunId;At=[DateTimeOffset]::UtcNow.ToString('o')}
 if($failure.Exception.Data.Contains('BlockExtra')){$blockedRecord.Extra=$failure.Exception.Data['BlockExtra']}
 $blockedRecord|ConvertTo-Json -Depth 30|Set-Content -LiteralPath (Join-Path $blocked 'BLOCKED.json') -Encoding utf8
 throw
}
Write-Output $publishedResult
