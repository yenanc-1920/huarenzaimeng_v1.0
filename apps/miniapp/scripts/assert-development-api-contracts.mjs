import assert from 'node:assert/strict'
import { existsSync, readFileSync, readdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { selectionMatchesCatalog } from '../src/api/topup-recovery-contract.ts'

const root=process.cwd()
const read=path=>readFileSync(resolve(root,path),'utf8')
const client=read('src/api/client.ts')
const home=read('src/pages/index/index.vue')
const recharge=read('src/pages/recharge/select.vue')
const products=read('src/pages/recharge/products.vue')
const quote=read('src/pages/recharge/quote.vue')
const projectContract=read('src/api/project-contract.ts')
const topupContract=read('src/api/p014-topup-contract.ts')
const payment=read('src/pages/payment/status.vue')
const orders=read('src/pages/order/list.vue')
const recovery=read('src/pages/order/recovery.vue')
const directory=read('src/pages/directory/list.vue')
const detail=read('src/pages/directory/detail.vue')
const auth=read('src/pages/auth/expired.vue')
const profile=read('src/pages/profile/index.vue')

assert.doesNotMatch(client,/\.\/mock|BuiltinSynthetic|p014BuiltinSynthetic|p021BuiltinSynthetic|PROJECT_MOCK_API|BUILTIN_MOCK/)
for(const source of [client,projectContract,topupContract]){
  assert.doesNotMatch(source,/REQUEST_MOCK_PAYMENT|CREATE_LOCAL_SYNTHETIC_PAYMENT_INTENT|QUERY_LOCAL_SYNTHETIC_PAYMENT_INTENT|REQUEST_MOCK_TOPUP|CREATE_LOCAL_SYNTHETIC_TOPUP|MOCK_PROJECTION_ONLY_NO_EXTERNAL_FACTS/)
}
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
assert.match(payment,/当前暂不可支付/)
assert.match(payment,/未扣款/)
assert.match(payment,/本次支付尚未开放，请稍后再试/)
assert.doesNotMatch(payment,/微信五项|预支付参数|配置工程词/)
assert.doesNotMatch(payment,/\{\{projection\.orderState\}\}/)

assert.doesNotMatch(home,/我的订单|开发样例|目录快照|无登记节假日/)
assert.match(home,/孟加拉黄页/)
for(const label of ['节假日','休息日','工作日'])assert.ok(home.includes(label))
for(const label of ['余额','流量','套餐'])assert.ok(products.includes(label))
assert.match(recharge,/getSupportedOperators/)
assert.doesNotMatch(recharge,/Grameenphone、Robi|合成运营商|开发样例|目录快照/)
assert.match(products,/api\.getCatalog/)
assert.match(products,/api\.getSupportedOperators/)
assert.match(products,/当前支持运营商/)
assert.match(products,/当前充值号码/)
assert.match(products,/识别运营商/)
for(const field of ['item.displayName','item.benefitText','item.validityText','item.finalAmountCny'])assert.ok(products.includes(field))
assert.doesNotMatch(products,/合成商品|开发样例|目录快照/)
assert.doesNotMatch(products,/优惠|到账时长|Grameenphone|Robi|Banglalink/)
assert.match(quote,/data-page-id="UX-P012"/)
assert.match(quote,/title="订单确认"/)
assert.match(quote,/>订单确认<\/view>/)
assert.match(quote,/'确认订单'/)
assert.doesNotMatch(quote,/确认并创建订单|确认充值/)
assert.match(quote,/报价剩余时间/)
assert.match(quote,/Date\.parse\(quote\.value\.validUntil\)>now\.value/)
assert.match(quote,/quote\.value\.validUntil/)
assert.match(quote,/quote\.entitlement\.benefitText/)
assert.match(quote,/quote\.entitlement\.displayName/)
assert.doesNotMatch(quote,/quote\.faceValue\.minor|totalAmountMinor.*到账内容|到账内容[\s\S]{0,100}quote\.total/)
assert.doesNotMatch(quote,/parseRechargeSelection\(uni\.getStorageSync\('rechargeSelection'\)\)[\s\S]{0,160}currentSelectionIsValid/,'P012 must not repeat the catalog read before createQuote')
assert.match(client,/async createQuote\(selection:RechargeSelection\)[\s\S]*loadCatalog\(selection\.operatorCode\)[\s\S]*selectionMatchesCatalog\(selection,current\)[\s\S]*requestBody\('\/quotes','POST'/,'the single pre-quote catalog validation must fail closed before POST')
const freshSelection={recipientPhone:'+8801712345678',maskedPhone:'+88017****678',operatorCode:'GRAMEENPHONE',operatorName:'Grameenphone',productRef:'GP-100',denominationRef:'BDT-100',itemKind:'PRESET_DENOMINATION',faceValue:{minor:10000,currency:'BDT'},productType:'BALANCE',displayName:'100塔卡余额',benefitText:'到账100塔卡余额',validityText:null,priceVersionRef:'PV-GP-100',supportedOperatorSetVersion:3,catalogVersion:7}
const freshCatalog={operatorQualification:'SUPPORTED',supportedOperatorSetVersion:3,catalogVersion:7,operatorCode:'GRAMEENPHONE',items:[{operatorCode:'GRAMEENPHONE',productRef:'GP-100',denominationRef:'BDT-100',itemKind:'PRESET_DENOMINATION',faceValue:{minor:10000,currency:'BDT'},productType:'BALANCE',displayName:'100塔卡余额',benefitText:'到账100塔卡余额',validityText:null,finalAmountCny:6.8,priceVersionRef:'PV-GP-100',available:true}],evidenceSemantics:'LOCAL_DATABASE_STATE_NOT_EXTERNAL_OPERATOR_FACT'}
assert.equal(selectionMatchesCatalog(freshSelection,freshCatalog),true,'a fresh catalog-backed selection must reach quote creation')
assert.equal(selectionMatchesCatalog(freshSelection,{...freshCatalog,catalogVersion:8}),false,'a genuinely stale catalog version must remain fail closed')

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
assert.match(recovery,/open-type="contact"/)
assert.match(auth,/userAgreementAccepted=ref\(false\),privacyPolicyAccepted=ref\(false\)/)
assert.match(auth,/open-type="agreePrivacyAuthorization"/)
assert.match(auth,/暂不登录，继续浏览/)
assert.match(auth,/callProjectApi\('\/buyer-auth\/v1\/session','GET'/)
assert.match(auth,/BUYER_SESSION_READY/)
assert.doesNotMatch(auth,/\/buyer-api\/v1\/session|storeBuyerSessionProjection/)
assert.doesNotMatch(auth,/setStorage|acceptedAt|subjectRef|subjectId/)
assert.match(profile,/pages\/legal\/user-agreement/)
assert.match(profile,/pages\/legal\/privacy-policy/)
assert.match(profile,/退出登录/)
assert.match(profile,/注销账号/)
assert.match(profile,/logoutBuyerSession/)
assert.match(profile,/requestBuyerAccountClosure/)
assert.match(profile,/仅退出当前会话，不会注销账号/)

const output=resolve(root,'dist/build/mp-weixin')
if(existsSync(output)){
  const files=[]
  const walk=directory=>{for(const entry of readdirSync(directory,{withFileTypes:true})){const path=resolve(directory,entry.name);entry.isDirectory()?walk(path):files.push(path)}}
  walk(output)
  const names=files.map(path=>path.slice(output.length+1).replaceAll('\\','/'))
  for(const artifact of ['api/mock.js','api/payment-intent-client.js','api/payment-intent-contract.js','api/p014-topup-synthetic.js','api/order-detail-synthetic.js'])assert.ok(!names.includes(artifact),`runtime artifact forbidden: ${artifact}`)
  const forbiddenRuntime=/REQUEST_MOCK_PAYMENT|CREATE_LOCAL_SYNTHETIC_PAYMENT_INTENT|QUERY_LOCAL_SYNTHETIC_PAYMENT_INTENT|REQUEST_MOCK_TOPUP|CREATE_LOCAL_SYNTHETIC_TOPUP|MOCK_PROJECTION_ONLY_NO_EXTERNAL_FACTS/
  for(const path of files){
    if(!/\.(?:js|wxml|json)$/.test(path))continue
    assert.doesNotMatch(readFileSync(path,'utf8'),forbiddenRuntime,`formal runtime synthetic semantic forbidden: ${path}`)
  }
}

console.log('development API frontend contracts: PASS')
