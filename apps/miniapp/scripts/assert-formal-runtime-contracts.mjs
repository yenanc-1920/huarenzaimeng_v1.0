import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { requestWechatOneTimeCode } from '../src/api/wechat-one-time-code.ts'
import { invokeWechatPayment, parseWechatPrepayParameters } from '../src/api/wechat-payment-port.ts'
import { factStateCopy, p014FactStateCopy, p014StateCopy, projectOrderStateCopy } from '../src/domain/formal-status-copy.ts'
import { parseCatalogProjection } from '../src/api/topup-recovery-contract.ts'
import { createFormalTransactionClient } from '../src/api/formal-transaction-client.ts'
import { parsePaymentView, parseReleaseOrderView, parseReleaseQuoteView, parseTopupView } from '../src/api/formal-transaction-contract.ts'

const root=process.cwd(),read=path=>readFileSync(resolve(root,path),'utf8')
const authPage=read('src/pages/auth/expired.vue'),authContract=read('src/api/auth-entry-contract.ts')
const paymentPage=read('src/pages/payment/status.vue'),refundPage=read('src/pages/refund/status.vue')
const profilePage=read('src/pages/profile/index.vue'),progressPage=read('src/pages/order/progress.vue')
const formalSources=[authPage,authContract,paymentPage,refundPage,profilePage,progressPage,
  read('src/api/client.ts'),read('src/api/topup-recovery-contract.ts'),read('src/api/p014-topup-contract.ts'),
  read('src/api/formal-transaction-contract.ts'),read('src/api/formal-transaction-client.ts'),
  read('src/api/wechat-one-time-code.ts'),read('src/api/wechat-payment-port.ts'),read('src/domain/formal-status-copy.ts')]

for(const source of formalSources)assert.doesNotMatch(source,/LOCAL_MOCK_NO_REAL_OPERATOR_FACTS|legacy-price-v1|REQUEST_MOCK_PAYMENT|CREATE_LOCAL_SYNTHETIC_PAYMENT_INTENT|QUERY_LOCAL_SYNTHETIC_PAYMENT_INTENT|REQUEST_MOCK_TOPUP|CREATE_LOCAL_SYNTHETIC_TOPUP/)
assert.doesNotMatch(read('src/api/wechat-one-time-code.ts'),/Storage|console\.|log\(/)
assert.match(authPage,/catch\{clearBuyerSessionToken\(\)/)
assert.match(paymentPage,/:data-payment-sdk="paymentAvailable\?'available':'disabled-by-default'"/)
assert.doesNotMatch(paymentPage,/@click="(?:pay|requestPayment|submitPayment)/)
for(const page of [profilePage,progressPage,refundPage])assert.match(page,/open-type="contact"/)
assert.doesNotMatch(profilePage,/pages\/support\/case/)

const code=await requestWechatOneTimeCode({login({success}){success({code:' one-time-code '})}})
assert.equal(code,'one-time-code')
await assert.rejects(()=>requestWechatOneTimeCode({login({fail}){fail()}}),/WX_LOGIN_FAILED/)
await assert.rejects(()=>requestWechatOneTimeCode({login({success}){success({})}}),/WX_LOGIN_CODE_MISSING/)

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
const payment=parsePaymentView({orderRef:'O-1',state:'PREPAY_CREATED',providerRef:'WX-1',amountMinor:525,currency:'CNY',refundedMinor:0,version:1})
assert.equal(payment.prepayParameters,null)
assert.throws(()=>parsePaymentView({...payment,prepayParameters:{timeStamp:'x'}}),/INVALID_PAYMENT_VIEW_DTO/)
assert.equal(parseTopupView({orderRef:'O-1',requestRef:'topup-1',state:'UNKNOWN',providerRef:null,evidenceRef:'UNKNOWN',version:1}).state,'UNKNOWN')

const calls=[]
const fake=createFormalTransactionClient({async request(path,method,data){calls.push({path,method,data});if(path.endsWith('/payment-intents/result'))return{statusCode:200,body:envelope({orderRef:'O-1',state:'PAID',providerRef:'WX-1',amountMinor:525,currency:'CNY',refundedMinor:0,version:2})};if(path.endsWith('/topup/query'))return{statusCode:200,body:envelope({orderRef:'O-1',requestRef:'topup-1',state:'DELIVERED',providerRef:'T-1',evidenceRef:'E-1',version:2})};return{statusCode:503,body:{status:'REJECTED',projectCode:'CHANNEL_DISABLED',data:null}}}})
assert.equal((await fake.paymentStatus('O-1')).state,'PAID')
assert.equal((await fake.queryTopupOriginal('O-1')).state,'DELIVERED')
await assert.rejects(()=>fake.submitTopup('O-1','topup-1'),/TRANSACTION_CHANNEL_DISABLED/)
assert.deepEqual(calls.map(call=>[call.method,call.path]),[['GET','/orders/O-1/payment-intents/result'],['POST','/orders/O-1/topup/query'],['POST','/orders/O-1/topup']])
console.log('FORMAL_RUNTIME_CONTRACTS_OK')
