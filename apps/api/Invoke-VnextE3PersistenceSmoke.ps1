param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^https://')]
    [string]$BaseUrl,

    [Parameter(Mandatory = $true)]
    [Security.SecureString]$TestAccessToken,

    [Parameter(Mandatory = $true)]
    [Security.SecureString]$ContentAdminToken
)

$ErrorActionPreference = 'Stop'
$base = $BaseUrl.TrimEnd('/')
$tokenPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($TestAccessToken)
$contentTokenPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($ContentAdminToken)

try {
    $token = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($tokenPointer)
    $contentToken = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($contentTokenPointer)
    $headers = @{
        'X-HZM-Test-Access-Token' = $token
        'X-Project-Subject-Ref' = 'SYN-SUBJECT-VNEXT-E3-20260802'
    }

    $health = Invoke-RestMethod -Method Get -Uri "$base/actuator/health"
    if ($health.status -ne 'UP') { throw 'health status is not UP' }

    $catalog = Invoke-RestMethod -Method Get -Uri "$base/api/v1/catalog?operatorCode=SYN-VNEXT-OP" `
        -Headers $headers
    if ($catalog.status -ne 'ACCEPTED' -or $catalog.data.operatorQualification -ne 'SUPPORTED' -or
        [long]$catalog.data.supportedOperatorSetVersion -ne 8602001 -or
        [long]$catalog.data.catalogVersion -ne 8602001 -or $catalog.data.items.Count -ne 1) {
        throw 'E3 catalog fixture is not the active MySQL projection'
    }

    $request = [ordered]@{
        phone = '8801700000000'
        operatorCode = 'SYN-VNEXT-OP'
        productRef = 'SYN-VNEXT-PRODUCT-1000'
        denominationRef = 'SYN-VNEXT-DENOM-1000'
        supportedOperatorSetVersion = 8602001
        catalogVersion = 8602001
        commandId = 'CMD-E3-VNEXT-QUOTE-20260802'
        idempotencyKey = 'IDEM-E3-VNEXT-QUOTE-20260802'
        mnpState = 'CONFIRMED'
    }
    $json = $request | ConvertTo-Json -Compress
    $first = Invoke-RestMethod -Method Post -Uri "$base/api/v1/quotes" -Headers $headers `
        -ContentType 'application/json' -Body $json
    if ($first.status -ne 'ACCEPTED' -or [string]::IsNullOrWhiteSpace($first.data.quoteRef)) {
        throw 'first quote write was not accepted'
    }

    $replay = Invoke-RestMethod -Method Post -Uri "$base/api/v1/quotes" -Headers $headers `
        -ContentType 'application/json' -Body $json
    if ($replay.data.quoteRef -ne $first.data.quoteRef) {
        throw 'exact replay returned a different quoteRef'
    }

    $conflictRequest = $request.Clone()
    $conflictRequest.phone = '8801700000999'
    $conflictStatus = $null
    $conflictCode = $null
    try {
        Invoke-RestMethod -Method Post -Uri "$base/api/v1/quotes" -Headers $headers `
            -ContentType 'application/json' -Body ($conflictRequest | ConvertTo-Json -Compress) | Out-Null
        throw 'same keys with different input unexpectedly succeeded'
    } catch {
        if ($_.Exception.Response) {
            $conflictStatus = [int]$_.Exception.Response.StatusCode
            $reader = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
            try { $conflictCode = ($reader.ReadToEnd() | ConvertFrom-Json).projectCode }
            finally { $reader.Dispose() }
        } else { throw }
    }
    if ($conflictStatus -ne 409 -or $conflictCode -ne 'IDEMPOTENCY_CONFLICT') {
        throw "unexpected conflict response: HTTP $conflictStatus / $conflictCode"
    }

    $contentHeaders = @{ 'X-HZM-Test-Access-Token' = $contentToken }
    $contentRef = 'SYN-CONTENT-VNEXT-E3-20260802'
    $verifiedAt = [DateTime]::UtcNow.AddMinutes(-1).ToString('o')
    $validUntil = [DateTime]::UtcNow.AddDays(1).ToString('o')
    $reviewBody = [ordered]@{
        commandId = 'CMD-E3-VNEXT-CONTENT-REVIEW-20260802'
        idempotencyKey = 'IDEM-E3-VNEXT-CONTENT-REVIEW-20260802'
        expectedAggregateVersion = 1
        action = 'RECORD_REVIEW'
        reason = 'E3_SYNTHETIC_REVIEW_ONLY'
        sourceCategory = 'SELF_RESEARCH'
        sourceRef = 'SYN-SOURCE-VNEXT-20260802'
        verificationScope = 'NAME_AND_PUBLIC_CONTACT_CHANNELS'
        verifiedBy = 'SYN-REVIEWER-VNEXT'
        verifiedAt = $verifiedAt
        validUntil = $validUntil
    } | ConvertTo-Json -Compress
    $reviewed = Invoke-RestMethod -Method Post `
        -Uri "$base/project-api/v1/internal/content/items/$contentRef/transitions" `
        -Headers $contentHeaders -ContentType 'application/json' -Body $reviewBody
    if ($reviewed.status -ne 'ACCEPTED' -or $reviewed.data.state -ne 'VERIFIED' -or
        [long]$reviewed.data.version -ne 2) { throw 'content review transition failed' }

    $publishBody = [ordered]@{
        commandId = 'CMD-E3-VNEXT-CONTENT-PUBLISH-20260802'
        idempotencyKey = 'IDEM-E3-VNEXT-CONTENT-PUBLISH-20260802'
        expectedAggregateVersion = 2
        action = 'PUBLISH'
        reason = 'E3_SYNTHETIC_PUBLIC_READ_SMOKE_ONLY'
    } | ConvertTo-Json -Compress
    $published = Invoke-RestMethod -Method Post `
        -Uri "$base/project-api/v1/internal/content/items/$contentRef/transitions" `
        -Headers $contentHeaders -ContentType 'application/json' -Body $publishBody
    if ($published.status -ne 'ACCEPTED' -or $published.data.state -ne 'PUBLISHED' -or
        [long]$published.data.version -ne 3) { throw 'content publish transition failed' }

    $publicContent = Invoke-RestMethod -Method Get `
        -Uri "$base/project-api/v1/content/items/$contentRef?contentVersion=3"
    if ($publicContent.status -ne 'ACCEPTED' -or $publicContent.data.contentRef -ne $contentRef) {
        throw 'published synthetic content was not readable'
    }

    $reportBody = [ordered]@{
        commandId = 'CMD-E3-VNEXT-CONTENT-REPORT-20260802'
        idempotencyKey = 'IDEM-E3-VNEXT-CONTENT-REPORT-20260802'
        contentVersion = 3
        expectedAggregateVersion = 3
        reason = 'E3_SYNTHETIC_REPORT_ONLY'
    } | ConvertTo-Json -Compress
    $reported = Invoke-RestMethod -Method Post `
        -Uri "$base/project-api/v1/content/items/$contentRef/reports" `
        -ContentType 'application/json' -Body $reportBody
    if ($reported.status -ne 'ACCEPTED' -or $reported.data.status -ne 'CONTENT_ERROR_REPORTED') {
        throw 'synthetic content report failed'
    }

    [pscustomobject]@{
        ExecutionStatus = 'API_PHASE_PASS_SQL_VERIFICATION_REQUIRED'
        Environment = 'E3_SYNTHETIC_ONLY'
        SubjectRef = 'SYN-SUBJECT-VNEXT-E3-20260802'
        OperatorCode = 'SYN-VNEXT-OP'
        CatalogVersion = 8602001
        QuoteRef = $first.data.quoteRef
        ReplayQuoteRef = $replay.data.quoteRef
        ConflictHttpStatus = $conflictStatus
        ConflictProjectCode = $conflictCode
        ContentRef = $contentRef
        ContentFinalVersion = 4
        ContentFinalState = 'UNDER_REVIEW'
        NextStep = 'Run the read-only quote/command queries in VnextE3CatalogFixture.sql'
    }
} finally {
    $token = $null
    $contentToken = $null
    if ($tokenPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($tokenPointer)
    }
    if ($contentTokenPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($contentTokenPointer)
    }
}
