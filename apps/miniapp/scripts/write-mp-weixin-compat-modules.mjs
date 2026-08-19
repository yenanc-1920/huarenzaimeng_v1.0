import { existsSync, mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { relative, resolve } from 'node:path'

const projectRoot = resolve(process.cwd())
const outputRoot = resolve(projectRoot, 'dist/build/mp-weixin')
const outputRelative = relative(projectRoot, outputRoot).replaceAll('\\', '/')

if (outputRelative !== 'dist/build/mp-weixin') throw new Error('UNSAFE_MP_WEIXIN_OUTPUT_TARGET')

// Contract fixtures are allowed in source tests but never in the runtime package.
for (const artifact of ['api/mock.js', 'api/p014-topup-synthetic.js', 'api/order-detail-synthetic.js']) {
  const target = resolve(outputRoot, artifact)
  if (existsSync(target)) rmSync(target)
}

// WeChat DevTools can retain the previous precompile dependency graph after a
// clean rebuild. Keep an inert file at the retired module path so the stale
// graph cannot abort compilation with ENOENT. Current pages must not require it.
const legacyDomainDir = resolve(outputRoot, 'domain')
mkdirSync(legacyDomainDir, { recursive: true })
writeFileSync(
  resolve(legacyDomainDir, 'temporal-overview-flow.js'),
  '"use strict";Object.defineProperty(exports,"__esModule",{value:true});/* WECHAT_PRECOMPILE_COMPAT_ONLY */\n',
  'utf8',
)

const legacyApiDir = resolve(outputRoot, 'api')
mkdirSync(legacyApiDir, { recursive: true })
writeFileSync(
  resolve(legacyApiDir, 'wechat-payment-port.js'),
  '"use strict";Object.defineProperty(exports,"__esModule",{value:true});/* WECHAT_PAYMENT_PRECOMPILE_COMPAT_ONLY */\n',
  'utf8',
)

console.log('mp-weixin compatibility modules written: domain/temporal-overview-flow.js, api/wechat-payment-port.js')
