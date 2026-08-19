export type WechatPrepayParameters = Readonly<{
  timeStamp: string; nonceStr: string; package: string; signType: 'RSA'; paySign: string
}>
export interface WechatPaymentSdkPort { requestPayment(input: WechatPrepayParameters): Promise<void> }
export const PAYMENT_SDK_DEFAULT_AVAILABLE = false as const
const text = (value: unknown): value is string => typeof value === 'string' && value.trim().length > 0
export function parseWechatPrepayParameters(value: unknown): WechatPrepayParameters {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('WECHAT_PREPAY_PARAMETERS_INVALID')
  const record = value as Record<string, unknown>, keys = ['timeStamp','nonceStr','package','signType','paySign']
  if (Object.keys(record).length !== keys.length || !Object.keys(record).every(key => keys.includes(key))
    || !text(record.timeStamp) || !/^\d+$/.test(record.timeStamp) || !text(record.nonceStr)
    || !text(record.package) || !record.package.startsWith('prepay_id=') || record.signType !== 'RSA' || !text(record.paySign)) {
    throw new Error('WECHAT_PREPAY_PARAMETERS_INVALID')
  }
  return Object.freeze({ timeStamp:record.timeStamp, nonceStr:record.nonceStr, package:record.package, signType:'RSA', paySign:record.paySign })
}
export const unavailableWechatPaymentSdk: WechatPaymentSdkPort = Object.freeze({
  async requestPayment(): Promise<void> { throw new Error('WECHAT_PAYMENT_CHANNEL_NOT_CONFIGURED') },
})
export const miniProgramWechatPaymentSdk: WechatPaymentSdkPort = Object.freeze({
  async requestPayment(input:WechatPrepayParameters): Promise<void> {
    const p=parseWechatPrepayParameters(input)
    await new Promise<void>((resolve,reject)=>uni.requestPayment({provider:'wxpay',...p,success:()=>resolve(),fail:()=>reject(new Error('WECHAT_PAYMENT_NOT_COMPLETED'))}))
  },
})
/** UI boundary only. The caller must supply frozen backend parameters and an explicit SDK port. */
export async function invokeWechatPayment(parameters: WechatPrepayParameters, port: WechatPaymentSdkPort = unavailableWechatPaymentSdk): Promise<'PAYMENT_SDK_COMPLETED'> {
  await port.requestPayment(parseWechatPrepayParameters(parameters))
  return 'PAYMENT_SDK_COMPLETED'
}
