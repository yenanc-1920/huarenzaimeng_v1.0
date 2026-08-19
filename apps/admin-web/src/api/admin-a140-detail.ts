export type A140Role = 'FIN' | 'CS' | 'SUPER_ADMIN'
export interface A140EntitlementFact { productRef:string;productType:string;displayName:string;benefitText:string;denominationBdt:number|null;dataAllowanceMb:number|null;voiceMinutes:number|null;smsCount:number|null;validityText:string|null }
export interface A140OrderFact { orderRef:string;quoteRef:string;state:string;amountMinor:number;currency:string;phoneMasked:string;operatorCode:string;productRef:string;priceVersionRef:string;entitlement:A140EntitlementFact|null;createdAt:string;updatedAt:string|null }
export interface A140PaymentFact { state:string;providerRef:string|null;amountMinor:number|null;currency:string|null;refundedMinor:number|null;version:number|null;updatedAt:string|null }
export interface A140TopupFact { state:string;providerRef:string|null;version:number|null;updatedAt:string|null }
export interface A140RefundFact { refundRef:string;state:string;amountMinor:number;currency:string;createdAt:string;updatedAt:string|null }
export interface A140TimelineFact { eventRef:string;eventType:string;source:'PROJECTED'|'OUTBOX';occurredAt:string;projectedOrDispatchedAt:string|null }
export interface A140Detail { schemaVersion:'ADMIN_READ_V1';projectionVersion:'A140-DETAIL-1';pageId:'A140';role:A140Role;order:A140OrderFact;payment:A140PaymentFact;topup:A140TopupFact;refunds:A140RefundFact[];timeline:A140TimelineFact[];reconciliationRefs:string[];customerCaseRefs:string[] }
type Transport=(input:RequestInfo|URL,init?:RequestInit)=>Promise<Response>
const record=(v:unknown):v is Record<string,unknown>=>typeof v==='object'&&v!==null&&!Array.isArray(v)
const exact=(v:Record<string,unknown>,keys:readonly string[])=>{const a=Object.keys(v).sort(),e=[...keys].sort();return a.length===e.length&&a.every((key,i)=>key===e[i])}
const text=(v:unknown):v is string=>typeof v==='string'&&v.trim().length>0
const nullableText=(v:unknown):v is string|null=>v===null||text(v)
const instant=(v:unknown):v is string=>text(v)&&Number.isFinite(Date.parse(v))
const nullableInstant=(v:unknown):v is string|null=>v===null||instant(v)
const minor=(v:unknown):v is number=>Number.isSafeInteger(v)&&Number(v)>=0
const nullableMinor=(v:unknown):v is number|null=>v===null||minor(v)
const nullableNumber=(v:unknown):v is number|null=>v===null||(typeof v==='number'&&Number.isFinite(v)&&v>=0)
const nullableInteger=(v:unknown):v is number|null=>v===null||(Number.isSafeInteger(v)&&Number(v)>=0)
const nullableVersion=(v:unknown):v is number|null=>v===null||(Number.isSafeInteger(v)&&Number(v)>0)
const ROOT=['schemaVersion','projectionVersion','pageId','role','order','payment','topup','refunds','timeline','reconciliationRefs','customerCaseRefs'] as const
const ORDER=['orderRef','quoteRef','state','amountMinor','currency','phoneMasked','operatorCode','productRef','priceVersionRef','entitlement','createdAt','updatedAt'] as const
const ENTITLEMENT=['productRef','productType','displayName','benefitText','denominationBdt','dataAllowanceMb','voiceMinutes','smsCount','validityText'] as const
const PAYMENT=['state','providerRef','amountMinor','currency','refundedMinor','version','updatedAt'] as const
const TOPUP=['state','providerRef','version','updatedAt'] as const
const REFUND=['refundRef','state','amountMinor','currency','createdAt','updatedAt'] as const
const TIMELINE=['eventRef','eventType','source','occurredAt','projectedOrDispatchedAt'] as const
export function parseA140Detail(v:unknown):A140Detail|null{
 if(!record(v)||!exact(v,ROOT)||v.schemaVersion!=='ADMIN_READ_V1'||v.projectionVersion!=='A140-DETAIL-1'||v.pageId!=='A140'||!['FIN','CS','SUPER_ADMIN'].includes(String(v.role)))return null
 const o=v.order,p=v.payment,t=v.topup
 if(!record(o)||!exact(o,ORDER)||!['orderRef','quoteRef','state','currency','phoneMasked','operatorCode','productRef','priceVersionRef'].every(k=>text(o[k]))||!minor(o.amountMinor)||!instant(o.createdAt)||!nullableInstant(o.updatedAt))return null
 const entitlement=o.entitlement
 if(entitlement!==null&&(!record(entitlement)||!exact(entitlement,ENTITLEMENT)||!['productRef','productType','displayName','benefitText'].every(k=>text(entitlement[k]))||!nullableNumber(entitlement.denominationBdt)||!nullableInteger(entitlement.dataAllowanceMb)||!nullableInteger(entitlement.voiceMinutes)||!nullableInteger(entitlement.smsCount)||!nullableText(entitlement.validityText)))return null
 if(!record(p)||!exact(p,PAYMENT)||!text(p.state)||!nullableText(p.providerRef)||!nullableMinor(p.amountMinor)||!nullableText(p.currency)||!nullableMinor(p.refundedMinor)||!nullableVersion(p.version)||!nullableInstant(p.updatedAt))return null
 if(!record(t)||!exact(t,TOPUP)||!text(t.state)||!nullableText(t.providerRef)||!nullableVersion(t.version)||!nullableInstant(t.updatedAt))return null
 if(!Array.isArray(v.refunds)||!v.refunds.every(x=>record(x)&&exact(x,REFUND)&&text(x.refundRef)&&text(x.state)&&minor(x.amountMinor)&&text(x.currency)&&instant(x.createdAt)&&nullableInstant(x.updatedAt)))return null
 if(!Array.isArray(v.timeline)||!v.timeline.every(x=>record(x)&&exact(x,TIMELINE)&&text(x.eventRef)&&text(x.eventType)&&['PROJECTED','OUTBOX'].includes(String(x.source))&&instant(x.occurredAt)&&nullableInstant(x.projectedOrDispatchedAt)))return null
 if(!Array.isArray(v.reconciliationRefs)||!v.reconciliationRefs.every(text)||!Array.isArray(v.customerCaseRefs)||!v.customerCaseRefs.every(text))return null
 return v as unknown as A140Detail
}
export async function loadA140Detail(orderRef:string,transport:Transport=fetch):Promise<A140Detail>{
 const response=await transport(`/admin-read/v1/pages/A140/${encodeURIComponent(orderRef)}`,{method:'GET',credentials:'include',headers:{Accept:'application/json'}})
 if(response.status===401)throw new Error('登录已失效，请重新登录');if(response.status===403)throw new Error('当前角色不能查看订单详情');if(response.status===404)throw new Error('没有找到这笔订单');if(!response.ok)throw new Error('订单详情暂不可用，请稍后重试')
 let body:unknown;try{body=await response.json()}catch{throw new Error('订单详情暂不可用，请稍后重试')};const parsed=parseA140Detail(body);if(!parsed)throw new Error('订单详情暂不可用，请稍后重试');return parsed
}
