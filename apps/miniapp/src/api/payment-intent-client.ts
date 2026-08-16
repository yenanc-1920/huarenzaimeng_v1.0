import {
  buildPaymentIntentCommand,
  buildPaymentIntentQueryRequest,
  parsePaymentIntentQueryResult,
  parsePaymentIntentResult,
  validatePaymentIntentQueryResult,
  validatePaymentIntentResult,
  type PaymentIntentQueryResult,
  type PaymentIntentResult,
} from './payment-intent-contract'
import type { OrderCreationProjection } from './order-creation-contract'
import type { ProjectQuote } from './project-contract'
import { parseAcceptedProjectEnvelope, ProjectApiError } from './project-envelope'
import { readSessionProjection } from '../domain/session'
import { callProjectApi } from './wechat-development-transport'
import { requireBuyerBearerToken } from './buyer-session-token'

const buyerBaseUrl = (import.meta.env.VITE_BUYER_API_BASE_URL || '/buyer-api/v1').replace(/\/$/, '')
const useWechatDevelopment = import.meta.env.VITE_PROJECT_TRANSPORT_MODE === 'WECHAT_DEVELOPMENT'

function requestBody(path:string,method:'GET'|'POST',data?:UniNamespace.RequestOptions['data']):Promise<unknown>{
  if(useWechatDevelopment)return callProjectApi(`/buyer-api/v1${path}`,method,data,requireBuyerBearerToken()).then(result=>result.data)
  return new Promise((resolve,reject)=>uni.request({
    url:`${buyerBaseUrl}${path}`,method,data,header:{Authorization:`Bearer ${requireBuyerBearerToken()}`},
    success:({data:body})=>resolve(body),fail:()=>reject(new ProjectApiError('NETWORK_ERROR')),
  }))
}

export const paymentIntentApi = {
  async create(orderRef:string,identity:{commandId:string;idempotencyKey:string},projection:OrderCreationProjection,
    frozenPriceSnapshot:ProjectQuote):Promise<PaymentIntentResult>{
    const command=buildPaymentIntentCommand(identity,orderRef,readSessionProjection(uni),projection,frozenPriceSnapshot)
    const body=await requestBody(`/orders/${encodeURIComponent(orderRef)}/payment-intents`,'POST',command)
    try{return validatePaymentIntentResult(parsePaymentIntentResult(body),orderRef,command,frozenPriceSnapshot)}catch(error){
      try{parseAcceptedProjectEnvelope(body)}catch(apiError){if(apiError instanceof ProjectApiError&&apiError.projectCode!=='INVALID_ENVELOPE')throw apiError}
      throw new ProjectApiError(error instanceof Error&&error.message.includes('UNKNOWN')?error.message:'PAYMENT_INTENT_WRITE_UNKNOWN')
    }
  },
  async query(orderRef:string,identity:{commandId:string;idempotencyKey:string},sourceProjection:OrderCreationProjection,
    frozenPriceSnapshot:ProjectQuote):Promise<PaymentIntentQueryResult>{
    const request=buildPaymentIntentQueryRequest(identity,orderRef,readSessionProjection(uni))
    const query=`?commandId=${encodeURIComponent(request.commandId)}&idempotencyKey=${encodeURIComponent(request.idempotencyKey)}`
      +`&sessionVersion=${request.sessionVersion}&authorizationSetRef=${encodeURIComponent(request.authorizationSetRef)}`
    const body=await requestBody(`/orders/${encodeURIComponent(orderRef)}/payment-intents/result${query}`,'GET')
    try{return validatePaymentIntentQueryResult(parsePaymentIntentQueryResult(body),orderRef,request,sourceProjection,frozenPriceSnapshot)}catch(error){
      try{parseAcceptedProjectEnvelope(body)}catch(apiError){if(apiError instanceof ProjectApiError&&apiError.projectCode!=='INVALID_ENVELOPE')throw apiError}
      throw new ProjectApiError(error instanceof Error?error.message:'INVALID_PAYMENT_INTENT_QUERY_DTO')
    }
  },
}
