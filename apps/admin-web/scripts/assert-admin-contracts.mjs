import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { loadAdminPage, parseAdminPageProjection, resolveAdminDataMode } from '../src/api/admin-read.ts'
import { loadA140Detail, parseA140Detail } from '../src/api/admin-a140-detail.ts'
import { appendReconciliationEvent, createCustomerCase, loadCustomerCase, loadReconciliation } from '../src/api/admin-workflow.ts'
import { pageRoleAllowed } from '../src/domain/admin.ts'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const source = (path) => readFileSync(join(root, path), 'utf8')
const app = source('src/App.vue')
const adapter = source('src/api/admin-read.ts')
const commandAdapter = source('src/api/admin-command.ts')
const domain = source('src/domain/admin.ts')
const managed = source('src/components/workspaces/ManagedContentWorkspace.vue')
const catalog = source('src/components/workspaces/A130Workspace.vue')
const editor = source('src/components/AdminRecordEditor.vue')
const channelPanel = source('src/components/A130ChannelPanel.vue')
const order = source('src/components/workspaces/A140Workspace.vue')
const dashboard = source('src/components/workspaces/B002Dashboard.vue')
const directory = source('src/components/workspaces/A121Workspace.vue')
const information = source('src/components/workspaces/A122Workspace.vue')
const workflowAdapter = source('src/api/admin-workflow.ts')
const a100 = source('src/components/workspaces/A100Workspace.vue')
const a110 = source('src/components/workspaces/A110Workspace.vue')
const a140Detail = source('src/components/AdminOrderDetail.vue')

assert.equal(existsSync(join(root, 'src/data/synthetic.ts')), false)
assert.doesNotMatch(app + adapter, /BUILTIN_SYNTHETIC|getSyntheticProjection|syntheticRole|previewRole/)
assert.equal(resolveAdminDataMode(undefined), 'PROJECT_API_PROXY')
assert.equal(resolveAdminDataMode(''), 'PROJECT_API_PROXY')
assert.equal(resolveAdminDataMode('PROJECT_API_PROXY'), 'PROJECT_API_PROXY')
assert.equal(resolveAdminDataMode('BUILTIN_SYNTHETIC'), null)
assert.match(app, /页面展示当前环境业务数据/)
assert.match(app, /未启用的外部服务不会被调用/)
assert.doesNotMatch(app, /本地数据库预置数据/)
for (const page of ['A100', 'A110', 'A120', 'A121', 'A122', 'A130', 'A140']) assert.match(app, new RegExp(`id: '${page}'`))
assert.match(app, /id: 'B002'/)
assert.match(app, /#\//)
assert.match(app, /B002Dashboard/)
assert.match(dashboard, /loadAdminPage/)
assert.match(dashboard, /当前没有可展示的趋势数据/)
for (const text of ['待处理客服案件', '交易差异', '待审核内容', '当前可售商品']) assert.match(dashboard, new RegExp(text))
assert.doesNotMatch(dashboard, /Math\.random|synthetic|mock/i)
for (const text of ['机构信息', '城市配置', '当地地址', 'V1 无图片']) assert.match(directory, new RegExp(text))
for (const text of ['周休与节假日', '生活资讯', '纯文字']) assert.match(information, new RegExp(text))
for (const text of ['商品列表', '供应商目录', '渠道映射', '定价配置', '价格试算']) assert.match(catalog, new RegExp(text))
assert.match(domain, /role: 'SUPER_ADMIN'/)
assert.doesNotMatch(managed + catalog, /<input[^>]*type=["']file|<input[^>]*(?:image|media|cover)|<button[^>]*>[^<]*(?:上传|素材)/)
assert.match(catalog, /供应商不可达/)
assert.match(catalog, /SUPER_ADMIN/)
assert.match(catalog, /catalogBatchRef/)
assert.match(catalog, /minimumMarginRate/)
assert.match(catalog, /itemKey\(item\)/)
assert.doesNotMatch(adapter + source('src/components/workspaces/A110Workspace.vue'), /悬账/)
for (const [page, role] of [['A100','CS'],['A110','FIN'],['A110','CS'],['A120','CONTENT'],['A121','CONTENT'],['A122','CONTENT'],['A130','CONTENT'],['A140','FIN'],['A140','CS']]) assert.equal(pageRoleAllowed(page, role), true)
assert.equal(pageRoleAllowed('A130', 'FIN'), false)
assert.match(order, /不提供手工改终态、调账、退款、再次充值或切换供应商/)
assert.match(commandAdapter, /credentials: 'include'/)
assert.match(commandAdapter, /'Idempotency-Key': crypto\.randomUUID\(\)/)
assert.match(commandAdapter, /\/admin-command\/v1\/\$\{resource\}/)
assert.match(commandAdapter, /price-versions\/trial/)
assert.match(commandAdapter, /minimumMarginSatisfied/)
assert.match(source('src/components/AdminCreatePanel.vue'), /minimumMarginRate/)
assert.match(source('src/components/AdminCreatePanel.vue'), /trial-breakdown/)
assert.match(editor, /promotionBearer/)
assert.match(editor, /product-mappings/)
assert.match(editor, /dataAllowanceMb/)
assert.match(channelPanel, /\/admin-read\/v1\/pages\/A130\/channels/)
assert.match(channelPanel, /transitionAdminObject\('channels'/)
assert.match(channelPanel, /updateAdminObject\('channels'/)
assert.doesNotMatch(channelPanel + catalog, /\/sync|syncCatalog|triggerSupplier/i)
assert.match(editor, /updateAdminObject/)
assert.match(editor, /transitionAdminObject/)
for (const action of ['submit', 'publish', 'unpublish', 'enable', 'disable']) assert.match(editor, new RegExp(`perform\\('${action}'\\)`))
assert.match(editor, /expectedVersion: props\.item\.version/)
assert.match(editor, /emit\('changed'\)/)
assert.doesNotMatch(commandAdapter, /Authorization|Bearer|localStorage|sessionStorage|provider.*(?:key|secret)/i)
assert.doesNotMatch(app + a140Detail, /AdminOrderSandboxDetail|sandbox-status/)
assert.match(workflowAdapter, /\/admin-workflow\/v1\/customer-cases/)
assert.match(workflowAdapter, /\/admin-workflow\/v1\/reconciliations/)
assert.match(workflowAdapter, /'Idempotency-Key': crypto\.randomUUID\(\)/)
assert.match(a100 + a110, /expectedVersion/)
assert.match(a110, /不会重复充值、擅自退款、调账或修改支付与充值终态/)
assert.match(a100 + a110, /stateLabel/)
assert.match(a110, /differenceLabel[\s\S]*roleLabel/)
assert.doesNotMatch(a140Detail, /JSON\.stringify|entitlementSnapshot|providerSku|providerCode|rawBenefit|rawSku/)

const projections = {
  A100: { schemaVersion: 'ADMIN_READ_V1', projectionVersion: 'CASE-V1', pageId: 'A100', role: 'SUPER_ADMIN', items: [{ caseRef: 'CASE-1', sourceType: 'ADMIN_MANUAL', issueType: 'TOPUP_STATUS_QUERY', relatedOrderRef: null, priorityCode: 'NORMAL', state: 'OPEN', ownerRef: null, updatedAt: '2026-08-16T00:00:00Z' }] },
  A110: { schemaVersion: 'ADMIN_READ_V1', projectionVersion: 'DIFF-V1', pageId: 'A110', role: 'FIN', items: [{ reconciliationRef: 'REC-1', orderRef: 'ORDER-1', differenceType: 'PAYMENT_TOPUP', amount: 6.8, currency: 'CNY', state: 'OPEN', ownerRef: null, discoveredAt: '2026-08-16T00:00:00Z', updatedAt: '2026-08-16T00:00:00Z' }] },
  A120: { schemaVersion: 'ADMIN_READ_V1', projectionVersion: 'REVIEW-V1', pageId: 'A120', role: 'SUPER_ADMIN', items: [{ contentRef: 'CONTENT-1', title: '待审核内容', sourceLabel: 'MANUAL', reviewLabel: '待审核', complaintLabel: '无投诉', visibilityLabel: '未发布', ownerLabel: '内容运营', historyLabel: '版本1', removalLabel: '无' }] },
  A121: { schemaVersion: 'ADMIN_READ_V1', projectionVersion: 'DIR-V1', pageId: 'A121', role: 'SUPER_ADMIN', items: [{ entryRef: 'DIR-1', cityRef: 'DHAKA', cityName: '达卡', category: 'MEDICAL', name: '社区医疗服务中心', summary: '为当地居民提供基础医疗服务', localAddress: 'Road 11, Banani, Dhaka 1213', phone: '+880 2 0000 0000', sourceRef: 'SOURCE-1', verifiedAt: '2026-08-16T00:00:00Z', validUntil: '2027-08-16T00:00:00Z', version: 1, state: 'PUBLISHED', updatedAt: '2026-08-16T00:00:00Z' }] },
  A122: { schemaVersion: 'ADMIN_READ_V1', projectionVersion: 'CONTENT-V1', pageId: 'A122', role: 'SUPER_ADMIN', items: [{ objectType: 'NEWS', objectRef: 'NEWS-1', countryCode: null, category: 'LIFE_REMINDER', title: '生活提醒', summary: '摘要', bodyText: '纯文字正文', sourceRef: 'SOURCE-1', editor: '内容运营', publishAt: '2026-08-16T00:00:00Z', startDate: null, endDate: null, weekendDays: null, effectiveFrom: null, effectiveUntil: null, validUntil: '2027-08-16T00:00:00Z', version: 1, state: 'PUBLISHED', updatedAt: '2026-08-16T00:00:00Z' }] },
  A130: { schemaVersion: 'ADMIN_READ_V1', projectionVersion: 'PRODUCT-V1', pageId: 'A130', role: 'SUPER_ADMIN', items: [{ productRef: 'GP-BALANCE-100', operatorCode: 'GRAMEENPHONE', productType: 'BALANCE', displayName: 'Grameenphone 100塔卡余额', benefitText: '到账100 BDT话费余额', denominationBdt: 100, validityText: '长期有效', providerCode: 'UNREACHABLE', providerSku: 'GP-100', countryCode: 'BD', dataAllowanceMb: null, voiceMinutes: null, smsCount: null, channelPriority: 10, phoneRule: null, saleStartAt: null, saleEndAt: null, catalogBatchRef: null, rawSkuName: null, rawBenefitText: null, supplierCost: null, settlementCurrency: null, supplierAvailability: null, catalogSyncedAt: null, normalizedType: null, normalizedOperator: null, mappingState: null, mappingFailureReason: null, state: 'ENABLED', version: 1, priceVersionRef: 'PRICE-V1', finalAmountCny: 6.80, fxSource: 'MANUAL', fxSnapshotRef: 'FX-1', priceState: 'ENABLED', effectiveFrom: '2026-08-16T00:00:00Z', effectiveUntil: '2027-08-16T00:00:00Z', priceVersion: 1, priceSupplierCost: null, priceSettlementCurrency: null, fxDirection: null, fxRate: null, fxUpdatedAt: null, fxValidUntil: null, bufferRate: null, markupRate: null, wechatFeeRate: null, taxRate: null, minimumMarginRate: null, roundingRule: null, promotionBearer: null, pricingScope: null }] },
  A140: { schemaVersion: 'ADMIN_READ_V1', projectionVersion: 'ORDER-V1', pageId: 'A140', role: 'SUPER_ADMIN', items: [{ orderRef: 'ORDER-1', maskedPhone: '017****0000', userStatusLabel: '等待付款', totalLabel: 'CNY 6.80', updatedLabel: '刚刚更新' }] },
}

for (const [pageId, payload] of Object.entries(projections)) {
  assert.ok(parseAdminPageProjection(payload, pageId))
  assert.ok(parseAdminPageProjection({ ...payload, items: [] }, pageId))
  const additionalRole = { A100: 'CS', A110: 'SUPER_ADMIN', A120: 'CONTENT', A121: 'CONTENT', A122: 'CONTENT', A130: 'CONTENT', A140: 'FIN' }[pageId]
  assert.ok(parseAdminPageProjection({ ...payload, role: additionalRole }, pageId))
  assert.equal(parseAdminPageProjection({ ...payload, extra: true }, pageId), null)
  const deniedRole = { A100: 'CONTENT', A110: 'CONTENT', A120: 'CS', A121: 'CS', A122: 'CS', A130: 'FIN', A140: 'CONTENT' }[pageId]
  assert.equal(parseAdminPageProjection({ ...payload, role: deniedRole }, pageId), null)
  let request
  const state = await loadAdminPage('PROJECT_API_PROXY', pageId, async (input, init) => {
    request = { input: String(input), init }
    return new Response(JSON.stringify(payload), { status: 200, headers: { 'Content-Type': 'application/json' } })
  })
  assert.equal(state.status, 'READY')
  assert.equal(request.input, `/admin-read/v1/pages/${pageId}`)
  assert.equal(request.init.method, 'GET')
  assert.equal(request.init.credentials, 'include')
  assert.deepEqual(request.init.headers, { Accept: 'application/json' })
  assert.equal('body' in request.init, false)
}

for (const [status, expected] of [[401, 'UNAUTHENTICATED'], [403, 'ACCESS_DENIED'], [404, 'UNAVAILABLE'], [500, 'UNAVAILABLE']]) {
  const state = await loadAdminPage('PROJECT_API_PROXY', 'A100', async () => new Response('', { status }))
  assert.equal(state.status, expected)
  assert.equal(state.data, null)
}

const invalid = await loadAdminPage('PROJECT_API_PROXY', 'A130', async () => new Response(JSON.stringify({ invalid: true }), { status: 200 }))
assert.equal(invalid.status, 'UNAVAILABLE')
assert.equal(invalid.data, null)

const a140Payload = {
  schemaVersion: 'ADMIN_READ_V1', projectionVersion: 'A140-DETAIL-1', pageId: 'A140', role: 'FIN',
  order: { orderRef: 'ORDER-1', quoteRef: 'QUOTE-1', state: 'PAID', amountMinor: 680, currency: 'CNY', phoneMasked: '017****0000', operatorCode: 'GRAMEENPHONE', productRef: 'GP-100', priceVersionRef: 'PRICE-1', entitlement: { productRef:'GP-100', productType:'BALANCE', displayName:'100塔卡余额', benefitText:'到账100 BDT话费余额', denominationBdt:100, dataAllowanceMb:null, voiceMinutes:null, smsCount:null, validityText:'长期有效' }, createdAt: '2026-08-18T00:00:00Z', updatedAt: '2026-08-18T00:01:00Z' },
  payment: { state: 'PAID', providerRef: 'PAY-1', amountMinor: 680, currency: 'CNY', refundedMinor: 0, version: 1, updatedAt: '2026-08-18T00:01:00Z' },
  topup: { state: 'UNKNOWN', providerRef: null, version: null, updatedAt: null }, refunds: [],
  timeline: [{ eventRef: 'EVENT-1', eventType: 'ORDER_CREATED', source: 'PROJECTED', occurredAt: '2026-08-18T00:00:00Z', projectedOrDispatchedAt: '2026-08-18T00:00:01Z' }], reconciliationRefs: [], customerCaseRefs: [],
}
assert.ok(parseA140Detail(a140Payload))
assert.equal(parseA140Detail({ ...a140Payload, extra: true }), null)
let a140Request
const a140Loaded = await loadA140Detail('ORDER-1', async (input, init) => { a140Request={ input:String(input), init }; return new Response(JSON.stringify(a140Payload), { status:200 }) })
assert.equal(a140Loaded.order.orderRef, 'ORDER-1')
assert.equal(a140Request.input, '/admin-read/v1/pages/A140/ORDER-1')
assert.equal(a140Request.init.credentials, 'include')
await assert.rejects(() => loadA140Detail('MISSING', async () => new Response('', { status:404 })), /没有找到这笔订单/)
assert.equal(parseA140Detail({ ...a140Payload, order:{...a140Payload.order, entitlement:{...a140Payload.order.entitlement, providerSku:'RAW'}} }), null)
assert.equal(parseA140Detail({ ...a140Payload, order:{...a140Payload.order, entitlement:{benefitText:'不完整'}} }), null)
assert.ok(parseA140Detail({ ...a140Payload, order:{...a140Payload.order, entitlement:null} }))

const caseDetailPayload = { schemaVersion:'1.0', projectionVersion:'workflow-detail-v1', item:{caseRef:'CASE-1',sourceType:'WECHAT',issueType:'TOPUP_QUERY',relatedOrderRef:'ORDER-1',priorityCode:'NORMAL',ownerRef:null,state:'OPEN',description:'用户查询充值结果',dataOrigin:'ADMIN',version:1,createdAt:'2026-08-18T00:00:00Z',updatedAt:'2026-08-18T00:00:00Z'}, history:[{eventRef:'EVT-1',eventType:'CREATE',note:'用户查询充值结果',evidenceRef:null,fromState:'NONE',toState:'OPEN',actorRef:'CS-1',createdAt:'2026-08-18T00:00:00Z'}] }
const reconDetailPayload = { schemaVersion:'1.0', projectionVersion:'workflow-detail-v1', item:{reconciliationRef:'REC-1',orderRef:'ORDER-1',differenceType:'PAYMENT_TOPUP_PENDING',amount:6.8,currency:'CNY',state:'OPEN',ownerRef:null,dataOrigin:'ADMIN',version:1,discoveredAt:'2026-08-18T00:00:00Z',updatedAt:'2026-08-18T00:00:00Z'}, history:[{eventRef:'REV-1',actionType:'NOTE',evidenceRef:null,note:'等待核对',fromState:'OPEN',toState:'IN_REVIEW',actorRef:'FIN-1',createdAt:'2026-08-18T00:00:00Z'}] }
assert.equal((await loadCustomerCase('CASE-1', async()=>new Response(JSON.stringify(caseDetailPayload),{status:200}))).item.state,'OPEN')
assert.equal((await loadReconciliation('REC-1', async()=>new Response(JSON.stringify(reconDetailPayload),{status:200}))).item.differenceType,'PAYMENT_TOPUP_PENDING')
for (const invalidCase of [
  {...caseDetailPayload,item:{...caseDetailPayload.item,state:'UNKNOWN'}},
  {...caseDetailPayload,history:[{...caseDetailPayload.history[0],eventType:'DELETE'}]},
  {...caseDetailPayload,item:{...caseDetailPayload.item,extra:true}},
]) await assert.rejects(()=>loadCustomerCase('CASE-1',async()=>new Response(JSON.stringify(invalidCase),{status:200})),/不符合约定/)
for (const invalidRecon of [
  {...reconDetailPayload,item:{...reconDetailPayload.item,differenceType:'RAW_PROVIDER_DIFFERENCE'}},
  {...reconDetailPayload,item:{...reconDetailPayload.item,state:'UNKNOWN'}},
  {...reconDetailPayload,history:[{...reconDetailPayload.history[0],actionType:'RECHARGE_AGAIN'}]},
]) await assert.rejects(()=>loadReconciliation('REC-1',async()=>new Response(JSON.stringify(invalidRecon),{status:200})),/不符合约定/)
const caseInput={sourceType:'WECHAT',issueType:'TOPUP_QUERY',relatedOrderRef:'ORDER-1',priorityCode:'NORMAL',description:'查询充值结果'}
assert.equal((await createCustomerCase(caseInput,async()=>new Response(JSON.stringify({caseRef:'CASE-1',state:'OPEN',version:1,replayed:false}),{status:200}))).version,1)
assert.equal((await createCustomerCase(caseInput,async()=>new Response(JSON.stringify({caseRef:'CASE-1',state:'OPEN',replayed:true}),{status:200}))).version,null)
for (const invalidCommand of [
  {caseRef:'CASE-1',state:'OPEN',version:1,replayed:false,extra:true},
  {caseRef:'CASE-1',state:'OPEN',version:1,replayed:true},
  {caseRef:'CASE-1',state:'UNKNOWN',version:1,replayed:false},
]) await assert.rejects(()=>createCustomerCase(caseInput,async()=>new Response(JSON.stringify(invalidCommand),{status:200})),/提交结果不符合约定/)
assert.equal((await appendReconciliationEvent('REC-1',{actionType:'NOTE',note:'已核对',expectedVersion:1},async()=>new Response(JSON.stringify({reconciliationRef:'REC-1',state:'IN_REVIEW',version:2,replayed:false}),{status:200}))).state,'IN_REVIEW')

console.log('admin real-data contract assertions: PASS')
