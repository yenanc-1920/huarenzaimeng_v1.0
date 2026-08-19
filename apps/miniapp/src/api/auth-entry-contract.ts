import { callProjectApi } from './wechat-development-transport'
import { clearBuyerSessionToken, commitBuyerSessionToken } from './buyer-session-token'

export type BuyerConsentCommand=Readonly<{userAgreementVersion:string;privacyPolicyVersion:string;userAgreementAccepted:true;privacyPolicyAccepted:true}>
export type WechatSessionSuccess = Readonly<{ schemaVersion:'BUYER_SESSION_V2';consentState:'VALID';outcome: 'AUTHENTICATED'; projectCode: 'BUYER_SESSION_CREATED'; requestRef: string; subjectRef: string; token: string; absoluteExpiresAt: string; retryClass: 'NONE' }>
const object = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null && !Array.isArray(value)
function parse(value: unknown, requestRef: string): WechatSessionSuccess {
  if (!object(value) || Object.keys(value).length !== 9 || value.schemaVersion!=='BUYER_SESSION_V2'||value.consentState!=='VALID'||value.outcome !== 'AUTHENTICATED' || value.projectCode !== 'BUYER_SESSION_CREATED' || value.requestRef !== requestRef || value.retryClass !== 'NONE' || typeof value.subjectRef !== 'string'||!value.subjectRef.trim() || typeof value.token !== 'string'||!value.token.trim() || typeof value.absoluteExpiresAt !== 'string'||!Number.isFinite(Date.parse(value.absoluteExpiresAt))) throw new Error('WECHAT_SESSION_UNKNOWN')
  return value as unknown as WechatSessionSuccess
}
export async function executeWechatDevelopmentSignIn(code:string,requestRef:string,guestRef:string,consent:BuyerConsentCommand):Promise<WechatSessionSuccess>{
  clearBuyerSessionToken()
  if(!code.trim()||!requestRef.trim()||!guestRef.trim()||!consent.userAgreementVersion.trim()||!consent.privacyPolicyVersion.trim()||consent.userAgreementAccepted!==true||consent.privacyPolicyAccepted!==true)throw new Error('WECHAT_SESSION_REQUEST_INVALID')
  const response=await callProjectApi('/buyer-auth/v1/wechat/session','POST',{code,requestRef,guestRef,consent})
  if(response.statusCode!==201){
    const projectCode=object(response.data)&&typeof response.data.projectCode==='string'?response.data.projectCode:''
    if(response.statusCode===409&&projectCode==='BUYER_CONSENT_REQUIRED')throw new Error('BUYER_CONSENT_REQUIRED')
    if(response.statusCode===409&&projectCode==='BUYER_ACCOUNT_CLOSURE_PENDING')throw new Error('BUYER_ACCOUNT_CLOSURE_PENDING')
    if(response.statusCode===503&&['BUYER_AUTH_CONFIGURATION_UNAVAILABLE','WECHAT_PROVIDER_TIMEOUT','WECHAT_PROVIDER_DNS_FAILURE','WECHAT_PROVIDER_TLS_CERTIFICATE_FAILURE','WECHAT_PROVIDER_TLS_HANDSHAKE_FAILURE','WECHAT_PROVIDER_CONNECTION_FAILED','WECHAT_PROVIDER_UNAVAILABLE','WECHAT_PROVIDER_HTTP_UNKNOWN','WECHAT_PROVIDER_RESPONSE_INVALID','WECHAT_PROVIDER_IDENTITY_INVALID','WECHAT_PROVIDER_BUSY'].includes(projectCode))throw new Error(projectCode)
    throw new Error(response.statusCode===401||response.statusCode===403?'WECHAT_SESSION_REVOKED':'WECHAT_SESSION_UNKNOWN')
  }
  const result = parse(response.data, requestRef)
  commitBuyerSessionToken({ token: result.token, subjectRef: result.subjectRef, absoluteExpiresAt: result.absoluteExpiresAt })
  return result
}
