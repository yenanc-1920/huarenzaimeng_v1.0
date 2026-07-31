import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import assert from 'node:assert/strict'
import { samePriceSnapshot } from '../src/domain/price-snapshot.ts'
import { isAllowedProjectionAction, isWhitelistedNavigation } from '../src/domain/actions.ts'
import { freezeOrderSnapshot, validateOrderSnapshot } from '../src/domain/snapshot-flow.ts'
import { parseProjectProjection, parseProjectQuote } from '../src/api/project-contract.ts'

const read = (path) => readFileSync(resolve(process.cwd(), path), 'utf8')
const cache = read('src/domain/projection.ts')
const client = read('src/api/client.ts')
const list = read('src/pages/order/list.vue')
const detail = read('src/pages/order/detail.vue')
const refund = read('src/pages/refund/status.vue')
const progress = read('src/pages/order/progress.vue')
const quote = read('src/pages/recharge/quote.vue')
const payment = read('src/pages/payment/status.vue')
const home = read('src/pages/index/index.vue')
const directory = read('src/pages/directory/list.vue')
const header = read('src/components/AppHeader.vue')
const detailPage = read('src/pages/order/detail.vue')

assert.match(cache, /Map<string, OrderProjection>/, 'projection cache must be partitioned by orderRef')
assert.match(cache, /candidate\.orderRef !== orderRef/, 'candidate orderRef must match requested orderRef')
assert.doesNotMatch(client, /getProjection\(orderRef\s*=/, 'getProjection must not have a fixed default orderRef')
assert.match(client, /acceptNewerProjection\(orderRef,projection\)/, 'cache acceptance must be scoped by orderRef')
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
assert.match(directory, /<button v-for=/, 'P040 clickable items must be buttons')
assert.match(header, /left && hasLeftHandler/, 'left header action requires a listener')
assert.match(header, /right && hasRightHandler/, 'right header action requires a listener')

const baseline={priceSnapshotRef:'ps-1',version:'v1',totalAmount:1280,currency:'CNY',phone:'01xx78',operator:'mock-op',product:'mock-product',validUntil:'2099-01-01T00:00:00.000Z'}
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

assert.match(client, /'X-Project-Subject-Ref':projectSubjectRef/, 'project subject header must be injected')
assert.match(client, /body\.status==='ACCEPTED'&&body\.projectCode==='OK'/, 'success must consume envelope data only')
assert.match(client, /new ProjectApiError\(body\.projectCode\)/, 'errors must expose projectCode')
assert.match(client, /createOrder\(quoteRef:string,commandId:string,idempotencyKey:string\)/, 'order request must carry command identity')
assert.match(client, /expectedProjectionVersion:number,expectedAggregateVersion:number/, 'money mock commands must carry both versions')
assert.doesNotMatch(client, /SemanticActionKey|semanticActionKey/, 'frontend must not generate SemanticActionKey')
assert.match(quote, /orderRef=projection\.orderRef/, 'orderRef must come from create-order response')
assert.match(payment, /core\.value\.projectionVersion,core\.value\.aggregateVersion/, 'payment must use projection dual versions')
assert.match(progress, /core\.value\.projectionVersion,core\.value\.aggregateVersion/, 'topup must use projection dual versions')
const validQuote={quoteRef:'Q-1',maskedPhone:'01xx78',operatorCode:'OP',productCode:'P1',totalAmountMinor:1280,currency:'CNY',expiresAt:'2099-01-01T00:00:00Z'}
assert.deepEqual(parseProjectQuote(validQuote),validQuote,'valid Quote DTO must parse without casting')
assert.throws(()=>parseProjectQuote({...validQuote,totalAmountMinor:'1280'}),/INVALID_QUOTE_DTO/,'invalid Quote DTO must be rejected')
const validProjection={orderRef:'O-1',quoteRef:'Q-1',orderState:'PAYMENT_CONFIRMED',priceSnapshot:validQuote,facts:{semantics:'MOCK_PROJECTION_ONLY_NO_EXTERNAL_FACTS',payment:'CONFIRMED',upstreamDebit:'UNKNOWN',delivery:'UNKNOWN',refund:'UNKNOWN'},projectionVersion:2,aggregateVersion:2,allowedActions:[{actionCode:'WAIT_OR_CONTACT_SUPPORT',expectedProjectionVersion:2,expectedAggregateVersion:null}]}
assert.equal(parseProjectProjection(validProjection).orderRef,'O-1','valid ProjectProjection must parse')
assert.throws(()=>parseProjectProjection({...validProjection,facts:{...validProjection.facts,semantics:'EXTERNAL_FACT'}}),/INVALID_PROJECT_PROJECTION_DTO/,'non-mock semantics must be rejected')

console.log('frontend contract assertions: PASS (DEV-INT + negative cases)')
