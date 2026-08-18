import { canonicalFingerprint } from '../domain/canonical-fingerprint.ts'
import { parseAcceptedProjectEnvelope, ProjectApiError } from './project-envelope.ts'
import { parsePaymentView, parseRefundView, parseTopupView, type PaymentView, type RefundView, type TopupView } from './formal-transaction-contract.ts'

export interface FormalTransactionResponse{statusCode:number;body:unknown}
export interface FormalTransactionTransport{request(path:string,method:'GET'|'POST',data?:Record<string,unknown>):Promise<FormalTransactionResponse>}
export interface PaymentCreateContext{
  commandId:string;idempotencyKey:string;sessionVersion:number;authorizationSetRef:string
  expectedProjectionVersion:number;expectedAggregateVersion:number
}

function accepted(response:FormalTransactionResponse):unknown{
  if(response.statusCode===503)throw new ProjectApiError('TRANSACTION_CHANNEL_DISABLED')
  if(response.statusCode<200||response.statusCode>=300){
    try{parseAcceptedProjectEnvelope(response.body)}catch(error){if(error instanceof ProjectApiError)throw error}
    throw new ProjectApiError('HTTP_STATUS_REJECTED')
  }
  return parseAcceptedProjectEnvelope(response.body)
}
function required(value:string,code:string){if(!value.trim())throw new ProjectApiError(code)}

export function createFormalTransactionClient(transport:FormalTransactionTransport){
  return{
    async createPayment(orderRef:string,context:PaymentCreateContext):Promise<PaymentView>{
      required(orderRef,'ORDER_REF_REQUIRED');required(context.commandId,'COMMAND_ID_REQUIRED');required(context.idempotencyKey,'IDEMPOTENCY_KEY_REQUIRED')
      required(context.authorizationSetRef,'BUYER_AUTHORIZATION_REQUIRED')
      const requestDigest=canonicalFingerprint('WECHAT_PAYMENT_CREATE',orderRef,context.commandId,context.idempotencyKey)
      return parsePaymentView(accepted(await transport.request(`/orders/${encodeURIComponent(orderRef)}/payment-intents`,'POST',{
        commandId:context.commandId,idempotencyKey:context.idempotencyKey,paymentIntentCreationPrecondition:'PAYMENT_INTENT_MUST_NOT_EXIST',
        sessionVersion:context.sessionVersion,authorizationSetRef:context.authorizationSetRef,
        expectedProjectionVersion:context.expectedProjectionVersion,expectedAggregateVersion:context.expectedAggregateVersion,requestDigest,
      })))
    },
    async paymentStatus(orderRef:string):Promise<PaymentView>{
      required(orderRef,'ORDER_REF_REQUIRED')
      return parsePaymentView(accepted(await transport.request(`/orders/${encodeURIComponent(orderRef)}/payment-intents/result`,'GET')))
    },
    async queryPaymentOriginal(orderRef:string):Promise<PaymentView>{
      required(orderRef,'ORDER_REF_REQUIRED')
      return parsePaymentView(accepted(await transport.request(`/orders/${encodeURIComponent(orderRef)}/payment-intents/query`,'POST')))
    },
    async createRefund(orderRef:string,refundRef:string,amountMinor:number):Promise<PaymentView>{
      required(orderRef,'ORDER_REF_REQUIRED');required(refundRef,'REFUND_REF_REQUIRED')
      if(!Number.isInteger(amountMinor)||amountMinor<1)throw new ProjectApiError('REFUND_AMOUNT_INVALID')
      const requestDigest=canonicalFingerprint('WECHAT_REFUND_CREATE',orderRef,refundRef,String(amountMinor))
      return parsePaymentView(accepted(await transport.request(`/orders/${encodeURIComponent(orderRef)}/refunds`,'POST',{refundRef,amountMinor,requestDigest})))
    },
    async refundStatus(orderRef:string,refundRef:string):Promise<RefundView>{
      required(orderRef,'ORDER_REF_REQUIRED');required(refundRef,'REFUND_REF_REQUIRED')
      return parseRefundView(accepted(await transport.request(`/orders/${encodeURIComponent(orderRef)}/refunds/${encodeURIComponent(refundRef)}`,'GET')))
    },
    async queryRefundOriginal(orderRef:string,refundRef:string):Promise<RefundView>{
      required(orderRef,'ORDER_REF_REQUIRED');required(refundRef,'REFUND_REF_REQUIRED')
      return parseRefundView(accepted(await transport.request(`/orders/${encodeURIComponent(orderRef)}/refunds/${encodeURIComponent(refundRef)}/query`,'POST')))
    },
    async submitTopup(orderRef:string,requestRef:string):Promise<TopupView>{
      required(orderRef,'ORDER_REF_REQUIRED');required(requestRef,'TOPUP_REQUEST_REF_REQUIRED')
      const requestDigest=canonicalFingerprint('TOPUP_SUBMIT',orderRef,requestRef)
      return parseTopupView(accepted(await transport.request(`/orders/${encodeURIComponent(orderRef)}/topup`,'POST',{requestRef,requestDigest})))
    },
    async topupStatus(orderRef:string):Promise<TopupView>{
      required(orderRef,'ORDER_REF_REQUIRED')
      return parseTopupView(accepted(await transport.request(`/orders/${encodeURIComponent(orderRef)}/topup`,'GET')))
    },
    async queryTopupOriginal(orderRef:string):Promise<TopupView>{
      required(orderRef,'ORDER_REF_REQUIRED')
      return parseTopupView(accepted(await transport.request(`/orders/${encodeURIComponent(orderRef)}/topup/query`,'POST')))
    },
  }
}
