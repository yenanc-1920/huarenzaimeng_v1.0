import assert from 'node:assert/strict'
import { existsSync, readFileSync, readdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { selectionMatchesCatalog } from '../src/api/topup-recovery-contract.ts'
import { commitAnonymousSessionToken, parseAnonymousSessionResponse, readAnonymousSessionToken, resetAnonymousSessionTokenForTests } from '../src/api/anonymous-session-contract.ts'

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
const lifeContent=read('src/pages/life-content/list.vue')
const userAgreement=read('src/pages/legal/user-agreement.vue')
const privacyPolicy=read('src/pages/legal/privacy-policy.vue')

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
assert.match(client,/anonymousEligible\?await transactionBearer\(data\):protectedRequest\?requireBuyerBearerToken\(\):undefined/)
assert.match(client,/method==='POST'.*path==='\/quotes'.*path==='\/orders'/)
assert.match(client,/\/buyer-auth\/v1|buyerAuthBaseUrl/)
assert.match(client,/const body=\{requestRef,guestRef:readOrCreateBuyerGuestRef\(uni\),consent\}/,'anonymous session body must contain only the frozen request fields')
assert.match(client,/sessionConsentCommand\(\{userAgreementAccepted:true,privacyPolicyAccepted:true\}\)/,'anonymous session must bind the current accepted consent versions')
assert.doesNotMatch(client,/Authorization: `Bearer \$\{(?:''|undefined|null)\}`/)
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
assert.doesNotMatch(quote,/uni\.navigateTo\(\{url:'\/pages\/auth\/expired'\}\)/,'guest quote creation must not force login')
const anonymousSessionBranch=quote.match(/if\(code==='ANONYMOUS_SESSION_UNAVAILABLE'\)\{([\s\S]*?)\}\s*else/)?.[1]??''
assert.match(anonymousSessionBranch,/quote\.value=null/)
assert.doesNotMatch(anonymousSessionBranch,/invalidateSelection|removeStorageSync/,'anonymous session failure must retain the fresh selection')
assert.match(client,/async createQuote\(selection:RechargeSelection\)[\s\S]*loadCatalog\(selection\.operatorCode\)[\s\S]*selectionMatchesCatalog\(selection,current\)[\s\S]*requestBody\('\/quotes','POST'/,'the single pre-quote catalog validation must fail closed before POST')
const freshSelection={recipientPhone:'+8801712345678',maskedPhone:'+88017****678',operatorCode:'GRAMEENPHONE',operatorName:'Grameenphone',productRef:'GP-BALANCE-100',denominationRef:'BDT-100',itemKind:'PRESET_DENOMINATION',faceValue:{minor:10000,currency:'BDT'},productType:'BALANCE',displayName:'100塔卡余额',benefitText:'到账100塔卡余额',validityText:null,priceVersionRef:'PRICE-GP-BALANCE-100-V1',supportedOperatorSetVersion:1,catalogVersion:1}
const freshCatalog={operatorQualification:'SUPPORTED',supportedOperatorSetVersion:1,catalogVersion:1,operatorCode:'GRAMEENPHONE',items:[{operatorCode:'GRAMEENPHONE',productRef:'GP-BALANCE-100',denominationRef:'BDT-100',itemKind:'PRESET_DENOMINATION',faceValue:{minor:10000,currency:'BDT'},productType:'BALANCE',displayName:'Grameenphone 100塔卡余额',benefitText:'到账100 BDT话费余额',validityText:'长期有效',finalAmountCny:6.8,priceVersionRef:'PRICE-GP-BALANCE-100-V1',available:true}],evidenceSemantics:'LOCAL_DATABASE_STATE_NOT_EXTERNAL_OPERATOR_FACT'}
assert.equal(selectionMatchesCatalog(freshSelection,freshCatalog),true,'a fresh catalog-backed selection must reach quote creation')
assert.equal(selectionMatchesCatalog(freshSelection,{...freshCatalog,catalogVersion:8}),false,'a genuinely stale catalog version must remain fail closed')
for(const [field,value] of [['supportedOperatorSetVersion',2],['catalogVersion',2],['operatorCode','ROBI']])assert.equal(selectionMatchesCatalog(freshSelection,{...freshCatalog,[field]:value}),false,`${field} drift must remain fail closed`)
for(const item of [{...freshCatalog.items[0],productRef:'OTHER'},{...freshCatalog.items[0],denominationRef:'BDT-200'},{...freshCatalog.items[0],faceValue:{minor:20000,currency:'BDT'}},{...freshCatalog.items[0],priceVersionRef:'PRICE-GP-BALANCE-100-V2'}])assert.equal(selectionMatchesCatalog(freshSelection,{...freshCatalog,items:[item]}),false,'product or price identity drift must remain fail closed')
assert.equal(selectionMatchesCatalog(freshSelection,{...freshCatalog,items:[{...freshCatalog.items[0],itemKind:'PRESET_PACKAGE'}]}),false,'itemKind drift must remain fail closed')
assert.equal(selectionMatchesCatalog(freshSelection,{...freshCatalog,items:[{...freshCatalog.items[0],faceValue:{minor:10000,currency:'CNY'}}]}),false,'faceValue.currency drift must remain fail closed')
assert.equal(selectionMatchesCatalog(freshSelection,{...freshCatalog,items:[{...freshCatalog.items[0],productType:'DATA'}]}),false,'productType drift must remain fail closed')

const anonymousRequestRef='REQ-ANON-0001',anonymousExpiry='2026-08-24T00:00:00Z'
const anonymousBody={outcome:'AUTHENTICATED',projectCode:'ANONYMOUS_SESSION_CREATED',requestRef:anonymousRequestRef,subjectRef:'ANON-SUBJECT-0001',token:'opaque-anonymous-token',absoluteExpiresAt:anonymousExpiry,retryClass:'NONE'}
const parsedAnonymous=parseAnonymousSessionResponse({statusCode:201,header:{'Cache-Control':'private, no-store'},data:anonymousBody},anonymousRequestRef,Date.parse('2026-08-23T00:00:00Z'))
assert.equal(parsedAnonymous.token,'opaque-anonymous-token')
for(const invalid of [
  {statusCode:200,header:{'Cache-Control':'no-store'},data:anonymousBody},
  {statusCode:201,header:{},data:anonymousBody},
  {statusCode:201,header:{'Cache-Control':'no-store'},data:{...anonymousBody,extra:true}},
  {statusCode:201,header:{'Cache-Control':'no-store'},data:{...anonymousBody,requestRef:'REQ-OTHER-0001'}},
  {statusCode:201,header:{'Cache-Control':'no-store'},data:{...anonymousBody,absoluteExpiresAt:'2026-08-24'}},
])assert.throws(()=>parseAnonymousSessionResponse(invalid,anonymousRequestRef,Date.parse('2026-08-23T00:00:00Z')),/ANONYMOUS_SESSION_RESPONSE_INVALID/)
resetAnonymousSessionTokenForTests()
commitAnonymousSessionToken(parsedAnonymous,Date.parse('2026-08-23T00:00:00Z'))
assert.equal(readAnonymousSessionToken(Date.parse('2026-08-23T23:59:59Z'))?.token,'opaque-anonymous-token')
assert.equal(readAnonymousSessionToken(Date.parse(anonymousExpiry)),null,'expired anonymous token must be removed from memory')
const anonymousSource=read('src/api/anonymous-session-contract.ts')
assert.doesNotMatch(anonymousSource,/setStorage|console\.|uni\.|localStorage|sessionStorage/,'anonymous bearer must remain memory-only and unlogged')

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
assert.match(auth,/allowedReturnTargets=new Set\(\['\/pages\/profile\/index','\/pages\/order\/list'\]\)/)
assert.match(auth,/uni\.reLaunch\(\{url:returnTo\.value\}\)/)
assert.doesNotMatch(auth,/\/buyer-api\/v1\/session|storeBuyerSessionProjection/)
assert.doesNotMatch(auth,/setStorage|acceptedAt|subjectRef|subjectId/)
assert.match(profile,/pages\/legal\/user-agreement/)
assert.match(profile,/pages\/legal\/privacy-policy/)
assert.match(profile,/退出登录/)
assert.match(profile,/注销账号/)
assert.match(profile,/logoutBuyerSession/)
assert.match(profile,/requestBuyerAccountClosure/)
assert.match(profile,/仅退出当前会话，不会注销账号/)
assert.doesNotMatch(profile,/当前使用默认头像|头像与昵称授权/)
assert.match(profile,/returnTo=%2Fpages%2Fprofile%2Findex/)
assert.match(profile,/\/static\/default-avatar\.png/)
assert.doesNotMatch(profile,/默认头像">人</)
assert.ok(existsSync(resolve(root,'src/static/default-avatar.png')))
assert.match(home,/\.temporal-refresh\{[^}]*display:flex;align-items:center;justify-content:center/)
assert.match(lifeContent,/\.topic-tabs button\{[^}]*display:flex;align-items:center;justify-content:center/)
for(const legal of [userAgreement,privacyPolicy])assert.match(legal,/\.legal-page :deep\(\.header\)\{position:sticky;top:0;z-index:20\}/)
assert.match(orders,/readBuyerSessionToken\(\)!==null/)
assert.match(orders,/returnTo=%2Fpages%2Forder%2Flist/)

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
