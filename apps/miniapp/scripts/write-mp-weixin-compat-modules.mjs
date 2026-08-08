import { mkdirSync, writeFileSync } from 'node:fs'
import { relative, resolve } from 'node:path'

const projectRoot = resolve(process.cwd())
const outputRoot = resolve(projectRoot, 'dist/build/mp-weixin')
const outputRelative = relative(projectRoot, outputRoot).replaceAll('\\', '/')

if (outputRelative !== 'dist/build/mp-weixin') throw new Error('UNSAFE_MP_WEIXIN_OUTPUT_TARGET')

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

console.log('mp-weixin compatibility module written: domain/temporal-overview-flow.js')
