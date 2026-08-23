import { existsSync, readFileSync, readdirSync } from 'node:fs'
import { dirname, relative, resolve } from 'node:path'
import { createRequire } from 'node:module'

const projectRoot = resolve(process.cwd())
const outputRoot = resolve(projectRoot, 'dist/build/mp-weixin')
const generatedProjectConfig = JSON.parse(readFileSync(resolve(outputRoot, 'project.config.json'), 'utf8'))
const generatedAppConfig = JSON.parse(readFileSync(resolve(outputRoot, 'app.json'), 'utf8'))
if (generatedProjectConfig.setting?.minified !== true) throw new Error('MP_WEIXIN_MINIFICATION_NOT_ENABLED')
if (generatedAppConfig.lazyCodeLoading !== 'requiredComponents') throw new Error('MP_WEIXIN_COMPONENT_LAZY_LOADING_NOT_ENABLED')
const requiredArtifacts = [
  'app.js',
  'app.json',
  'api/client.js',
  'api/anonymous-session-contract.js',
  'api/formal-transaction-client.js',
  'api/formal-transaction-contract.js',
  'api/wechat-payment-port.js',
  'api/p014-topup-contract.js',
  'pages/index/index.js',
  'pages/index/index.wxml',
  'pages/recharge/select.js',
  'pages/recharge/select.wxml',
  'pages/order/list.js',
  'pages/order/list.wxml',
  'pages/order/detail.js',
  'pages/order/detail.wxml',
  'pages/directory/list.js',
  'pages/directory/list.wxml',
  'pages/life-content/list.js',
  'pages/life-content/list.wxml',
  'pages/order/progress.js',
  'pages/order/progress.wxml',
  'pages/payment/status.js',
  'pages/payment/status.wxml',
  'pages/refund/status.js',
  'pages/refund/status.wxml',
  'pages/auth/expired.js',
  'pages/profile/index.wxml',
]

for (const artifact of requiredArtifacts) {
  if (!existsSync(resolve(outputRoot, artifact))) throw new Error(`MP_WEIXIN_ARTIFACT_MISSING:${artifact}`)
}

for (const artifact of ['api/mock.js', 'api/payment-intent-client.js', 'api/payment-intent-contract.js', 'api/p014-topup-synthetic.js', 'api/order-detail-synthetic.js']) {
  if (existsSync(resolve(outputRoot, artifact))) throw new Error(`MP_WEIXIN_DEV_SYNTHETIC_ARTIFACT_PRESENT:${artifact}`)
}

function collectJavaScript(directory) {
  const files = []
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const path = resolve(directory, entry.name)
    if (entry.isDirectory()) files.push(...collectJavaScript(path))
    else if (entry.isFile() && entry.name.endsWith('.js')) files.push(path)
  }
  return files
}

function collectFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = resolve(directory, entry.name)
    return entry.isDirectory() ? collectFiles(path) : [path]
  })
}

const outputFiles = collectFiles(outputRoot)
const packageBytes = outputFiles.reduce((total, file) => total + readFileSync(file).byteLength, 0)
if (packageBytes > 1.5 * 1024 * 1024) throw new Error(`MP_WEIXIN_MAIN_PACKAGE_TOO_LARGE:${packageBytes}`)
const mediaBytes = outputFiles
  .filter((file) => /\.(?:png|jpe?g|gif|webp|svg|mp3|aac|wav)$/i.test(file))
  .reduce((total, file) => total + readFileSync(file).byteLength, 0)
if (mediaBytes > 200 * 1024) throw new Error(`MP_WEIXIN_STATIC_MEDIA_TOO_LARGE:${mediaBytes}`)

let staticRequireCount = 0
const forbiddenRuntimeSemantics = /LOCAL_MOCK_NO_REAL_OPERATOR_FACTS|legacy-price-v1|REQUEST_MOCK_PAYMENT|CREATE_LOCAL_SYNTHETIC_PAYMENT_INTENT|QUERY_LOCAL_SYNTHETIC_PAYMENT_INTENT|REQUEST_MOCK_TOPUP|CREATE_LOCAL_SYNTHETIC_TOPUP|MOCK_PROJECTION_ONLY_NO_EXTERNAL_FACTS/
for (const file of collectJavaScript(outputRoot)) {
  const source = readFileSync(file, 'utf8')
  if (forbiddenRuntimeSemantics.test(source)) {
    throw new Error(`MP_WEIXIN_SYNTHETIC_SEMANTIC_PRESENT:${relative(outputRoot,file).replaceAll('\\','/')}`)
  }
  for (const forbidden of ['P021-V01','P021-V16','visualScenario','p021-visual-fixtures','P021-VISUAL-SUBJECT','P021-VISUAL-AUTH']) {
    if (source.includes(forbidden)) throw new Error(`MP_WEIXIN_P021_DEV_FIXTURE_LEAK:${forbidden}:${relative(outputRoot,file).replaceAll('\\','/')}`)
  }
  const allRequireCount = (source.match(/\brequire\s*\(/g) || []).length
  const staticRequires = [...source.matchAll(/\brequire\s*\(\s*['"]([^'"]+)['"]\s*\)/g)]
  if (staticRequires.length !== allRequireCount) {
    throw new Error(`MP_WEIXIN_DYNAMIC_REQUIRE_NOT_ALLOWED:${relative(outputRoot, file).replaceAll('\\', '/')}`)
  }
  for (const match of staticRequires) {
    const specifier = match[1]
    if (!specifier.startsWith('.')) continue
    const dependency = resolve(dirname(file), specifier)
    if (!existsSync(dependency)) {
      const owner = relative(outputRoot, file).replaceAll('\\', '/')
      const missing = relative(outputRoot, dependency).replaceAll('\\', '/')
      throw new Error(`MP_WEIXIN_STATIC_MODULE_MISSING:${owner}->${missing}`)
    }
    staticRequireCount += 1
  }
}

for (const [page, root] of [
  ['pages/index/index.wxml', 'data-page-root="home"'],
  ['pages/recharge/select.wxml', 'data-page-root="recharge-select"'],
  ['pages/order/list.wxml', 'data-page-root="order-list"'],
  ['pages/order/detail.wxml', 'data-page-root="order-detail"'],
  ['pages/directory/list.wxml', 'data-page-root="directory-list"'],
  ['pages/life-content/list.wxml', 'data-page-root="life-content-list"'],
  ['pages/order/progress.wxml', 'data-page-root="p014-progress"'],
]) {
  if (!readFileSync(resolve(outputRoot, page), 'utf8').includes(root)) {
    throw new Error(`MP_WEIXIN_VISIBLE_ROOT_MISSING:${page}`)
  }
}

const commonClient = readFileSync(resolve(outputRoot, 'api/client.js'), 'utf8')
const generatedApp = readFileSync(resolve(outputRoot, 'app.js'), 'utf8')
const generatedHome = readFileSync(resolve(outputRoot, 'pages/index/index.js'), 'utf8')
const generatedPayment = readFileSync(resolve(outputRoot, 'pages/payment/status.js'), 'utf8')
const generatedTransactionContract = readFileSync(resolve(outputRoot, 'api/formal-transaction-contract.js'), 'utf8')
const retiredTemporalFlow = readFileSync(resolve(outputRoot, 'domain/temporal-overview-flow.js'), 'utf8')
const retiredPaymentPort = readFileSync(resolve(outputRoot, 'api/wechat-payment-port.js'), 'utf8')
if (/require\(["']\.\/payment-intent-contract\.js["']\)/.test(commonClient)) {
  throw new Error('MP_WEIXIN_COMMON_ENTRY_PAYMENT_INTENT_DEPENDENCY_NOT_ISOLATED')
}
if (!/require\(["']\.\/api\/anonymous-session-contract\.js["']\)/.test(generatedApp)) {
  throw new Error('MP_WEIXIN_ANONYMOUS_SESSION_MODULE_NOT_REGISTERED_BY_APP')
}
if (/require\(["']\.\/temporal-overview-contract\.js["']\)/.test(commonClient)) {
  throw new Error('MP_WEIXIN_COMMON_ENTRY_TEMPORAL_CONTRACT_DEPENDENCY_NOT_ISOLATED')
}
if (/temporal-overview-flow\.js/.test(generatedHome)) {
  throw new Error('MP_WEIXIN_HOME_PAGE_ONLY_TEMPORAL_MODULE_NOT_INLINED')
}
if (!/WECHAT_PRECOMPILE_COMPAT_ONLY/.test(retiredTemporalFlow)) {
  throw new Error('MP_WEIXIN_RETIRED_TEMPORAL_MODULE_COMPATIBILITY_MISSING')
}
if (/wechat-payment-port\.js/.test(generatedPayment) || /wechat-payment-port\.js/.test(generatedTransactionContract)) {
  throw new Error('MP_WEIXIN_PAYMENT_COMPAT_MODULE_MUST_NOT_BE_REQUIRED')
}
if (!/WECHAT_PAYMENT_PRECOMPILE_COMPAT_ONLY/.test(retiredPaymentPort)) {
  throw new Error('MP_WEIXIN_RETIRED_PAYMENT_MODULE_COMPATIBILITY_MISSING')
}

const systemInfo = { statusBarHeight:20, windowWidth:375, pixelRatio:2, platform:'devtools', system:'Windows', language:'zh_CN',
  version:'1', SDKVersion:'3', brand:'devtools', model:'devtools', screenHeight:800, safeArea:{top:20,left:0,right:375,bottom:800} }
globalThis.getApp = () => ({})
globalThis.App = globalThis.Page = globalThis.Component = () => undefined
globalThis.wx = {
  getSystemInfoSync:()=>systemInfo,getAppBaseInfo:()=>systemInfo,getWindowInfo:()=>systemInfo,getDeviceInfo:()=>systemInfo,
  getLaunchOptionsSync:()=>({}),getStorageSync:()=>undefined,setStorage:()=>undefined,
  createApp:()=>undefined,createPage:()=>undefined,createComponent:()=>undefined,
}
for (const entry of [
  'app.js',
  'pages/index/index.js',
  'pages/recharge/select.js',
  'pages/order/list.js',
  'pages/order/detail.js',
  'pages/directory/list.js',
  'pages/life-content/list.js',
  'pages/order/progress.js',
  'pages/payment/status.js',
]) {
  try {
    createRequire(import.meta.url)(resolve(outputRoot, entry))
  } catch (error) {
    throw new Error(`MP_WEIXIN_APPSERVICE_LOAD_FAILED:${entry}:${error instanceof Error ? error.message : String(error)}`)
  }
}

console.log(`mp-weixin appservice load: PASS (home + six entry pages including P013/P014; ${staticRequireCount} relative static requires)`)
