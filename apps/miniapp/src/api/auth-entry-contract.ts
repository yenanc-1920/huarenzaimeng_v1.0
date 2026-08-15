import { callProjectApi } from './wechat-development-transport'
import { clearBuyerSessionToken, commitBuyerSessionToken } from './buyer-session-token'

export type WechatSessionSuccess = Readonly<{ outcome: 'AUTHENTICATED'; projectCode: 'BUYER_SESSION_CREATED'; requestRef: string; subjectRef: string; token: string; absoluteExpiresAt: string; retryClass: 'NONE' }>
const object = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null && !Array.isArray(value)
function parse(value: unknown, requestRef: string): WechatSessionSuccess {
  if (!object(value) || Object.keys(value).length !== 7 || value.outcome !== 'AUTHENTICATED' || value.projectCode !== 'BUYER_SESSION_CREATED' || value.requestRef !== requestRef || value.retryClass !== 'NONE' || typeof value.subjectRef !== 'string' || typeof value.token !== 'string' || typeof value.absoluteExpiresAt !== 'string') throw new Error('WECHAT_SESSION_UNKNOWN')
  return value as unknown as WechatSessionSuccess
}
export async function executeWechatDevelopmentSignIn(code: string, requestRef: string): Promise<WechatSessionSuccess> {
  clearBuyerSessionToken()
  if (!code.trim() || !requestRef.trim()) throw new Error('WECHAT_SESSION_REQUEST_INVALID')
  const response = await callProjectApi('/buyer-auth/v1/wechat/session', 'POST', { code, requestRef })
  if (response.statusCode !== 201) throw new Error(response.statusCode === 401 || response.statusCode === 403 ? 'WECHAT_SESSION_REVOKED' : 'WECHAT_SESSION_UNKNOWN')
  const result = parse(response.data, requestRef)
  commitBuyerSessionToken({ token: result.token, subjectRef: result.subjectRef, absoluteExpiresAt: result.absoluteExpiresAt })
  return result
}
