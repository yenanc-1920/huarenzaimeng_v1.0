import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { requestWechatOneTimeCode } from '../src/api/wechat-one-time-code.ts'
import { invokeWechatPayment, parseWechatPrepayParameters } from '../src/api/wechat-payment-port.ts'
import { factStateCopy, p014FactStateCopy, p014StateCopy, projectOrderStateCopy } from '../src/domain/formal-status-copy.ts'
import { parseCatalogProjection } from '../src/api/topup-recovery-contract.ts'
import { createFormalTransactionClient } from '../src/api/formal-transaction-client.ts'
import { parsePaymentView, parseReleaseOrderView, parseReleaseQuoteView, parseTopupView } from '../src/api/formal-transaction-contract.ts'
import { agreementsAccepted,consentAcceptances,officialPrivacyGranted,sessionConsentCommand } from '../src/domain/login-privacy-state.ts'
import { readOrCreateBuyerGuestRef } from '../src/domain/buyer-guest-ref.ts'
import { parseBuyerClosureView } from '../src/domain/buyer-account-lifecycle.ts'

const root=process.cwd(),read=path=>readFileSync(resolve(root,path),'utf8')
const authPage=read('src/pages/auth/expired.vue'),authContract=read('src/api/auth-entry-contract.ts')
const paymentPage=read('src/pages/payment/status.vue'),refundPage=read('src/pages/refund/status.vue')
const profilePage=read('src/pages/profile/index.vue'),progressPage=read('src/pages/order/progress.vue')
const pagesManifest=read('src/pages.json'),privacyStateSource=read('src/domain/login-privacy-state.ts')
const userAgreementPage=read('src/pages/legal/user-agreement.vue'),privacyPolicyPage=read('src/pages/legal/privacy-policy.vue')
const lifecycleContract=read('src/api/buyer-account-lifecycle-contract.ts')
const formalSources=[authPage,authContract,paymentPage,refundPage,profilePage,progressPage,
  read('src/api/client.ts'),read('src/api/topup-recovery-contract.ts'),read('src/api/p014-topup-contract.ts'),
  read('src/api/formal-transaction-contract.ts'),read('src/api/formal-transaction-client.ts'),
  read('src/api/wechat-one-time-code.ts'),read('src/api/wechat-payment-port.ts'),read('src/domain/formal-status-copy.ts')]

for(const source of formalSources)assert.doesNotMatch(source,/LOCAL_MOCK_NO_REAL_OPERATOR_FACTS|legacy-price-v1|REQUEST_MOCK_PAYMENT|CREATE_LOCAL_SYNTHETIC_PAYMENT_INTENT|QUERY_LOCAL_SYNTHETIC_PAYMENT_INTENT|REQUEST_MOCK_TOPUP|CREATE_LOCAL_SYNTHETIC_TOPUP/)
assert.doesNotMatch(read('src/api/wechat-one-time-code.ts'),/Storage|console\.|log\(/)
assert.match(authPage,/catch(?:\([^)]*\))?\{\s*clearBuyerSessionToken\(\)/)
assert.match(authPage,/userAgreementAccepted=ref\(false\),privacyPolicyAccepted=ref\(false\)/)
assert.match(authPage,/value="AGREEMENTS_BUNDLE"/)
assert.equal((authPage.match(/<checkbox\s/g)||[]).length,1)
assert.match(authPage,/userAgreementAccepted\.value=accepted\s+privacyPolicyAccepted\.value=accepted/)
assert.match(authPage,/open-type="agreePrivacyAuthorization"/)
assert.match(authPage,/@agreeprivacyauthorization="onOfficialPrivacyAuthorized"/)
assert.match(authPage,/privacyState\.value!==\'OFFICIAL_PRIVACY_GRANTED\'/)
assert.ok(authPage.indexOf("privacyState.value!=='OFFICIAL_PRIVACY_GRANTED'")<authPage.indexOf('requestWechatOneTimeCode(wx)'))
assert.doesNotMatch(authPage+privacyStateSource,/setStorage|acceptedAt|subjectRef|subjectId/)
assert.match(authPage,/暂不登录，继续浏览/)
assert.match(profilePage,/用户协议/)
assert.match(profilePage,/隐私政策/)
assert.match(profilePage,/退出登录/)
assert.match(profilePage,/注销账号/)
assert.match(profilePage,/logoutBuyerSession/)
assert.match(profilePage,/requestBuyerAccountClosure/)
assert.match(profilePage,/openProfileSignIn=.*pages\/auth\/expired/)
assert.match(profilePage,/@click="openProfileSignIn"/)
assert.match(profilePage,/点击登录或注册/)
assert.match(profilePage,/仅退出当前会话，不会注销账号/)
assert.match(profilePage,/受理后立即退出所有设备/)
assert.match(lifecycleContract,/\/buyer-auth\/v1\/session\/logout/)
assert.match(lifecycleContract,/\/buyer-auth\/v1\/account-closure-requests/)
assert.match(lifecycleContract,/expectedVersion:1/)
assert.match(lifecycleContract,/'Idempotency-Key':idempotencyKey/)
assert.match(pagesManifest,/pages\/legal\/user-agreement/)
assert.match(pagesManifest,/pages\/legal\/privacy-policy/)
assert.match(userAgreementPage,/华人在孟小程序用户协议/)
assert.match(privacyPolicyPage,/华人在孟小程序隐私政策/)
assert.match(paymentPage,/:data-payment-sdk="paymentAvailable\?'available':'disabled-by-default'"/)
assert.match(paymentPage,/v-if="paymentAvailable"[^>]+@click="pay"/)
assert.match(paymentPage,/uni\.requestPayment\(\{provider:'wxpay',\.\.\.params/)
assert.match(paymentPage,/finally\{paying\.value=false;await load\(\)\}/)
for(const page of [profilePage,progressPage,refundPage])assert.match(page,/open-type="contact"/)
assert.doesNotMatch(profilePage,/pages\/support\/case/)

const code=await requestWechatOneTimeCode({login({success}){success({code:' one-time-code '})}})
assert.equal(code,'one-time-code')
await assert.rejects(()=>requestWechatOneTimeCode({login({fail}){fail()}}),/WX_LOGIN_FAILED/)
await assert.rejects(()=>requestWechatOneTimeCode({login({success}){success({})}}),/WX_LOGIN_CODE_MISSING/)

assert.equal(agreementsAccepted({userAgreementAccepted:false,privacyPolicyAccepted:false}),false)
assert.equal(agreementsAccepted({userAgreementAccepted:true,privacyPolicyAccepted:false}),false)
assert.equal(agreementsAccepted({userAgreementAccepted:false,privacyPolicyAccepted:true}),false)
assert.equal(agreementsAccepted({userAgreementAccepted:true,privacyPolicyAccepted:true}),true)
assert.deepEqual(consentAcceptances({userAgreementAccepted:false,privacyPolicyAccepted:true}),[])
const acceptances=consentAcceptances({userAgreementAccepted:true,privacyPolicyAccepted:true})
assert.deepEqual(acceptances.map(({policyType,policyVersion,accepted})=>({policyType,policyVersion,accepted})),[
  {policyType:'USER_AGREEMENT',policyVersion:'2026-08-28',accepted:true},
  {policyType:'PRIVACY_POLICY',policyVersion:'2026-08-28',accepted:true}
])
assert.deepEqual(sessionConsentCommand({userAgreementAccepted:true,privacyPolicyAccepted:true}),{userAgreementVersion:'2026-08-28',privacyPolicyVersion:'2026-08-28',userAgreementAccepted:true,privacyPolicyAccepted:true})
assert.equal(sessionConsentCommand({userAgreementAccepted:true,privacyPolicyAccepted:false}),null)
const guestStore={value:null,getStorageSync(){return this.value},setStorageSync(_key,value){this.value=value}}
assert.equal(readOrCreateBuyerGuestRef(guestStore,1723000000000,.25),'GUEST-lzj9pon4-0hra0hs')
assert.equal(readOrCreateBuyerGuestRef(guestStore,1723000001000,.75),'GUEST-lzj9pon4-0hra0hs')
const closure=parseBuyerClosureView({schemaVersion:'BUYER_CLOSURE_V1',closureRef:'CLOSURE-1',state:'REQUESTED',blockerCount:2,version:1,requestRef:'CLOSURE-REQUEST-1',replayed:false},'CLOSURE-REQUEST-1')
assert.equal(closure.blockerCount,2)
for(const invalid of [
  {...closure,state:'CLOSED'},
  {...closure,version:2},
  {...closure,blockerCount:-1},
  {...closure,rawSupplierPayload:{}},
  {...closure,requestRef:'OTHER-REQUEST'}
])assert.throws(()=>parseBuyerClosureView(invalid,'CLOSURE-REQUEST-1'),/BUYER_CLOSURE_RESPONSE_INVALID/)
assert.equal(officialPrivacyGranted({detail:{errMsg:'agreePrivacyAuthorization:ok'}}),true)
for(const malformed of [undefined,null,{}, {detail:null},{detail:{}},{detail:{errMsg:'agreePrivacyAuthorization:fail'}},{detail:{errMsg:1}}])assert.equal(officialPrivacyGranted(malformed),false)

const parameters=parseWechatPrepayParameters({timeStamp:'1723000000',nonceStr:'nonce',package:'prepay_id=frozen',signType:'RSA',paySign:'signature'})
await assert.rejects(()=>invokeWechatPayment(parameters),/WECHAT_PAYMENT_CHANNEL_NOT_CONFIGURED/)
let received=null
assert.equal(await invokeWechatPayment(parameters,{async requestPayment(input){received=input}}),'PAYMENT_SDK_COMPLETED')
assert.deepEqual(received,parameters)
await assert.rejects(()=>invokeWechatPayment({...parameters,package:'not-a-prepay-id'}),/WECHAT_PREPAY_PARAMETERS_INVALID/)

assert.deepEqual(projectOrderStateCopy('UNKNOWN_NEW_STATE'),{label:'暂时无法确认',unknown:true})
assert.deepEqual(factStateCopy('UNKNOWN'),{label:'暂时无法确认',unknown:true})
assert.deepEqual(p014StateCopy('TOPUP_RESULT_UNKNOWN'),{label:'充值结果暂时无法确认',unknown:true})
assert.deepEqual(p014FactStateCopy('UNKNOWN'),{label:'暂时无法确认',unknown:true})
assert.throws(()=>parseCatalogProjection({supportedOperatorSetVersion:1,catalogVersion:1,operatorCode:'OP',operatorQualification:'SUPPORTED',items:[{operatorCode:'OP',productRef:'P',denominationRef:'D',itemKind:'PRESET_DENOMINATION',amountMinor:100,currency:'BDT'}],evidenceSemantics:'LOCAL_MOCK_NO_REAL_OPERATOR_FACTS'}),/INVALID_CATALOG_DTO/)
assert.throws(()=>parseCatalogProjection({supportedOperatorSetVersion:1,catalogVersion:1,operatorCode:'OP',operatorQualification:'SUPPORTED',items:[{operatorCode:'OP',productRef:'P',denominationRef:'D',itemKind:'PRESET_DENOMINATION',amountMinor:100,currency:'BDT'}],evidenceSemantics:'LOCAL_DATABASE_STATE_NOT_EXTERNAL_OPERATOR_FACT'}),/INVALID_CATALOG_DTO/)

const digest='a'.repeat(64),envelope=data=>({status:'ACCEPTED',projectCode:'OK',data})
const quote=parseReleaseQuoteView({quoteRef:'Q-1',requestRef:'cmd-1',phoneMasked:'+88017****678',operatorCode:'GP',productRef:'GP-50',priceVersionRef:'PV-1',finalAmountCny:5.25,currency:'CNY',catalogVersion:17,validUntil:'2026-08-18T12:00:00Z',snapshotDigest:digest})
assert.equal(quote.productRef,'GP-50')
assert.throws(()=>parseReleaseQuoteView({...quote,benefitText:'invented'}),/INVALID_RELEASE_QUOTE_DTO/)
assert.throws(()=>parseReleaseQuoteView({...quote,requestDigest:digest}),/INVALID_RELEASE_QUOTE_DTO/)
assert.throws(()=>parseReleaseQuoteView({...quote,phoneDigest:digest}),/INVALID_RELEASE_QUOTE_DTO/)
const order=parseReleaseOrderView({orderRef:'O-1',quoteRef:'Q-1',requestRef:'cmd-2',snapshotDigest:digest,orderState:'CREATED',paymentState:'UNPAID',deliveryState:'NOT_STARTED',refundState:'NOT_REQUESTED',projectionVersion:1,aggregateVersion:1})
assert.equal(order.paymentState,'UNPAID')
assert.throws(()=>parseReleaseOrderView({...order,requestDigest:digest}),/INVALID_RELEASE_ORDER_DTO/)
const payment=parsePaymentView({orderRef:'O-1',state:'PREPAY_CREATED',providerRef:'WX-1',amountMinor:525,currency:'CNY',refundedMinor:0,version:1,prepayParameters:null})
assert.equal(payment.prepayParameters,null)
assert.throws(()=>parsePaymentView({...payment,prepayParameters:{timeStamp:'x'}}),/INVALID_PAYMENT_VIEW_DTO/)
assert.equal(parseTopupView({orderRef:'O-1',requestRef:'topup-1',state:'UNKNOWN',providerRef:null,evidenceRef:'UNKNOWN',version:1}).state,'UNKNOWN')

const calls=[]
const fake=createFormalTransactionClient({async request(path,method,data){calls.push({path,method,data});if(path.endsWith('/payment-intents/result'))return{statusCode:200,body:envelope({orderRef:'O-1',state:'PAID',providerRef:'WX-1',amountMinor:525,currency:'CNY',refundedMinor:0,version:2,prepayParameters:null})};if(path.endsWith('/topup/query'))return{statusCode:200,body:envelope({orderRef:'O-1',requestRef:'topup-1',state:'DELIVERED',providerRef:'T-1',evidenceRef:'E-1',version:2})};return{statusCode:503,body:{status:'REJECTED',projectCode:'CHANNEL_DISABLED',data:null}}}})
assert.equal((await fake.paymentStatus('O-1')).state,'PAID')
assert.equal((await fake.queryTopupOriginal('O-1')).state,'DELIVERED')
await assert.rejects(()=>fake.submitTopup('O-1','topup-1'),/TRANSACTION_CHANNEL_DISABLED/)
assert.deepEqual(calls.map(call=>[call.method,call.path]),[['GET','/orders/O-1/payment-intents/result'],['POST','/orders/O-1/topup/query'],['POST','/orders/O-1/topup']])
console.log('FORMAL_RUNTIME_CONTRACTS_OK')
