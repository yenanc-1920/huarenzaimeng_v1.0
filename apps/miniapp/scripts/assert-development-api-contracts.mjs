import assert from 'node:assert/strict'
import { existsSync, readFileSync, readdirSync } from 'node:fs'
import { resolve } from 'node:path'

const root=process.cwd()
const read=path=>readFileSync(resolve(root,path),'utf8')
const client=read('src/api/client.ts')
const home=read('src/pages/index/index.vue')
const recharge=read('src/pages/recharge/select.vue')
const payment=read('src/pages/payment/status.vue')
const orders=read('src/pages/order/list.vue')
const recovery=read('src/pages/order/recovery.vue')
const directory=read('src/pages/directory/list.vue')
const detail=read('src/pages/directory/detail.vue')

assert.doesNotMatch(client,/\.\/mock|BuiltinSynthetic|p014BuiltinSynthetic|p021BuiltinSynthetic|PROJECT_MOCK_API|BUILTIN_MOCK/)
assert.match(client,/\/eligibility\?phone=/)
assert.match(client,/\/catalog\?operatorCode=/)
assert.match(client,/getSupportedOperators/)
assert.match(client,/\/directory\/cities/)
assert.match(client,/\/directory\/entries/)
assert.match(client,/\/content\/life-items/)
assert.match(client,/\/home\/temporal-overview/)
assert.match(client,/Authorization: `Bearer \$\{requireBuyerBearerToken\(\)\}`/)
assert.doesNotMatch(payment,/paymentIntentApi|LocalSynthetic|createPaymentIntent|confirmPayment/)
assert.match(payment,/api\.getCoreProjection/)
assert.match(payment,/支付暂不可用/)

assert.doesNotMatch(home,/我的订单|开发样例|目录快照|无登记节假日/)
assert.match(home,/孟加拉黄页/)
for(const label of ['节假日','休息日','工作日'])assert.ok(home.includes(label))
for(const label of ['余额','流量','套餐'])assert.ok(recharge.includes(label))
assert.match(recharge,/getSupportedOperators/)
assert.doesNotMatch(recharge,/Grameenphone、Robi|合成运营商|开发样例|目录快照/)

assert.match(directory,/getDirectoryCities/)
assert.match(directory,/cityCode/)
assert.match(directory,/const categories=computed/)
assert.match(detail,/showDial\.value=true/)
assert.match(detail,/v-if="showDial"/)
assert.match(detail,/uni\.makePhoneCall/)
assert.match(detail,/reportReason/)
assert.match(detail,/reportNote/)
for(const reason of ['INCORRECT_INFO','PHONE_INVALID','ADDRESS_INVALID','CLOSED','OTHER'])assert.ok(detail.includes(reason))
assert.match(client,/'Idempotency-Key':identity\.idempotencyKey/)
assert.doesNotMatch(detail,/A120|内容复核候选/)
for(const page of [orders,recovery]){
  const template=page.match(/<template>([\s\S]*?)<\/template>/)?.[1]??''
  assert.doesNotMatch(template,/GUEST|BUYER|authorizationSetRef|authorizationEvidenceVersion|recoveryMaterialRef|材料引用/)
}

const output=resolve(root,'dist/build/mp-weixin')
if(existsSync(output)){
  const files=[]
  const walk=directory=>{for(const entry of readdirSync(directory,{withFileTypes:true})){const path=resolve(directory,entry.name);entry.isDirectory()?walk(path):files.push(path)}}
  walk(output)
  const names=files.map(path=>path.slice(output.length+1).replaceAll('\\','/'))
  for(const artifact of ['api/mock.js','api/payment-intent-client.js','api/payment-intent-contract.js','api/p014-topup-synthetic.js','api/order-detail-synthetic.js'])assert.ok(!names.includes(artifact),`runtime artifact forbidden: ${artifact}`)
}

console.log('development API frontend contracts: PASS')
