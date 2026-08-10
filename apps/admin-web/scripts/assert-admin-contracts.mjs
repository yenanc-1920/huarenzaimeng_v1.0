import assert from 'node:assert/strict'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative, resolve } from 'node:path'
import { A110_READ_PROXY_PATH, loadAdminPage, parseA110ReadResponse, parseAdminPageProjection, resolveAdminDataMode } from '../src/api/admin-read.ts'
import { getSyntheticA110Response } from '../src/data/synthetic.ts'
import { loadP021AdminOrderDetail, parseP021AdminResponse } from '../src/api/admin-order-detail.ts'

const root = resolve(import.meta.dirname, '..')
const read = (path) => readFileSync(join(root, path), 'utf8')
const collectFiles = (directory) => readdirSync(directory).flatMap((name) => {
  const path = join(directory, name)
  return statSync(path).isDirectory() ? collectFiles(path) : [path]
})

const app = read('src/App.vue')
const adapter = read('src/api/admin-read.ts')
const controller = read('src/api/admin-read-controller.ts')
const domain = read('src/domain/admin.ts')
const synthetic = read('src/data/synthetic.ts')
const workspace = read('src/components/AdminWorkspace.vue')
const accessDenied = read('src/components/AccessDenied.vue')
const loginRequired = read('src/components/LoginRequired.vue')
const adminInitialization = read('src/components/AdminInitialization.vue')
const adminAuth = read('src/api/admin-auth.ts')
const readUnavailable = read('src/components/ReadUnavailable.vue')
const a100 = read('src/components/workspaces/A100Workspace.vue')
const a110 = read('src/components/workspaces/A110Workspace.vue')
const a120 = read('src/components/workspaces/A120Workspace.vue')
const a130 = read('src/components/workspaces/A130Workspace.vue')
const a140 = read('src/components/workspaces/A140Workspace.vue')
const p021AdminAdapter = read('src/api/admin-order-detail.ts')
const p021AdminDetail = read('src/components/P021AdminOrderDetail.vue')
const styles = read('src/style.css')
const viteConfig = read('vite.config.ts')
const a110VisualEntry = read('scripts/a110-visual-fixture-entry.ts')

for (const id of ['UX-A100', 'UX-A110', 'UX-A120', 'UX-A130', 'UX-A140']) {
  const allWorkspaces = [a100, a110, a120, a130, a140].join('\n')
  assert.equal(allWorkspaces.split(`data-page-id="${id}"`).length - 1, 1, `${id} must have one stable root`)
  assert.equal(app.includes(id), false, `${id} must not be visible from the app shell`)
}

assert.match(workspace, /A100Workspace/)
assert.match(workspace, /A110Workspace/)
assert.match(workspace, /A120Workspace/)
assert.match(workspace, /A130Workspace/)
assert.match(workspace, /A140Workspace/)
assert.match(app, /status: 'LOADING', data: null/)
assert.match(app, /createAdminReadController/)
assert.match(controller, /sink\(\{ status: 'LOADING', data: null/)
assert.match(controller, /currentGeneration !== generation/)
assert.match(controller, /status: 'UNAVAILABLE', data: null/)
assert.match(app, /ReadUnavailable/)
assert.match(app, /AccessDenied/)
assert.match(app, /LoginRequired/)
assert.match(app, /readState\.status === 'UNAUTHENTICATED'/)
assert.match(app, /class="standalone-login"/)
assert.match(app, /data-read-state="AUTH_CHECK"/)
assert.match(app, /确认完成前不会显示后台菜单或业务数据/)
assert.match(app, /试用数据库数据/)
assert.match(app, /当前读取试用数据库中的持久化记录，不代表真实用户生产数据/)
assert.match(app, /退出登录/)
assert.match(app, /logoutAdmin/)
assert.match(app, /AdminInitialization/)
assert.match(app, /@authenticated="authenticated"/)
assert.match(app, /@click="refresh"/)
assert.match(app, /<nav class="sidebar" aria-label="后台页面导航">/)
assert.match(app, /:aria-current="activePage === page\.id \? 'page' : undefined"/)
assert.match(app, /data-read-state="LOADING" role="status" aria-live="polite"/)
assert.match(app, /:data-read-state="readState\.data\.items\.length === 0 \? 'READY_EMPTY' : 'READY'"/)
assert.match(app, /role="status" aria-live="polite"/)
assert.match(accessDenied, /data-read-state="ACCESS_DENIED"[\s\S]*role="status" aria-live="polite"/)
assert.match(accessDenied, /AUTHORITY_UNAVAILABLE/)
assert.match(accessDenied, /当前无法安全显示，请重新进入/)
assert.match(accessDenied, /当前无法访问此页面/)
assert.match(loginRequired, /data-read-state="UNAUTHENTICATED"[\s\S]*role="alert" aria-live="assertive"/)
assert.match(loginRequired, /请先登录/)
assert.match(loginRequired, /不开放自行注册/)
assert.match(loginRequired, /type="password"/)
assert.match(loginRequired, /autocomplete="current-password"/)
assert.doesNotMatch(loginRequired, /<select|CS|FIN|CONTENT|SUPER_ADMIN|localStorage|sessionStorage/)
assert.match(adminInitialization, /autocomplete="new-password"/)
assert.match(adminInitialization, /一次性初始化码/)
assert.doesNotMatch(adminInitialization, /localStorage|sessionStorage/)
assert.match(adminAuth, /credentials: 'include'/)
assert.match(adminAuth, /logoutAdmin = \(\) => jsonRequest\('\/logout', \{\}\)/)
assert.doesNotMatch(adminAuth, /localStorage|sessionStorage|Authorization|Bearer/)
assert.match(readUnavailable, /data-read-state="UNAVAILABLE"[\s\S]*role="alert" aria-live="assertive"/)

assert.match(a100, /role="list" aria-label="客服案件列表"/)
assert.match(a100, /role="listitem"/)
for (const [name, source, expectedTables] of [['A110', a110, 2], ['A120', a120, 1], ['A130', a130, 2], ['A140', a140, 2]]) {
  assert.equal(source.split('role="table"').length - 1, expectedTables, `${name} table relationship count mismatch`)
  assert.ok(source.includes('role="row"'), `${name} row relationship missing`)
  assert.ok(source.includes('role="columnheader"'), `${name} columnheader relationship missing`)
  assert.ok(source.includes('role="cell"'), `${name} cell relationship missing`)
}

assert.match(adapter, /credentials: 'include'/)
assert.match(adapter, /headers: \{ Accept: 'application\/json' \}/)
assert.doesNotMatch(adapter, /Authorization|Bearer|X-Actor|X-Role|localStorage|sessionStorage/)
assert.equal(resolveAdminDataMode(undefined), null)
assert.equal(resolveAdminDataMode(''), null)
assert.equal(resolveAdminDataMode('BUILTIN_SYNTHETIC'), null)
assert.equal(resolveAdminDataMode(undefined, true), 'BUILTIN_SYNTHETIC')
assert.equal(resolveAdminDataMode('BUILTIN_SYNTHETIC', true), 'BUILTIN_SYNTHETIC')
assert.equal(resolveAdminDataMode('PROJECT_API_PROXY'), 'PROJECT_API_PROXY')
assert.doesNotMatch(p021AdminAdapter, /Authorization|Bearer|X-Actor|X-Role|localStorage|sessionStorage/)
assert.match(p021AdminAdapter, /credentials: 'include'/)
assert.match(p021AdminAdapter, /\/admin-read\/v1\/orders\//)
assert.match(p021AdminDetail, /data-page-id="UX-P021-ADMIN"/)
assert.doesNotMatch(adapter, /getSyntheticProjection[\s\S]*catch[\s\S]*getSyntheticProjection/)
assert.match(adapter, /if \(!parsed\) return \{ status: 'UNAVAILABLE', data: null/)
assert.match(adapter, /value\.pageId !== requestedPage/)
assert.match(adapter, /exactKeys\(value, \['schemaVersion', 'projectionVersion', 'pageId', 'role', 'items'\]\)/)
assert.match(adapter, /A110_READ_PROXY_PATH = '\/admin-read\/v1\/reconciliations'/)
assert.match(adapter, /A110_ROOT_KEYS = \['requestRef', 'viewState', 'projectCode', 'schemaVersion', 'roleProjection', 'roleBindingVersion', 'authorizationDecisionVersion', 'projectionVersion', 'items', 'allowedActions', 'retryClass'\]/)
assert.match(adapter, /exactKeys\(value, A110_ROOT_KEYS\)/)
assert.doesNotMatch(adapter.slice(adapter.indexOf('export function parseA110ReadResponse'), adapter.indexOf('export const parseAdminPageProjection')), /CONTENT/)

for (const variant of ['A100:CS', 'A120:CONTENT', 'A130:CONTENT', 'A130:FIN', 'A140:CS', 'A140:FIN']) {
  assert.match(adapter, new RegExp(`'${variant}'`), `${variant} mapper missing`)
}
for (const deniedVariant of ['A130:CS', 'A140:CONTENT']) {
  assert.equal(adapter.includes(`'${deniedVariant}'`), false, `${deniedVariant} must not have a DTO mapper`)
}
assert.equal(adapter.includes("'A110:CONTENT'"), false, 'A110 CONTENT must not have a DTO mapper')
assert.match(domain, /supportBatchLabel: string/)
assert.match(domain, /priceCostCandidateLabel: string/)
assert.doesNotMatch(a130.split('<template v-else>')[0], /denominationLabel|currencyLabel|supportBatchLabel/)
assert.doesNotMatch(a130.split('<template v-else>')[0], /priceCostCandidateLabel/)
assert.match(a130.split('<template v-else>')[1], /priceCostCandidateLabel/)
assert.doesNotMatch(a140.split('<template v-else>')[0], /refundLayerLabel|factStatusLabel|agingLabel/)
assert.doesNotMatch(a140.split('<template v-else>')[1], /maskedPhone|session|risk/i)
assert.match(a100, /nextReviewLabel/)
assert.match(a100, /noteStatusLabel/)
assert.match(a100, /escalationStatusLabel/)
assert.match(a120, /ownerLabel/)
assert.match(a120, /historyLabel/)
assert.match(a120, /removalLabel/)
assert.match(a120, /写操作资格为 0/)
assert.match(a110, /data-page-id="UX-A110"/)
assert.match(a110, /W \{\{ factNames\[code\] \}\}|\{\{ code \}\} \{\{ factNames\[code\] \}\}/)
for (const label of ['收款', '上游扣款', '到账', '退款', '本地账务']) assert.match(a110, new RegExp(label), `A110 fact label missing: ${label}`)
assert.match(a110, /NAVIGATE_A100/)
assert.match(a110, /NAVIGATE_A140/)
const a110ClickBindings = [...a110.matchAll(/@click="([^"]+)"/g)].map((match) => match[1])
assert.deepEqual(a110ClickBindings, ["$emit('navigate', 'A100')", "$emit('navigate', 'A140')"])
const a110FinanceTemplate = a110.split('<template v-else>')[0]
const a110SupportTemplate = a110.split('<template v-else>')[1]
assert.doesNotMatch(a110SupportTemplate, /factSummaries|differenceCategories|amountMinor|currency|timeline/)
assert.doesNotMatch(a110FinanceTemplate, /maskedSubjectSummary|userFacingSummary|confirmedItems|unconfirmedItems/)

assert.match(styles, /@media\(max-width:900px\)/)
assert.match(styles, /overflow-x:\s*auto/)
assert.match(styles, /:focus-visible/)
assert.match(styles, /min-height:\s*44px/)

for (const secretName of ['HZ_CONTENT_ADMIN_TOKEN', 'HZ_TEST_ACCESS_TOKEN']) {
  assert.match(viteConfig, new RegExp(`'${secretName}'`), `${secretName} release guard missing`)
}

const browserFiles = collectFiles(join(root, 'src')).filter((path) => /\.(?:ts|vue|css)$/.test(path))
const browserSource = browserFiles.map((path) => `${relative(root, path)}\n${readFileSync(path, 'utf8')}`).join('\n')
assert.equal(/HZ_CONTENT_ADMIN_TOKEN|HZ_TEST_ACCESS_TOKEN|VITE_HZ_CONTENT_ADMIN_TOKEN|VITE_HZ_TEST_ACCESS_TOKEN/.test(browserSource), false, 'browser source must not contain administrator secret names')
assert.equal(/\b(fetch|XMLHttpRequest)\b/.test(synthetic), false, 'synthetic data must not perform network calls')
assert.doesNotMatch(app, /run-admin-state-fixtures|pageFixtures|READY_TO_ERROR/)
assert.equal((a110VisualEntry.match(/'ADM02-A110-V(?:0[1-9]|1[0-4])(?:-(?:Z200|W800))?'/g) ?? []).length, 20, 'A110 local visual allowlist must contain exactly 20 entries')
assert.match(app, /import\.meta\.env\.DEV/)
assert.match(app, /import\('\.\.\/scripts\/a110-visual-fixture-entry'\)/)
for (const source of [app.slice(app.indexOf('<template>')), accessDenied, readUnavailable, a110]) {
  for (const forbidden of ['投影', 'Cookie', '令牌', '资格为 0', '写操作资格']) assert.equal(source.includes(forbidden), false, `visible engineering term must be absent: ${forbidden}`)
}
for (const source of [app.slice(app.indexOf('<template>')), accessDenied, readUnavailable, a100, a110, a120, a130, a140, synthetic, a110VisualEntry]) {
  assert.equal(source.includes('合成'), false, 'visible fixture/page copy must not contain 合成')
}

const a130Content = {
  schemaVersion: 'ADMIN_READ_V1', projectionVersion: 'PV-1', pageId: 'A130', role: 'CONTENT',
  items: [{ catalogRef: 'CAT-1', displayName: '示例商品', operatorLabel: '示例运营商', categoryLabel: '预设项目', contentStatusLabel: '待复核', saleReviewLabel: '等待另一职责' }],
}
const a140Finance = {
  schemaVersion: 'ADMIN_READ_V1', projectionVersion: 'PV-1', pageId: 'A140', role: 'FIN',
  items: [{ orderRef: 'ORDER-1', refundLayerLabel: '退款候选待确认', factStatusLabel: '资金事实未知', totalLabel: 'CNY —', agingLabel: '账龄待确认' }],
}
const a130Finance = {
  schemaVersion: 'ADMIN_READ_V1', projectionVersion: 'PV-1', pageId: 'A130', role: 'FIN',
  items: [{ catalogRef: 'CAT-2', displayName: '示例商品', denominationLabel: 'BDT —', currencyLabel: '待确认', priceCostCandidateLabel: '价格与成本候选未提供', validityLabel: '尚未确定', supportBatchLabel: '尚未确定', financeReviewLabel: '等待复核' }],
}
assert.ok(parseAdminPageProjection(a130Content, 'A130'))
assert.ok(parseAdminPageProjection(a130Finance, 'A130'))
assert.ok(parseAdminPageProjection(a140Finance, 'A140'))
assert.equal(parseAdminPageProjection({ ...a130Content, extra: 'blocked' }, 'A130'), null)
assert.equal(parseAdminPageProjection({ ...a130Content, role: 'FIN' }, 'A130'), null)
assert.equal(parseAdminPageProjection({ ...a130Finance, items: a130Finance.items.map(({ priceCostCandidateLabel: _removed, ...item }) => item) }, 'A130'), null)
assert.equal(parseAdminPageProjection({ ...a130Content, items: [{ ...a130Content.items[0], priceCostCandidateLabel: 'blocked' }] }, 'A130'), null)
assert.equal(parseAdminPageProjection(a140Finance, 'A130'), null)
assert.equal(parseAdminPageProjection({ ...a140Finance, items: [{ ...a140Finance.items[0], maskedPhone: 'blocked' }] }, 'A140'), null)

const p021Timeline = [{ timelineItemRef: 'TL-1', sequence: 1, projectionVersion: 1, stateCode: 'TOPUP_PROCESSING', occurredAt: null, userMessageCode: 'TOPUP_PROCESSING' }]
const p021Cs = { outcome: 'ACCEPTED', projectCode: 'ADMIN_ORDER_DETAIL_READ', currentProjection: {
  orderRef: 'ORDER-1', stateCode: 'TOPUP_PROCESSING', maskedTarget: '******1234', confirmedItems: ['PAYMENT_CONFIRMATION'], unknownItems: ['TOPUP_RESULT'], responsibilityCode: 'SYSTEM_RECHECK', updatedAt: '2026-08-03T01:00:00Z', nextReviewPoint: null, timeline: p021Timeline,
} }
const p021Fin = { outcome: 'ACCEPTED', projectCode: 'ADMIN_ORDER_DETAIL_READ', currentProjection: {
  orderRef: 'ORDER-1', stateCode: 'TOPUP_PROCESSING', totalMinor: 125000, currency: 'BDT', targetValueDisplay: '1000 BDT', targetCurrency: 'BDT', confirmedItems: ['PAYMENT_CONFIRMATION'], unknownItems: ['TOPUP_RESULT'], responsibilityCode: 'SYSTEM_RECHECK', updatedAt: '2026-08-03T01:00:00Z', nextReviewPoint: null, timeline: p021Timeline,
} }
assert.equal(parseP021AdminResponse(p021Cs, 'CS').status, 'READY')
assert.equal(parseP021AdminResponse(p021Fin, 'FIN').status, 'READY')
assert.equal(parseP021AdminResponse(p021Cs, 'FIN').status, 'UNAVAILABLE')
assert.equal(parseP021AdminResponse(p021Fin, 'CS').status, 'UNAVAILABLE')
assert.equal(parseP021AdminResponse({ ...p021Cs, currentProjection: { ...p021Cs.currentProjection, extra: 'blocked' } }, 'CS').status, 'UNAVAILABLE')
assert.equal(parseP021AdminResponse({ outcome: 'REJECTED', projectCode: 'ADMIN_ORDER_DETAIL_NOT_AVAILABLE', currentProjection: null }, 'CS').status, 'ACCESS_DENIED')
const p021Denied = { outcome: 'REJECTED', projectCode: 'ADMIN_ORDER_DETAIL_NOT_AVAILABLE', currentProjection: null }
assert.equal((await loadP021AdminOrderDetail('ORDER-1', 'CONTENT', async () => new Response(JSON.stringify(p021Denied), { status: 200 }))).status, 'ACCESS_DENIED')
assert.equal((await loadP021AdminOrderDetail('ORDER-1', 'CONTENT', async () => new Response(JSON.stringify(p021Cs), { status: 200 }))).status, 'UNAVAILABLE')
let capturedP021Request
const p021Proxy = await loadP021AdminOrderDetail('ORDER-1', 'FIN', async (input, init) => {
  capturedP021Request = { input: String(input), init }
  return new Response(JSON.stringify(p021Fin), { status: 200, headers: { 'Content-Type': 'application/json' } })
})
assert.equal(p021Proxy.status, 'READY')
assert.equal(capturedP021Request.input, '/admin-read/v1/orders/ORDER-1')
assert.equal(capturedP021Request.init.method, 'GET')
assert.equal(capturedP021Request.init.credentials, 'include')
assert.deepEqual(capturedP021Request.init.headers, { Accept: 'application/json' })
assert.equal('body' in capturedP021Request.init, false)

const a110FinReadyRaw = getSyntheticA110Response('FIN', 'READY')
const a110CsReadyRaw = getSyntheticA110Response('CS', 'READY')
const a110ContentDeniedRaw = getSyntheticA110Response('CONTENT', 'ACCESS_DENIED')
assert.equal(Object.keys(a110FinReadyRaw).length, 11, 'A110 transport envelope must contain exactly eleven fields')
assert.equal(parseA110ReadResponse(a110FinReadyRaw, 'FIN').status, 'READY')
assert.equal(parseA110ReadResponse(a110CsReadyRaw, 'CS').status, 'READY')
assert.equal(parseA110ReadResponse(a110ContentDeniedRaw, 'CONTENT').status, 'ACCESS_DENIED')
assert.equal(typeof a110FinReadyRaw.projectionVersion, 'number')
assert.equal(a110FinReadyRaw.items[0].projectionVersion, a110FinReadyRaw.projectionVersion)
assert.equal(parseA110ReadResponse({ ...a110FinReadyRaw, extra: 'blocked' }, 'FIN').status, 'UNAVAILABLE')
const missingA110Root = { ...a110FinReadyRaw }; delete missingA110Root.requestRef
assert.equal(parseA110ReadResponse(missingA110Root, 'FIN').status, 'UNAVAILABLE')
assert.equal(parseA110ReadResponse({ ...a110FinReadyRaw, roleProjection: 'CS' }, 'FIN').status, 'UNAVAILABLE')
assert.equal(parseA110ReadResponse({ ...a110FinReadyRaw, projectionVersion: '1' }, 'FIN').status, 'UNAVAILABLE')
assert.equal(parseA110ReadResponse({ ...a110FinReadyRaw, items: [{ ...a110FinReadyRaw.items[0], projectionVersion: 2 }] }, 'FIN').status, 'UNAVAILABLE')
assert.equal(parseA110ReadResponse({ ...a110FinReadyRaw, items: [{ ...a110FinReadyRaw.items[0], supportRef: null, nextReviewPoint: null }] }, 'FIN').status, 'READY')
assert.equal(parseA110ReadResponse({ ...a110CsReadyRaw, items: [{ ...a110CsReadyRaw.items[0], nextReviewPoint: null }] }, 'CS').status, 'READY')
assert.equal(parseA110ReadResponse({ ...a110FinReadyRaw, items: [{ ...a110FinReadyRaw.items[0], maskedSubjectSummary: 'blocked' }] }, 'FIN').status, 'UNAVAILABLE')
assert.equal(parseA110ReadResponse({ ...a110CsReadyRaw, items: [{ ...a110CsReadyRaw.items[0], factSummaries: {} }] }, 'CS').status, 'UNAVAILABLE')
assert.equal(parseA110ReadResponse({ ...a110CsReadyRaw, items: [{ ...a110CsReadyRaw.items[0], confirmedItems: ['UNKNOWN_INTERNAL_CODE'] }] }, 'CS').status, 'UNAVAILABLE')
assert.equal(parseA110ReadResponse({ ...a110FinReadyRaw, viewState: 'UNKNOWN_NEW_STATE', projectCode: 'A110_UNKNOWN_NEW_STATE' }, 'FIN').status, 'UNAVAILABLE')
assert.equal(parseA110ReadResponse({ ...a110FinReadyRaw, allowedActions: [...a110FinReadyRaw.allowedActions, 'WRITE_REVIEW'] }, 'FIN').status, 'UNAVAILABLE')
const readyWithConflict = getSyntheticA110Response('FIN', 'ASYMMETRIC_FACTS')
assert.equal(parseA110ReadResponse({ ...readyWithConflict, viewState: 'READY', projectCode: 'A110_READY' }, 'FIN').status, 'UNAVAILABLE')
const readError = getSyntheticA110Response('FIN', 'READ_ERROR')
assert.equal(readError.roleProjection, null)
assert.equal(readError.projectionVersion, null)
assert.equal(parseA110ReadResponse(readError, 'FIN').status, 'UNAVAILABLE')
const authorityUnknown = parseA110ReadResponse(getSyntheticA110Response('FIN', 'AUTHORITY_UNKNOWN'), 'FIN')
const revoked = parseA110ReadResponse(getSyntheticA110Response('FIN', 'REVOKED'), 'FIN')
assert.equal(authorityUnknown.denialKind, 'AUTHORITY_UNAVAILABLE')
assert.equal(revoked.denialKind, 'AUTHORITY_UNAVAILABLE')
assert.equal(authorityUnknown.message, '当前无法安全显示，请重新进入')
assert.equal(revoked.message, '当前无法安全显示，请重新进入')
for (const state of ['EMPTY', 'READ_ERROR', 'UNAVAILABLE', 'ACCESS_DENIED', 'AUTHORITY_UNKNOWN', 'REVOKED', 'VERSION_CONFLICT', 'LONG_RUNNING_UNKNOWN', 'ASYMMETRIC_FACTS', 'REFUND_DELIVERY_CONFLICT']) {
  const role = state === 'ACCESS_DENIED' ? 'CONTENT' : 'FIN'
  const parsed = parseA110ReadResponse(getSyntheticA110Response(role, state), role)
  assert.equal(parsed.data === null, ['READ_ERROR', 'UNAVAILABLE', 'ACCESS_DENIED', 'AUTHORITY_UNKNOWN', 'REVOKED', 'VERSION_CONFLICT'].includes(state), `A110 ${state} data withdrawal mismatch`)
}

const builtinReady = await loadAdminPage('BUILTIN_SYNTHETIC', 'A130', 'FIN')
const builtinDenied = await loadAdminPage('BUILTIN_SYNTHETIC', 'A130', 'CS')
const builtinA110Fin = await loadAdminPage('BUILTIN_SYNTHETIC', 'A110', 'FIN')
const builtinA110Content = await loadAdminPage('BUILTIN_SYNTHETIC', 'A110', 'CONTENT')
assert.equal(builtinReady.status, 'READY')
assert.equal(builtinDenied.status, 'ACCESS_DENIED')
assert.equal(builtinDenied.data, null)
assert.equal(builtinA110Fin.status, 'READY')
assert.equal(builtinA110Content.status, 'ACCESS_DENIED')
assert.equal(builtinA110Content.data, null)

let capturedRequest
const proxyReady = await loadAdminPage('PROJECT_API_PROXY', 'A140', 'CS', async (input, init) => {
  capturedRequest = { input: String(input), init }
  return new Response(JSON.stringify({
    schemaVersion: 'ADMIN_READ_V1', projectionVersion: 'PV-2', pageId: 'A140', role: 'CS',
    items: [{ orderRef: 'ORDER-2', maskedPhone: '01•• •••• 00', userStatusLabel: '待确认', totalLabel: 'CNY —', updatedLabel: '待确认' }],
  }), { status: 200, headers: { 'Content-Type': 'application/json' } })
})
assert.equal(proxyReady.status, 'READY')
assert.equal(capturedRequest.input, '/admin-read/v1/pages/A140')
assert.equal(capturedRequest.init.credentials, 'include')
assert.deepEqual(capturedRequest.init.headers, { Accept: 'application/json' })

let capturedA110Request
const proxyA110Ready = await loadAdminPage('PROJECT_API_PROXY', 'A110', 'FIN', async (input, init) => {
  capturedA110Request = { input: String(input), init }
  return new Response(JSON.stringify(a110FinReadyRaw), { status: 200, headers: { 'Content-Type': 'application/json' } })
})
assert.equal(proxyA110Ready.status, 'READY')
assert.equal(capturedA110Request.input, A110_READ_PROXY_PATH)
assert.equal(capturedA110Request.init.method, 'GET')
assert.equal('body' in capturedA110Request.init, false)
assert.deepEqual(capturedA110Request.init.headers, { Accept: 'application/json' })

const proxyCases = [
  await loadAdminPage('PROJECT_API_PROXY', 'A100', 'CS', async () => new Response('', { status: 404 })),
  await loadAdminPage('PROJECT_API_PROXY', 'A100', 'CS', async () => new Response('', { status: 500 })),
  await loadAdminPage('PROJECT_API_PROXY', 'A100', 'CS', async () => new Response(JSON.stringify({ invalid: true }), { status: 200 })),
  await loadAdminPage('PROJECT_API_PROXY', 'A100', 'CS', async () => { throw new Error('network interrupted') }),
]
for (const state of proxyCases) {
  assert.equal(state.status, 'UNAVAILABLE')
  assert.equal(state.data, null)
}

const proxyUnauthenticated = await loadAdminPage('PROJECT_API_PROXY', 'A100', 'CS', async () => new Response('', { status: 401 }))
const proxyForbidden = await loadAdminPage('PROJECT_API_PROXY', 'A100', 'CS', async () => new Response('', { status: 403 }))
assert.equal(proxyUnauthenticated.status, 'UNAUTHENTICATED')
assert.equal(proxyUnauthenticated.data, null)
assert.equal(proxyForbidden.status, 'ACCESS_DENIED')
assert.equal(proxyForbidden.data, null)

console.log('admin frontend contract assertions: PASS')
