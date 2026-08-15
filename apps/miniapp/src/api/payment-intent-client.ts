import { mockCreatePaymentIntent, mockQueryPaymentIntent } from './mock'
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

const baseUrl = (import.meta.env.VITE_API_BASE_URL || '/api/v1').replace(/\/$/, '')
const useProjectMockApi = import.meta.env.VITE_USE_PROJECT_MOCK_API === 'true'
const useWechatDevelopment = import.meta.env.VITE_PROJECT_TRANSPORT_MODE === 'WECHAT_DEVELOPMENT'
const projectSubjectRef = import.meta.env.VITE_MOCK_PROJECT_SUBJECT_REF || 'miniapp-local-mock-subject-v1'

function requestBody(path:string,method:'GET'|'POST',data?:UniNamespace.RequestOptions['data']):Promise<unknown>{
  if(useWechatDevelopment)return callProjectApi(`/buyer-api/v1${path}`,method,data,requireBuyerBearerToken()).then(result=>result.data)
  return new Promise((resolve,reject)=>uni.request({
    url:`${baseUrl}${path}`,method,data,header:{'X-Project-Subject-Ref':projectSubjectRef},
    success:({data:body})=>resolve(body),fail:()=>reject(new ProjectApiError('NETWORK_ERROR')),
  }))
}

export const paymentIntentApi = {
  async create(orderRef:string,identity:{commandId:string;idempotencyKey:string},projection:OrderCreationProjection,
    frozenPriceSnapshot:ProjectQuote):Promise<PaymentIntentResult>{
    const command=buildPaymentIntentCommand(identity,orderRef,readSessionProjection(uni),projection,frozenPriceSnapshot)
    if(!useProjectMockApi&&!useWechatDevelopment)return validatePaymentIntentResult(await mockCreatePaymentIntent(orderRef,command,projection),orderRef,command,frozenPriceSnapshot)
    const body=await requestBody(`/orders/${encodeURIComponent(orderRef)}/payment-intents`,'POST',command)
    try{return validatePaymentIntentResult(parsePaymentIntentResult(body),orderRef,command,frozenPriceSnapshot)}catch(error){
      try{parseAcceptedProjectEnvelope(body)}catch(apiError){if(apiError instanceof ProjectApiError&&apiError.projectCode!=='INVALID_ENVELOPE')throw apiError}
      throw new ProjectApiError(error instanceof Error&&error.message.includes('UNKNOWN')?error.message:'PAYMENT_INTENT_WRITE_UNKNOWN')
    }
  },
  async query(orderRef:string,identity:{commandId:string;idempotencyKey:string},sourceProjection:OrderCreationProjection,
    frozenPriceSnapshot:ProjectQuote):Promise<PaymentIntentQueryResult>{
    const request=buildPaymentIntentQueryRequest(identity,orderRef,readSessionProjection(uni))
    if(!useProjectMockApi&&!useWechatDevelopment)return validatePaymentIntentQueryResult(await mockQueryPaymentIntent(orderRef,request),orderRef,request,sourceProjection,frozenPriceSnapshot)
    const query=`?commandId=${encodeURIComponent(request.commandId)}&idempotencyKey=${encodeURIComponent(request.idempotencyKey)}`
      +`&sessionVersion=${request.sessionVersion}&authorizationSetRef=${encodeURIComponent(request.authorizationSetRef)}`
    const body=await requestBody(`/orders/${encodeURIComponent(orderRef)}/payment-intents/result${query}`,'GET')
    try{return validatePaymentIntentQueryResult(parsePaymentIntentQueryResult(body),orderRef,request,sourceProjection,frozenPriceSnapshot)}catch(error){
      try{parseAcceptedProjectEnvelope(body)}catch(apiError){if(apiError instanceof ProjectApiError&&apiError.projectCode!=='INVALID_ENVELOPE')throw apiError}
      throw new ProjectApiError(error instanceof Error?error.message:'INVALID_PAYMENT_INTENT_QUERY_DTO')
    }
  },
}
