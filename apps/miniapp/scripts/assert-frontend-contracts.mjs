import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { spawnSync } from 'node:child_process'
import { resolve } from 'node:path'

const root = process.cwd()
const read = (path) => readFileSync(resolve(root, path), 'utf8')

const formal = spawnSync(process.execPath, ['scripts/assert-development-api-contracts.mjs'], {
  cwd: root,
  encoding: 'utf8',
})
if (formal.stdout) process.stdout.write(formal.stdout)
if (formal.stderr) process.stderr.write(formal.stderr)
assert.equal(formal.status, 0, 'formal development API contracts must pass')

const client = read('src/api/client.ts')
assert.doesNotMatch(client, /new URLSearchParams\(/, 'miniapp API client must not depend on browser-only URLSearchParams')
assert.match(client, /cityCode=\$\{encodeURIComponent\(cityCode\)\}/, 'directory city query must be encoded without browser-only APIs')
const payment = read('src/pages/payment/status.vue')
const progress = read('src/pages/order/progress.vue')
const directory = read('src/pages/directory/list.vue')
const directoryDetail = read('src/pages/directory/detail.vue')
assert.match(directory, /LIFE_SERVICE:'生活服务'/, 'directory list category LIFE_SERVICE must have Chinese copy')
assert.match(directory, /MEDICAL:'医疗服务'/, 'directory list category MEDICAL must have Chinese copy')
assert.match(directoryDetail, /LIFE_SERVICE:'生活服务'/, 'directory detail category LIFE_SERVICE must have Chinese copy')
assert.match(directoryDetail, /MEDICAL:'医疗服务'/, 'directory detail category MEDICAL must have Chinese copy')
assert.match(directory, /directoryCategoryLabel\(item\.category\)/, 'directory list must not expose category codes')
assert.match(directoryDetail, /directoryCategoryLabel\(item\.category\)/, 'directory detail must not expose category codes')
const quote = read('src/pages/recharge/quote.vue')
const products = read('src/pages/recharge/products.vue')

assert.match(payment, /api\.getCoreProjection\(orderRef\.value\)/, 'P013 must read the formal order projection')
assert.match(client, /getCoreProjection[\s\S]*parseReleaseOrderView/, 'formal projections must pass the frozen release parser')
assert.match(payment, /当前暂不可支付[\s\S]*未扣款[\s\S]*没有向运营商提交充值/, 'P013 must fail closed')
assert.match(payment, /paymentReadState=ref<'IDLE'\|'LOADING'\|'READY'\|'READ_ERROR'>[\s\S]*payment\.value=null[\s\S]*paymentReadState\.value='READ_ERROR'/, 'P013 must revoke stale payment state when status read fails')
assert.match(payment, /支付状态暂时无法读取，已撤销旧结果/, 'P013 must expose an explicit payment read failure')
assert.match(payment, /data-payment-read-state="paymentReadState"/, 'P013 must project READ_ERROR for deterministic UI verification')
assert.match(payment, /paymentReadState==='READ_ERROR'[\s\S]*旧支付结果已撤销，不会重新发起支付[\s\S]*v-else-if="unpaid&&!paymentAvailable"/, 'P013 must not present a normal order hero after payment read failure')
assert.doesNotMatch(payment, /REQUEST_MOCK_PAYMENT|LOCAL_SYNTHETIC|confirmPayment/, 'P013 must not expose retired payment actions')

assert.match(progress, /api\.getPaymentStatus\(orderRef\.value\)[\s\S]*api\.getTopupStatus\(orderRef\.value\)/, 'P014 must bind topup eligibility to the formal payment and topup status endpoints')
assert.match(progress, /paymentPaid[\s\S]*canSubmit/, 'P014 submit must remain bound to a trusted paid terminal state')
assert.doesNotMatch(progress, /REQUEST_MOCK_TOPUP|CREATE_LOCAL_SYNTHETIC_TOPUP/, 'P014 must not expose retired topup actions')
assert.match(quote, /entitlement\.benefitText/, 'P012 delivery content must come from the catalog entitlement snapshot')
assert.doesNotMatch(quote, /到账内容[\s\S]*totalAmountMinor/, 'P012 must not reuse the payment amount as delivery content')
assert.match(products, /Promise\.all\(\[api\.getSupportedOperators\(\),api\.getCatalog\(stored\.operatorCode\)\]\)/, 'P011 must read the supported operator set and current catalog together')
assert.match(products, /item\.displayName[\s\S]*item\.benefitText[\s\S]*item\.validityText[\s\S]*item\.finalAmountCny/, 'P011 must render only catalog-backed benefit and price fields')
assert.doesNotMatch(products, /优惠|到账时长|Grameenphone|Robi|Banglalink/, 'P011 must not invent offer, timing, or operator facts')
assert.match(quote, /title="订单确认"[\s\S]*>订单确认<\/view>/, 'P012 title must be consistent')
assert.match(quote, /\{\{loading\?'正在确认…':'确认订单'\}\}/, 'P012 primary action must use the frozen copy')
assert.match(quote, /Date\.parse\(quote\.value\.validUntil\)>now\.value/, 'P012 expiry must reactively close the order action')

assert.match(directory, /api\.getDirectory\(cityCode\.value\|\|undefined\)/, 'P040 must use server-scoped city data')
assert.match(directory, /entryRef=\$\{encodeURIComponent\(item\.entryRef\)\}/, 'P040 must carry the opaque entry reference')
assert.match(directoryDetail, /loading\.value=true[\s\S]*item\.value=null[\s\S]*api\.getDirectoryDetail\(entryRef\.value\)/, 'P041 must revoke old detail before reread')
assert.match(directoryDetail, /INCORRECT_INFO[\s\S]*PHONE_INVALID[\s\S]*ADDRESS_INVALID[\s\S]*CLOSED[\s\S]*OTHER/, 'P041 must use the fixed feedback reason set')
assert.match(directoryDetail, /reportNote[\s\S]*maxlength="500"/, 'P041 must provide the bounded optional report note')
assert.match(client, /source\.dayType==='REST_DAY'/, 'P001 must map the published weekend rule to rest-day copy')
assert.match(client, /source\.dayType==='HOLIDAY'/, 'P001 must map the published holiday rule to holiday copy')
assert.match(client, /state:'READ_ERROR'[\s\S]*sourceType:null/, 'P001 must fail closed when holiday rule metadata is incomplete')

console.log('legacy frontend contract compatibility gate: PASS')
