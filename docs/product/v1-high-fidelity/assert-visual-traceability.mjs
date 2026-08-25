import { readFileSync, existsSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const root = resolve(here, '../../..')
const matrixPath = resolve(here, '视觉验收截图矩阵.md')
const matrix = readFileSync(matrixPath, 'utf8')
const pages = JSON.parse(readFileSync(resolve(root, 'apps/miniapp/src/pages.v1.json'), 'utf8')).pages.map((page) => page.path)
const adminApp = readFileSync(resolve(root, 'apps/admin-web/src/App.vue'), 'utf8')

const mini = [
  ['P001', 'UX-P001', 'pages/index/index', 'P001-首页.svg', 'apps/miniapp/src/pages/index/index.vue'],
  ['P010', 'UX-P010', 'pages/recharge/select', 'P010-号码与运营商.svg', 'apps/miniapp/src/pages/recharge/select.vue'],
  ['P011', 'UX-P011', 'pages/recharge/products', 'P011-商品选择.svg', 'apps/miniapp/src/pages/recharge/products.vue'],
  ['P012', 'UX-P012', 'pages/recharge/quote', 'P012-订单确认.svg', 'apps/miniapp/src/pages/recharge/quote.vue'],
  ['P013', 'UX-P013', 'pages/payment/status', 'P013-支付状态.svg', 'apps/miniapp/src/pages/payment/status.vue'],
  ['P014', 'UX-P014', 'pages/order/progress', 'P014-充值进度.svg', 'apps/miniapp/src/pages/order/progress.vue'],
  ['P015', 'UX-P015', 'pages/refund/status', 'P015-异常与退款.svg', 'apps/miniapp/src/pages/refund/status.vue'],
  ['P020', 'UX-P020', 'pages/order/list', 'P020-我的订单.svg', 'apps/miniapp/src/pages/order/list.vue'],
  ['P021', 'UX-P021', 'pages/order/detail', 'P021-订单详情.svg', 'apps/miniapp/src/pages/order/detail.vue'],
  ['P022', 'UX-P022', 'pages/auth/expired', 'P022-微信登录.svg', 'apps/miniapp/src/pages/auth/expired.vue'],
  ['P023', 'UX-P023', 'pages/order/recovery', 'P023-找回历史订单.svg', 'apps/miniapp/src/pages/order/recovery.vue'],
  ['P024', 'UX-P024', 'pages/profile/index', 'P024-我的.svg', 'apps/miniapp/src/pages/profile/index.vue'],
  ['P040', 'UX-P040', 'pages/directory/list', 'P040-黄页.svg', 'apps/miniapp/src/pages/directory/list.vue'],
  ['P041', 'UX-P041', 'pages/directory/detail', 'P041-黄页详情.svg', 'apps/miniapp/src/pages/directory/detail.vue'],
  ['P042', 'UX-P042', 'pages/life-content/list', 'P042-生活资讯.svg', 'apps/miniapp/src/pages/life-content/list.vue'],
  ['P043', 'UX-P043', 'pages/life-content/detail', 'P043-资讯详情.svg', 'apps/miniapp/src/pages/life-content/detail.vue'],
]

const admin = [
  ['B001', '/admin/login', 'ROOT', 'B001-后台登录.svg', 'apps/admin-web/src/components/LoginRequired.vue'],
  ['B002', '/#/B002', 'ROOT', 'B002-运营工作台.svg', 'apps/admin-web/src/components/workspaces/B002Dashboard.vue'],
  ['A100', '/#/A100', 'LIST', 'A100-客服案件列表.svg', 'apps/admin-web/src/components/workspaces/A100Workspace.vue'],
  ['A100-EDIT', '/#/A100', 'CREATE_OR_FOLLOW_UP', 'A100-EDIT-案件新建跟进.svg', 'apps/admin-web/src/components/workspaces/A100Workspace.vue'],
  ['A110', '/#/A110', 'LIST', 'A110-交易差异列表.svg', 'apps/admin-web/src/components/workspaces/A110Workspace.vue'],
  ['A110-DETAIL', '/#/A110', 'DETAIL', 'A110-DETAIL-交易差异处置.svg', 'apps/admin-web/src/components/workspaces/A110Workspace.vue'],
  ['A120', '/#/A120', 'ROOT', 'A120-内容审核总队列.svg', 'apps/admin-web/src/components/workspaces/A120Workspace.vue'],
  ['A121', '/#/A121', 'DIRECTORY_LIST', 'A121-黄页管理.svg', 'apps/admin-web/src/components/workspaces/A121Workspace.vue'],
  ['A121-CITY', '/#/A121', 'CITY_LIST', 'A121-CITY-城市配置.svg', 'apps/admin-web/src/components/workspaces/A121Workspace.vue'],
  ['A121-EDIT', '/#/A121', 'CREATE_OR_EDIT', 'A121-EDIT-机构新增编辑.svg', 'apps/admin-web/src/components/workspaces/A121Workspace.vue'],
  ['A122', '/#/A122', 'HOLIDAY_LIST', 'A122-节假日资讯管理.svg', 'apps/admin-web/src/components/workspaces/A122Workspace.vue'],
  ['A122-HOLIDAY', '/#/A122', 'HOLIDAY_EDIT', 'A122-HOLIDAY-周休节假日编辑.svg', 'apps/admin-web/src/components/workspaces/A122Workspace.vue'],
  ['A122-NEWS', '/#/A122', 'NEWS_EDIT', 'A122-NEWS-资讯新增编辑.svg', 'apps/admin-web/src/components/workspaces/A122Workspace.vue'],
  ['A130', '/#/A130', 'PRODUCT_LIST', 'A130-商品列表.svg', 'apps/admin-web/src/components/workspaces/A130Workspace.vue'],
  ['A130-CATALOG', '/#/A130', 'CATALOG', 'A130-CATALOG-供应商目录.svg', 'apps/admin-web/src/components/workspaces/A130Workspace.vue'],
  ['A130-EDIT', '/#/A130', 'PRODUCT_EDIT', 'A130-EDIT-商品新增编辑.svg', 'apps/admin-web/src/components/workspaces/A130Workspace.vue'],
  ['A130-CHANNEL', '/#/A130', 'CHANNEL', 'A130-CHANNEL-渠道映射.svg', 'apps/admin-web/src/components/workspaces/A130Workspace.vue'],
  ['A130-PRICE', '/#/A130', 'PRICE', 'A130-PRICE-定价配置.svg', 'apps/admin-web/src/components/workspaces/A130Workspace.vue'],
  ['A130-PRICE-TEST', '/#/A130', 'TRIAL', 'A130-PRICE-TEST-试算版本.svg', 'apps/admin-web/src/components/workspaces/A130Workspace.vue'],
  ['A140', '/#/A140', 'LIST', 'A140-订单运营列表.svg', 'apps/admin-web/src/components/workspaces/A140Workspace.vue'],
  ['A140-DETAIL', '/#/A140', 'DETAIL', 'A140-DETAIL-订单退款只读详情.svg', 'apps/admin-web/src/components/AdminOrderDetail.vue'],
]

function requireCondition(condition, message) {
  if (!condition) throw new Error(message)
}

function requireUnique(values, label) {
  requireCondition(new Set(values).size === values.length, `${label} must be unique`)
}

requireCondition(mini.length === 16, 'mini business page count must be 16')
requireCondition(admin.length === 21, 'admin frame count must be 21')
requireUnique(mini.map(([id]) => id), 'mini ids')
requireUnique(mini.map(([, pageId]) => pageId), 'mini pageIds')
requireUnique(mini.map(([, , route]) => route), 'mini routes')
requireUnique(mini.map(([, , , baseline]) => baseline), 'mini baselines')
requireUnique(admin.map(([frame]) => frame), 'admin frameIds')
requireUnique(admin.map(([, , , baseline]) => baseline), 'admin baselines')

const businessRoutes = new Set(mini.map(([, , route]) => route))
const auxiliaryRoutes = pages.filter((route) => !businessRoutes.has(route))
requireCondition(pages.length === 18, `pages.v1.json must contain 18 archived routes, got ${pages.length}`)
requireCondition(auxiliaryRoutes.length === 2 && auxiliaryRoutes.includes('pages/legal/user-agreement') && auxiliaryRoutes.includes('pages/legal/privacy-policy'), 'only the two legal routes may sit outside the 16-page business matrix')

for (const [id, pageId, route, baseline, source] of mini) {
  requireCondition(pages.filter((value) => value === route).length === 1, `${id} route missing or duplicated: ${route}`)
  const sourceText = readFileSync(resolve(root, source), 'utf8')
  requireCondition(sourceText.includes(`data-page-id="${pageId}"`), `${id} source missing ${pageId}`)
  requireCondition(existsSync(resolve(here, baseline)), `${id} baseline missing: ${baseline}`)
  const marker = `<!-- VISUAL_TRACE_MINI|${id}|${pageId}|${route}|${baseline}|NOT_EVIDENCED|NO_REAL_DEVICE_EVIDENCE -->`
  requireCondition(matrix.includes(marker), `${id} trace marker missing or evidence state changed`)
}

for (const [frame, route, entry, baseline, source] of admin) {
  const sourceText = readFileSync(resolve(root, source), 'utf8')
  requireCondition(sourceText.includes('data-visual-frame-id'), `${frame} source has no visual frame tracking attribute`)
  requireCondition(sourceText.includes(frame), `${frame} source does not expose its frame id`)
  requireCondition(existsSync(resolve(here, baseline)), `${frame} baseline missing: ${baseline}`)
  const marker = `<!-- VISUAL_TRACE_ADMIN|${frame}|${route}|${entry}|${baseline}|${source}|NOT_EVIDENCED -->`
  requireCondition(matrix.includes(marker), `${frame} trace marker missing or evidence state changed`)
  if (route === '/admin/login') requireCondition(adminApp.includes('/admin/login'), 'admin login route missing')
  else if (['/A120', '/A121', '/A122'].includes(route)) requireCondition(adminApp.includes(`'${route.slice(3)}'`), `${frame} V2 top-level route missing in App.vue`)
}

console.log('visual traceability contracts: PASS (mini=16; admin=21; status=NOT_EVIDENCED; device=NO_REAL_DEVICE_EVIDENCE)')
