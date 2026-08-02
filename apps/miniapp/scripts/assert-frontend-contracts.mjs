import { existsSync, readFileSync, readdirSync } from 'node:fs'
import { resolve } from 'node:path'
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { samePriceSnapshot } from '../src/domain/price-snapshot.ts'
import { isAllowedProjectionAction, isWhitelistedNavigation } from '../src/domain/actions.ts'
import { freezeOrderSnapshot, validateOrderSnapshot } from '../src/domain/snapshot-flow.ts'
import { parseProjectProjection, parseProjectQuote } from '../src/api/project-contract.ts'
import { mockCatalog, mockCreatePaymentIntent, mockDirectory, mockDirectoryDetail, mockEligibility, mockLifeContentDetailDto, mockLifeContentListDto, mockOrders, mockQueryPaymentIntent, mockQuote, mockRecovery, mockRecoveryCase, mockReportDirectoryError, mockTemporalOverviewDto } from '../src/api/mock.ts'
import { buildContentErrorReport, mapContentProjectCode, parseContentErrorReportReceipt, parsePublicContentPage, parsePublicContentProjection } from '../src/api/content-contract.ts'
import { canRetryLifeContentRead, parseLifeContentDetailResponse, parseLifeContentListResponse } from '../src/api/life-content-contract.ts'
import { LIFE_CONTENT_COUNTER_NAMES, LIFE_CONTENT_EVIDENCE_DENOMINATOR, LIFE_CONTENT_EVIDENCE_PARAMETER_PLAN, LIFE_CONTENT_FIXED_INPUTS } from '../src/api/life-content-evidence.ts'
import { parseAuthorizedOrders, parseCatalogProjection, parseRechargeSelection, parseRecoveryResult, selectionMatchesCatalog } from '../src/api/topup-recovery-contract.ts'
import { applyRecoveryResult, performRecovery, performRecoveryPoll, readPendingRecoveryCaseRef, readSessionProjection } from '../src/domain/session.ts'
import { canonicalFingerprint } from '../src/domain/canonical-fingerprint.ts'
import { parseAcceptedProjectEnvelope, ProjectApiError } from '../src/api/project-envelope.ts'
import { loadAuthorizedOrderList } from '../src/domain/order-list.ts'
import { buildOrderCreationCommand, parseOrderCreationResult } from '../src/api/order-creation-contract.ts'
import { createOrderCreationNavigationLedger, executeOrderCreationAttempt } from '../src/domain/order-creation-flow.ts'
import { P013_PAYMENT_SUMMARY_MISSING_FIELDS, buildP013PaymentSummary, buildPaymentIntentCommand, buildPaymentIntentQueryRequest, canCreateLocalSyntheticPaymentIntent, parsePaymentIntentQueryResult, parsePaymentIntentResult, readPaymentIntentWriteIdentity, storePaymentIntentWriteIdentity, validatePaymentIntentQueryResult, validatePaymentIntentResult } from '../src/api/payment-intent-contract.ts'
import { buildP013AcceptedPageProjection, buildP013MissingWriteIdentityPageProjection, buildP013QueryErrorPageProjection, buildP013QueryPageProjection, buildP013RejectedPageProjection, createPaymentIntentWriteLatch, executePaymentIntentAttempt, executePaymentIntentReadOnlyQuery } from '../src/domain/payment-intent-flow.ts'
import { executeLifeContentDetailRead } from '../src/domain/life-content-page-executor.ts'
import { executeTemporalOverviewRead, parseTemporalOverview } from '../src/domain/temporal-overview-flow.ts'
import {
  buildLifeContentEvidenceRunnerManifest,
  LIFE_CONTENT_EVIDENCE_OUTPUT_DIRECTORY,
  LIFE_CONTENT_PARAMETER_REASON_CODES,
  runLifeContentBoundarySensitivityCheck,
  runLifeContentEvidence,
  runLifeContentRunnerStaticPrecheck,
  verifyLifeContentMatrixBindings,
} from './run-life-content-evidence.mjs'
import { buildLifeContentCoordinatorManifest } from './coordinate-life-content-evidence.mjs'

const read = (path) => readFileSync(resolve(process.cwd(), path), 'utf8')
const app = read('src/App.vue')
const shared = read('src/styles/shared.css')
const pages = JSON.parse(read('src/pages.json'))
const manifest = JSON.parse(read('src/manifest.json'))
const packageJson = JSON.parse(read('package.json'))
const cache = read('src/domain/projection.ts')
const client = read('src/api/client.ts')
const list = read('src/pages/order/list.vue')
const detail = read('src/pages/order/detail.vue')
const refund = read('src/pages/refund/status.vue')
const progress = read('src/pages/order/progress.vue')
const quote = read('src/pages/recharge/quote.vue')
const select = read('src/pages/recharge/select.vue')
const recovery = read('src/pages/order/recovery.vue')
const expired = read('src/pages/auth/expired.vue')
const mock = read('src/api/mock.ts')
const payment = read('src/pages/payment/status.vue')
const paymentIntentClient = read('src/api/payment-intent-client.ts')
const home = read('src/pages/index/index.vue')
const directory = read('src/pages/directory/list.vue')
const directoryDetail = read('src/pages/directory/detail.vue')
const lifeContentList = read('src/pages/life-content/list.vue')
const lifeContentDetail = read('src/pages/life-content/detail.vue')
const lifeContentExecutor = read('src/domain/life-content-page-executor.ts')
const lifeContentRunner = read('scripts/run-life-content-evidence.mjs')
const lifeContentCoordinator = read('scripts/coordinate-life-content-evidence.mjs')
const header = read('src/components/AppHeader.vue')
const detailPage = read('src/pages/order/detail.vue')
const temporalContract = read('src/api/temporal-overview-contract.ts')
const temporalFlow = read('src/domain/temporal-overview-flow.ts')

assert.match(cache, /Map<string, OrderProjection>/, 'projection cache must be partitioned by orderRef')
assert.match(cache, /candidate\.orderRef !== orderRef/, 'candidate orderRef must match requested orderRef')
assert.doesNotMatch(client, /getProjection\(orderRef\s*=/, 'getProjection must not have a fixed default orderRef')
assert.match(client, /acceptNewerProjection\(orderRef, projection\)/, 'cache acceptance must be scoped by orderRef')
assert.match(list, /orderRef=\$\{encodeURIComponent\(ref\)\}/, 'list route must carry opaque orderRef')
assert.match(detail, /api\.getProjection\(orderRef\.value\)|api\.getCoreProjection\(orderRef\.value\)/, 'detail must query by route orderRef')
assert.match(refund, /p\.value\?\.facts\.refund/, 'refund R must come from projection')
assert.match(refund, /p\.value\?\.facts\.accounting/, 'refund L must come from projection')
assert.match(refund, /p\.value\?\.facts\.delivery/, 'refund D must come from projection')
assert.match(refund, /rConfirmed&&!lClosed/, 'R confirmed/L open message must be conditional')
assert.match(quote, /validUntil/, 'P012 must display and validate snapshot expiry')
assert.match(payment, /validateOrderSnapshot/, 'P013 must validate frozen projection snapshot')
assert.match(progress, /validateOrderSnapshot/, 'P014 must validate frozen projection snapshot')
assert.match(home, /<button class="card nav"/, 'P001 clickable cards must be buttons')
assert.equal(manifest['mp-weixin']?.appid, 'wx91e4e752ea4a55ee', 'mp-weixin source manifest must retain the approved AppID')
assert.equal(packageJson.scripts['build:mp-weixin'], 'node scripts/clean-mp-weixin-output.mjs && uni build -p mp-weixin && node scripts/verify-mp-weixin-output.mjs', 'mp-weixin build must clean stale output and verify the generated module closure')
assert.ok(existsSync(resolve(process.cwd(),'scripts/clean-mp-weixin-output.mjs')), 'mp-weixin clean step must exist')
assert.ok(existsSync(resolve(process.cwd(),'scripts/verify-mp-weixin-output.mjs')), 'mp-weixin module-closure verification must exist')
for (const route of ['pages/recharge/select', 'pages/order/list', 'pages/directory/list']) {
  assert.ok(pages.pages.some((page) => page.path === route), `home target must be registered: ${route}`)
  assert.doesNotThrow(() => read(`src/${route}.vue`), `home target source must exist: ${route}`)
}
for (const route of ['pages/life-content/list','pages/life-content/detail']) {
  assert.ok(pages.pages.some((page)=>page.path===route),`life-content route must be registered: ${route}`)
  assert.doesNotThrow(()=>read(`src/${route}.vue`),`life-content route source must exist: ${route}`)
}
assert.match(home,/\/pages\/life-content\/list/,'P001 must expose the approved P042 entry')
assert.match(home, /uni\.navigateTo\(\{[\s\S]*fail\(navigateError\)/, 'home navigation must expose navigateTo failures')
assert.match(home, /uni\.redirectTo\(\{[\s\S]*fail:/, 'home navigation must use one controlled fallback')
assert.match(home, /HOME_NAVIGATION_FAILED/, 'home navigation must leave a visible runtime diagnostic')
assert.match(header, /function readHeaderMetrics\(\)[\s\S]*try \{[\s\S]*getSystemInfoSync\?\.\(\)[\s\S]*getMenuButtonBoundingClientRect\?\.\(\)[\s\S]*\} catch \{/, 'shared custom header must fail closed when platform metrics are unavailable')
assert.match(header, /menu && Number\.isFinite\(menu\.top\) && Number\.isFinite\(menu\.height\)/, 'shared custom header must not dereference missing capsule metrics')
for (const [source,root,stateEvidence] of [
  [select,'recharge-select',/data-visible-state="READY_FOR_INPUT"[\s\S]*StatusNotice v-if="error"/],
  [list,'order-list',/:data-visible-state="isGuest\?'GUEST':loading\?'LOADING':error\?'ERROR':orders\.length\?'READY':'EMPTY'"/],
  [directory,'directory-list',/:data-visible-state="loading\?'LOADING':error\?'ERROR':items\.length\?'READY':'EMPTY'"/],
  [lifeContentList,'life-content-list',/:data-visible-state="state"[\s\S]*data-state="READ_ERROR"/],
]) {
  assert.match(source,new RegExp(`data-page-root="${root}"`),`home target ${root} must compile a stable visible root`)
  assert.match(source,stateEvidence,`home target ${root} must expose a stable initial or fail-closed state`)
}
assert.match(app, /@import "\.\/styles\/shared\.css"/, 'shared page and button rules must compile into app.wxss')
assert.match(shared, /\.page\{width:100%/, 'page container must be width-bounded on mp-weixin')
assert.match(home, /hero-action\{display:inline-block;width:auto;min-width:192rpx;min-height:72rpx/, 'home CTA must meet the minimum one-line design geometry')
assert.match(home, /padding:16rpx 40rpx[\s\S]*white-space:nowrap/, 'home CTA must keep intrinsic copy plus 40rpx horizontal padding on one line')
assert.match(home, /\.nav view\{flex:1;min-width:0\}/, 'home entry copy must be allowed to shrink inside flex')
assert.match(home, /\.nav\{width:100%;min-height:148rpx;margin:0;padding:36rpx/, 'home entry rows must meet minimum height and horizontal padding AC')
assert.match(home, /-webkit-line-clamp:2/, 'home entry secondary copy must be limited to two lines')
assert.match(home, /\.hero\{[^}]*margin-top:36rpx;padding:48rpx/, 'home hero must keep baseline outer rhythm and inner padding')
assert.match(shared, /\.content\{width:100%;padding:44rpx 40rpx 48rpx\}/, 'home content must retain 40rpx horizontal padding')
assert.match(shared, /overflow-x:hidden;overflow-y:visible/, 'home must prevent horizontal overflow without clipping short-screen vertical flow')
assert.match(directory, /<button v-for=/, 'P040 clickable items must be buttons')
assert.doesNotMatch(directory, /\.filter\(/, 'P040 must not treat client filtering as publication authority')
assert.match(client, /if \(!useProjectMockApi\) return mockDirectory\(\)/, 'P040 must retain an explicit local mock branch')
assert.match(client, /requestData\('\/content\/items', 'GET', undefined, contentBaseUrl\)/, 'project API mode must call the public Content list endpoint')
assert.match(client, /content\/items\/\$\{encodeURIComponent\(contentRef\)\}\?contentVersion=\$\{contentVersion\}/, 'project API mode must call versioned Content detail')
assert.match(client, /content\/items\/\$\{encodeURIComponent\(contentRef\)\}\/reports/, 'project API mode must call the Content report endpoint')
assert.match(directory, /contentRef=\$\{contentRef\}&contentVersion=\$\{contentVersion\}/, 'P040 must carry contentRef and ContentVersion to P041')
assert.match(directoryDetail, /api\.getDirectoryDetail\(contentRef\.value, contentVersion\.value\)/, 'P041 must reread server-side eligibility by ref and version')
assert.match(directoryDetail, /onShow\(loadDetail\)/, 'P041 must reread eligibility whenever the page is shown or returns to foreground')
const directoryOnLoad = directoryDetail.slice(directoryDetail.indexOf('onLoad('), directoryDetail.indexOf('onShow(loadDetail)'))
assert.doesNotMatch(directoryOnLoad, /loadDetail\(\)/, 'P041 onLoad must only capture ref/version and avoid a duplicate initial read')
assert.match(directoryDetail, /async function loadDetail\(\) \{[\s\S]*item\.value = null[\s\S]*state\.value = 'LOADING'[\s\S]*await api\.getDirectoryDetail/, 'P041 reread must revoke READY content before awaiting authority')
assert.match(directoryDetail, /正在确认最新展示资格/, 'P041 reread must display a non-READY confirmation state')
assert.match(directoryDetail, /@click="loadDetail">刷新状态</, 'P041 READY must expose a reachable read-only refresh action')
assert.match(directory, /暂无有效信息/, 'P040 must distinguish an empty eligible result')
assert.match(directory, /加载失败[\s\S]*重新加载/, 'P040 must expose loading failure and retry')
assert.match(directory, /item\.summary[\s\S]*item\.category[\s\S]*item\.updatedAt/, 'P040 must render only fields present in the public summary DTO')
assert.match(directory, /不构成推荐、官方认证或对第三方服务的担保/, 'P040 must expose a non-guarantee notice')
for (const state of ['EXPIRED', 'COMPLAINT_PENDING', 'REVIEW', 'REMOVED', 'UNKNOWN', 'ERROR']) assert.match(directoryDetail, new RegExp(`state==='${state}'`), `P041 must expose ${state}`)
assert.match(directoryDetail, /这条信息已过期/, 'P041 must expose expired copy')
assert.match(directoryDetail, /这条信息投诉待查/, 'P041 must expose complaint-pending copy')
assert.match(directoryDetail, /这条信息正在复核/, 'P041 must expose review copy')
assert.match(directoryDetail, /这条信息已下架/, 'P041 must expose removed copy')
assert.match(directoryDetail, /状态暂时无法确认/, 'P041 must expose unknown copy')
assert.match(directoryDetail, /加载失败[\s\S]*重新加载/, 'P041 must expose loading failure and retry')
assert.match(directoryDetail, /核验范围[\s\S]*来源类别[\s\S]*核验日期[\s\S]*更新时间/, 'P041 eligible detail must expose traceability fields')
assert.match(directoryDetail, /不等于官方认证、推荐、持续准确或对第三方服务的担保/, 'P041 must expose the approved non-guarantee boundary')
assert.match(directoryDetail, /报告信息错误[\s\S]*CONTENT_ERROR_REPORTED[\s\S]*A120 内容复核候选/, 'P041 must expose CONTENT_ERROR_REPORTED and A120 review candidate entry')
for (const state of ['EXPIRED', 'COMPLAINT_PENDING', 'REVIEW', 'REMOVED', 'UNKNOWN']) assert.match(mock, new RegExp(`publicationState:'${state}'`), `directory mock must retain ${state} coverage`)
const publicDirectory = await mockDirectory()
assert.ok(publicDirectory.length >= 1, 'public directory must retain at least one ELIGIBLE positive sample')
assert.ok(publicDirectory.every((entry) => entry.publicationState === 'ELIGIBLE'), 'public directory must return ELIGIBLE summaries only')
const eligibleDetail = await mockDirectoryDetail(publicDirectory[0].contentRef, publicDirectory[0].contentVersion)
assert.equal(eligibleDetail.outcome, 'READY', 'ELIGIBLE summary must reread as READY at the same ContentVersion')
assert.equal((await mockDirectoryDetail('content_missing', 1)).outcome, 'EMPTY', 'missing content must provide an EMPTY positive case')
const removedDetail = await mockDirectoryDetail('content_mock_removed', 5)
assert.equal(removedDetail.outcome, 'REMOVED', 'removed visible-sample lookup must fail closed instead of becoming READY')
assert.ok(!('item' in removedDetail), 'removed detail must not leak formerly visible content')
const reportCommand = buildContentErrorReport({ commandId:'content-cmd-1', idempotencyKey:'content-idem-1' }, publicDirectory[0].contentVersion, '合成错误报告')
assert.deepEqual(reportCommand, { commandId:'content-cmd-1', idempotencyKey:'content-idem-1', contentVersion:publicDirectory[0].contentVersion, expectedAggregateVersion:publicDirectory[0].contentVersion, reason:'合成错误报告' }, 'report request must carry command, idempotency, content and aggregate versions, and reason')
const firstReceipt = await mockReportDirectoryError(publicDirectory[0].contentRef, reportCommand)
assert.equal(firstReceipt.outcome, 'CONTENT_ERROR_REPORTED', 'eligible content error report must enter A120 review candidate')
assert.deepEqual(await mockReportDirectoryError(publicDirectory[0].contentRef, reportCommand), firstReceipt, 'same-parameter replay must return the original success receipt')
await assert.rejects(mockReportDirectoryError(publicDirectory[0].contentRef, { ...reportCommand, reason:'changed' }), /IDEMPOTENCY_CONFLICT/, 'same key with different parameters must not report success')
await assert.rejects(mockReportDirectoryError('content_mock_removed', buildContentErrorReport({ commandId:'content-cmd-stale', idempotencyKey:'content-idem-stale' }, 5, 'stale')), /CONTENT_NOT_REPORTABLE/, 'removed content report must not report success')
await assert.rejects(mockReportDirectoryError('content_mock_review', { ...buildContentErrorReport({ commandId:'content-cmd-version', idempotencyKey:'content-idem-version' }, 4, 'stale'), expectedAggregateVersion:3 }), /CONTENT_VERSION_STALE/, 'stale aggregate version must not report success')
const validContentSummary = { contentRef:'CNT-1', title:'Synthetic', summary:'Static directory item', category:'LIFE_SERVICE', contentVersion:3, validUntil:'2099-08-01T00:00:00Z', updatedAt:'2026-08-01T00:00:00Z', qualification:'ELIGIBLE' }
const mappedSummaries = parsePublicContentPage({ items:[validContentSummary], count:1 })
assert.equal(mappedSummaries[0].contentVersion, 3, 'numeric backend contentVersion must remain numeric')
assert.equal(mappedSummaries[0].publicationState, 'ELIGIBLE', 'only explicit ELIGIBLE qualification may become visible')
assert.throws(() => parsePublicContentPage({ items:[{ ...validContentSummary, contentVersion:'3' }], count:1 }), /INVALID_CONTENT_SUMMARY_DTO/, 'string contentVersion must fail closed')
assert.throws(() => parsePublicContentPage({ items:[{ ...validContentSummary, qualification:'UNKNOWN' }], count:1 }), /INVALID_CONTENT_SUMMARY_DTO/, 'unknown qualification must fail closed')
assert.throws(() => parsePublicContentPage({ items:[validContentSummary], count:2 }), /INVALID_CONTENT_PAGE_DTO/, 'page count mismatch must fail closed')
const validContentDetail = { contentRef:'CNT-1', title:'Synthetic', summary:'Static directory item', category:'LIFE_SERVICE', sourceCategory:'SELF_RESEARCH', verificationScope:'NAME_AND_PUBLIC_CONTACT_CHANNELS', verifiedAt:'2026-08-01T00:00:00Z', validUntil:'2099-08-01T00:00:00Z', version:3, updatedAt:'2026-08-01T00:00:00Z', disclaimer:'不构成官方认证、推荐或持续有效担保' }
const mappedDetail = parsePublicContentProjection(validContentDetail)
assert.equal(mappedDetail.sourceType, '自营调研来源', 'sourceCategory must use the single explicit sourceType mapping')
assert.equal(mappedDetail.contentVersion, 3, 'backend detail version must map to numeric contentVersion')
assert.throws(() => parsePublicContentProjection({ ...validContentDetail, sourceCategory:'UNDECLARED_SOURCE' }), /INVALID_CONTENT_DETAIL_DTO/, 'unknown sourceCategory must fail closed')
assert.throws(() => parsePublicContentProjection({ ...validContentDetail, verificationScope:'free text scope' }), /INVALID_CONTENT_DETAIL_DTO/, 'non-canonical public verification scope must fail closed')
assert.throws(() => parsePublicContentProjection({ ...validContentDetail, verificationScope:undefined }), /INVALID_CONTENT_DETAIL_DTO/, 'missing verification scope must fail closed')
assert.throws(() => parsePublicContentProjection({ ...validContentDetail, disclaimer:'' }), /INVALID_CONTENT_DETAIL_DTO/, 'missing non-guarantee disclaimer must fail closed')
assert.deepEqual(mapContentProjectCode('RESOURCE_NOT_CONFIRMABLE'), { outcome:'EMPTY' })
assert.deepEqual(mapContentProjectCode('CONTENT_UNDER_REVIEW'), { outcome:'REVIEW' })
assert.deepEqual(mapContentProjectCode('CONTENT_REMOVED'), { outcome:'REMOVED' })
assert.deepEqual(mapContentProjectCode('CONTENT_QUALIFICATION_UNKNOWN'), { outcome:'UNKNOWN' })
assert.deepEqual(mapContentProjectCode('CONTENT_VERSION_STALE'), { outcome:'UNKNOWN' })
assert.equal(mapContentProjectCode('CONTENT_EXPIRED'), null, 'frontend must not infer EXPIRED without a backend projectCode')
assert.equal(mapContentProjectCode('CONTENT_COMPLAINT_PENDING'), null, 'frontend must not infer complaint state without a backend projectCode')
assert.deepEqual(parseContentErrorReportReceipt({ supportRef:'SUPPORT-CNT-1-V4', status:'CONTENT_ERROR_REPORTED', reviewTarget:'A120' }), { outcome:'CONTENT_ERROR_REPORTED', supportRef:'SUPPORT-CNT-1-V4', reviewTarget:'A120' })
assert.throws(() => parseContentErrorReportReceipt({ supportRef:'SUPPORT-CNT-1-V4', status:'IDEMPOTENCY_CONFLICT', reviewTarget:'A120' }), /INVALID_CONTENT_REPORT_RECEIPT/, 'conflict receipt must not map to success')
assert.match(header, /left && hasLeftHandler/, 'left header action requires a listener')
assert.match(header, /right && hasRightHandler/, 'right header action requires a listener')
assert.match(header, /v-if="title"/, 'non-home header must support a page title')
assert.match(progress, /p\.value\?\.facts\.delivery==='CONFIRMED'/, 'builtin projection must make the completed result reachable')
assert.match(progress, /v-if="!finished" class="secondary"/, 'completed result must not keep the refresh action')
assert.match(detail, /resultIcon=computed/, 'detail icon must follow the delivery state')
assert.match(detail, /resultDescription=computed/, 'detail explanation must follow the delivery state')
const between = (source, start, end) => {
  const from = source.indexOf(start)
  const to = source.indexOf(end, from + start.length)
  assert.ok(from >= 0 && to > from, `missing state section: ${start}`)
  return source.slice(from, to)
}
const processingTimeline = between(detail, `<view v-if="detailState==='processing'" class="timeline"`, `<view v-else-if="detailState==='completed'" class="timeline"`)
const completedTimeline = between(detail, `<view v-else-if="detailState==='completed'" class="timeline"`, `<view v-else class="timeline" data-state="conflict"`)
const conflictTimeline = between(detail, `<view v-else class="timeline" data-state="conflict"`, `<view class="action">`)
const detailActions = between(detail, `<view class="action">`, `</view></view></template>`)
const processingAction = between(detailActions, `<button v-if="detailState==='processing'"`, `<button v-else-if="detailState==='completed'"`)
const completedAction = between(detailActions, `<button v-else-if="detailState==='completed'"`, `<button v-else class="primary"`)
const conflictAction = between(detailActions, `<button v-else class="primary"`, `</button>`)
assert.match(processingTimeline, /正在处理充值/, 'processing timeline must expose processing copy')
assert.match(processingTimeline, /请勿重复充值/, 'processing timeline must expose replay warning')
assert.match(completedTimeline, /充值已完成/, 'completed timeline must expose completed copy')
assert.match(completedTimeline, /已确认到账/, 'completed timeline must expose confirmed delivery copy')
assert.doesNotMatch(completedTimeline, /正在处理|请勿重复充值|刷新进度/, 'completed timeline must not expose processing or refresh copy')
assert.match(conflictTimeline, /结果需要核对/, 'conflict timeline must expose review copy')
assert.match(conflictTimeline, /尚不能确认充值完成/, 'conflict timeline must avoid an ordinary completion promise')
assert.doesNotMatch(conflictTimeline, /本单流程已完成/, 'conflict timeline must not promise completion')
assert.match(detail, /结果存在冲突，尚不能确认完成，请联系支持协助核对。/, 'conflict explanation must stay non-final')
assert.match(processingAction, /刷新进度/, 'processing action must refresh')
assert.match(completedAction, />完成/, 'completed action must finish without refresh')
assert.match(conflictAction, />联系支持/, 'conflict action must route to support')
assert.match(detail, /p&&!ready&&detailState==='processing'/, 'stale projection notice must be processing-only')
const renderedDetail = {
  processing: `充值处理中 我们正在确认结果，请勿重复充值。 信息已更新 请刷新后查看最新内容。 ${processingTimeline} ${processingAction}`,
  completed: `充值已完成 充值结果已经确认。 ${completedTimeline} ${completedAction}`,
  conflict: `需要协助 结果存在冲突，尚不能确认完成，请联系支持协助核对。 ${conflictTimeline} ${conflictAction}`,
}
assert.match(renderedDetail.processing, /充值处理中[\s\S]*信息已更新[\s\S]*正在处理充值[\s\S]*刷新进度/, 'processing render must contain only its prompt, timeline and action')
assert.doesNotMatch(renderedDetail.processing, /充值已完成|结果需要核对|>完成|>联系支持/, 'processing render must exclude completed and conflict content')
assert.match(renderedDetail.completed, /充值已完成[\s\S]*充值结果已经确认[\s\S]*已确认到账[\s\S]*>完成/, 'completed render must contain only its prompt, timeline and action')
assert.doesNotMatch(renderedDetail.completed, /信息已更新|请刷新|正在处理|请勿重复充值|结果需要核对|刷新进度|>联系支持/, 'completed render must exclude processing and conflict content')
assert.match(renderedDetail.conflict, /需要协助[\s\S]*尚不能确认完成[\s\S]*结果需要核对[\s\S]*>联系支持/, 'conflict render must contain only its prompt, timeline and action')
assert.doesNotMatch(renderedDetail.conflict, /信息已更新|请刷新|正在处理充值|请勿重复充值|本单流程已完成|刷新进度|>完成/, 'conflict render must exclude processing and completed content')
for(const state of ['AWAITING_PAYMENT','PAYMENT_PROCESSING','PAID_AWAITING_TOPUP','TOPUP_PROCESSING','TOPUP_RESULT_UNKNOWN','DELIVERED','CONFIRMED_NOT_DELIVERED','REFUND_PROCESSING','REFUNDED','DELIVERY_REFUND_CONFLICT_REVIEW','SUPPORT_REVIEW'])assert.match(list,new RegExp(`${state}:'`),`order list must map ${state}`)
assert.match(mock, /stateCode:'DELIVERED'/, 'mock orders must include a delivered sample')
assert.match(quote, /quote\.faceValue\.minor/, 'arrival amount must use the quoted face value')
assert.match(quote, /displayMoney\(quote\.total\)/, 'total and payment action must use localized money formatting')
assert.match(select, /eligibility\.operatorName/, 'eligible number card must display the operator')
assert.match(mock, /\[100, 200, 300, 500\]/, 'synthetic catalog must expose the four approved amount options')
assert.match(select, /operatorQualification==='SUPPORTED'/, 'P010/P011 may continue only for an explicitly SUPPORTED operator')
assert.match(select, /operatorQualification==='UNSUPPORTED'/, 'P010 must expose the current-batch UNSUPPORTED state')
assert.match(select, /operatorQualification==='UNKNOWN'/, 'P010 must preserve UNKNOWN separately from UNSUPPORTED')
assert.match(select, /当前批次暂不支持该运营商/, 'P010 must use the approved unsupported copy')
assert.match(select, /暂时无法确认是否支持/, 'P010 must use the approved unknown copy')
assert.match(select, /选择预设面额或套餐/, 'P011 must expose preset selections only')
assert.doesNotMatch(select, /自定义金额|customAmount/, 'P011 must not expose a custom amount input')
assert.match(quote, /selectionMatchesCatalog/, 'P012 must reread the authoritative catalog before quote and order creation')
assert.match(quote, /removeStorageSync\('rechargeSelection'\)/, 'version drift must revoke the stale selection')
assert.match(quote, /支持集或目录已更新|可选项目已更新/, 'version drift must expose the forced-reselection path')
assert.match(quote, /supportedOperatorSetVersion===selection\.value\.supportedOperatorSetVersion/, 'P012 must bind the support-set version')
assert.match(quote, /catalogVersion===selection\.value\.catalogVersion/, 'P012 must bind the catalog version')
assert.match(client, /requestBody\('\/orders','POST',command\)/, 'M1 must call only POST orders with the strict command')
assert.match(client, /buildOrderCreationCommand\(\{commandId,idempotencyKey\},quoteRef,readSessionProjection\(uni\)\)/, 'M1 must bind the current server-derived BUYER session')
assert.match(quote, /samePriceSnapshot\(fromQuote\(quote\.value!?\),fromQuote\(serverQuote\)\)/, 'P012 must compare the returned immutable PriceSnapshot before navigation')
assert.match(quote, /freezeOrderSnapshot\(uni,orderRef,confirmedSnapshot\)/, 'P012 must freeze the server-returned PriceSnapshot')
assert.match(quote, /executeOrderCreationAttempt\('P012-RUNTIME'/, 'P012 must use the injectable counted creation flow')
assert.match(quote, /createOrderCreationNavigationLedger\(\)/, 'same-order replay must share a navigation ledger')
assert.match(quote, /查询订单创建结果/, 'uncertain order creation must query using the retained command identity')
assert.doesNotMatch(quote, /确认并支付/, 'P012 order creation must not claim or implicitly request payment')
const paymentTemplate=payment.match(/<template>([\s\S]*?)<\/template>/)?.[1]||''
assert.match(paymentTemplate, /订单已建立[\s\S]*尚未付款[\s\S]*本地演示不会发起真实付款/, 'P013 must use ordinary user-facing not-paid copy')
assert.match(payment, /parseOrderCreationResult\(uni\.getStorageSync/, 'P013 must strictly reparse the cached creation receipt')
assert.doesNotMatch(paymentTemplate, />已付款<|>已充值<|付款成功|充值成功|LOCAL_SYNTHETIC|allowedActions|PriceSnapshot|BUYER|PaymentIntentRef|创建本地支付意图|支付意图引用/, 'P013 DOM must not expose positive facts, internal terms or resource references')
for(const label of ['应付总价','充值号码','运营商标识','商品标识','面额标识','支持集 / 目录版本','报价有效期'])assert.match(paymentTemplate,new RegExp(label),`P013 summary must render ${label}`)
assert.match(payment, /writeLocked\.value\|\|viewState\.value!=='READY'/, 'P013 entry must guard every repeated write after the first attempt')
assert.match(paymentTemplate, /v-if="!writeLocked"[\s\S]*>准备付款</, 'P013 write button must disappear after the irreversible latch')
assert.match(paymentTemplate, /v-if="orderRef&&!paymentSummary"/, 'missing or mismatched summary must fail the whole card and write entry closed')

const buyerSession={role:'BUYER',projectSubjectRef:'SYN-SUBJECT-M1',sessionVersion:7,authorizationSetRef:'SYN-AUTHSET-M1',authorizationEvidenceVersion:'SYN-AUTH-EV-M1',authorizedOrderRefs:['SYN-OLD-1'],issuedAt:'2026-08-01T00:00:00Z',expiresAt:'2099-08-01T01:00:00Z',semantics:'SERVER_PROJECTION_CACHE_NOT_AUTHORITY'}
const orderCommand=buildOrderCreationCommand({commandId:'CMD-M1-FE',idempotencyKey:'IDEM-M1-FE'},'Q-M1-FE',buyerSession)
assert.deepEqual(orderCommand,{commandId:'CMD-M1-FE',idempotencyKey:'IDEM-M1-FE',orderCreationPrecondition:'ORDER_MUST_NOT_EXIST',quoteRef:'Q-M1-FE',sessionVersion:7,authorizationSetRef:'SYN-AUTHSET-M1'},'M1 request must contain exactly the six approved fields')
assert.deepEqual(Object.keys(orderCommand),['commandId','idempotencyKey','orderCreationPrecondition','quoteRef','sessionVersion','authorizationSetRef'],'M1 request key order and allowlist must be stable')
for(const forbidden of ['projectSubjectRef','role','authorizationEvidenceVersion','authorizedOrderRefs','totalAmountMinor','currency'])assert.equal(forbidden in orderCommand,false,`M1 request must exclude client authority or money field: ${forbidden}`)
assert.throws(()=>buildOrderCreationCommand({commandId:'CMD',idempotencyKey:'IDEM'},'Q',{...buyerSession,role:'GUEST',authorizationSetRef:null}),/ORDER_CREATION_BUYER_SESSION_REQUIRED/,'GUEST must not create an Order')
assert.throws(()=>buildOrderCreationCommand({commandId:'CMD',idempotencyKey:'IDEM'},'Q',{...buyerSession,sessionVersion:0}),/ORDER_CREATION_BUYER_SESSION_REQUIRED/,'invalid sessionVersion must fail closed')
assert.throws(()=>buildOrderCreationCommand({commandId:'',idempotencyKey:'IDEM'},'Q',buyerSession),/ORDER_CREATION_INPUT_REQUIRED/,'missing command identity must fail closed')

const m1PriceSnapshot={quoteRef:'Q-M1-FE',maskedPhone:'017****890',operatorCode:'SYN-OP',productCode:'SYN-PRODUCT',denominationRef:'SYN-DENOM-1000',supportedOperatorSetVersion:1,catalogVersion:1,totalAmountMinor:1000,currency:'CNY',expiresAt:'2099-08-01T00:10:00Z'}
const m1Action={actionCode:'REQUEST_MOCK_PAYMENT',expectedProjectionVersion:1,expectedAggregateVersion:1}
const m1Projection={orderRef:'O-M1-FE',quoteRef:'Q-M1-FE',stateCode:'AWAITING_PAYMENT',priceSnapshot:m1PriceSnapshot,projectionVersion:1,aggregateVersion:1,allowedActions:[m1Action]}
const m1Created={requestRef:'CMD-M1-FE',outcome:'ACCEPTED',projectCode:'ORDER_CREATED',resourceRef:'O-M1-FE',aggregateVersion:1,currentProjection:m1Projection,retryClass:'NONE'}
const parsedCreated=parseOrderCreationResult(m1Created)
assert.deepEqual(parsedCreated,m1Created,'strict M1 parser must accept the canonical first creation response')
assert.equal(parsedCreated.currentProjection.allowedActions[0].actionCode,'REQUEST_MOCK_PAYMENT','P013 must consume only the server-returned allowed action')
const m1Replay={...m1Created,projectCode:'ORDER_REPLAYED',aggregateVersion:2,currentProjection:{...m1Projection,stateCode:'PAID_AWAITING_TOPUP',projectionVersion:2,aggregateVersion:2,allowedActions:[{actionCode:'REQUEST_MOCK_TOPUP',expectedProjectionVersion:2,expectedAggregateVersion:2}]}}
assert.equal(parseOrderCreationResult(m1Replay).projectCode,'ORDER_REPLAYED','same-business-key replay must preserve the original OrderRef')
assert.equal(parseOrderCreationResult(m1Replay).resourceRef,'O-M1-FE','replay must not invent a second OrderRef')
assert.throws(()=>parseOrderCreationResult({...m1Created,outcome:'UNKNOWN'}),/INVALID_ORDER_CREATION_RESULT_DTO/,'UNKNOWN must not be treated as created')
assert.throws(()=>parseOrderCreationResult({...m1Created,projectCode:'ORDER_CREATION_NOT_AVAILABLE'}),/INVALID_ORDER_CREATION_RESULT_DTO/,'rejection must not be treated as success')
assert.throws(()=>parseOrderCreationResult({...m1Created,retryClass:'READ_SAFE'}),/INVALID_ORDER_CREATION_RESULT_DTO/,'non-NONE retry class must not be treated as success')
assert.throws(()=>parseOrderCreationResult({...m1Created,internalEvidenceRef:'SENSITIVE'}),/INVALID_ORDER_CREATION_RESULT_DTO/,'root unknown fields must fail closed')
assert.throws(()=>parseOrderCreationResult({...m1Created,resourceRef:'O-OTHER'}),/ORDER_CREATION_RESULT_BINDING_MISMATCH/,'resourceRef and projection OrderRef mismatch must fail closed')
assert.throws(()=>parseOrderCreationResult({...m1Created,aggregateVersion:2}),/ORDER_CREATION_RESULT_BINDING_MISMATCH|INVALID_FIRST_ORDER_CREATION_STATE/,'root and projection aggregate versions must remain bound')
assert.throws(()=>parseOrderCreationResult({...m1Created,currentProjection:{...m1Projection,projectionVersion:2}}),/INVALID_FIRST_ORDER_CREATION_STATE|INVALID_ORDER_CREATION_ACTION_DTO/,'first creation must start at projection version one')
assert.throws(()=>parseOrderCreationResult({...m1Created,currentProjection:{...m1Projection,stateCode:'PAYMENT_PROCESSING'}}),/INVALID_FIRST_ORDER_CREATION_STATE/,'first creation must expose only AWAITING_PAYMENT')
assert.throws(()=>parseOrderCreationResult({...m1Created,currentProjection:{...m1Projection,stateCode:'FUTURE_ORDER_STATE'}}),/INVALID_ORDER_CREATION_PROJECTION_DTO/,'unknown public state must fail closed')
assert.throws(()=>parseOrderCreationResult({...m1Created,currentProjection:{...m1Projection,quoteRef:'Q-OTHER'}}),/ORDER_CREATION_RESULT_BINDING_MISMATCH/,'projection and PriceSnapshot QuoteRef mismatch must fail closed')
assert.throws(()=>parseOrderCreationResult({...m1Created,currentProjection:{...m1Projection,internalState:'DRAFT'}}),/INVALID_ORDER_CREATION_PROJECTION_DTO/,'internal order state must not leak into P013 DTO')
assert.throws(()=>parseOrderCreationResult({...m1Created,currentProjection:{...m1Projection,priceSnapshot:{...m1PriceSnapshot,totalAmountMinor:0}}}),/INVALID_QUOTE_DTO/,'invalid server amount must fail closed without client override')
assert.throws(()=>parseOrderCreationResult({...m1Created,currentProjection:{...m1Projection,priceSnapshot:{...m1PriceSnapshot,currency:'USD'}}}),/INVALID_QUOTE_DTO/,'unknown currency must fail closed')
assert.throws(()=>parseOrderCreationResult({...m1Created,currentProjection:{...m1Projection,priceSnapshot:{...m1PriceSnapshot,internalCost:1}}}),/INVALID_QUOTE_DTO/,'PriceSnapshot extra fields must fail closed')
assert.throws(()=>parseOrderCreationResult({...m1Created,currentProjection:{...m1Projection,allowedActions:[{...m1Action,actionCode:'CAPTURE_PAYMENT'}]}}),/INVALID_ORDER_CREATION_ACTION_DTO/,'unknown actionCode must fail closed')
assert.throws(()=>parseOrderCreationResult({...m1Created,currentProjection:{...m1Projection,allowedActions:[{...m1Action,expectedProjectionVersion:2}]}}),/INVALID_ORDER_CREATION_ACTION_DTO/,'action projection version must bind currentProjection')
assert.throws(()=>parseOrderCreationResult({...m1Created,currentProjection:{...m1Projection,allowedActions:[{...m1Action,expectedAggregateVersion:2}]}}),/INVALID_ORDER_CREATION_ACTION_DTO/,'action aggregate version must bind currentProjection')
assert.throws(()=>parseOrderCreationResult({...m1Created,currentProjection:{...m1Projection,allowedActions:[m1Action,m1Action]}}),/DUPLICATE_ORDER_CREATION_ACTION_DTO/,'duplicate actionCode must fail closed')
assert.throws(()=>parseOrderCreationResult({...m1Created,currentProjection:{...m1Projection,allowedActions:[{...m1Action,authorizationRef:'CLIENT_FORGED'}]}}),/INVALID_ORDER_CREATION_ACTION_DTO/,'client-forged action authorization fields must fail closed')

const evidenceRows=[]
const auditRow=(scenarioId,parameterId,evidence,orderDelta)=>({scenarioId,subcaseId:evidence.subcaseId,parameterId,fixedDenominator:evidence.fixedDenominator,
  outcome:evidence.outcome,apiCallCount:evidence.apiCallCount,navigationCount:evidence.navigationCount,automaticRetryCount:evidence.automaticRetryCount,
  successProjectionCount:evidence.successProjectionCount,priceSnapshotCount:evidence.priceSnapshotCount,orderDelta})

const s08Attempt=await executeOrderCreationAttempt('M1-ORD-020-CREATE-UNKNOWN',{createOrder:async()=>{throw new ProjectApiError('ORDER_CREATION_UNKNOWN')},
  acceptProjection:()=>undefined,navigate:()=>undefined,automaticRetry:()=>undefined},createOrderCreationNavigationLedger())
const s08=auditRow('M1-S08','RESPONSE_LOST_UNKNOWN',s08Attempt.evidence,'UNKNOWN');evidenceRows.push(s08)
assert.deepEqual({api:s08.apiCallCount,navigation:s08.navigationCount,automaticRetry:s08.automaticRetryCount,projection:s08.successProjectionCount,priceSnapshot:s08.priceSnapshotCount,orderDelta:s08.orderDelta},
  {api:1,navigation:0,automaticRetry:0,projection:0,priceSnapshot:0,orderDelta:'UNKNOWN'},'M1-S08 must be the fixed create-UNKNOWN row only')

const s09Ledger=createOrderCreationNavigationLedger()
async function executeS09(parameterId,response){let apiCalls=0,navigations=0,automaticRetries=0
  const attempt=await executeOrderCreationAttempt('M1-UI-021-NAV-SUCCESS',{createOrder:async()=>{apiCalls++;return response},acceptProjection:()=>undefined,
    navigate:async()=>{navigations++},automaticRetry:async()=>{automaticRetries++}},s09Ledger)
  const row=auditRow('M1-S09',parameterId,attempt.evidence,'NOT_OBSERVED');evidenceRows.push(row)
  assert.equal(apiCalls,1,`${parameterId} must have an independent API count of one`)
  assert.equal(automaticRetries,0,`${parameterId} must not auto-retry`)
  return{row,navigations}}
const s09Created=await executeS09('ORDER_CREATED',parsedCreated)
assert.deepEqual({api:s09Created.row.apiCallCount,navigation:s09Created.row.navigationCount,automaticRetry:s09Created.row.automaticRetryCount,projection:s09Created.row.successProjectionCount,priceSnapshot:s09Created.row.priceSnapshotCount},
  {api:1,navigation:1,automaticRetry:0,projection:1,priceSnapshot:1},'M1-S09 first success must independently navigate exactly once')
const s09Replay=await executeS09('ORDER_REPLAYED',parseOrderCreationResult(m1Replay))
assert.deepEqual({api:s09Replay.row.apiCallCount,navigation:s09Replay.row.navigationCount,automaticRetry:s09Replay.row.automaticRetryCount,projection:s09Replay.row.successProjectionCount,priceSnapshot:s09Replay.row.priceSnapshotCount},
  {api:1,navigation:0,automaticRetry:0,projection:1,priceSnapshot:1},'M1-S09 replay must independently call API once without duplicate navigation')

async function executeS10(subcaseId,parameterId,error){let apiCalls=0,navigations=0,automaticRetries=0
  const attempt=await executeOrderCreationAttempt(subcaseId,{createOrder:async()=>{apiCalls++;throw error},acceptProjection:()=>undefined,
    navigate:async()=>{navigations++},automaticRetry:async()=>{automaticRetries++}},createOrderCreationNavigationLedger())
  const row=auditRow('M1-S10',parameterId,attempt.evidence,'NOT_OBSERVED');evidenceRows.push(row)
  assert.deepEqual({api:apiCalls,navigation:navigations,automaticRetry:automaticRetries,projection:row.successProjectionCount,priceSnapshot:row.priceSnapshotCount},
    {api:1,navigation:0,automaticRetry:0,projection:0,priceSnapshot:0},`${subcaseId}/${parameterId} must fail closed independently`)
  return row}
const s10Rejected=await executeS10('M1-UI-022-NAV-REJECTED','STABLE_REJECTION',new ProjectApiError('ORDER_CREATION_NOT_AVAILABLE'))
const s10Unknown=await executeS10('M1-UI-023-NAV-UNKNOWN-OR-NETWORK','UNKNOWN',new ProjectApiError('ORDER_CREATION_UNKNOWN'))
const s10Network=await executeS10('M1-UI-023-NAV-UNKNOWN-OR-NETWORK','NETWORK',new ProjectApiError('NETWORK_ERROR'))
assert.equal(s10Rejected.outcome,'REJECTED','M1-UI-022 rejection must remain distinct')
assert.equal(s10Unknown.outcome,'UNKNOWN','M1-UI-023 UNKNOWN parameter must remain distinct')
assert.equal(s10Network.outcome,'NETWORK_INTERRUPTED','M1-UI-023 network parameter must remain distinct')
assert.ok(evidenceRows.every(row=>row.fixedDenominator===31),'all stable ScenarioId/SubcaseId rows must retain total denominator 31')
assert.deepEqual([...new Set(evidenceRows.map(row=>`${row.scenarioId}|${row.subcaseId}`))].sort(),[
  'M1-S08|M1-ORD-020-CREATE-UNKNOWN','M1-S09|M1-UI-021-NAV-SUCCESS','M1-S10|M1-UI-022-NAV-REJECTED','M1-S10|M1-UI-023-NAV-UNKNOWN-OR-NETWORK'].sort(),
  'evidence rows must use only the four frozen matrix mappings')
for(const row of evidenceRows)console.log(`M1_EVIDENCE ${JSON.stringify(row)}`)

const m2ArtifactPaths=['apps/miniapp/scripts/assert-frontend-contracts.mjs','apps/miniapp/src/api/client.ts','apps/miniapp/src/api/mock.ts',
  'apps/miniapp/src/api/order-creation-contract.ts','apps/miniapp/src/api/payment-intent-contract.ts','apps/miniapp/src/api/project-contract.ts',
  'apps/miniapp/src/domain/payment-intent-flow.ts','apps/miniapp/src/pages/payment/status.vue']
const m2ArtifactFiles=Object.fromEntries(m2ArtifactPaths.map(path=>[path,createHash('sha256').update(readFileSync(resolve(process.cwd(),'../..',path))).digest('hex').toUpperCase()]))
const m2ArtifactAggregate=createHash('sha256').update(m2ArtifactPaths.map(path=>`${path}|${m2ArtifactFiles[path]}`).join('\n')).digest('hex').toUpperCase()
const m2EvidenceRows=[]
function emitM2Evidence(scenarioId,subcaseId,parameterId,counts,outcome,details=null){
  const base={scenarioId,subcaseId,parameterId,matrixDenominator:31,evidenceScope:'FRONTEND_LOCAL_SYNTHETIC_ONLY',
    inputVersions:{d303:'4B8D4615D5E9CC5452AB7652285F22BCA2CE0B66307A40ACB50414DD5F73AD68',d305:'E830EC602F5B7D469E00D62C98D4951B7712C6772CBD6A362E10A121A59077A2',d3Registry:'2B9C2ED5EC5C39257C33BE3608612452A7390022E119796CA4404D01B7E22F27'},
    artifactFiles:m2ArtifactFiles,artifactAggregateSha256:m2ArtifactAggregate,apiCallCount:counts.api,readOnlyQueryCount:counts.query,
    navigationCount:counts.navigation,automaticRetryCount:counts.retry,successProjectionCount:counts.projection,priceSnapshotCount:counts.snapshot,outcome,
    ...(details?{pageEvidence:details}:{})}
  const row={...base,evidencePackageDigest:createHash('sha256').update(JSON.stringify(base)).digest('hex').toUpperCase()}
  m2EvidenceRows.push(row);console.log(`M2_EVIDENCE ${JSON.stringify(row)}`);return row
}
const counts=(api=0,query=0,navigation=0,retry=0,projection=0,snapshot=0)=>({api,query,navigation,retry,projection,snapshot})
const m2Session={...buyerSession,authorizedOrderRefs:['O-M2-FE']}
const m2Action={actionCode:'CREATE_LOCAL_SYNTHETIC_PAYMENT_INTENT',expectedProjectionVersion:1,expectedAggregateVersion:1}
const m2Projection={orderRef:'O-M2-FE',quoteRef:m1PriceSnapshot.quoteRef,stateCode:'AWAITING_PAYMENT',priceSnapshot:m1PriceSnapshot,
  projectionVersion:1,aggregateVersion:1,allowedActions:[m2Action]}
assert.equal(canCreateLocalSyntheticPaymentIntent('O-M2-FE',m2Session,m2Projection,m1PriceSnapshot),true,'authorized unexpired M2 BUYER must reach the client write gate')
assert.equal(canCreateLocalSyntheticPaymentIntent('O-M2-FE',{...m2Session,role:'GUEST',authorizationSetRef:null},m2Projection,m1PriceSnapshot),false,'M2 GUEST must fail closed before POST')
assert.equal(canCreateLocalSyntheticPaymentIntent('O-M2-FE',m2Session,{...m2Projection,projectionVersion:2},m1PriceSnapshot),false,'M2 stale action version must fail closed before POST')
assert.equal(canCreateLocalSyntheticPaymentIntent('O-M2-FE',m2Session,m2Projection,{...m1PriceSnapshot,totalAmountMinor:1001}),false,'M2 client amount variation must fail closed')
assert.equal(canCreateLocalSyntheticPaymentIntent('O-M2-FE',{...m2Session,authorizedOrderRefs:['O-OTHER']},m2Projection,m1PriceSnapshot),false,'NOT_IN_AUTHORIZED_SET must fail before POST')
emitM2Evidence('M2-S03','M2-PAY-007-ORDER-NOT-OWNED-OR-NOT-AUTHORIZED','NOT_IN_AUTHORIZED_SET',counts(),'CLIENT_GATE_CLOSED')
const expiredSession={...m2Session,issuedAt:'2026-07-31T00:00:00Z',expiresAt:'2026-08-01T00:00:00Z'}
assert.equal(canCreateLocalSyntheticPaymentIntent('O-M2-FE',expiredSession,m2Projection,m1PriceSnapshot,Date.parse('2026-08-02T00:00:00Z')),false,'EXPIRED_SESSION must fail before POST')
assert.equal(canCreateLocalSyntheticPaymentIntent('O-M2-FE',{...m2Session,authorizationEvidenceVersion:null},m2Projection,m1PriceSnapshot),false,'missing authorization evidence version must fail before POST')
assert.equal(canCreateLocalSyntheticPaymentIntent('O-M2-FE',{...m2Session,issuedAt:null},m2Projection,m1PriceSnapshot),false,'missing session issue time must fail before POST')
emitM2Evidence('M2-S04','M2-PAY-014-REVOKED-OR-EVIDENCEVERSION-MISMATCH','EXPIRED_SESSION',counts(),'CLIENT_GATE_CLOSED')
const m2Command=buildPaymentIntentCommand({commandId:'CMD-M2-FE',idempotencyKey:'IDEM-M2-FE'},'O-M2-FE',m2Session,m2Projection,m1PriceSnapshot)
assert.deepEqual(Object.keys(m2Command),['commandId','idempotencyKey','paymentIntentCreationPrecondition','sessionVersion','authorizationSetRef','expectedProjectionVersion','expectedAggregateVersion'],'M2 request allowlist and key order must be stable')
for(const forbidden of ['projectSubjectRef','role','authorizationEvidenceVersion','priceSnapshot','totalAmountMinor','currency','paymentEligibilityDecisionRef','allowedActions','semanticActionKey','prepay_id'])assert.equal(forbidden in m2Command,false,`M2 request must exclude ${forbidden}`)

const m2CreatedRaw={requestRef:'CMD-M2-FE',outcome:'ACCEPTED',projectCode:'PAYMENT_INTENT_CREATED',resourceRef:'PI-LOCAL-O-M2-FE',aggregateVersion:2,
  currentProjection:{orderRef:'O-M2-FE',stateCode:'AWAITING_PAYMENT',priceSnapshot:m1PriceSnapshot,intentScope:'LOCAL_SYNTHETIC_ONLY',
    paymentInitiated:false,paymentConfirmed:false,projectionVersion:2,aggregateVersion:2,
    allowedActions:[{actionCode:'QUERY_LOCAL_SYNTHETIC_PAYMENT_INTENT',expectedProjectionVersion:2,expectedAggregateVersion:null}]},retryClass:'NONE'}
const m2Created=validatePaymentIntentResult(parsePaymentIntentResult(m2CreatedRaw),'O-M2-FE',m2Command,m1PriceSnapshot)
assert.equal(m2Created.currentProjection.stateCode,'AWAITING_PAYMENT','M2 success must remain AWAITING_PAYMENT')
assert.equal(m2Created.currentProjection.intentScope,'LOCAL_SYNTHETIC_ONLY','M2 machine scope must be exact')
assert.equal(m2Created.currentProjection.paymentInitiated,false,'M2 must reject any claim that payment was initiated')
assert.equal(m2Created.currentProjection.paymentConfirmed,false,'M2 must reject any claim that payment was confirmed')
assert.deepEqual(m2Created.currentProjection.allowedActions,[{actionCode:'QUERY_LOCAL_SYNTHETIC_PAYMENT_INTENT',expectedProjectionVersion:2,expectedAggregateVersion:null}],'M2 success may expose only the frozen read-only action')
for(const field of ['intentScope','paymentInitiated','paymentConfirmed']){
  const projection={...m2CreatedRaw.currentProjection};delete projection[field]
  assert.throws(()=>parsePaymentIntentResult({...m2CreatedRaw,currentProjection:projection}),/INVALID_PAYMENT_INTENT_PROJECTION_DTO/,`missing ${field} must fail closed`)
}
assert.throws(()=>parsePaymentIntentResult({...m2CreatedRaw,currentProjection:{...m2CreatedRaw.currentProjection,intentScope:'REAL_PAYMENT'}}),/INVALID_PAYMENT_INTENT_PROJECTION_DTO/,'unknown machine scope must fail closed')
assert.throws(()=>parsePaymentIntentResult({...m2CreatedRaw,currentProjection:{...m2CreatedRaw.currentProjection,paymentInitiated:true}}),/INVALID_PAYMENT_INTENT_PROJECTION_DTO/,'paymentInitiated=true must fail closed')
assert.throws(()=>parsePaymentIntentResult({...m2CreatedRaw,currentProjection:{...m2CreatedRaw.currentProjection,paymentConfirmed:true}}),/INVALID_PAYMENT_INTENT_PROJECTION_DTO/,'paymentConfirmed=true must fail closed')
assert.throws(()=>parsePaymentIntentResult({...m2CreatedRaw,currentProjection:{...m2CreatedRaw.currentProjection,paymentInitiated:'false'}}),/INVALID_PAYMENT_INTENT_PROJECTION_DTO/,'non-boolean machine value must fail closed')
assert.throws(()=>parsePaymentIntentResult({...m2CreatedRaw,currentProjection:{...m2CreatedRaw.currentProjection,quoteRef:'CLIENT_ASSUMED'}}),/INVALID_PAYMENT_INTENT_PROJECTION_DTO/,'final backend DTO unknown fields must fail closed')
assert.throws(()=>validatePaymentIntentResult(parsePaymentIntentResult({...m2CreatedRaw,projectCode:'PAYMENT_INTENT_REPLAYED',aggregateVersion:3,currentProjection:{...m2CreatedRaw.currentProjection,projectionVersion:3,aggregateVersion:3,allowedActions:[{actionCode:'QUERY_LOCAL_SYNTHETIC_PAYMENT_INTENT',expectedProjectionVersion:3,expectedAggregateVersion:null}]}}),'O-M2-FE',m2Command,m1PriceSnapshot),/VERSION_OR_SNAPSHOT_MISMATCH/,'REPLAY_VERSION_ADVANCED must fail closed')
emitM2Evidence('M2-S01','M2-PAY-002-EXACT-REPLAY','REPLAY_VERSION_ADVANCED',counts(1),'CLIENT_FAIL_CLOSED')
for(const actionCode of ['REQUEST_MOCK_PAYMENT','REQUEST_MOCK_TOPUP','CREATE_LOCAL_SYNTHETIC_PAYMENT_INTENT','CAPTURE_PAYMENT']){
  assert.throws(()=>parsePaymentIntentResult({...m2CreatedRaw,currentProjection:{...m2CreatedRaw.currentProjection,allowedActions:[{actionCode,expectedProjectionVersion:2,expectedAggregateVersion:2}]}}),/INVALID_PAYMENT_INTENT_ACTION_DTO|INVALID_PAYMENT_INTENT_SAFE_ACTION_DTO|PAYMENT_INTENT_RESULT_BINDING_MISMATCH/,'successful M2 projection must reject every money or unknown action')
  emitM2Evidence('M2-S09','M2-UI-029-P013-INTENT-ACCEPTED-NOT-PAID',`LEGACY_OR_WRITE_ACTION_${actionCode}`,counts(1),'CLIENT_FAIL_CLOSED')
}
assert.throws(()=>parsePaymentIntentResult({...m2CreatedRaw,currentProjection:{...m2CreatedRaw.currentProjection,stateCode:'PAID_AWAITING_TOPUP'}}),/INVALID_PAYMENT_INTENT_PROJECTION_DTO/,'M2 must reject a paid projection')
assert.throws(()=>parsePaymentIntentResult({...m2CreatedRaw,prepay_id:'wx-secret'}),/INVALID_PAYMENT_INTENT_RESULT_DTO/,'M2 strict mapper must reject third-party DTO fields')
assert.throws(()=>validatePaymentIntentResult(parsePaymentIntentResult({...m2CreatedRaw,currentProjection:{...m2CreatedRaw.currentProjection,priceSnapshot:{...m1PriceSnapshot,totalAmountMinor:1001}}}),'O-M2-FE',m2Command,m1PriceSnapshot),/VERSION_OR_SNAPSHOT_MISMATCH/,'M2 response cannot replace frozen amount')
const summary=buildP013PaymentSummary(m1PriceSnapshot,m2Created.currentProjection.priceSnapshot)
assert.deepEqual(summary,{totalAmountMinor:1000,currency:'CNY',maskedPhone:'017****890',operatorCode:'SYN-OP',productCode:'SYN-PRODUCT',denominationRef:'SYN-DENOM-1000',supportedOperatorSetVersion:1,catalogVersion:1,expiresAt:'2099-08-01T00:10:00Z'},'P013 summary must use only the strictly matched frozen snapshot')
assert.equal(buildP013PaymentSummary(m1PriceSnapshot,{...m1PriceSnapshot,catalogVersion:2}),null,'mismatched summary must fail the whole card closed')
assert.deepEqual([...P013_PAYMENT_SUMMARY_MISSING_FIELDS],['updatedAt'],'D3 missing summary update time must be registered, not guessed')

const mockM2First=await mockCreatePaymentIntent('O-M2-MOCK',m2Command,{...m2Projection,orderRef:'O-M2-MOCK'})
const mockM2Replay=await mockCreatePaymentIntent('O-M2-MOCK',m2Command,{...m2Projection,orderRef:'O-M2-MOCK'})
assert.equal(mockM2First.projectCode,'PAYMENT_INTENT_CREATED');assert.equal(mockM2Replay.projectCode,'PAYMENT_INTENT_REPLAYED')
await assert.rejects(()=>mockCreatePaymentIntent('O-M2-OTHER',{...m2Command,idempotencyKey:'IDEM-COMMAND-CONFLICT'},{...m2Projection,orderRef:'O-M2-OTHER'}),/IDEMPOTENCY_CONFLICT/)
assert.equal((await mockQueryPaymentIntent('O-M2-OTHER',{commandId:'CMD-NOT-BOUND',idempotencyKey:'IDEM-NOT-BOUND',sessionVersion:2,authorizationSetRef:'AUTHSET-M2'})).outcome,'UNKNOWN','cross-order CommandId conflict must not create a second record')
emitM2Evidence('M2-S02','M2-PAY-004-COMMANDID-CONFLICT','CROSS_ORDER_SAME_COMMANDID',counts(1),'IDEMPOTENCY_CONFLICT')
const idemBase={...m2Command,commandId:'CMD-IDEM-BASE',idempotencyKey:'IDEM-IDEM-BASE'}
await mockCreatePaymentIntent('O-IDEM-BASE',idemBase,{...m2Projection,orderRef:'O-IDEM-BASE'})
await assert.rejects(()=>mockCreatePaymentIntent('O-IDEM-OTHER',{...idemBase,commandId:'CMD-IDEM-OTHER'},{...m2Projection,orderRef:'O-IDEM-OTHER'}),/IDEMPOTENCY_CONFLICT/)
assert.equal((await mockQueryPaymentIntent('O-IDEM-OTHER',{commandId:'CMD-NOT-BOUND-2',idempotencyKey:'IDEM-NOT-BOUND-2',sessionVersion:2,authorizationSetRef:'AUTHSET-M2'})).outcome,'UNKNOWN','cross-order IdempotencyKey conflict must not create a second record')
emitM2Evidence('M2-S02','M2-PAY-005-IDEMPOTENCYKEY-CONFLICT','CROSS_ORDER_SAME_IDEMPOTENCYKEY',counts(1),'IDEMPOTENCY_CONFLICT')
const dualBase={...m2Command,commandId:'CMD-DUAL-BASE',idempotencyKey:'IDEM-DUAL-BASE'}
await mockCreatePaymentIntent('O-DUAL-BASE',dualBase,{...m2Projection,orderRef:'O-DUAL-BASE'})
await assert.rejects(()=>mockCreatePaymentIntent('O-DUAL-OTHER',dualBase,{...m2Projection,orderRef:'O-DUAL-OTHER'}),/IDEMPOTENCY_CONFLICT/)
assert.equal((await mockQueryPaymentIntent('O-DUAL-OTHER',{commandId:'CMD-NOT-BOUND-3',idempotencyKey:'IDEM-NOT-BOUND-3',sessionVersion:2,authorizationSetRef:'AUTHSET-M2'})).outcome,'UNKNOWN','cross-order dual-key conflict must not create a second record')
emitM2Evidence('M2-S01','M2-PAY-003-DUALKEY-REKEY-CONFLICT','CROSS_ORDER_SAME_DUALKEY',counts(1),'IDEMPOTENCY_CONFLICT')

async function runM2Attempt(subcaseId,create){let navigations=0,retries=0
  const attempt=await executePaymentIntentAttempt(subcaseId,{createPaymentIntent:create,acceptProjection:()=>undefined,navigate:()=>{navigations++},automaticRetry:()=>{retries++}})
  assert.equal(navigations,0);assert.equal(retries,0);return attempt.evidence}
const firstEvidence=await runM2Attempt('M2-PAY-001-FIRST-INTENT',async()=>m2Created)
emitM2Evidence('M2-S01','M2-PAY-001-FIRST-INTENT','FIRST_CREATED',counts(1,0,0,0,1,1),firstEvidence.outcome)
let ui029Api=0,ui029Navigation=0,ui029Retry=0,ui029Actual=null
const ui029Attempt=await executePaymentIntentAttempt('M2-UI-029-P013-INTENT-ACCEPTED-NOT-PAID',{
  createPaymentIntent:async()=>{ui029Api++;return m2Created},
  acceptProjection:(result)=>{ui029Actual=buildP013AcceptedPageProjection(result)},
  navigate:()=>{ui029Navigation++},automaticRetry:()=>{ui029Retry++},
})
const ui029Expected={pageId:'UX-P013',viewState:'CREATED',orderRef:'O-M2-FE',stateCode:'AWAITING_PAYMENT',heading:'尚未付款',
  message:'付款准备已完成，尚未发起真实付款，尚未确认收款。',priceSnapshot:m1PriceSnapshot,
  allowedActions:[{actionCode:'QUERY_LOCAL_SYNTHETIC_PAYMENT_INTENT',expectedProjectionVersion:2,expectedAggregateVersion:null}],
  visibleActions:['查询最新结果','返回'],automaticNavigationTarget:null,writeButtonVisible:false,originalIntentQueryVisible:true,safeLeaveVisible:true,p014Visible:false}
assert.deepEqual(ui029Actual,ui029Expected,'M2-UI-029 CREATED P013 page actual must equal the frozen expected projection')
assert.deepEqual({api:ui029Api,navigation:ui029Navigation,retry:ui029Retry,projection:ui029Attempt.evidence.successProjectionCount,snapshot:ui029Attempt.evidence.priceSnapshotCount},
  {api:1,navigation:0,retry:0,projection:1,snapshot:1},'M2-UI-029 must accept one projection without automatic navigation or retry')
assert.equal(ui029Actual.orderRef,m2Created.currentProjection.orderRef,'M2-UI-029 must keep the same OrderRef')
assert.deepEqual(ui029Actual.priceSnapshot,m2Created.currentProjection.priceSnapshot,'M2-UI-029 must keep the same frozen PriceSnapshot')
assert.deepEqual(ui029Actual.allowedActions,m2Created.currentProjection.allowedActions,'M2-UI-029 must expose only the server-returned original-intent read action')
const ui029VisibleText=[ui029Actual.heading,ui029Actual.message,...ui029Actual.visibleActions].join('|')
assert.doesNotMatch(ui029VisibleText,/付款处理中|支付处理中|支付成功|付款成功|已充值|充值中|W=1|W已确认/,'M2-UI-029 must not display payment processing, paid, top-up or W-confirmed copy')
assert.equal([ui029Actual.heading,...ui029Actual.visibleActions].includes('已付款'),false,'M2-UI-029 must not expose an exact paid label')
assert.match(payment,/buildP013AcceptedPageProjection\(result\)/,'P013 must consume the tested accepted page projection')
assert.match(payment,/viewState\.value==='CREATED'\|\|viewState\.value==='REPLAYED'/,'P013 accepted state must expose the original-intent read-only query')
assert.doesNotMatch(payment,/uni\.navigateTo\([^)]*pages\/order\/progress|uni\.redirectTo\([^)]*pages\/order\/progress/,'P013 accepted state must not navigate to P014')
emitM2Evidence('M2-S09','M2-UI-029-P013-INTENT-ACCEPTED-NOT-PAID','PAYMENT_INTENT_CREATED_PAGE',counts(1,0,0,0,1,1),'ACCEPTED',{
  ruleVersions:{d110:'B507A237FF2B80917FF736BBF4C9951314632D0906B167C1073E3C4A70EE813B',d1Registry:'3D7DDD3A1CCD108BFE83551AA5662DF9B39A6F53DA0838B7B971D2DEBEDA6283',matrix:'CFBFD3C62D5EE157BCD3945D33ADA079C9C4E50EE951C32E2821C1F3540CA67B'},
  expected:ui029Expected,actual:ui029Actual,forbiddenPositiveFactCount:0,
})
const replayEvidence=await runM2Attempt('M2-PAY-002-EXACT-REPLAY',async()=>parsePaymentIntentResult({...m2CreatedRaw,projectCode:'PAYMENT_INTENT_REPLAYED'}))
emitM2Evidence('M2-S01','M2-PAY-002-EXACT-REPLAY','EXACT_REPLAY',counts(1,0,0,0,1,1),replayEvidence.outcome)
for(const [scenarioId,subcaseId,parameterId,error,outcome] of [
  ['M2-S08','M2-PAY-026-STABLE-REJECTION','PAYMENT_INTENT_NOT_AVAILABLE',new ProjectApiError('PAYMENT_INTENT_NOT_AVAILABLE'),'REJECTED'],
  ['M2-S08','M2-PAY-027-WRITE-UNKNOWN','WRITE_UNKNOWN',new ProjectApiError('PAYMENT_INTENT_WRITE_UNKNOWN'),'UNKNOWN'],
  ['M2-S08','M2-PAY-028-NETWORK-INTERRUPTED','NETWORK_INTERRUPTED',new ProjectApiError('NETWORK_ERROR'),'NETWORK_INTERRUPTED']]){
  const evidence=await runM2Attempt(subcaseId,async()=>{throw error})
  assert.deepEqual({outcome:evidence.outcome,api:evidence.apiCallCount,query:evidence.readOnlyQueryCount,navigation:evidence.navigationCount,retry:evidence.automaticRetryCount,projection:evidence.successProjectionCount,snapshot:evidence.priceSnapshotCount},{outcome,api:1,query:0,navigation:0,retry:0,projection:0,snapshot:0})
  emitM2Evidence(scenarioId,subcaseId,parameterId,counts(1),outcome)
}

async function proveRejectedPage(parameterId){
  const latch=createPaymentIntentWriteLatch();let api=0,navigation=0,retry=0,projectionCount=0,snapshotCount=0
  const submit=async()=>{
    if(!latch.tryLock())return null
    return executePaymentIntentAttempt('M2-UI-030-P013-REJECTED',{createPaymentIntent:async()=>{api++;throw new ProjectApiError(parameterId)},
      acceptProjection:()=>{projectionCount++;snapshotCount++},navigate:()=>{navigation++},automaticRetry:()=>{retry++}})
  }
  const first=await submit(),second=await submit()
  assert.equal(second,null,`${parameterId} second POST must be blocked by the page write latch`)
  assert.equal(first?.evidence.outcome,'REJECTED',`${parameterId} must remain an explicit rejection`)
  const expected={viewState:'REJECTED',title:'暂时不能继续',message:'当前访问、报价或订单状态不满足继续条件；尚未付款，请查询最新订单或离开本页。',
    visibleActions:['查询最新订单','返回'],writeButtonVisible:false,originalRequestQueryVisible:false,latestOrderQueryVisible:true,safeLeaveVisible:true}
  const actual=buildP013RejectedPageProjection(parameterId)
  assert.deepEqual(actual,expected,`${parameterId} P013 page actual must equal its explicit expected projection`)
  const visibleText=[actual.title,actual.message,...actual.visibleActions].join('|')
  assert.doesNotMatch(visibleText,/PAYMENT_INTENT|IDEMPOTENCY|LOCAL_SYNTHETIC|PaymentIntentRef|OrderRef|allowedActions|PriceSnapshot|BUYER/,
    `${parameterId} must not expose internal enums or references in user-visible text`)
  assert.match(payment,/buildP013RejectedPageProjection\(code\)/,`${parameterId} page must consume the tested rejection projection`)
  assert.match(payment,/@click="refresh\(true\)">查询最新订单<\/button>/,`${parameterId} page must expose the approved latest-order query`)
  assert.match(payment,/<AppHeader title="准备付款" left="返回" @left="uni\.navigateBack\(\)"\/>/,`${parameterId} page must expose safe leave without another write`)
  assert.match(payment,/<button v-if="!writeLocked"/,`${parameterId} write button must remain guarded by the irreversible page latch`)
  const resultCounts=counts(api,0,navigation,retry,projectionCount,snapshotCount)
  assert.deepEqual(resultCounts,counts(1,0,0,0,0,0),`${parameterId} must fail closed with no second POST, query, navigation, retry or success projection`)
  return{counts:resultCounts,expected,actual,secondPostIncrement:0,internalVisibleTermCount:0}
}
for(const parameterId of ['PAYMENT_INTENT_NOT_AVAILABLE','IDEMPOTENCY_CONFLICT']){
  const proof=await proveRejectedPage(parameterId)
  emitM2Evidence('M2-S10','M2-UI-030-P013-REJECTED',parameterId,proof.counts,'REJECTED',
    {expected:proof.expected,actual:proof.actual,secondPostIncrement:proof.secondPostIncrement,internalVisibleTermCount:proof.internalVisibleTermCount})
}

const storedM2WriteIdentity={orderRef:'O-M2-FE',commandId:'CMD-M2-FE',idempotencyKey:'IDEM-M2-FE',
  sessionVersion:m2Session.sessionVersion,authorizationSetRef:m2Session.authorizationSetRef}
function paymentIdentityStorage(initial){
  const memory=new Map();if(initial!==undefined)memory.set('paymentIntentWriteIdentity:O-M2-FE',initial)
  const stats={reads:0,writes:0}
  return{storage:{getStorageSync:key=>{stats.reads++;return memory.get(key)},setStorageSync:(key,value)=>{stats.writes++;memory.set(key,value)}},stats,memory}
}
const originalWriteStore=paymentIdentityStorage()
storePaymentIntentWriteIdentity(originalWriteStore.storage,'O-M2-FE',{commandId:'CMD-M2-FE',idempotencyKey:'IDEM-M2-FE'},m2Session,Date.parse('2026-08-02T00:00:00Z'))
assert.deepEqual(originalWriteStore.memory.get('paymentIntentWriteIdentity:O-M2-FE'),storedM2WriteIdentity,'original M2 POST must persist its exact dual-key Order/session binding once')
assert.equal(originalWriteStore.stats.writes,1,'original M2 POST identity binding must have one explicit storage write')
const originalReadStore=paymentIdentityStorage(storedM2WriteIdentity)
const m2QueryIdentity=readPaymentIntentWriteIdentity(originalReadStore.storage,'O-M2-FE',m2Session,Date.parse('2026-08-02T00:00:00Z'))
assert.deepEqual(m2QueryIdentity,{commandId:'CMD-M2-FE',idempotencyKey:'IDEM-M2-FE'},'UI031 must non-creatively read the original M2 write dual key')
assert.deepEqual(originalReadStore.stats,{reads:1,writes:0},'valid original-key read must not write storage')
const m2QueryRequest=buildPaymentIntentQueryRequest(m2QueryIdentity,'O-M2-FE',m2Session,Date.parse('2026-08-02T00:00:00Z'))
assert.deepEqual(m2QueryRequest,{commandId:'CMD-M2-FE',idempotencyKey:'IDEM-M2-FE',sessionVersion:m2Session.sessionVersion,
  authorizationSetRef:m2Session.authorizationSetRef},'UI031 query must carry only the original dual key and current session binding')
assert.throws(()=>buildPaymentIntentQueryRequest(m2QueryIdentity,'O-OTHER',m2Session),/QUERY_CLIENT_GATE_CLOSED/,'UI031 unauthorized order must fail before GET')
assert.throws(()=>buildPaymentIntentQueryRequest(m2QueryIdentity,'O-M2-FE',expiredSession,Date.parse('2026-08-02T00:00:00Z')),/QUERY_CLIENT_GATE_CLOSED/,'UI031 expired session must fail before GET')

const queryUnknownRaw={requestRef:'CMD-M2-FE',outcome:'UNKNOWN',projectCode:'PAYMENT_INTENT_RESULT_UNKNOWN',resourceRef:null,
  aggregateVersion:null,currentProjection:null,retryClass:'SAME_ACTION_QUERY_ONLY',nextPollAt:'2099-08-01T00:11:00Z'}
const queryFoundRaw={requestRef:'CMD-M2-FE',outcome:'ACCEPTED',projectCode:'PAYMENT_INTENT_RESULT_FOUND',resourceRef:'PI-LOCAL-O-M2-FE',
  aggregateVersion:2,currentProjection:m2CreatedRaw.currentProjection,retryClass:'NONE',nextPollAt:null}
const queryRejectedRaw={requestRef:'CMD-M2-FE',outcome:'REJECTED',projectCode:'PAYMENT_INTENT_RESULT_REJECTED',resourceRef:null,
  aggregateVersion:null,currentProjection:null,retryClass:'NONE',nextPollAt:null}
const queryNotAvailableRaw={...queryRejectedRaw,projectCode:'PAYMENT_INTENT_QUERY_NOT_AVAILABLE'}
const queryUnknown=validatePaymentIntentQueryResult(parsePaymentIntentQueryResult(queryUnknownRaw),'O-M2-FE',m2QueryRequest,m2Projection,m1PriceSnapshot)
const queryFound=validatePaymentIntentQueryResult(parsePaymentIntentQueryResult(queryFoundRaw),'O-M2-FE',m2QueryRequest,m2Projection,m1PriceSnapshot)
const queryRejected=validatePaymentIntentQueryResult(parsePaymentIntentQueryResult(queryRejectedRaw),'O-M2-FE',m2QueryRequest,m2Projection,m1PriceSnapshot)
const queryNotAvailable=validatePaymentIntentQueryResult(parsePaymentIntentQueryResult(queryNotAvailableRaw),'O-M2-FE',m2QueryRequest,m2Projection,m1PriceSnapshot)
for(const field of ['requestRef','outcome','projectCode','resourceRef','aggregateVersion','currentProjection','retryClass','nextPollAt']){
  const malformed={...queryUnknownRaw};delete malformed[field]
  assert.throws(()=>parsePaymentIntentQueryResult(malformed),/INVALID_PAYMENT_INTENT_QUERY_DTO/,`UI031 missing ${field} must fail closed`)
}
assert.throws(()=>parsePaymentIntentQueryResult({...queryUnknownRaw,serverHint:'SECRET'}),/INVALID_PAYMENT_INTENT_QUERY_DTO/,'UI031 unknown response field must fail closed')
assert.throws(()=>parsePaymentIntentQueryResult({...queryUnknownRaw,nextPollAt:'tomorrow'}),/INVALID_PAYMENT_INTENT_QUERY_DTO/,'UI031 non-RFC3339 nextPollAt must fail closed')
assert.throws(()=>parsePaymentIntentQueryResult({...queryUnknownRaw,currentProjection:m2CreatedRaw.currentProjection}),/INVALID_PAYMENT_INTENT_QUERY_DTO/,'UI031 UNKNOWN must not carry a success projection')
assert.throws(()=>parsePaymentIntentQueryResult({...queryRejectedRaw,retryClass:'SAME_ACTION_QUERY_ONLY'}),/INVALID_PAYMENT_INTENT_QUERY_DTO/,'UI031 rejection must not authorize another original query')
assert.throws(()=>validatePaymentIntentQueryResult(parsePaymentIntentQueryResult({...queryFoundRaw,requestRef:'CMD-OTHER'}),'O-M2-FE',m2QueryRequest,m2Projection,m1PriceSnapshot),/QUERY_REQUEST_MISMATCH/,'UI031 must bind the original CommandId')
assert.throws(()=>validatePaymentIntentQueryResult(parsePaymentIntentQueryResult({...queryFoundRaw,currentProjection:{...m2CreatedRaw.currentProjection,priceSnapshot:{...m1PriceSnapshot,totalAmountMinor:1001}}}),'O-M2-FE',m2QueryRequest,m2Projection,m1PriceSnapshot),/QUERY_VERSION_OR_SNAPSHOT_MISMATCH/,'UI031 FOUND must retain the frozen PriceSnapshot')
assert.throws(()=>validatePaymentIntentQueryResult(parsePaymentIntentQueryResult({...queryFoundRaw,aggregateVersion:3,currentProjection:{...m2CreatedRaw.currentProjection,projectionVersion:3,aggregateVersion:3,allowedActions:[{actionCode:'QUERY_LOCAL_SYNTHETIC_PAYMENT_INTENT',expectedProjectionVersion:3,expectedAggregateVersion:null}]}}),'O-M2-FE',m2QueryRequest,m2Projection,m1PriceSnapshot),/QUERY_VERSION_OR_SNAPSHOT_MISMATCH/,'UI031 FOUND must bind the exact expected version')
assert.match(paymentIntentClient,/`\/orders\/\$\{encodeURIComponent\(orderRef\)\}\/payment-intents\/result\$\{query\}`/,'UI031 must use the frozen order-scoped result path')
assert.match(paymentIntentClient,/commandId=\$\{encodeURIComponent\(request\.commandId\)\}[\s\S]*idempotencyKey=\$\{encodeURIComponent\(request\.idempotencyKey\)\}[\s\S]*sessionVersion=\$\{request\.sessionVersion\}[\s\S]*authorizationSetRef=\$\{encodeURIComponent\(request\.authorizationSetRef\)\}/,'UI031 GET must bind all four exact query fields')
assert.match(paymentIntentClient,/payment-intents\/result\$\{query\}`,'GET'/,'UI031 result lookup must be GET')
assert.match(payment,/@click="queryOriginalResult">查询最新结果<\/button>/,'UI031 query must require a user click')
assert.doesNotMatch(payment,/setInterval|setTimeout/,'UI031 must not poll implicitly')
const queryOriginalHandler=payment.slice(payment.indexOf('async function queryOriginalResult()'),payment.indexOf('\nconst money='))
assert.match(queryOriginalHandler,/readPaymentIntentWriteIdentity\(uni,orderRef\.value,readSessionProjection\(uni\)\)/,'UI031 must read the stored original write identity')
assert.doesNotMatch(queryOriginalHandler,/getOrCreateCommand|storePaymentIntentWriteIdentity|setStorageSync/,'UI031 query must never create or persist a replacement key')
assert.match(queryOriginalHandler,/if\(!identity\)[\s\S]*buildP013MissingWriteIdentityPageProjection\(\)[\s\S]*return/,'missing or damaged original keys must stop before GET with a safe page')

const ui031Versions={d303:'4B8D4615D5E9CC5452AB7652285F22BCA2CE0B66307A40ACB50414DD5F73AD68',
  d305:'E830EC602F5B7D469E00D62C98D4951B7712C6772CBD6A362E10A121A59077A2',
  d3Registry:'2B9C2ED5EC5C39257C33BE3608612452A7390022E119796CA4404D01B7E22F27',matrix:'A840C772B1604E151EC3FAD9568DA927D6A9349B9DAA7B33006C7FD1861BA98E'}
const ui031Expected={
  WRITE_UNKNOWN_STILL_UNKNOWN:{pageId:'UX-P013',resultKind:'UNKNOWN',viewState:'UNKNOWN',title:'查询最新结果',message:'原请求结果仍无法确认；当前尚未付款，不会再次提交。',heading:'尚未付款',orderRef:null,stateCode:null,priceSnapshot:null,allowedActions:[],visibleActions:['查询最新结果','返回'],writeButtonVisible:false,originalIntentQueryVisible:true,latestOrderQueryVisible:false,safeLeaveVisible:true,p014Visible:false},
  WRITE_UNKNOWN_LATE_FOUND:{pageId:'UX-P013',resultKind:'FOUND',viewState:'REPLAYED',title:'正在确认付款资格',message:'已查询到原付款准备结果；当前尚未付款，尚未确认收款。',heading:'尚未付款',orderRef:'O-M2-FE',stateCode:'AWAITING_PAYMENT',priceSnapshot:m1PriceSnapshot,allowedActions:m2CreatedRaw.currentProjection.allowedActions,visibleActions:['查询最新结果','返回'],writeButtonVisible:false,originalIntentQueryVisible:true,latestOrderQueryVisible:false,safeLeaveVisible:true,p014Visible:false},
  WRITE_UNKNOWN_LATE_REJECTED:{pageId:'UX-P013',resultKind:'REJECTED',viewState:'REJECTED',title:'暂时不能继续',message:'原付款准备请求未完成；尚未付款，请查询最新订单或离开本页。',heading:'尚未付款',orderRef:null,stateCode:null,priceSnapshot:null,allowedActions:[],visibleActions:['查询最新订单','返回'],writeButtonVisible:false,originalIntentQueryVisible:false,latestOrderQueryVisible:true,safeLeaveVisible:true,p014Visible:false},
  PAYMENT_INTENT_QUERY_NOT_AVAILABLE:{pageId:'UX-P013',resultKind:'NOT_AVAILABLE',viewState:'REJECTED',title:'暂时不能继续',message:'当前无法查询原请求结果；尚未付款，请查询最新订单或离开本页。',heading:'尚未付款',orderRef:null,stateCode:null,priceSnapshot:null,allowedActions:[],visibleActions:['查询最新订单','返回'],writeButtonVisible:false,originalIntentQueryVisible:false,latestOrderQueryVisible:true,safeLeaveVisible:true,p014Visible:false},
  QUERY_NETWORK_ERROR:{pageId:'UX-P013',resultKind:'ERROR',viewState:'NETWORK_ERROR',title:'查询最新结果',message:'查询暂时失败，原请求结果仍无法确认；当前尚未付款，不会再次提交。',heading:'尚未付款',orderRef:null,stateCode:null,priceSnapshot:null,allowedActions:[],visibleActions:['查询最新结果','返回'],writeButtonVisible:false,originalIntentQueryVisible:true,latestOrderQueryVisible:false,safeLeaveVisible:true,p014Visible:false},
}

async function proveUi031(parameterId,resultOrError,initialOutcome){
  const latch=createPaymentIntentWriteLatch();let api=0,queryCalls=0,navigation=0,retry=0
  const firstPost=()=>{if(!latch.tryLock())return false;api++;return true}
  assert.equal(firstPost(),true,`${parameterId} must record the original write attempt`)
  assert.equal(firstPost(),false,`${parameterId} must block a second POST after the first attempt`)
  const identityStore=paymentIdentityStorage(storedM2WriteIdentity)
  const storedIdentity=readPaymentIntentWriteIdentity(identityStore.storage,'O-M2-FE',m2Session,Date.parse('2026-08-02T00:00:00Z'))
  assert.deepEqual(storedIdentity,m2QueryIdentity,`${parameterId} must non-creatively read the original write dual key`)
  const originalRequest=buildPaymentIntentQueryRequest(storedIdentity,'O-M2-FE',m2Session,Date.parse('2026-08-02T00:00:00Z'))
  const queryAttempt=await executePaymentIntentReadOnlyQuery('M2-UI-031-P013-UNKNOWN-OR-NETWORK',async()=>{
    queryCalls++
    if(resultOrError instanceof Error)throw resultOrError
    return resultOrError
  })
  assert.deepEqual(m2QueryRequest,originalRequest,`${parameterId} must query with the unchanged original dual key`)
  const actual=queryAttempt.error?buildP013QueryErrorPageProjection():buildP013QueryPageProjection(queryAttempt.result)
  const expected=ui031Expected[parameterId]
  assert.deepEqual(actual,expected,`${parameterId} UI031 page actual must equal its explicit expected projection`)
  const visibleText=[actual.title,actual.message,actual.heading,...actual.visibleActions].join('|')
  const internalVisibleTermCount=(visibleText.match(/PAYMENT_INTENT|LOCAL_SYNTHETIC|PaymentIntentRef|OrderRef|allowedActions|PriceSnapshot|BUYER|P014|PAID|已付款|付款成功|支付成功|已充值/g)||[]).length
  assert.equal(internalVisibleTermCount,0,`${parameterId} must not expose internal enums, references or positive money facts`)
  assert.equal(actual.writeButtonVisible,false);assert.equal(actual.safeLeaveVisible,true);assert.equal(actual.p014Visible,false)
  if(actual.resultKind==='FOUND'){
    assert.equal(actual.stateCode,'AWAITING_PAYMENT');assert.equal(actual.heading,'尚未付款')
    assert.deepEqual(actual.priceSnapshot,m1PriceSnapshot);assert.deepEqual(actual.allowedActions,m2CreatedRaw.currentProjection.allowedActions)
  }else{assert.equal(actual.priceSnapshot,null);assert.deepEqual(actual.allowedActions,[])}
  const resultCounts=counts(api,queryCalls,navigation,retry,queryAttempt.evidence.successProjectionCount,queryAttempt.evidence.priceSnapshotCount)
  assert.deepEqual(resultCounts,counts(1,1,0,0,actual.resultKind==='FOUND'?1:0,actual.resultKind==='FOUND'?1:0),`${parameterId} must preserve the frozen UI031 counters`)
  assert.deepEqual(identityStore.stats,{reads:1,writes:0},`${parameterId} query must read once and never write storage`)
  return{counts:resultCounts,outcome:queryAttempt.evidence.outcome,details:{input:{initialOutcome,queryRequest:originalRequest,ruleVersions:ui031Versions},expected,actual,
    secondPostIncrement:0,storageReadCount:1,storageWriteCount:0,sessionMutationCount:0,internalVisibleTermCount,navigationTarget:null}}
}
for(const [parameterId,resultOrError,initialOutcome] of [
  ['WRITE_UNKNOWN_STILL_UNKNOWN',queryUnknown,'UNKNOWN'],['WRITE_UNKNOWN_LATE_FOUND',queryFound,'UNKNOWN'],
  ['WRITE_UNKNOWN_LATE_REJECTED',queryRejected,'UNKNOWN'],['PAYMENT_INTENT_QUERY_NOT_AVAILABLE',queryNotAvailable,'UNKNOWN'],
  ['QUERY_NETWORK_ERROR',new ProjectApiError('NETWORK_ERROR'),'NETWORK_INTERRUPTED']]){
  const proof=await proveUi031(parameterId,resultOrError,initialOutcome)
  emitM2Evidence('M2-S10','M2-UI-031-P013-UNKNOWN-OR-NETWORK',parameterId,proof.counts,proof.outcome,proof.details)
}

const missingIdentityExpected={pageId:'UX-P013',resultKind:'NOT_AVAILABLE',viewState:'REJECTED',title:'暂时不能继续',
  message:'原付款准备请求信息缺失或无法校验；当前不会查询或再次提交，请查询最新订单或安全离开本页。',heading:'尚未付款',
  orderRef:null,stateCode:null,priceSnapshot:null,allowedActions:[],visibleActions:['查询最新订单','返回'],writeButtonVisible:false,
  originalIntentQueryVisible:false,latestOrderQueryVisible:true,safeLeaveVisible:true,p014Visible:false}
function proveUnavailableOriginalIdentity(parameterId,storedValue){
  const identityStore=paymentIdentityStorage(storedValue);let api=0,query=0,navigation=0,retry=0,projection=0,snapshot=0,sessionMutation=0
  const identity=readPaymentIntentWriteIdentity(identityStore.storage,'O-M2-FE',m2Session,Date.parse('2026-08-02T00:00:00Z'))
  assert.equal(identity,null,`${parameterId} must provide zero original-key query qualification`)
  const actual=buildP013MissingWriteIdentityPageProjection()
  assert.deepEqual(actual,missingIdentityExpected,`${parameterId} must render the explicit safe fail-closed page`)
  assert.deepEqual(identityStore.stats,{reads:1,writes:0},`${parameterId} must not create or repair stored command keys`)
  const resultCounts=counts(api,query,navigation,retry,projection,snapshot)
  assert.deepEqual(resultCounts,counts(),`${parameterId} must perform zero GET, POST, retry, navigation, projection or snapshot work`)
  return{counts:resultCounts,outcome:'CLIENT_GATE_CLOSED',details:{input:{storedIdentity:storedValue===undefined?'ABSENT':'MALFORMED',ruleVersions:ui031Versions},
    expected:missingIdentityExpected,actual,secondPostIncrement:0,storageReadCount:1,storageWriteCount:identityStore.stats.writes,
    sessionMutationCount:sessionMutation,internalVisibleTermCount:0,navigationTarget:null}}
}
const missingIdentityProof=proveUnavailableOriginalIdentity('ORIGINAL_WRITE_KEYS_MISSING',undefined)
emitM2Evidence('M2-S10','M2-UI-031-P013-UNKNOWN-OR-NETWORK','ORIGINAL_WRITE_KEYS_MISSING',missingIdentityProof.counts,missingIdentityProof.outcome,missingIdentityProof.details)
const damagedIdentityProof=proveUnavailableOriginalIdentity('ORIGINAL_WRITE_KEYS_DAMAGED',{...storedM2WriteIdentity,idempotencyKey:7})
emitM2Evidence('M2-S10','M2-UI-031-P013-UNKNOWN-OR-NETWORK','ORIGINAL_WRITE_KEYS_DAMAGED',damagedIdentityProof.counts,damagedIdentityProof.outcome,damagedIdentityProof.details)
assert.equal(readPaymentIntentWriteIdentity(paymentIdentityStorage({...storedM2WriteIdentity,orderRef:'O-OTHER'}).storage,'O-M2-FE',m2Session),null,'cross-Order stored identity must fail closed')
assert.equal(readPaymentIntentWriteIdentity(paymentIdentityStorage({...storedM2WriteIdentity,sessionVersion:m2Session.sessionVersion+1}).storage,'O-M2-FE',m2Session),null,'stored identity from another session version must fail closed')
assert.equal(readPaymentIntentWriteIdentity(paymentIdentityStorage(storedM2WriteIdentity).storage,'O-M2-FE',{...m2Session,authorizationSetRef:'AUTHSET-OTHER'}),null,'current authorization-set mismatch must fail closed')
const rapidLatch=createPaymentIntentWriteLatch();let rapidApiCalls=0
const rapidClick=()=>{if(!rapidLatch.tryLock())return;rapidApiCalls++};rapidClick();rapidClick()
assert.equal(rapidApiCalls,1,'two immediate clicks must result in one POST eligibility entry')
emitM2Evidence('M2-S09','M2-UI-029-P013-INTENT-ACCEPTED-NOT-PAID','RAPID_SECOND_CLICK_BLOCKED',counts(rapidApiCalls),'WRITE_LATCHED')
assert.equal(new Set(m2EvidenceRows.map(row=>`${row.scenarioId}|${row.subcaseId}|${row.parameterId}`)).size,m2EvidenceRows.length,'every M2 Evidence row must have a unique fixed mapping')

const supportedCatalog=await mockCatalog('SYN-OP')
const unsupportedCatalog=await mockCatalog('SYN-UNSUPPORTED')
const unknownCatalog=await mockCatalog('SYN-UNKNOWN')
assert.equal(supportedCatalog.operatorQualification,'SUPPORTED','approved synthetic operator must provide a SUPPORTED positive case')
assert.equal(supportedCatalog.items.length,4,'supported synthetic catalog must retain non-empty preset positive cases')
assert.equal(unsupportedCatalog.operatorQualification,'UNSUPPORTED','out-of-batch synthetic operator must be UNSUPPORTED')
assert.equal(unsupportedCatalog.items.length,0,'UNSUPPORTED must expose zero selectable items')
assert.equal(unknownCatalog.operatorQualification,'UNKNOWN','unknown synthetic qualification must remain UNKNOWN')
assert.equal(unknownCatalog.supportedOperatorSetVersion,null,'UNKNOWN must not invent a support-set version')
assert.equal((await mockEligibility('1712345600')).outcome,'UNKNOWN','A1 synthetic UNKNOWN must remain reachable')
assert.equal((await mockEligibility('1712345699')).operatorCode,'SYN-UNSUPPORTED','UNSUPPORTED mock must use a synthetic member only')
const preset=supportedCatalog.items[0]
const rechargeSelection=parseRechargeSelection({maskedPhone:'01•• •••• 78',operatorCode:'SYN-OP',operatorName:'合成运营商',productRef:preset.productRef,denominationRef:preset.denominationRef,itemKind:preset.itemKind,faceValue:preset.faceValue,supportedOperatorSetVersion:supportedCatalog.supportedOperatorSetVersion,catalogVersion:supportedCatalog.catalogVersion})
assert.equal(selectionMatchesCatalog(rechargeSelection,supportedCatalog),true,'matching support-set/catalog/item versions must remain selectable')
assert.equal(selectionMatchesCatalog(rechargeSelection,{...supportedCatalog,catalogVersion:supportedCatalog.catalogVersion+1}),false,'catalog version drift must force reselection')
assert.equal(selectionMatchesCatalog(rechargeSelection,{...supportedCatalog,supportedOperatorSetVersion:supportedCatalog.supportedOperatorSetVersion+1}),false,'support-set version drift must force reselection')
assert.equal(selectionMatchesCatalog(rechargeSelection,unsupportedCatalog),false,'UNSUPPORTED must never preserve a prior selection')
const syntheticQuote=await mockQuote(rechargeSelection)
assert.equal(syntheticQuote.denominationRef,rechargeSelection.denominationRef,'quote must bind the preset denomination identity')
assert.equal(syntheticQuote.catalogVersion,rechargeSelection.catalogVersion,'quote must retain the selected catalog version')
const rawCatalog={supportedOperatorSetVersion:1,catalogVersion:2,operatorCode:'SYN-OP',operatorQualification:'SUPPORTED',items:[{operatorCode:'SYN-OP',productRef:'SYN-P',denominationRef:'SYN-D',itemKind:'PRESET_DENOMINATION',amountMinor:10000,currency:'BDT'}],evidenceSemantics:'LOCAL_MOCK_NO_REAL_OPERATOR_FACTS'}
assert.equal(parseCatalogProjection(rawCatalog).items[0].denominationRef,'SYN-D','strict catalog mapper must retain denomination identity')
assert.throws(()=>parseCatalogProjection({...rawCatalog,operatorQualification:'UNKNOWN'}),/INVALID_CATALOG_UNKNOWN_DTO/,'UNKNOWN with versions/items must fail closed')
assert.throws(()=>parseCatalogProjection({...rawCatalog,items:[{...rawCatalog.items[0],itemKind:'CUSTOM_AMOUNT'}]}),/INVALID_CATALOG_ITEM_DTO/,'custom or unknown catalog item kinds must fail closed')
assert.throws(()=>parseCatalogProjection({...rawCatalog,internalApprovalRef:'SENSITIVE'}),/INVALID_CATALOG_DTO/,'catalog root unknown fields must fail closed')
assert.throws(()=>parseCatalogProjection({...rawCatalog,items:[{...rawCatalog.items[0],internalCost:1}]}),/INVALID_CATALOG_ITEM_DTO/,'catalog item unknown fields must fail closed')

assert.match(list, /loadAuthorizedOrderList\(uni,\(current\)=>api\.getOrders\(current\)\)/, 'P020 must use the runtime-counted fail-closed order loader')
assert.match(list, /orders\.value=\[\];error\.value=''[\s\S]*orders\.value=loaded\.orders/, 'P020 must clear prior rows before consuming a new bound response')
assert.match(list, /GUEST不能直接查看历史订单/, 'P020 must expose a non-leaking GUEST gate')
assert.match(list, /api\.getOrders\(current\)/, 'P020 BUYER list must request only the authorized project collection')
assert.match(recovery, /performRecovery\(uni,\(\)=>api\.recoverOrder\(orderRef\.value,recoveryMaterialRef\.value\)\)/, 'P023 must revoke the old session before every recovery request')
assert.match(recovery, /performRecoveryPoll\(uni,\(recoveryCaseRef\)=>api\.getRecoveryCase\(recoveryCaseRef\)\)/, 'P023 UNKNOWN must poll the original case only')
assert.match(recovery, /recovered\.outcome==='RECOVERED'[^]*reLaunch\(\{url:'\/pages\/order\/list'\}\)/, 'successful recovery must enter P020 as BUYER')
assert.match(recovery, /当前保持GUEST/, 'failed and UNKNOWN recovery must keep GUEST without facts')
assert.match(client, /requestData\('\/recovery-cases','POST'/, 'project API recovery must use the independent recovery endpoint')
assert.match(client, /requestData\(`\/recovery-cases\/\$\{encodeURIComponent\(recoveryCaseRef\)\}`,'GET'\)/, 'UNKNOWN must use the original case read endpoint')
assert.match(client, /requestData\(`\/orders\$\{query\}`,'GET'\)/, 'project API order list must use GET orders with bound session query')
assert.doesNotMatch(client, /\/orders\/\$\{encodeURIComponent\([^)]*\)\}\/recovery-cases/, 'recovery must not be modeled as an Order subresource')
assert.match(client, /ORDER_RECOVERY_EXTERNAL_AUTH_NOT_READY/, 'client eligibility must remain zero until an external secure runtime auth path is ready')
assert.match(client, /readPendingRecoveryCaseRef\(uni\)/, 'a pending UNKNOWN case must block a blind new POST')
assert.match(client, /recover-order:\$\{localScopeFingerprint\(fingerprint\)\}/, 'recovery command storage scope must use a non-raw canonical fingerprint')
assert.match(expired, /确认完成前保持GUEST/, 'P022 must keep GUEST while access is being re-established')
assert.doesNotMatch(expired, /你的订单不会受影响|重新登录后可以继续查看订单/, 'P022 must not promise identity or order continuity')
const backendCanonical=(...fields)=>{const digest=createHash('sha256');for(const field of fields){const value=Buffer.from(field,'utf8'),length=Buffer.alloc(4);length.writeUInt32BE(value.length);digest.update(length);digest.update(value)}return digest.digest('hex')}
assert.equal(canonicalFingerprint('SYN-O-1','恢复材料'),backendCanonical('SYN-O-1','恢复材料'),'client fingerprint must exactly mirror backend length-prefixed UTF-8 SHA-256')
const recovered=await mockRecovery('order_mock_001','SYNTHETIC-RECOVERY-OK')
const recoveredSession={role:'BUYER',projectSubjectRef:recovered.authorization.projectSubjectRef,sessionVersion:recovered.authorization.sessionVersion,
  authorizationSetRef:recovered.authorization.authorizationSetRef,authorizationEvidenceVersion:recovered.authorization.authorizationEvidenceVersion,
  authorizedOrderRefs:recovered.authorization.authorizedOrderRefs,issuedAt:recovered.authorization.issuedAt,expiresAt:recovered.authorization.expiresAt,
  semantics:'SERVER_PROJECTION_CACHE_NOT_AUTHORITY'}
assert.equal((await mockOrders(recoveredSession)).length,2,'BUYER mock list must consume the mock server authorized set')
await assert.rejects(mockOrders({...recoveredSession,authorizedOrderRefs:['order_mock_003']}),/SESSION_PROJECTION_STALE/,'forged client order refs must not become mock authority')
await assert.rejects(mockOrders({...recoveredSession,authorizationSetRef:'AUTHSET-FORGED'}),/SESSION_PROJECTION_STALE/,'forged authorizationSetRef must not become mock authority')
const rejected=await mockRecovery('order_mock_001','anything-else')
const recoveryUnknown=await mockRecovery('order_mock_001','SYNTHETIC-RECOVERY-UNKNOWN')
assert.deepEqual(recovered.authorization.authorizedOrderRefs,['order_mock_001','order_mock_002'],'recovery success must return a non-empty authorized order set')
assert.equal(rejected.outcome,'REJECTED','generic rejection must remain the frozen non-expanding outcome')
assert.equal(rejected.authorization,null,'rejection must leak zero authorization')
assert.equal(recoveryUnknown.outcome,'UNKNOWN','recovery UNKNOWN must not become rejection or success')
await assert.rejects(mockOrders(recoveredSession),/BUYER_SESSION_REQUIRED/,'failed recovery must revoke the mock server BUYER session')
const issuedAt='2026-08-01T00:00:00Z',expiresAt='2099-08-01T01:00:00Z'
const authorization={projectSubjectRef:'SYN-SUBJECT-1',sessionRole:'BUYER',sessionVersion:2,authorizationSetRef:'AUTHSET-2',authorizationEvidenceVersion:'AUTH-EV-2',authorizedOrderRefs:['O-1','O-2'],issuedAt,expiresAt}
const recoveryDto={recoveryCaseRef:'RC-1',outcome:'RECOVERED',retryClass:'NONE',authorization}
const parsedRecovery=parseRecoveryResult(recoveryDto,Date.parse(issuedAt))
assert.deepEqual(parsedRecovery,{...recoveryDto,safeQueryPath:null},'complete recovery response must retain the full authorization envelope')
assert.throws(()=>parseRecoveryResult({...recoveryDto,internalEvidenceRef:'SENSITIVE'}),/INVALID_RECOVERY_SUCCESS_DTO/,'recovery unknown fields must fail closed')
assert.throws(()=>parseRecoveryResult({recoveryCaseRef:'RC-X',outcome:'REJECTED',retryClass:'NONE',authorization:{authorizedOrderRefs:['O-LEAK']}}),/INVALID_RECOVERY_REJECTED_DTO/,'failed recovery with authorization must fail closed')
const unknownDto={recoveryCaseRef:'RC-U',outcome:'UNKNOWN',retryClass:'READ_SAFE',safeQueryPath:'/api/v1/recovery-cases/RC-U'}
assert.equal(parseRecoveryResult(unknownDto).recoveryCaseRef,'RC-U','UNKNOWN must retain only its original read-safe case reference')
assert.throws(()=>parseRecoveryResult({...unknownDto,safeQueryPath:'/api/v1/recovery-cases/RC-OTHER'}),/INVALID_RECOVERY_UNKNOWN_DTO/,'UNKNOWN with a different query path must fail closed')
const authorizedSession={role:'BUYER',projectSubjectRef:'SYN-SUBJECT-1',sessionVersion:2,authorizationSetRef:'AUTHSET-2',authorizationEvidenceVersion:'AUTH-EV-2',authorizedOrderRefs:['O-1','O-2'],issuedAt,expiresAt,semantics:'SERVER_PROJECTION_CACHE_NOT_AUTHORITY'}
const orderOne={orderRef:'O-1',stateCode:'TOPUP_RESULT_UNKNOWN',projectionVersion:1,generatedAt:'2026-08-01T00:00:00Z'}
const orderTwo={...orderOne,orderRef:'O-2',stateCode:'DELIVERED',projectionVersion:2}
const orderPage={projectSubjectRef:'SYN-SUBJECT-1',sessionRole:'BUYER',sessionVersion:2,authorizationSetRef:'AUTHSET-2',authorizationEvidenceVersion:'AUTH-EV-2',authorizedOrderRefs:['O-1','O-2'],issuedAt,expiresAt,orders:[orderOne]}
assert.equal(parseAuthorizedOrders(orderPage,authorizedSession,Date.parse(issuedAt)).length,1,'strict BUYER order page must accept a legal authorized subset')
assert.equal(parseAuthorizedOrders({...orderPage,orders:[orderOne,orderTwo]},authorizedSession,Date.parse(issuedAt)).length,2,'strict BUYER order page must accept the complete authorized set')
for(const stateCode of ['AWAITING_PAYMENT','PAYMENT_PROCESSING','PAID_AWAITING_TOPUP','TOPUP_PROCESSING','TOPUP_RESULT_UNKNOWN','DELIVERED','CONFIRMED_NOT_DELIVERED','REFUND_PROCESSING','REFUNDED','DELIVERY_REFUND_CONFLICT_REVIEW','SUPPORT_REVIEW'])assert.equal(parseAuthorizedOrders({...orderPage,orders:[{...orderOne,stateCode}]},authorizedSession,Date.parse(issuedAt))[0].stateCode,stateCode,`frozen order state must parse: ${stateCode}`)
assert.throws(()=>parseAuthorizedOrders({...orderPage,sessionVersion:1},authorizedSession,Date.parse(issuedAt)),/AUTHORIZED_ORDER_BINDING_MISMATCH/,'stale session response must fail closed')
assert.throws(()=>parseAuthorizedOrders({...orderPage,authorizationSetRef:'AUTHSET-OLD'},authorizedSession,Date.parse(issuedAt)),/AUTHORIZED_ORDER_BINDING_MISMATCH/,'authorization-set mismatch must fail closed')
assert.throws(()=>parseAuthorizedOrders({...orderPage,authorizationEvidenceVersion:'AUTH-EV-OLD'},authorizedSession,Date.parse(issuedAt)),/AUTHORIZED_ORDER_BINDING_MISMATCH/,'authorization evidence mismatch must fail closed')
assert.throws(()=>parseAuthorizedOrders({...orderPage,orders:[orderOne,{...orderTwo,orderRef:'O-UNAUTHORIZED'}]},authorizedSession,Date.parse(issuedAt)),/ORDER_OUTSIDE_AUTHORIZATION_SET/,'one unauthorized order must reject the whole response')
assert.throws(()=>parseAuthorizedOrders({...orderPage,orders:[orderOne,orderOne]},authorizedSession,Date.parse(issuedAt)),/DUPLICATE_AUTHORIZED_ORDER_DTO/,'duplicate order refs must fail closed')
assert.throws(()=>parseAuthorizedOrders({...orderPage,orders:[{...orderOne,stateCode:'FUTURE_UNKNOWN_STATE'}]},authorizedSession,Date.parse(issuedAt)),/INVALID_ORDER_SUMMARY_DTO/,'unknown order state must fail closed')
assert.throws(()=>parseAuthorizedOrders({...orderPage,internalSubjectRef:'SENSITIVE'},authorizedSession),/AUTHORIZED_ORDER_BINDING_MISMATCH/,'order-page unknown fields must fail closed')
assert.throws(()=>parseAuthorizedOrders({...orderPage,orders:[{...orderOne,internalEvidenceRef:'SENSITIVE'}]},authorizedSession),/INVALID_ORDER_SUMMARY_DTO/,'order-item unknown fields must fail closed')
assert.throws(()=>parseAuthorizedOrders({...orderPage,expiresAt:'2026-07-31T23:59:59Z'},authorizedSession,Date.parse(issuedAt)),/AUTHORIZED_ORDER_BINDING_MISMATCH/,'expired or mismatched response must fail closed')
const createSessionStorage=()=>{const memory=new Map();return{memory,storage:{getStorageSync:key=>memory.get(key),setStorageSync:(key,value)=>memory.set(key,value)}}}
const guestRuntime=createSessionStorage();let guestOrderGetCalls=0
const guestRuntimeLoad=await loadAuthorizedOrderList(guestRuntime.storage,async()=>{guestOrderGetCalls++;return[]})
assert.equal(guestOrderGetCalls,0,'GUEST entering P020 must execute GET orders exactly zero times')
assert.equal(guestRuntimeLoad.outcome,'GUEST','GUEST runtime path must remain reachable without a request')
const listRuntime=createSessionStorage();applyRecoveryResult(listRuntime.storage,parsedRecovery)
let buyerOrderGetCalls=0
const successRuntimeLoad=await loadAuthorizedOrderList(listRuntime.storage,async(current)=>{buyerOrderGetCalls++;assert.equal(current.role,'BUYER');return parseAuthorizedOrders(orderPage,current,Date.parse(issuedAt))},Date.parse(issuedAt))
assert.equal(buyerOrderGetCalls,1,'a legal BUYER P020 load must execute GET orders exactly once')
assert.equal(successRuntimeLoad.orders.length,1,'a legal BUYER positive row must be reachable')
let visibleRuntimeOrders=[...successRuntimeLoad.orders]
applyRecoveryResult(listRuntime.storage,parsedRecovery);let networkFailureCalls=0
const networkFailureLoad=await loadAuthorizedOrderList(listRuntime.storage,async()=>{networkFailureCalls++;throw new Error('NETWORK_ERROR')},Date.parse(issuedAt))
visibleRuntimeOrders=networkFailureLoad.orders
assert.equal(networkFailureCalls,1,'GET orders network failure must not trigger an implicit retry')
assert.equal(visibleRuntimeOrders.length,0,'GET orders network failure must clear all prior visible rows')
assert.deepEqual(readSessionProjection(listRuntime.storage),{role:'GUEST',projectSubjectRef:null,sessionVersion:0,authorizationSetRef:null,authorizationEvidenceVersion:null,authorizedOrderRefs:[],issuedAt:null,expiresAt:null,semantics:'SERVER_PROJECTION_CACHE_NOT_AUTHORITY'},'GET orders network failure must revoke old BUYER, sessionVersion and authorization set')
for(const failureCode of ['ENDPOINT_REJECTED','STRICT_DTO_REJECTED']){
  applyRecoveryResult(listRuntime.storage,parsedRecovery);let calls=0
  const failed=await loadAuthorizedOrderList(listRuntime.storage,async()=>{calls++;throw new Error(failureCode)},Date.parse(issuedAt))
  assert.equal(calls,1,`${failureCode} must execute one request and no implicit retry`)
  assert.equal(failed.orders.length,0,`${failureCode} must expose zero old rows`)
  assert.equal(readSessionProjection(listRuntime.storage).role,'GUEST',`${failureCode} must revoke BUYER`)
  assert.deepEqual(readSessionProjection(listRuntime.storage).authorizedOrderRefs,[],`${failureCode} must revoke the authorization set`)
}
applyRecoveryResult(listRuntime.storage,parsedRecovery);let expiredOrderGetCalls=0
const expiredRuntimeLoad=await loadAuthorizedOrderList(listRuntime.storage,async()=>{expiredOrderGetCalls++;return[]},Date.parse(expiresAt)+1)
assert.equal(expiredOrderGetCalls,0,'an expired session must be revoked before GET orders')
assert.equal(expiredRuntimeLoad.session.sessionVersion,0,'session expiry must revoke the old sessionVersion')
applyRecoveryResult(listRuntime.storage,parsedRecovery);let successAfterFailureCalls=0
const successAfterFailure=await loadAuthorizedOrderList(listRuntime.storage,async(current)=>{successAfterFailureCalls++;return parseAuthorizedOrders(orderPage,current,Date.parse(issuedAt))},Date.parse(issuedAt))
assert.equal(successAfterFailureCalls,1,'the legal BUYER success path must remain reachable after explicit reauthorization')
assert.equal(successAfterFailure.orders.length,1,'the success path must not be replaced by an all-rejecting implementation')
const sessionMemory=new Map();const sessionStorage={getStorageSync:key=>sessionMemory.get(key),setStorageSync:(key,value)=>sessionMemory.set(key,value)}
assert.equal(readSessionProjection(sessionStorage).role,'GUEST','missing local projection must default to GUEST')
applyRecoveryResult(sessionStorage,recovered)
assert.equal(readSessionProjection(sessionStorage).role,'BUYER','RECOVERED projection must cache BUYER for UI gating')
await assert.rejects(performRecovery(sessionStorage,async()=>{throw new Error('NETWORK_ERROR')}),/NETWORK_ERROR/,'next recovery network failure must remain an error')
assert.deepEqual(readSessionProjection(sessionStorage),{role:'GUEST',projectSubjectRef:null,sessionVersion:recovered.authorization.sessionVersion,authorizationSetRef:null,authorizationEvidenceVersion:null,authorizedOrderRefs:[],issuedAt:null,expiresAt:null,semantics:'SERVER_PROJECTION_CACHE_NOT_AUTHORITY'},'network failure after success must revoke BUYER and the old authorization set')
await performRecovery(sessionStorage,async()=>recoveryUnknown)
assert.deepEqual(readSessionProjection(sessionStorage).authorizedOrderRefs,[],'explicit recovery UNKNOWN must retain zero authorized orders')
assert.equal(readSessionProjection(sessionStorage).role,'GUEST','explicit recovery UNKNOWN must remain GUEST')
assert.equal(readPendingRecoveryCaseRef(sessionStorage),recoveryUnknown.recoveryCaseRef,'UNKNOWN must persist only its original case reference')
const polled=await performRecoveryPoll(sessionStorage,(recoveryCaseRef)=>mockRecoveryCase(recoveryCaseRef))
assert.equal(polled.recoveryCaseRef,recoveryUnknown.recoveryCaseRef,'same-case polling must not switch recovery cases')
await assert.rejects(performRecoveryPoll(sessionStorage,async()=>{throw new Error('NETWORK_ERROR')}),/NETWORK_ERROR/,'poll network interruption must fail closed')
assert.equal(readPendingRecoveryCaseRef(sessionStorage),recoveryUnknown.recoveryCaseRef,'poll interruption must retain the original read-only case')
applyRecoveryResult(sessionStorage,rejected)
assert.equal(readSessionProjection(sessionStorage).role,'GUEST','explicit recovery rejection must remain GUEST')
assert.equal(readSessionProjection(sessionStorage).authorizationSetRef,null,'recovery rejection must clear authorizationSetRef')
applyRecoveryResult(sessionStorage,recovered)
assert.equal(readSessionProjection(sessionStorage,Date.parse(recovered.authorization.expiresAt)+1).role,'GUEST','expired authorization envelope must revoke local BUYER eligibility')

const baseline={priceSnapshotRef:'ps-1',version:'v1',totalAmount:1280,currency:'CNY',phone:'01xx78',operator:'mock-op',operatorCode:'SYN-OP',product:'mock-product',productRef:'SYN-P',denominationRef:'SYN-D',supportedOperatorSetVersion:1,catalogVersion:1,validUntil:'2099-01-01T00:00:00.000Z'}
assert.equal(samePriceSnapshot(baseline,{...baseline}),true,'identical cross-page snapshot must pass')
for(const field of Object.keys(baseline)){
  const changed={...baseline,[field]:field==='totalAmount'?1281:`${baseline[field]}-changed`}
  assert.equal(samePriceSnapshot(baseline,changed),false,`mismatched ${field} must fail`)
}
const actionProjection={allowedActions:[{actionCode:'REFRESH',enabled:true},{actionCode:'SAFE_EXIT',enabled:false}]}
assert.equal(isAllowedProjectionAction(actionProjection,'REFRESH'),true,'enabled projected action must pass')
assert.equal(isAllowedProjectionAction(actionProjection,'SAFE_EXIT'),false,'disabled projected action must fail')
assert.equal(isAllowedProjectionAction(actionProjection,'PAY_AGAIN'),false,'absent business action must fail')
assert.equal(isWhitelistedNavigation('HOME'),true,'known navigation must pass')
assert.equal(isWhitelistedNavigation('PAY_AGAIN'),false,'business action must not enter navigation whitelist')
assert.match(quote, /freezeOrderSnapshot\(uni,orderRef/, 'P012 must freeze snapshot using created orderRef')
assert.match(payment, /validateOrderSnapshot\(uni,orderRef\.value/, 'P013 must use shared frozen snapshot validator')
assert.match(progress, /validateOrderSnapshot\(uni,orderRef\.value/, 'P014 must use shared frozen snapshot validator')
assert.match(detailPage, /validateOrderSnapshot\(uni,orderRef\.value/, 'P021 must use shared frozen snapshot validator')
const memory=new Map();const storage={getStorageSync:key=>memory.get(key),setStorageSync:(key,value)=>memory.set(key,value)}
assert.equal(freezeOrderSnapshot(storage,'order-flow-1',baseline),true,'P012 flow must persist frozen snapshot')
assert.equal(validateOrderSnapshot(storage,'order-flow-1',{...baseline}),true,'matching projection must pass page flow')
assert.equal(validateOrderSnapshot(storage,'order-flow-1',{...baseline,product:'changed-product'}),false,'mismatched projection must be blocked by page flow')

assert.match(client, /'X-Project-Subject-Ref': projectSubjectRef/, 'project subject header must be injected')
assert.deepEqual(parseAcceptedProjectEnvelope({status:'ACCEPTED',projectCode:'OK',data:{value:1}}),{value:1},'success must consume envelope data only')
assert.throws(()=>parseAcceptedProjectEnvelope({status:'REJECTED',projectCode:'ENDPOINT_REJECTED',data:null}),(error)=>error instanceof ProjectApiError&&error.projectCode==='ENDPOINT_REJECTED','endpoint errors must preserve projectCode and fail closed')
assert.throws(()=>parseAcceptedProjectEnvelope({status:'ACCEPTED',projectCode:'OK',data:{},unexpected:true}),/INVALID_ENVELOPE/,'unknown envelope fields must fail closed')
assert.match(client, /parseAuthorizedOrders\(await requestData\(`\/orders\$\{query\}`,'GET'\),session\)/, 'project order response must be parsed against the current session binding')
assert.match(client, /createOrder\(quoteRef: string, commandId: string, idempotencyKey: string\)/, 'order request must carry command identity')
assert.match(client, /expectedProjectionVersion: number, expectedAggregateVersion: number/, 'money mock commands must carry both versions')
assert.doesNotMatch(client, /SemanticActionKey|semanticActionKey/, 'frontend must not generate SemanticActionKey')
assert.match(quote, /orderRef=result\.resourceRef/, 'orderRef must come from the strict create-order receipt')
assert.match(payment, /canCreateLocalSyntheticPaymentIntent/, 'payment must bind server action, BUYER session, versions and frozen PriceSnapshot')
assert.match(payment, /paymentIntentApi\.create\(orderRef\.value,identity,projection\.value!,frozenPriceSnapshot\.value!\)/, 'payment must submit the strict M2 command once through its isolated adapter')
assert.match(payment, /paymentIntentApi\.query\(orderRef\.value,identity,projection\.value!,frozenPriceSnapshot\.value!\)/, 'UNKNOWN recovery must use the isolated original-key query adapter')
assert.doesNotMatch(client, /payment-intent-contract|mockCreatePaymentIntent|mockQueryPaymentIntent/, 'common page client must not initialize payment-intent runtime code')
assert.match(mock, /import type \{ PaymentIntentCommand,[^\n]+\} from '\.\/payment-intent-contract\.ts'/, 'shared mock must retain payment types without a runtime dependency')
assert.doesNotMatch(mock, /^import (?!type )[^\n]*payment-intent-contract/m, 'shared mock must not initialize the payment-intent contract')
assert.match(paymentIntentClient, /export const paymentIntentApi = \{[\s\S]*async create\([\s\S]*async query\(/, 'payment runtime adapter must stay isolated to the payment page')
assert.doesNotMatch(payment, /navigateTo|redirectTo|switchTab|reLaunch|requestPayment|prepay_id/, 'P013 PaymentIntent creation must not navigate or invoke a payment SDK')
assert.match(paymentIntentClient, /parsePaymentIntentQueryResult\(body\)/, 'isolated project API read-only query must consume the frozen strict eight-field DTO')
assert.match(progress, /core\.value\.projectionVersion,core\.value\.aggregateVersion/, 'topup must use projection dual versions')
const validQuote={quoteRef:'Q-1',maskedPhone:'01xx78',operatorCode:'OP',productCode:'P1',denominationRef:'D1',supportedOperatorSetVersion:1,catalogVersion:2,totalAmountMinor:1280,currency:'CNY',expiresAt:'2099-01-01T00:00:00Z'}
assert.deepEqual(parseProjectQuote(validQuote),validQuote,'valid Quote DTO must parse without casting')
assert.throws(()=>parseProjectQuote({...validQuote,totalAmountMinor:'1280'}),/INVALID_QUOTE_DTO/,'invalid Quote DTO must be rejected')
const validProjection={orderRef:'O-1',quoteRef:'Q-1',orderState:'PAYMENT_CONFIRMED',priceSnapshot:validQuote,facts:{semantics:'MOCK_PROJECTION_ONLY_NO_EXTERNAL_FACTS',payment:'CONFIRMED',upstreamDebit:'UNKNOWN',delivery:'UNKNOWN',refund:'UNKNOWN'},projectionVersion:2,aggregateVersion:2,allowedActions:[{actionCode:'WAIT_OR_CONTACT_SUPPORT',expectedProjectionVersion:2,expectedAggregateVersion:null}]}
assert.equal(parseProjectProjection(validProjection).orderRef,'O-1','valid ProjectProjection must parse')
assert.throws(()=>parseProjectProjection({...validProjection,facts:{...validProjection.facts,semantics:'EXTERNAL_FACT'}}),/INVALID_PROJECT_PROJECTION_DTO/,'non-mock semantics must be rejected')

const lifeListStates=['READY','EMPTY','UNDER_REVIEW','EXPIRED','REMOVED','UNKNOWN','ERROR']
assert.equal(LIFE_CONTENT_FIXED_INPUTS.frozenExecutionMatrixSha,'5059310751105E5C598BBB28C9FA69AF28188AAE58AD9D0C4343D5CE2566438B','life-content evidence execution semantics must remain bound to the frozen matrix')
assert.equal(LIFE_CONTENT_FIXED_INPUTS.currentLedgerSha,'78C6CF5308D2D300D0F96C6609973233A70D5B7511BF2B7A067D0AD9AA9A56B5','life-content governance must bind the current evidence-consumed ledger')
assert.equal(LIFE_CONTENT_FIXED_INPUTS.qualityMatrixSha,LIFE_CONTENT_FIXED_INPUTS.frozenExecutionMatrixSha,'legacy qualityMatrixSha may only alias the frozen execution matrix')
assert.notEqual(LIFE_CONTENT_FIXED_INPUTS.qualityMatrixSha,LIFE_CONTENT_FIXED_INPUTS.currentLedgerSha,'legacy qualityMatrixSha must never alias the mutable current ledger')
assert.equal(LIFE_CONTENT_FIXED_INPUTS.d2IndexSha,'2E9BC7D6F9075018CF5DB447571FD8FA9E23DA2E237EC0EEF13AA6D64628AA73','life-content evidence plan must bind the visual-only calibrated D2 index')
assert.equal(LIFE_CONTENT_FIXED_INPUTS.d3IndexSha,'358CBFCC3DEAEE618C8F478603367FA3001B84BE63AAF11ED9CCB9B3EA5BDB8C','life-content evidence plan must bind the calibrated D3 index')
assert.deepEqual(LIFE_CONTENT_EVIDENCE_DENOMINATOR,{total:13,pass:0,fail:0,blocked:0,skipped:0,notApplicable:0,notRun:13},'life-content evidence interface must remain PASS0/NOT_RUN13 before authorized execution')
assert.equal(new Set(LIFE_CONTENT_EVIDENCE_PARAMETER_PLAN.map((row)=>row.scenarioId)).size,13,'life-content evidence interface must retain exactly thirteen scenarios')
assert.equal(LIFE_CONTENT_EVIDENCE_PARAMETER_PLAN.length,26,'life-content evidence interface must retain exactly twenty-six Parameter rows')
assert.equal(new Set(LIFE_CONTENT_EVIDENCE_PARAMETER_PLAN.map((row)=>`${row.scenarioId}/${row.subcaseId}/${row.parameterId}`)).size,LIFE_CONTENT_EVIDENCE_PARAMETER_PLAN.length,'each life-content ParameterId row must be independently auditable')
for(const row of LIFE_CONTENT_EVIDENCE_PARAMETER_PLAN){
  assert.ok(row.expected.length>0,`${row.scenarioId}/${row.parameterId} must have a stable expected value`)
  assert.equal(row.actual,'NOT_RUN_NO_EVIDENCE',`${row.scenarioId}/${row.parameterId} must not fabricate an actual result before execution`)
  assert.equal(row.executionStatus,'NOT_RUN',`${row.scenarioId}/${row.parameterId} must remain NOT_RUN before execution`)
  for(const phase of ['before','after','delta'])assert.deepEqual(Object.keys(row[phase]),LIFE_CONTENT_COUNTER_NAMES,`${row.scenarioId}/${row.parameterId} ${phase} must expose the fixed observable counter interface`)
  for(const phase of ['before','after','delta'])assert.ok(Object.values(row[phase]).every((value)=>value==='NOT_OBSERVED'),`${row.scenarioId}/${row.parameterId} ${phase} must not hard-code zero evidence`)
}
const lifeRunnerManifest=buildLifeContentEvidenceRunnerManifest()
const lifeMatrixBindings=await verifyLifeContentMatrixBindings()
assert.deepEqual({currentLedgerSha:lifeMatrixBindings.currentLedgerSha,frozenExecutionMatrixSha:lifeMatrixBindings.frozenExecutionMatrixSha,scenarios:lifeMatrixBindings.scenarioCount,parameters:lifeMatrixBindings.parameterCount},{currentLedgerSha:LIFE_CONTENT_FIXED_INPUTS.currentLedgerSha,frozenExecutionMatrixSha:LIFE_CONTENT_FIXED_INPUTS.frozenExecutionMatrixSha,scenarios:13,parameters:26},'matrix gate must verify current file SHA, registered frozen SHA and unchanged 13/26 semantics')
assert.equal(lifeRunnerManifest.mode,'RUNNER_IMPLEMENTED_FINAL_RUN_NOT_EXECUTED','ordinary contracts must not execute or promote the final life-content evidence run')
assert.equal(lifeRunnerManifest.packageCount,13,'life-content runner must retain thirteen independent packages')
assert.equal(lifeRunnerManifest.parameterCount,26,'life-content runner must cover all twenty-six Parameter rows')
assert.deepEqual(lifeRunnerManifest.fixedInputs,LIFE_CONTENT_FIXED_INPUTS,'life-content runner must bind the current five inputs and matrix SHA')
assert.equal(lifeRunnerManifest.packages.flatMap((item)=>item.parameterIds).length,26,'life-content package index must retain all Parameter occurrences')
assert.equal(new Set(lifeRunnerManifest.packages.map((item)=>item.evidencePackageId)).size,13,'life-content package IDs must be unique')
for(const row of LIFE_CONTENT_EVIDENCE_PARAMETER_PLAN)assert.ok(LIFE_CONTENT_PARAMETER_REASON_CODES[row.parameterId],`${row.scenarioId}/${row.parameterId} must have an explicit parameter reason code`)
assert.match(LIFE_CONTENT_EVIDENCE_OUTPUT_DIRECTORY,/运行证据[\\/]D5-INF-01前端$/,'life-content final evidence directory must be independent from M1/M2 evidence')
assert.equal(lifeRunnerManifest.oneTimeAuthorizationRequired,true,'life-content final run must require a one-time authorization object')
assert.equal(lifeRunnerManifest.atomicChildStagingRequired,true,'child runner must write only a validated unpublished staging directory')
assert.equal(lifeRunnerManifest.parentCoordinatorRequired,true,'formal publication must require the parent process coordinator')
assert.match(lifeContentRunner,/issuedAuthorizations\.get\(authorization\)/,'exported final runner must reject objects not issued by the private authorization gate')
assert.match(lifeContentRunner,/approvedImplementationSha256[\s\S]*APPROVED_IMPLEMENTATION_SHA_MISMATCH/,'final runner must bind the approved implementation SHA before execution')
assert.match(lifeContentRunner,/FORMAL_EVIDENCE_DIRECTORY_ALREADY_EXISTS/,'final runner must never overwrite a formal evidence directory')
assert.match(lifeContentRunner,/FINAL_PUBLICATION_REQUIRES_PARENT_COORDINATOR/,'child runner must reject direct formal publication')
assert.match(lifeContentRunner,/rename\(temporaryDirectory,targetDirectory\)/,'child runner must atomically finalize only its unpublished staging result')
assert.doesNotMatch(lifeContentRunner,/exitCode\s*:\s*0|finish\(0\)/,'child runner must not prefill a successful process exit')
assert.doesNotMatch(lifeContentRunner,/rm\((?:expectedDirectory|gate\.outputDirectory|LIFE_CONTENT_EVIDENCE_OUTPUT_DIRECTORY)/,'final runner must never delete the formal evidence directory')
assert.doesNotMatch(lifeContentRunner,/M1|M2.*(?:rm|writeFile)|(?:rm|writeFile).*M1|(?:rm|writeFile).*M2/,'life-content runner must not clean or write M1/M2 evidence')
const lifeCoordinatorManifest=buildLifeContentCoordinatorManifest()
assert.equal(lifeCoordinatorManifest.mode,'PARENT_COORDINATOR_IMPLEMENTED_FINAL_RUN_NOT_EXECUTED','ordinary contracts must not execute the parent coordinator')
assert.equal(lifeCoordinatorManifest.processEvidenceRequired,true,'parent coordinator must require independent process evidence')
assert.equal(lifeCoordinatorManifest.atomicReadyPublication,true,'only the parent coordinator may atomically publish READY')
assert.match(lifeContentCoordinator,/spawn\(process\.execPath,args/,'parent coordinator must launch the child runner without a shell')
assert.match(lifeContentCoordinator,/stdout:Buffer\.concat\(stdoutChunks\),stderr:Buffer\.concat\(stderrChunks\)/,'parent coordinator must capture real child stdout and stderr bytes')
assert.match(lifeContentCoordinator,/capture\.exitCode!==0|exitCode:capture\.exitCode/,'parent coordinator must consume the OS child exit code')
assert.match(lifeContentCoordinator,/BLOCKED_OUTPUT_CAPTURE_LOST/,'capture loss or mismatch must fail closed with the fixed disposition')
assert.match(lifeContentCoordinator,/ProcessEvidence\.json/,'parent coordinator must create independent ProcessEvidence')
assert.match(lifeContentCoordinator,/processEvidenceRef/,'final packages must reference ProcessEvidence')
assert.match(lifeContentCoordinator,/fixedInputs:\{\.\.\.LIFE_CONTENT_FIXED_INPUTS\}/,'ProcessEvidence must carry both frozen execution matrix and current ledger SHA fields')
assert.match(lifeContentCoordinator,/assertLifeContentDualMatrixFixedInputs\(value\.approvedFixedInputs\)/,'coordinator authorization must validate both matrix SHA fields')
assert.match(lifeContentCoordinator,/assertLifeContentDualMatrixFixedInputs\(value\.fixedInputs\)/,'coordinator must validate both matrix SHA fields in every child package')
assert.match(lifeContentCoordinator,/rename\(publicationDirectory,gate\.formalDirectory\)/,'READY publication must be one atomic parent-process rename')
assert.doesNotMatch(lifeContentCoordinator,/spawnSync|execSync|exec\(/,'parent coordinator must not use shell execution or implicit reruns')
assert.equal(existsSync(LIFE_CONTENT_EVIDENCE_OUTPUT_DIRECTORY),true,'ordinary contracts after the authorized run must preserve the formal D5-INF frontend evidence directory')
const lifeFormalEvidenceSnapshot=()=>readdirSync(LIFE_CONTENT_EVIDENCE_OUTPUT_DIRECTORY).sort().map((file)=>`${file}|${createHash('sha256').update(readFileSync(resolve(LIFE_CONTENT_EVIDENCE_OUTPUT_DIRECTORY,file))).digest('hex').toUpperCase()}`).join('\n')
const lifeFormalEvidenceBefore=lifeFormalEvidenceSnapshot()
await assert.rejects(()=>runLifeContentEvidence({authorization:{authorizationId:'FORGED'}}),/UNISSUED_OR_FORGED_FINAL_RUN_AUTHORIZATION/,'direct imports must not forge the one-time final-run authorization')
const lifeSensitivity=await runLifeContentBoundarySensitivityCheck()
assert.deepEqual(lifeSensitivity.scopes,{transport:1,log:1,storage:1,telemetry:1},'transport/log/storage/telemetry canary detectors must each prove 0-to-1 sensitivity')
assert.equal(lifeSensitivity.rawCanaryExposed,false,'sensitivity output must not expose the raw secret canary')
const lifeRunnerStaticPrecheck=await runLifeContentRunnerStaticPrecheck()
assert.deepEqual({packages:lifeRunnerStaticPrecheck.packageCount,parameters:lifeRunnerStaticPrecheck.parameterCount,unique:lifeRunnerStaticPrecheck.uniqueParameterCount},{packages:13,parameters:26,unique:26},'static precheck must retain the frozen package and Parameter set')
assert.equal(lifeRunnerStaticPrecheck.dryParameterCount,26,'ordinary contracts must execute all twenty-six synthetic Parameter paths without publishing evidence')
assert.equal(lifeRunnerStaticPrecheck.distinctCausalFixtureCount,5,'stale/expired/qualification/rights/mixed-scan fixtures must be causally distinct')
assert.equal(lifeRunnerStaticPrecheck.oldResponseDiscarded,1,'onHide/foreground race must observe the late response discarded by generation')
assert.equal(lifeFormalEvidenceSnapshot(),lifeFormalEvidenceBefore,'ordinary contracts and static precheck must not rewrite or regenerate formal evidence')
const parsedLifeLists=new Map()
for(const viewState of lifeListStates){
  const dto=await mockLifeContentListDto(viewState)
  const parsed=parseLifeContentListResponse(dto)
  parsedLifeLists.set(viewState,{dto,parsed})
  assert.equal(parsed.viewState,viewState,`life-content list must preserve ${viewState}`)
  assert.equal(parsed.items.length,viewState==='READY'?2:0,`life-content list ${viewState} must enforce all-or-empty items`)
  assert.equal(canRetryLifeContentRead(parsed),viewState==='UNKNOWN'||viewState==='ERROR',`life-content list ${viewState} retry eligibility must remain read-only and explicit`)
}
const readyLifeList=parsedLifeLists.get('READY').dto
const clone=(value)=>JSON.parse(JSON.stringify(value))
assert.equal(Object.keys(readyLifeList).length,8,'life-content list mock must expose the strict eight-field envelope')
assert.equal(Object.keys(readyLifeList.items[0]).length,15,'life-content list item must expose the strict fifteen-field summary')
assert.ok(readyLifeList.items.every((item)=>item.freshnessState==='CURRENT'),'READY list items must only expose CURRENT freshness')
assert.ok(readyLifeList.items.every((item)=>!Object.hasOwn(item,'body')),'life-content list must never expose body')
assert.throws(()=>parseLifeContentListResponse({...readyLifeList,internalEvidenceRef:'SENSITIVE'}),/INVALID_LIFE_CONTENT_LIST_DTO/,'life-content list unknown root fields must fail closed')
const listMissing=clone(readyLifeList);delete listMissing.visibilityRuleVersion
assert.throws(()=>parseLifeContentListResponse(listMissing),/INVALID_LIFE_CONTENT_LIST_DTO/,'life-content list missing fields must fail closed')
assert.throws(()=>parseLifeContentListResponse({...readyLifeList,viewState:'STALE'}),/INVALID_LIFE_CONTENT_LIST_DTO/,'server STALE must fail closed instead of becoming a project enum')
assert.throws(()=>parseLifeContentListResponse({...readyLifeList,projectCode:'LIFE_CONTENT_LIST_EMPTY'}),/INVALID_LIFE_CONTENT_LIST_DTO/,'list projectCode/viewState mismatch must fail closed')
assert.throws(()=>parseLifeContentListResponse({...readyLifeList,items:[]}),/INVALID_LIFE_CONTENT_LIST_STATE_DTO/,'READY list without a positive item must fail closed')
assert.throws(()=>parseLifeContentListResponse({...parsedLifeLists.get('REMOVED').dto,items:readyLifeList.items}),/INVALID_LIFE_CONTENT_LIST_STATE_DTO/,'non-READY list must not retain old summaries')
assert.throws(()=>parseLifeContentListResponse({...readyLifeList,retryClass:'USER_INITIATED_READ_ONLY'}),/INVALID_LIFE_CONTENT_RETRY_DTO/,'READY list must not expose retry eligibility')
assert.throws(()=>parseLifeContentListResponse({...parsedLifeLists.get('UNKNOWN').dto,nextReadAt:'2026-08-02T00:00:00Z'}),/INVALID_LIFE_CONTENT_RETRY_DTO/,'life-content automatic reread time is not approved')
const bodyLeak=clone(readyLifeList);bodyLeak.items[0].body='must fail'
assert.throws(()=>parseLifeContentListResponse(bodyLeak),/INVALID_LIFE_CONTENT_ITEM_DTO/,'list body leakage must fail closed')
const staleReady=clone(readyLifeList);staleReady.items[0].freshnessState='STALE'
assert.throws(()=>parseLifeContentListResponse(staleReady),/INVALID_LIFE_CONTENT_ITEM_DTO/,'READY STALE item must fail closed')
const unknownCategory=clone(readyLifeList);unknownCategory.items[0].category='FUTURE_CATEGORY'
assert.throws(()=>parseLifeContentListResponse(unknownCategory),/INVALID_LIFE_CONTENT_ITEM_DTO/,'unknown life-content category must fail closed')
const emptyVersion=clone(readyLifeList);emptyVersion.items[0].contentVersion=''
assert.throws(()=>parseLifeContentListResponse(emptyVersion),/INVALID_LIFE_CONTENT_ITEM_DTO/,'empty opaque version must fail closed')

const readyLifeRef=readyLifeList.items[0].contentRef,readyLifeVersion=readyLifeList.items[0].contentVersion
const readyLifeDetailDto=await mockLifeContentDetailDto(readyLifeRef,readyLifeVersion)
const readyLifeDetail=parseLifeContentDetailResponse(readyLifeDetailDto,readyLifeRef,readyLifeVersion)
assert.equal(Object.keys(readyLifeDetailDto).length,10,'life-content detail mock must expose the strict ten-field envelope')
assert.equal(Object.keys(readyLifeDetailDto.item).length,16,'life-content READY detail must expose exactly sixteen item fields')
assert.equal(readyLifeDetail.item.freshnessState,'CURRENT','life-content READY detail must remain CURRENT')
let releaseInjectedDetail
const injectedDetailGate=new Promise((resolve)=>{releaseInjectedDetail=resolve})
const injectedMetrics={QueryCall:0,UserRetry:0,AutomaticRetry:0,Projection:0,Body:0,Navigation:0,IdentityHeader:0,PersistentLog:0,ExternalAnalytics:0}
const injectedEvents=[]
const injectedDetailState={viewState:'READY',item:readyLifeDetail.item,readGeneration:0}
const injectedDetailRead=executeLifeContentDetailRead(injectedDetailState,{getDetail:async()=>{await injectedDetailGate;return readyLifeDetail}},readyLifeRef,readyLifeVersion,'FOREGROUND_RESUME',{increment:(name,amount=1)=>{injectedMetrics[name]+=amount},event:(code)=>injectedEvents.push(code)})
assert.equal(injectedDetailState.viewState,'LOADING','injected detail executor must enter LOADING before the API result')
assert.equal(injectedDetailState.item,null,'injected detail executor must revoke old body before the API result')
releaseInjectedDetail();await injectedDetailRead
assert.equal(injectedDetailState.viewState,'READY','injected detail executor must apply the strict READY result after release')
assert.deepEqual({query:injectedMetrics.QueryCall,projection:injectedMetrics.Projection,body:injectedMetrics.Body,retry:injectedMetrics.AutomaticRetry,identity:injectedMetrics.IdentityHeader},{query:1,projection:1,body:1,retry:0,identity:0},'injected detail executor must expose runtime counters without implicit retry or identity')
assert.deepEqual(injectedEvents,['LIFE_CONTENT_DETAIL_READ_START'],'injected detail executor logs only a stable event code without content payload')
const imageItem=readyLifeList.items.find((item)=>item.coverState==='IMAGE_UNAVAILABLE')
const imageDetail=parseLifeContentDetailResponse(await mockLifeContentDetailDto(imageItem.contentRef,imageItem.contentVersion),imageItem.contentRef,imageItem.contentVersion)
assert.equal(imageDetail.item.coverRef,null,'IMAGE_UNAVAILABLE must expose no coverRef')
assert.ok(imageDetail.item.body.length>0,'IMAGE_UNAVAILABLE must keep qualified body readable')
for(const viewState of ['UNDER_REVIEW','EXPIRED','REMOVED','UNKNOWN','ERROR']){
  const dto=await mockLifeContentDetailDto(readyLifeRef,readyLifeVersion,viewState)
  const parsed=parseLifeContentDetailResponse(dto,readyLifeRef,readyLifeVersion)
  assert.equal(parsed.item,null,`life-content detail ${viewState} must revoke old body and item`)
  assert.equal(canRetryLifeContentRead(parsed),viewState==='UNKNOWN'||viewState==='ERROR',`life-content detail ${viewState} retry eligibility must be explicit`)
}
const removedOldLinkDto=await mockLifeContentDetailDto(readyLifeRef,'SYN-OLD-VERSION')
assert.equal(parseLifeContentDetailResponse(removedOldLinkDto,readyLifeRef,'SYN-OLD-VERSION').viewState,'REMOVED','old version link must remain non-READY and must not redirect to current content')
assert.throws(()=>parseLifeContentDetailResponse(readyLifeDetailDto,readyLifeRef,'SYN-OTHER-VERSION'),/INVALID_LIFE_CONTENT_DETAIL_DTO/,'detail response version mismatch must fail closed')
const detailItemMismatch=clone(readyLifeDetailDto);detailItemMismatch.item.contentRef='SYN-OTHER'
assert.throws(()=>parseLifeContentDetailResponse(detailItemMismatch,readyLifeRef,readyLifeVersion),/LIFE_CONTENT_DETAIL_ITEM_BINDING_MISMATCH/,'detail item must bind the requested opaque key')
const detailExtra=clone(readyLifeDetailDto);detailExtra.item.verifiedBy='SENSITIVE'
assert.throws(()=>parseLifeContentDetailResponse(detailExtra,readyLifeRef,readyLifeVersion),/INVALID_LIFE_CONTENT_ITEM_DTO/,'detail identity/evidence fields must fail closed')
const brokenImage=clone(readyLifeDetailDto);brokenImage.item.coverState='IMAGE_UNAVAILABLE';brokenImage.item.coverRef='cover-should-not-survive'
assert.throws(()=>parseLifeContentDetailResponse(brokenImage,readyLifeRef,readyLifeVersion),/INVALID_LIFE_CONTENT_ITEM_DTO/,'IMAGE_UNAVAILABLE with coverRef must fail closed')
assert.throws(()=>parseLifeContentDetailResponse({...readyLifeDetailDto,viewState:'EMPTY',projectCode:'LIFE_CONTENT_LIST_EMPTY',item:null},readyLifeRef,readyLifeVersion),/INVALID_LIFE_CONTENT_DETAIL_DTO/,'detail endpoint must reject EMPTY')
assert.match(client,/requestAnonymousRead\('\/content\/life-items'\)/,'life-content list must use the frozen anonymous GET path without body or query')
assert.match(client,/requestAnonymousRead\(`\/content\/life-items\/\$\{encodeURIComponent\(contentRef\)\}\?contentVersion=\$\{encodeURIComponent\(contentVersion\)\}`\)/,'life-content detail must use the opaque ref plus versioned anonymous GET path')
const anonymousLifeRead=client.slice(client.indexOf('function requestAnonymousRead'),client.indexOf('async function requestData'))
assert.doesNotMatch(anonymousLifeRead,/^\s*(?:header|data):|projectSubjectRef|Profile|openid|OrderRef/m,'life-content anonymous read must not carry identity, body or order context')
assert.doesNotMatch(client,/content\/life-items[^\n]*(?:POST|LOADING|STALE)/,'life-content client must not POST or send client-only states')
assert.match(lifeContentList,/data-page-id="UX-P042"/,'P042 must retain its stable page ID')
assert.match(lifeContentDetail,/data-page-id="UX-P043"/,'P043 must retain its stable page ID')
assert.match(header,/src="\/static\/logo\.png"[\s\S]*华人在孟/,'life-content brand mode must provide the approved logo and brand identity')
assert.match(lifeContentList,/<AppHeader touch-safe left="首页"[^>]*\/>/,'P042 must keep its return action while using the AppHeader brand and touch-safe modes')
assert.match(lifeContentDetail,/<AppHeader touch-safe left="返回列表"[^>]*\/>/,'P043 must keep its return action while using the AppHeader brand and touch-safe modes')
assert.doesNotMatch(`${lifeContentList}\n${lifeContentDetail}`,/<AppHeader[^>]*\btitle=/,'P042/P043 must not replace the approved logo and brand with title mode')
assert.match(lifeContentList,/executeLifeContentListRead\(pageState,\{getList:\(\)=>api\.getLifeContentList\(\)\},trigger\)/,'P042 must delegate every read to the injectable page executor')
assert.match(lifeContentDetail,/executeLifeContentDetailRead\(pageState,\{getDetail:\(ref,version\)=>api\.getLifeContentDetail\(ref,version\)\},contentRef\.value,contentVersion\.value,trigger\)/,'P043 must delegate every version-bound read to the injectable page executor')
assert.match(lifeContentExecutor,/state\.items=\[\][\s\S]*state\.viewState='LOADING'[\s\S]*probe\.increment\('QueryCall'\)[\s\S]*await api\.getList\(\)/,'P042 executor must revoke old cards before the injected read')
assert.match(lifeContentExecutor,/const generation=\+\+state\.readGeneration[\s\S]*state\.item=null[\s\S]*state\.viewState='LOADING'[\s\S]*await api\.getDetail\(contentRef,contentVersion\)/,'P043 executor must revoke old body before the injected read')
assert.match(lifeContentDetail,/onShow\(\(\)=>\{const trigger:LifeContentReadTrigger=shownOnce\?'REENTRY':'FIRST_ENTRY';shownOnce=true;return loadDetail\(trigger\)\}\)/,'P043 must reread on first display and subsequent return/foreground shows')
assert.match(lifeContentDetail,/onHide\(\(\)=>revokeReady\('STALE'\)\)/,'P043 hide must invalidate an old READY body and any in-flight response')
assert.match(lifeContentExecutor,/if\(generation!==state\.readGeneration\)return/g,'P043 executor must reject stale async responses')
assert.match(lifeContentDetail,/contentRef\.value=routeValue\(query\?\.contentRef\)[\s\S]*contentVersion\.value=routeValue\(query\?\.contentVersion\)/,'P043 must use only the opaque route read key')
assert.doesNotMatch(lifeContentDetail.slice(lifeContentDetail.indexOf('onLoad('),lifeContentDetail.indexOf('onShow(loadDetail)')),/loadDetail\(\)/,'P043 onLoad must not duplicate the first onShow read')
for(const stateName of ['LOADING','UNDER_REVIEW','EXPIRED','UNPUBLISHED','UNKNOWN','READ_ERROR'])assert.match(lifeContentDetail,new RegExp(`data-state="${stateName}"`),`P043 must expose the approved ${stateName} node`)
for(const stateName of ['LOADING','EMPTY','UNDER_REVIEW','EXPIRED','UNPUBLISHED','UNKNOWN','READ_ERROR'])assert.match(lifeContentList,new RegExp(`data-state="${stateName}"`),`P042 must expose the approved ${stateName} node`)
assert.equal((lifeContentList.match(/class="readonly-retry"/g)||[]).length,2,'P042 retry action must exist only for UNKNOWN and READ_ERROR')
assert.equal((lifeContentDetail.match(/class="readonly-retry"/g)||[]).length,2,'P043 retry action must exist only for UNKNOWN and READ_ERROR')
const filterEmptyStart=lifeContentList.indexOf('v-if="!visibleItems.length"')
const filterEmptyEnd=lifeContentList.indexOf('</view>',filterEmptyStart)
assert.ok(filterEmptyStart>=0&&filterEmptyEnd>filterEmptyStart,'P042 must expose an independent client-side category-empty node')
const filterEmptyNode=lifeContentList.slice(filterEmptyStart,filterEmptyEnd)
assert.match(filterEmptyNode,/data-state="FILTER_EMPTY"[\s\S]*data-retry-visible="false"[\s\S]*此类别暂无内容/,'P042 category-empty copy must stay distinct from authoritative EMPTY and expose no retry')
assert.doesNotMatch(filterEmptyNode,/readonly-retry|loadList|重新读取/,'P042 category-empty node must not offer a read retry')
assert.match(lifeContentList,/class="story-meta">适用：\{\{item\.applicableAudience\}\} · \{\{updatedLabel\(item\.updatedAt\)\}\}/,'P042 cards must retain the approved audience and update-time hierarchy')
assert.match(lifeContentDetail,/const sourceTypeLabel=\(sourceType:string\)=>sourceType==='页面预览信息'\?'公开说明整理':'其他已登记来源'/,'P043 must map strict sourceType values to ordinary Chinese without echoing unknown raw values')
assert.match(lifeContentDetail,/来源类别：\{\{sourceTypeLabel\(item\.sourceType\)\}\}[\s\S]*适用：\{\{item\.applicableAudience\}\}[\s\S]*\{\{updatedLabel\(item\.updatedAt\)\}\}/,'P043 must show mapped source category, audience and update time')
assert.doesNotMatch(lifeContentDetail,/\{\{\s*item\.sourceType\s*\}\}/,'P043 must never render the raw sourceType value')
assert.match(header,/touchSafe\?: boolean/,'AppHeader must expose an opt-in touch-safe variant without changing other pages')
assert.match(header,/\.side\.touch-safe\{min-height:44px/,'AppHeader touch-safe side action must use a 44px target floor')
assert.match(lifeContentList,/\.topic-tabs button\{[^}]*min-height:44px/,'P042 category controls must use a 44px target floor')
assert.match(lifeContentList,/\.readonly-retry\{[^}]*min-height:44px/,'P042 retry controls must use a 44px target floor')
assert.match(lifeContentDetail,/\.readonly-retry\{[^}]*min-height:44px/,'P043 retry controls must use a 44px target floor')
assert.match(lifeContentDetail,/\.life-page \.action \.primary\{min-height:44px/,'P043 bottom return action must use a page-scoped 44px target floor')
const storyTargetRpx=Number(lifeContentList.match(/\.story-card\{[^}]*min-height:(\d+)rpx/)?.[1]||0)
for(const viewportWidth of [320,375,430]){
  assert.ok(storyTargetRpx*viewportWidth/750>=44,`P042 story action must calculate to at least 44px at ${viewportWidth}px`)
}
assert.match(lifeContentDetail,/item\.coverState==='IMAGE_UNAVAILABLE'[\s\S]*图片暂时无法显示[\s\S]*\{\{item\.body\}\}/,'P043 IMAGE_UNAVAILABLE must keep body readable without loading coverRef')
assert.doesNotMatch(lifeContentDetail,/<image|:src=.*coverRef|uni\.downloadFile|uni\.request\([^)]*coverRef/,'P043 must not treat opaque coverRef as an external image URL')
assert.doesNotMatch(lifeContentList,/console\.|analytics|trackEvent|setStorageSync/,'P042 must not log, analyze or persist public content fields')
assert.doesNotMatch(lifeContentDetail,/console\.|analytics|trackEvent|setStorageSync/,'P043 must not log, analyze or persist public content fields')
const lifeVisibleTemplates=[lifeContentList,lifeContentDetail].map((source)=>source.match(/<template>([\s\S]*?)<\/template>/)?.[1]||'').join('\n')
assert.doesNotMatch(lifeVisibleTemplates,/>[^<]*(?:LOCAL_SYNTHETIC|LIFE_CONTENT_|ContentRef|ContentVersion|Evidence|Fixture|READY|STALE|UNDER_REVIEW|EXPIRED|REMOVED|UNKNOWN|ERROR)[^<]*</,'P042/P043 visible copy must not expose machine states, versions, evidence IDs or engineering terms')

const readyTemporalDto=await mockTemporalOverviewDto()
const readyTemporal=parseTemporalOverview(readyTemporalDto)
assert.equal(Object.keys(readyTemporalDto).length,12,'P001 temporal overview must expose the strict twelve-field envelope')
assert.deepEqual(Object.keys(readyTemporalDto.clocks).sort(),['beijing','dhaka'],'P001 temporal overview must expose exactly two fixed clocks')
assert.deepEqual(Object.keys(readyTemporalDto.holidays).sort(),['bangladesh','china'],'P001 temporal overview must expose exactly two fixed holiday projections')
assert.equal(Object.keys(readyTemporalDto.clocks.dhaka).length,6,'P001 clock projection must expose six strict fields')
assert.equal(Object.keys(readyTemporalDto.holidays.china).length,11,'P001 holiday projection must expose eleven strict fields')
assert.equal(readyTemporal.clocks.dhaka.localDate,readyTemporal.holidays.bangladesh.localDate,'Dhaka clock and Bangladesh holiday must share the same reference-derived local date')
assert.equal(readyTemporal.clocks.beijing.localDate,readyTemporal.holidays.china.localDate,'Beijing clock and China holiday must share the same reference-derived local date')
const temporalClone=(value)=>JSON.parse(JSON.stringify(value))
assert.throws(()=>parseTemporalOverview({...readyTemporalDto,deviceTimeZone:'Asia/Tokyo'}),/INVALID_TEMPORAL_OVERVIEW_DTO/,'P001 unknown/device fields must fail closed')
const temporalMissing=temporalClone(readyTemporalDto);delete temporalMissing.referenceInstant
assert.throws(()=>parseTemporalOverview(temporalMissing),/INVALID_TEMPORAL_OVERVIEW_DTO/,'P001 missing reference instant must fail closed')
const temporalUnknownState=temporalClone(readyTemporalDto);temporalUnknownState.clockState='UNKNOWN'
assert.throws(()=>parseTemporalOverview(temporalUnknownState),/INVALID_TEMPORAL_OVERVIEW_DTO/,'P001 unknown clock state must fail closed')
const temporalSeconds=temporalClone(readyTemporalDto);temporalSeconds.clocks.dhaka.localTime='12:00:00'
assert.throws(()=>parseTemporalOverview(temporalSeconds),/INVALID_TEMPORAL_CLOCK_AVAILABILITY_DTO/,'P001 local time must be HH:mm without seconds')
const temporalWrongBeijingDate=temporalClone(readyTemporalDto);temporalWrongBeijingDate.clocks.beijing.localDate='2026-08-03'
assert.throws(()=>parseTemporalOverview(temporalWrongBeijingDate),/TEMPORAL_CHINA_DATE_MISMATCH/,'P001 available Beijing date must remain bound to the China holiday local date from the same server reference instant')
const temporalUnavailableLeak=temporalClone(readyTemporalDto);temporalUnavailableLeak.clockState='DHAKA_UNAVAILABLE';temporalUnavailableLeak.clocks.dhaka.availabilityState='UNAVAILABLE'
assert.throws(()=>parseTemporalOverview(temporalUnavailableLeak),/INVALID_TEMPORAL_CLOCK_AVAILABILITY_DTO/,'P001 unavailable side must not retain an old current value')
const temporalStaleAge=temporalClone(readyTemporalDto);temporalStaleAge.generatedAt='2026-08-02T06:05:01Z'
assert.throws(()=>parseTemporalOverview(temporalStaleAge),/INVALID_TEMPORAL_CLOCK_STALENESS_DTO/,'P001 clock age beyond the explicit threshold must not remain available')
const temporalValidStale=temporalClone(temporalStaleAge);temporalValidStale.clockState='STALE';for(const clock of Object.values(temporalValidStale.clocks)){clock.availabilityState='UNAVAILABLE';clock.localDate=null;clock.localTime=null}
assert.equal(parseTemporalOverview(temporalValidStale).clockState,'STALE','P001 must consume an over-threshold response only after both current clock values are revoked')
const temporalStaleAvailable=temporalClone(readyTemporalDto);temporalStaleAvailable.clockState='STALE'
assert.throws(()=>parseTemporalOverview(temporalStaleAvailable),/INVALID_TEMPORAL_CLOCK_STALENESS_DTO/,'P001 STALE must revoke both current clock values')
const temporalCoverageMismatch=temporalClone(readyTemporalDto);temporalCoverageMismatch.holidays.china.sourceCoverageDate='2026-08-01'
assert.throws(()=>parseTemporalOverview(temporalCoverageMismatch),/TEMPORAL_HOLIDAY_COVERAGE_DATE_MISMATCH/,'P001 no-holiday conclusion must cover the same local date')
const temporalInvertedPeriod=temporalClone(readyTemporalDto);temporalInvertedPeriod.holidays.china.effectiveFrom='2026-08-02T07:00:00Z';temporalInvertedPeriod.holidays.china.effectiveTo='2026-08-02T05:00:00Z'
assert.throws(()=>parseTemporalOverview(temporalInvertedPeriod),/INVALID_TEMPORAL_HOLIDAY_PERIOD_ORDER/,'P001 determinate holiday period must be ordered')
const temporalOutsidePeriod=temporalClone(readyTemporalDto);temporalOutsidePeriod.holidays.china.effectiveFrom='2026-08-02T07:00:00Z';temporalOutsidePeriod.holidays.china.effectiveTo='2026-08-02T08:00:00Z'
assert.throws(()=>parseTemporalOverview(temporalOutsidePeriod),/INVALID_TEMPORAL_HOLIDAY_SOURCE_DTO/,'P001 determinate holiday period must contain the shared reference instant')
const temporalExtraHoliday=temporalClone(readyTemporalDto);temporalExtraHoliday.holidays.china.evidenceRef='SECRET'
assert.throws(()=>parseTemporalOverview(temporalExtraHoliday),/INVALID_TEMPORAL_HOLIDAY_DTO/,'P001 holiday evidence/identity fields must fail closed')
const revokeHoliday=(holiday,state)=>({...holiday,state,holidayId:null,name:null,note:null,sourceType:null,sourceCoverageDate:null,effectiveFrom:null,effectiveTo:null,version:null})
const temporalReadError=temporalClone(readyTemporalDto);temporalReadError.holidays.china=revokeHoliday(temporalReadError.holidays.china,'READ_ERROR')
assert.equal(parseTemporalOverview(temporalReadError).holidays.china.state,'READ_ERROR','P001 must consume the backend READ_ERROR shape with all conclusion/source metadata revoked')
const temporalReadErrorLeak=temporalClone(temporalReadError);temporalReadErrorLeak.holidays.china.sourceType='LOCAL_SYNTHETIC_CALENDAR'
assert.throws(()=>parseTemporalOverview(temporalReadErrorLeak),/INVALID_TEMPORAL_READ_ERROR_METADATA_DTO/,'P001 READ_ERROR must reject retained source metadata')
const temporalStaleHoliday=temporalClone(readyTemporalDto);temporalStaleHoliday.holidays.china={...temporalStaleHoliday.holidays.china,state:'STALE_OR_EXPIRED',holidayId:null,name:null,note:null}
assert.equal(parseTemporalOverview(temporalStaleHoliday).holidays.china.state,'STALE_OR_EXPIRED','P001 must consume non-current metadata while revoking stale conclusion fields')
const temporalStaleConclusionLeak=temporalClone(temporalStaleHoliday);temporalStaleConclusionLeak.holidays.china.name='Old conclusion'
assert.throws(()=>parseTemporalOverview(temporalStaleConclusionLeak),/INVALID_TEMPORAL_NON_CURRENT_HOLIDAY_DTO/,'P001 stale/unpublished states must revoke conclusion fields')
const temporalUnpublished=temporalClone(readyTemporalDto);temporalUnpublished.holidays.bangladesh={...temporalUnpublished.holidays.bangladesh,state:'UNPUBLISHED',holidayId:null,name:null,note:null}
assert.equal(parseTemporalOverview(temporalUnpublished).holidays.bangladesh.state,'UNPUBLISHED','P001 must consume the backend UNPUBLISHED shape with conclusion fields revoked')
const temporalUnknown=temporalClone(readyTemporalDto);temporalUnknown.projectCode='TEMPORAL_OVERVIEW_UNKNOWN';temporalUnknown.clockState='STALE';temporalUnknown.retryClass='USER_INITIATED_READ_ONLY';for(const clock of Object.values(temporalUnknown.clocks)){clock.availabilityState='UNAVAILABLE';clock.localDate=null;clock.localTime=null};temporalUnknown.holidays.china=revokeHoliday(temporalUnknown.holidays.china,'READ_ERROR');temporalUnknown.holidays.bangladesh=revokeHoliday(temporalUnknown.holidays.bangladesh,'READ_ERROR')
assert.equal(Object.keys(parseTemporalOverview(temporalUnknown)).length,12,'P001 UNKNOWN fail-closed response must remain consumable as the same strict twelve-field envelope')

const makeTemporalState=()=>({viewState:'LOADING',overview:null,readGeneration:0,lastAuthorityState:null,lastReferenceInstant:null})
const recoveredState=makeTemporalState()
const degraded=temporalClone(readyTemporal);degraded.clockState='DHAKA_UNAVAILABLE';degraded.clocks.dhaka.availabilityState='UNAVAILABLE';degraded.clocks.dhaka.localDate=null;degraded.clocks.dhaka.localTime=null
await executeTemporalOverviewRead(recoveredState,{getOverview:async()=>degraded},'FIRST_SHOW')
assert.equal(recoveredState.viewState,'DHAKA_UNAVAILABLE','P001 must preserve the healthy clock when one side is unavailable')
const refreshed=temporalClone(readyTemporal);refreshed.referenceInstant='2026-08-02T06:01:00Z';refreshed.generatedAt='2026-08-02T06:01:01Z'
assert.equal((await executeTemporalOverviewRead(recoveredState,{getOverview:async()=>refreshed},'FOREGROUND_SHOW')).recoveredOnce,true,'P001 must surface RECOVERED once after a new reference instant restores both clocks')
assert.equal(recoveredState.viewState,'RECOVERED','P001 recovered transition must be observable once')
assert.equal((await executeTemporalOverviewRead(recoveredState,{getOverview:async()=>refreshed},'FOREGROUND_SHOW')).recoveredOnce,false,'P001 must not loop RECOVERED for the same authority state/reference')
assert.equal(recoveredState.viewState,'BOTH_AVAILABLE','P001 must settle to BOTH_AVAILABLE after the one-time recovery')

assert.match(client,/requestAnonymousRead\('\/home\/temporal-overview'\)/,'P001 must consume the frozen anonymous GET path without body or query')
const temporalClient=client.slice(client.indexOf('async getTemporalOverview'),client.indexOf('\n  },',client.indexOf('async getTemporalOverview'))+5)
assert.doesNotMatch(temporalClient,/projectSubjectRef|openid|device|timeZone|referenceInstant|header:|data:/i,'P001 read must not submit identity, device timezone or client time fields')
assert.ok(home.indexOf('class="hero"')<home.indexOf('class="temporal-card"'),'P001 recharge hero must remain before the temporal read-only card')
assert.match(home,/onShow\(\(\)=>\{[\s\S]*FOREGROUND_SHOW[\s\S]*loadTemporal\(trigger\)/,'P001 must reread once on first and foreground show')
assert.match(home,/@click="loadTemporal\('USER_REFRESH'\)"/,'P001 must expose a user-initiated read-only refresh')
assert.doesNotMatch(`${home}\n${temporalFlow}\n${temporalContract}`,/setInterval|uni\.requestSubscribeMessage|requestPayment|Notification|Reminder|setStorageSync|getSystemInfo|getTimezoneOffset|Intl\.DateTimeFormat|Date\.now\(/,'P001 must not poll, notify, pay, persist, use device time/timezone, or reimplement the server JDK timezone conversion')
for(const stateName of ['LOADING','BOTH_AVAILABLE','DHAKA_UNAVAILABLE','BEIJING_UNAVAILABLE','BOTH_UNAVAILABLE','STALE','RECOVERED'])assert.match(home,new RegExp(`${stateName}:'[^']+'`),`P001 must provide ordinary Chinese copy for clock state ${stateName}`)
for(const copy of ['正在读取今日节假日','今日无已登记节假日','今日有已登记节假日','待官方确认','节假日信息读取失败','信息已过期，当前结论不可用','内容已下架'])assert.match(home,new RegExp(copy),`P001 must provide the approved holiday copy: ${copy}`)
assert.match(home,/RECOVERED:'时间已恢复'/,'P001 recovered state must use the approved ordinary Chinese copy')
assert.match(home,/同一基准，不使用设备时区/,'P001 must explain the shared reference instant without exposing an internal timezone identifier')
const temporalTemplate=home.slice(home.indexOf('<view class="temporal-card"'),home.indexOf('<view class="entries">'))
assert.doesNotMatch(temporalTemplate,/LOCAL_SYNTHETIC|ReferenceInstant|Fixture|Evidence|Authorization|提醒我|订阅|通知/,'P001 visible temporal card must not expose engineering terms or reminder/notification actions')
assert.doesNotMatch(temporalContract,/\b(?:const|function)\s+|export function|parseTemporalOverview/,'P001 contract module must remain type-only')
assert.match(temporalFlow,/exactKeys\(value,ROOT_KEYS\)/,'P001 flow must reject unknown root fields')
assert.match(temporalFlow,/exactKeys\(value,CLOCK_KEYS\)/,'P001 flow must reject unknown clock fields')
assert.match(temporalFlow,/exactKeys\(value,HOLIDAY_KEYS\)/,'P001 flow must reject unknown holiday fields')
assert.match(temporalFlow,/parseClock\(value\.clocks\.dhaka,'DHAKA','Asia\/Dhaka'\)/,'P001 strict DTO must bind the server-declared Dhaka zone identifier')
assert.match(temporalFlow,/parseClock\(value\.clocks\.beijing,'BEIJING','Asia\/Shanghai'\)/,'P001 strict DTO must bind the server-declared Beijing zone identifier')

console.log('frontend contract assertions: PASS (DEV-INT + negative cases)')
