import type { CatalogItem, CatalogProjection, Money, OrderSummary, OrderSummaryState, ProjectSessionProjection, RechargeSelection, RecoveryResult } from '../domain/types'

const object = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null
const text = (value: unknown): value is string => typeof value === 'string' && value.length > 0
const integer = (value: unknown): value is number => typeof value === 'number' && Number.isInteger(value) && value > 0
const currency = (value: unknown): value is Money['currency'] => value === 'CNY' || value === 'BDT'
const itemKind = (value: unknown): value is CatalogItem['itemKind'] => value === 'PRESET_DENOMINATION' || value === 'PRESET_PACKAGE'
const productType = (value:unknown):value is NonNullable<CatalogItem['productType']> => value==='BALANCE'||value==='DATA'||value==='BUNDLE'
const orderStates:readonly OrderSummaryState[] = ['AWAITING_PAYMENT','PAYMENT_PROCESSING','PAID_AWAITING_TOPUP','TOPUP_PROCESSING','TOPUP_RESULT_UNKNOWN','DELIVERED','CONFIRMED_NOT_DELIVERED','REFUND_PROCESSING','REFUNDED','DELIVERY_REFUND_CONFLICT_REVIEW','SUPPORT_REVIEW']
const orderState = (value:unknown):value is OrderSummaryState => typeof value==='string'&&orderStates.includes(value as OrderSummaryState)
const exactKeys = (value:Record<string,unknown>,allowed:string[]):boolean => Object.keys(value).length===allowed.length&&Object.keys(value).every((key)=>allowed.includes(key))
const instant = (value:unknown):value is string => text(value)&&Number.isFinite(Date.parse(value))
const sameStringSet=(left:string[],right:string[]):boolean=>left.length===right.length&&new Set(left).size===left.length&&left.every((item)=>right.includes(item))

export function parseCatalogProjection(value: unknown): CatalogProjection {
  if(object(value)&&exactKeys(value,['supportedOperatorSetVersion','catalogVersion','operatorCode','operatorName','supportedOperators','items'])){
    if(!integer(value.supportedOperatorSetVersion)||!integer(value.catalogVersion)||!text(value.operatorCode)||!text(value.operatorName)||!Array.isArray(value.supportedOperators)||!Array.isArray(value.items))throw new Error('INVALID_CATALOG_DTO')
    const operatorCode=value.operatorCode
    const supported=value.supportedOperators.map(entry=>{if(!object(entry)||!exactKeys(entry,['operatorCode','displayName'])||!text(entry.operatorCode)||!text(entry.displayName))throw new Error('INVALID_SUPPORTED_OPERATOR_DTO');return{operatorCode:entry.operatorCode,displayName:entry.displayName}})
    const isSupported=supported.some(entry=>entry.operatorCode===operatorCode)
    if(!isSupported)return{operatorQualification:'UNSUPPORTED',supportedOperatorSetVersion:value.supportedOperatorSetVersion,catalogVersion:value.catalogVersion,operatorCode,items:[],evidenceSemantics:'LOCAL_DATABASE_STATE_NOT_EXTERNAL_OPERATOR_FACT'}
    const items:CatalogItem[]=value.items.map(entry=>{if(!object(entry)||!exactKeys(entry,['productRef','operatorCode','productType','displayName','benefitText','denominationBdt','validityText','finalAmountCny','priceVersionRef','priceValidUntil'])||entry.operatorCode!==operatorCode||!text(entry.productRef)||!productType(entry.productType)||!text(entry.displayName)||!text(entry.benefitText)||typeof entry.denominationBdt!=='number'||entry.denominationBdt<=0||(entry.validityText!==null&&!text(entry.validityText))||typeof entry.finalAmountCny!=='number'||entry.finalAmountCny<=0||!text(entry.priceVersionRef)||!instant(entry.priceValidUntil))throw new Error('INVALID_CATALOG_ITEM_DTO');return{operatorCode,productRef:entry.productRef,denominationRef:`BDT-${entry.denominationBdt}`,itemKind:entry.productType==='BALANCE'?'PRESET_DENOMINATION':'PRESET_PACKAGE',faceValue:{minor:Math.round(entry.denominationBdt*100),currency:'BDT'},displayName:entry.displayName,productType:entry.productType,benefitText:entry.benefitText,validityText:entry.validityText as string|null,finalAmountCny:entry.finalAmountCny,priceVersionRef:entry.priceVersionRef,available:true}})
    if(!items.length)throw new Error('EMPTY_SUPPORTED_CATALOG_DTO')
    return{operatorQualification:'SUPPORTED',supportedOperatorSetVersion:value.supportedOperatorSetVersion,catalogVersion:value.catalogVersion,operatorCode,items,evidenceSemantics:'LOCAL_DATABASE_STATE_NOT_EXTERNAL_OPERATOR_FACT'}
  }
  throw new Error('INVALID_CATALOG_DTO')
}

export function selectionMatchesCatalog(selection: RechargeSelection, catalog: CatalogProjection): boolean {
  if(catalog.operatorQualification!=='SUPPORTED'||catalog.operatorCode!==selection.operatorCode
      ||catalog.supportedOperatorSetVersion!==selection.supportedOperatorSetVersion||catalog.catalogVersion!==selection.catalogVersion)return false
  return catalog.items.some((item)=>item.productRef===selection.productRef&&item.denominationRef===selection.denominationRef
    &&item.itemKind===selection.itemKind&&item.faceValue.minor===selection.faceValue.minor&&item.faceValue.currency===selection.faceValue.currency
    &&item.productType===selection.productType&&item.priceVersionRef===selection.priceVersionRef)
}

export function parseRechargeSelection(value:unknown):RechargeSelection{
  if(!object(value)||!exactKeys(value,['recipientPhone','maskedPhone','operatorCode','operatorName','productRef','denominationRef','itemKind','faceValue','productType','displayName','benefitText','validityText','priceVersionRef','supportedOperatorSetVersion','catalogVersion'])
    ||!text(value.recipientPhone)||!/^\+8801[3-9][0-9]{8}$/.test(value.recipientPhone)||!text(value.maskedPhone)||!text(value.operatorCode)||!text(value.operatorName)||!text(value.productRef)||!text(value.denominationRef)
    ||!itemKind(value.itemKind)||!object(value.faceValue)||!exactKeys(value.faceValue,['minor','currency'])||!integer(value.faceValue.minor)
    ||!currency(value.faceValue.currency)||!productType(value.productType)||!text(value.displayName)||!text(value.benefitText)
    ||(value.validityText!==null&&!text(value.validityText))||!text(value.priceVersionRef)
    ||!integer(value.supportedOperatorSetVersion)||!integer(value.catalogVersion))throw new Error('INVALID_RECHARGE_SELECTION')
  return{recipientPhone:value.recipientPhone,maskedPhone:value.maskedPhone,operatorCode:value.operatorCode,operatorName:value.operatorName,productRef:value.productRef,
    denominationRef:value.denominationRef,itemKind:value.itemKind,faceValue:{minor:value.faceValue.minor,currency:value.faceValue.currency},
    productType:value.productType,displayName:value.displayName,benefitText:value.benefitText,validityText:value.validityText as string|null,priceVersionRef:value.priceVersionRef,
    supportedOperatorSetVersion:value.supportedOperatorSetVersion,catalogVersion:value.catalogVersion}
}

function parseOrderSummary(value:unknown):OrderSummary{
  if(!object(value)||!exactKeys(value,['orderRef','stateCode','projectionVersion','generatedAt'])
    ||!text(value.orderRef)||!orderState(value.stateCode)||!integer(value.projectionVersion)||!instant(value.generatedAt))throw new Error('INVALID_ORDER_SUMMARY_DTO')
  return{orderRef:value.orderRef,stateCode:value.stateCode,projectionVersion:value.projectionVersion,updatedAt:value.generatedAt}
}

export function parseAuthorizedOrders(value:unknown,session:ProjectSessionProjection,now=Date.now()):OrderSummary[]{
  if(session.role!=='BUYER'||!text(session.projectSubjectRef)||!integer(session.sessionVersion)||!text(session.authorizationSetRef)
    ||!text(session.authorizationEvidenceVersion)||!instant(session.issuedAt)||!instant(session.expiresAt)
    ||Date.parse(session.issuedAt)>=Date.parse(session.expiresAt)||Date.parse(session.expiresAt)<=now
    ||session.authorizedOrderRefs.length===0||new Set(session.authorizedOrderRefs).size!==session.authorizedOrderRefs.length)throw new Error('INVALID_EXPECTED_SESSION_PROJECTION')
  if(!object(value)||!exactKeys(value,['projectSubjectRef','sessionRole','sessionVersion','authorizationSetRef','authorizationEvidenceVersion','authorizedOrderRefs','issuedAt','expiresAt','orders'])
    ||value.projectSubjectRef!==session.projectSubjectRef||value.sessionRole!=='BUYER'||value.sessionVersion!==session.sessionVersion
    ||value.authorizationSetRef!==session.authorizationSetRef||value.authorizationEvidenceVersion!==session.authorizationEvidenceVersion
    ||value.issuedAt!==session.issuedAt||value.expiresAt!==session.expiresAt||!Array.isArray(value.authorizedOrderRefs)
    ||!value.authorizedOrderRefs.every(text)||!sameStringSet(value.authorizedOrderRefs,session.authorizedOrderRefs)
    ||!Array.isArray(value.orders))throw new Error('AUTHORIZED_ORDER_BINDING_MISMATCH')
  const items=value.orders.map(parseOrderSummary)
  const refs=new Set(items.map((item)=>item.orderRef))
  if(refs.size!==items.length)throw new Error('DUPLICATE_AUTHORIZED_ORDER_DTO')
  const authorized=new Set(session.authorizedOrderRefs)
  if(items.some((item)=>!authorized.has(item.orderRef)))throw new Error('ORDER_OUTSIDE_AUTHORIZATION_SET')
  return items
}

type RecoveryAuthorization=Extract<RecoveryResult,{outcome:'RECOVERED'}>['authorization']
function parseAuthorization(value:unknown,now:number):RecoveryAuthorization{
  if(!object(value)||!exactKeys(value,['projectSubjectRef','sessionRole','sessionVersion','authorizationSetRef','authorizationEvidenceVersion','authorizedOrderRefs','issuedAt','expiresAt'])
    ||!text(value.projectSubjectRef)||value.sessionRole!=='BUYER'||!integer(value.sessionVersion)||!text(value.authorizationSetRef)
    ||!text(value.authorizationEvidenceVersion)||!Array.isArray(value.authorizedOrderRefs)||value.authorizedOrderRefs.length===0
    ||!value.authorizedOrderRefs.every(text)||new Set(value.authorizedOrderRefs).size!==value.authorizedOrderRefs.length
    ||!instant(value.issuedAt)||!instant(value.expiresAt)||Date.parse(value.issuedAt)>=Date.parse(value.expiresAt)||Date.parse(value.expiresAt)<=now)throw new Error('INVALID_RECOVERY_AUTHORIZATION_DTO')
  return{projectSubjectRef:value.projectSubjectRef,sessionRole:'BUYER',sessionVersion:value.sessionVersion,authorizationSetRef:value.authorizationSetRef,
    authorizationEvidenceVersion:value.authorizationEvidenceVersion,authorizedOrderRefs:[...value.authorizedOrderRefs],issuedAt:value.issuedAt,expiresAt:value.expiresAt}
}

export function parseRecoveryResult(value:unknown,now=Date.now()):RecoveryResult{
  if(!object(value)||!text(value.outcome)||!text(value.recoveryCaseRef))throw new Error('INVALID_RECOVERY_DTO')
  if(value.outcome==='RECOVERED'){
    if(!exactKeys(value,['recoveryCaseRef','outcome','retryClass','authorization'])||value.retryClass!=='NONE')throw new Error('INVALID_RECOVERY_SUCCESS_DTO')
    return{outcome:'RECOVERED',recoveryCaseRef:value.recoveryCaseRef,retryClass:'NONE',safeQueryPath:null,authorization:parseAuthorization(value.authorization,now)}
  }
  if(value.outcome==='REJECTED'){
    if(!exactKeys(value,['recoveryCaseRef','outcome','retryClass'])||value.retryClass!=='NONE')throw new Error('INVALID_RECOVERY_REJECTED_DTO')
    return{outcome:'REJECTED',recoveryCaseRef:value.recoveryCaseRef,retryClass:'NONE',safeQueryPath:null,authorization:null}
  }
  if(value.outcome==='UNKNOWN'){
    const path=`/api/v1/recovery-cases/${value.recoveryCaseRef}`
    if(!exactKeys(value,['recoveryCaseRef','outcome','retryClass','safeQueryPath'])||value.retryClass!=='READ_SAFE'||value.safeQueryPath!==path)throw new Error('INVALID_RECOVERY_UNKNOWN_DTO')
    return{outcome:'UNKNOWN',recoveryCaseRef:value.recoveryCaseRef,retryClass:'READ_SAFE',safeQueryPath:path,authorization:null}
  }
  throw new Error('INVALID_RECOVERY_OUTCOME_DTO')
}
