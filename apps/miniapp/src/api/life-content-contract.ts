import type {
  LifeContentCategory,
  LifeContentCoverState,
  LifeContentDetailItem,
  LifeContentDetailResult,
  LifeContentDetailState,
  LifeContentListResult,
  LifeContentReadState,
  LifeContentRetryClass,
  LifeContentSummary,
} from '../domain/types'

const LIST_KEYS = ['requestRef','viewState','projectCode','schemaVersion','visibilityRuleVersion','items','retryClass','nextReadAt']
const DETAIL_KEYS = ['requestRef','viewState','projectCode','schemaVersion','visibilityRuleVersion','contentRef','contentVersion','item','retryClass','nextReadAt']
const SUMMARY_KEYS = ['contentRef','contentVersion','category','title','summary','sourceType','jurisdiction','applicableAudience','publishedAt','updatedAt','effectiveFrom','effectiveTo','freshnessState','coverState','coverRef']
const DETAIL_ITEM_KEYS = [...SUMMARY_KEYS,'body']

const LIST_PROJECT_CODES:Record<LifeContentReadState,string> = {
  READY:'LIFE_CONTENT_LIST_READY', EMPTY:'LIFE_CONTENT_LIST_EMPTY', UNDER_REVIEW:'LIFE_CONTENT_LIST_UNDER_REVIEW',
  EXPIRED:'LIFE_CONTENT_LIST_EXPIRED', REMOVED:'LIFE_CONTENT_LIST_REMOVED', UNKNOWN:'LIFE_CONTENT_LIST_UNKNOWN', ERROR:'LIFE_CONTENT_LIST_ERROR',
}
const DETAIL_PROJECT_CODES:Record<LifeContentDetailState,string> = {
  READY:'LIFE_CONTENT_DETAIL_READY', UNDER_REVIEW:'LIFE_CONTENT_DETAIL_UNDER_REVIEW', EXPIRED:'LIFE_CONTENT_DETAIL_EXPIRED',
  REMOVED:'LIFE_CONTENT_DETAIL_REMOVED', UNKNOWN:'LIFE_CONTENT_DETAIL_UNKNOWN', ERROR:'LIFE_CONTENT_DETAIL_ERROR',
}

const object = (value:unknown):value is Record<string,unknown> => typeof value === 'object' && value !== null && !Array.isArray(value)
const text = (value:unknown):value is string => typeof value === 'string' && value.length > 0
const exactKeys = (value:Record<string,unknown>,keys:string[]):boolean =>
  Object.keys(value).length === keys.length && Object.keys(value).every((key) => keys.includes(key))
const rfc3339 = (value:unknown):value is string => typeof value === 'string'
  && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/.test(value)
  && Number.isFinite(Date.parse(value))
const readState = (value:unknown):value is LifeContentReadState =>
  value === 'READY' || value === 'EMPTY' || value === 'UNDER_REVIEW' || value === 'EXPIRED'
  || value === 'REMOVED' || value === 'UNKNOWN' || value === 'ERROR'
const detailState = (value:unknown):value is LifeContentDetailState => readState(value) && value !== 'EMPTY'
const category = (value:unknown):value is LifeContentCategory => value === 'LIFE_REMINDER' || value === 'HOLIDAY_EXPLANATION'
const coverState = (value:unknown):value is LifeContentCoverState =>
  value === 'AVAILABLE' || value === 'NOT_CONFIGURED' || value === 'IMAGE_UNAVAILABLE'
const retryClass = (value:unknown):value is LifeContentRetryClass => value === 'NONE' || value === 'USER_INITIATED_READ_ONLY'

function assertRetry(viewState:LifeContentReadState,retry:LifeContentRetryClass,nextReadAt:unknown):void {
  if (nextReadAt !== null || (viewState !== 'UNKNOWN' && viewState !== 'ERROR' && retry !== 'NONE')) {
    throw new Error('INVALID_LIFE_CONTENT_RETRY_DTO')
  }
}

function parseSummary(value:unknown,detail:boolean):LifeContentSummary | LifeContentDetailItem {
  const keys = detail ? DETAIL_ITEM_KEYS : SUMMARY_KEYS
  if (!object(value) || !exactKeys(value,keys) || !text(value.contentRef) || !text(value.contentVersion)
      || !category(value.category) || !text(value.title) || !text(value.summary) || !text(value.sourceType)
      || !text(value.jurisdiction) || !text(value.applicableAudience) || !rfc3339(value.publishedAt)
      || !rfc3339(value.updatedAt) || !rfc3339(value.effectiveFrom) || !rfc3339(value.effectiveTo)
      || value.freshnessState !== 'CURRENT' || !coverState(value.coverState)
      || (value.coverRef !== null && !text(value.coverRef))
      || (value.coverState === 'IMAGE_UNAVAILABLE' && value.coverRef !== null)
      || (detail && !text(value.body))) throw new Error('INVALID_LIFE_CONTENT_ITEM_DTO')
  const summary:LifeContentSummary = {
    contentRef:value.contentRef, contentVersion:value.contentVersion, category:value.category, title:value.title,
    summary:value.summary, sourceType:value.sourceType, jurisdiction:value.jurisdiction, applicableAudience:value.applicableAudience,
    publishedAt:value.publishedAt, updatedAt:value.updatedAt, effectiveFrom:value.effectiveFrom, effectiveTo:value.effectiveTo,
    freshnessState:'CURRENT', coverState:value.coverState, coverRef:value.coverRef,
  }
  return detail ? {...summary,body:value.body as string} : summary
}

export function parseLifeContentListResponse(value:unknown):LifeContentListResult {
  if (!object(value) || !exactKeys(value,LIST_KEYS) || !text(value.requestRef) || !readState(value.viewState)
      || value.projectCode !== LIST_PROJECT_CODES[value.viewState] || value.schemaVersion !== 'LIFE_CONTENT_READ_V1'
      || !text(value.visibilityRuleVersion) || !Array.isArray(value.items) || !retryClass(value.retryClass)) {
    throw new Error('INVALID_LIFE_CONTENT_LIST_DTO')
  }
  assertRetry(value.viewState,value.retryClass,value.nextReadAt)
  if ((value.viewState === 'READY' && value.items.length === 0) || (value.viewState !== 'READY' && value.items.length !== 0)) {
    throw new Error('INVALID_LIFE_CONTENT_LIST_STATE_DTO')
  }
  const items = value.items.map((item) => parseSummary(item,false) as LifeContentSummary)
  if (new Set(items.map((item) => `${item.contentRef}\u0000${item.contentVersion}`)).size !== items.length) {
    throw new Error('DUPLICATE_LIFE_CONTENT_ITEM_DTO')
  }
  return {requestRef:value.requestRef,viewState:value.viewState,projectCode:value.projectCode,schemaVersion:'LIFE_CONTENT_READ_V1',
    visibilityRuleVersion:value.visibilityRuleVersion,items,retryClass:value.retryClass,nextReadAt:null}
}

export function parseLifeContentDetailResponse(value:unknown,contentRef:string,contentVersion:string):LifeContentDetailResult {
  if (!text(contentRef) || !text(contentVersion)) throw new Error('INVALID_LIFE_CONTENT_READ_KEY')
  if (!object(value) || !exactKeys(value,DETAIL_KEYS) || !text(value.requestRef) || !detailState(value.viewState)
      || value.projectCode !== DETAIL_PROJECT_CODES[value.viewState] || value.schemaVersion !== 'LIFE_CONTENT_READ_V1'
      || !text(value.visibilityRuleVersion) || value.contentRef !== contentRef || value.contentVersion !== contentVersion
      || !retryClass(value.retryClass)) throw new Error('INVALID_LIFE_CONTENT_DETAIL_DTO')
  assertRetry(value.viewState,value.retryClass,value.nextReadAt)
  if (value.viewState !== 'READY') {
    if (value.item !== null) throw new Error('LIFE_CONTENT_NON_READY_ITEM_MUST_BE_NULL')
    return {requestRef:value.requestRef,viewState:value.viewState,projectCode:value.projectCode,schemaVersion:'LIFE_CONTENT_READ_V1',
      visibilityRuleVersion:value.visibilityRuleVersion,contentRef,contentVersion,item:null,retryClass:value.retryClass,nextReadAt:null}
  }
  const item = parseSummary(value.item,true) as LifeContentDetailItem
  if (item.contentRef !== contentRef || item.contentVersion !== contentVersion) throw new Error('LIFE_CONTENT_DETAIL_ITEM_BINDING_MISMATCH')
  return {requestRef:value.requestRef,viewState:'READY',projectCode:value.projectCode,schemaVersion:'LIFE_CONTENT_READ_V1',
    visibilityRuleVersion:value.visibilityRuleVersion,contentRef,contentVersion,item,retryClass:value.retryClass,nextReadAt:null}
}

export function canRetryLifeContentRead(result:{viewState:LifeContentReadState;retryClass:LifeContentRetryClass}):boolean {
  return (result.viewState === 'UNKNOWN' || result.viewState === 'ERROR') && result.retryClass === 'USER_INITIATED_READ_ONLY'
}
