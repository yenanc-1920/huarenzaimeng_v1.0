import { existsSync, readdirSync, rmSync } from 'node:fs'
import { relative, resolve } from 'node:path'

const projectRoot = resolve(process.cwd())
const outputRoot = resolve(projectRoot, 'dist/build/mp-weixin')
const outputRelative = relative(projectRoot, outputRoot).replaceAll('\\', '/')

if (outputRelative !== 'dist/build/mp-weixin') throw new Error('UNSAFE_MP_WEIXIN_OUTPUT_TARGET')

let removed = 0
if (existsSync(outputRoot)) {
  for (const name of readdirSync(outputRoot)) {
    rmSync(resolve(outputRoot, name), { recursive: true, force: true })
    removed += 1
  }
}

console.log(`mp-weixin clean output entries removed: ${removed}`)
