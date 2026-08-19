import { callProjectApi } from './wechat-development-transport'
import { clearBuyerSessionToken,requireBuyerBearerToken } from './buyer-session-token'
import { parseBuyerClosureView,type BuyerClosureView } from '../domain/buyer-account-lifecycle'

export async function logoutBuyerSession():Promise<'LOGGED_OUT'|'RESULT_UNKNOWN'>{
  const token=requireBuyerBearerToken()
  const response=await callProjectApi('/buyer-auth/v1/session/logout','POST',undefined,token)
  if(response.statusCode===204){clearBuyerSessionToken();return'LOGGED_OUT'}
  if(response.statusCode===401){clearBuyerSessionToken();return'LOGGED_OUT'}
  if(response.statusCode===503){clearBuyerSessionToken();return'RESULT_UNKNOWN'}
  throw new Error('BUYER_LOGOUT_REJECTED')
}

export async function requestBuyerAccountClosure(requestRef:string,reason:string,idempotencyKey:string):Promise<BuyerClosureView>{
  const token=requireBuyerBearerToken()
  if(!/^[A-Za-z0-9._:-]{8,128}$/.test(requestRef)||!reason.trim()||!/^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$/.test(idempotencyKey))throw new Error('BUYER_CLOSURE_REQUEST_INVALID')
  const response=await callProjectApi('/buyer-auth/v1/account-closure-requests','POST',{requestRef,reason,expectedVersion:1},token,{'Idempotency-Key':idempotencyKey})
  if(response.statusCode!==202)throw new Error(response.statusCode===409?'BUYER_CLOSURE_CONFLICT':'BUYER_CLOSURE_UNAVAILABLE')
  clearBuyerSessionToken()
  return parseBuyerClosureView(response.data,requestRef)
}
