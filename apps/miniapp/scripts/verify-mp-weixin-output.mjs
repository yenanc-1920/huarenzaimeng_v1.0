import { existsSync, readFileSync, readdirSync } from 'node:fs'
import { dirname, relative, resolve } from 'node:path'
import { createRequire } from 'node:module'

const projectRoot = resolve(process.cwd())
const outputRoot = resolve(projectRoot, 'dist/build/mp-weixin')
const requiredArtifacts = [
  'app.js',
  'app.json',
  'api/client.js',
  'api/mock.js',
  'api/payment-intent-contract.js',
  'pages/recharge/select.js',
  'pages/recharge/select.wxml',
  'pages/order/list.wxml',
  'pages/directory/list.wxml',
  'pages/life-content/list.wxml',
]

for (const artifact of requiredArtifacts) {
  if (!existsSync(resolve(outputRoot, artifact))) throw new Error(`MP_WEIXIN_ARTIFACT_MISSING:${artifact}`)
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

let staticRequireCount = 0
for (const file of collectJavaScript(outputRoot)) {
  const source = readFileSync(file, 'utf8')
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
  ['pages/recharge/select.wxml', 'data-page-root="recharge-select"'],
  ['pages/order/list.wxml', 'data-page-root="order-list"'],
  ['pages/directory/list.wxml', 'data-page-root="directory-list"'],
  ['pages/life-content/list.wxml', 'data-page-root="life-content-list"'],
]) {
  if (!readFileSync(resolve(outputRoot, page), 'utf8').includes(root)) {
    throw new Error(`MP_WEIXIN_VISIBLE_ROOT_MISSING:${page}`)
  }
}

const commonClient = readFileSync(resolve(outputRoot, 'api/client.js'), 'utf8')
const commonMock = readFileSync(resolve(outputRoot, 'api/mock.js'), 'utf8')
if (/require\(["']\.\/payment-intent-contract\.js["']\)/.test(commonClient)
  || /require\(["']\.\/payment-intent-contract\.js["']\)/.test(commonMock)) {
  throw new Error('MP_WEIXIN_COMMON_ENTRY_PAYMENT_INTENT_DEPENDENCY_NOT_ISOLATED')
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
try {
  createRequire(import.meta.url)(resolve(outputRoot, 'pages/recharge/select.js'))
} catch (error) {
  throw new Error(`MP_WEIXIN_RECHARGE_APPSERVICE_LOAD_FAILED:${error instanceof Error ? error.message : String(error)}`)
}

console.log(`mp-weixin appservice load: PASS (recharge entry; ${staticRequireCount} relative static requires)`)
