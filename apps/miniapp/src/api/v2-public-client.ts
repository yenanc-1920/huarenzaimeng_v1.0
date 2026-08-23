import type {
  ContentErrorReportResult, DirectoryCity, DirectoryItem, DirectorySummary,
  LifeContentDetailResult, LifeContentListResult,
} from '../domain/types'
import type { TemporalOverviewReadResponse } from './temporal-overview-contract'
import { parseAcceptedProjectEnvelope } from './project-envelope'

const baseUrl=(import.meta.env.VITE_API_BASE_URL||'/api/v1').replace(/\/$/,'')
const record=(value:unknown):value is Record<string,unknown>=>!!value&&typeof value==='object'&&!Array.isArray(value)
const text=(value:unknown):value is string=>typeof value==='string'&&value.trim().length>0

function request(path:string,method:'GET'|'POST'='GET',data?:UniNamespace.RequestOptions['data'],headers:Record<string,string>={}):Promise<{body:unknown;statusCode:number;headers:unknown}>{
  return new Promise((resolve,reject)=>uni.request({
    url:`${baseUrl}${path}`,method,data,header:headers,
    success:({data:body,statusCode,header})=>statusCode>=200&&statusCode<300?resolve({body,statusCode,headers:header}):reject(new Error('HTTP_STATUS_REJECTED')),
    fail:()=>reject(new Error('NETWORK_ERROR')),
  }))
}

async function data(path:string,method:'GET'|'POST'='GET',body?:UniNamespace.RequestOptions['data'],headers?:Record<string,string>){
  return parseAcceptedProjectEnvelope((await request(path,method,body,headers)).body)
}

function directoryRows(value:unknown):DirectorySummary[]{
  if(!Array.isArray(value))throw new Error('INVALID_DIRECTORY_ENTRIES_DTO')
  return value.map(row=>{
    if(!record(row)||!text(row.entryRef)||!text(row.cityCode)||!text(row.cityName)||!text(row.category)||!text(row.displayName)
      ||!text(row.summary)||!text(row.localAddress)||!text(row.phone)||!text(row.verifiedAt)||!text(row.validUntil)||!text(row.updatedAt))throw new Error('INVALID_DIRECTORY_ENTRY_DTO')
    return row as unknown as DirectorySummary
  })
}

function requestKey(){
  const uuid=globalThis.crypto?.randomUUID?.()
  if(!uuid)throw new Error('SECURE_REQUEST_ID_UNAVAILABLE')
  return uuid
}

export const v2PublicApi={
  async getDirectoryCities():Promise<DirectoryCity[]>{
    const value=await data('/directory/cities')
    if(!Array.isArray(value))throw new Error('INVALID_DIRECTORY_CITIES_DTO')
    return value.map(row=>{
      if(!record(row)||!text(row.cityCode)||!text(row.countryCode)||!text(row.displayName)||!text(row.localName)||!text(row.timezoneId))throw new Error('INVALID_DIRECTORY_CITY_DTO')
      return row as unknown as DirectoryCity
    })
  },
  async getDirectory(cityCode?:string,category?:string):Promise<DirectorySummary[]>{
    const query:string[]=[]
    if(cityCode)query.push(`cityCode=${encodeURIComponent(cityCode)}`)
    if(category)query.push(`category=${encodeURIComponent(category)}`)
    return directoryRows(await data(`/directory/entries${query.length?`?${query.join('&')}`:''}`))
  },
  async getDirectoryDetail(entryRef:string):Promise<DirectoryItem>{
    if(!entryRef)throw new Error('DIRECTORY_ENTRY_REF_REQUIRED')
    const value=await data(`/directory/entries/${encodeURIComponent(entryRef)}`)
    const item=directoryRows([value])[0]
    return record(value)&&typeof value.sourceLabel==='string'?{...item,sourceLabel:value.sourceLabel}:item
  },
  async reportDirectoryError(entryRef:string,reasonCode:string,description:string):Promise<ContentErrorReportResult>{
    if(!entryRef)throw new Error('DIRECTORY_ENTRY_REF_REQUIRED')
    const value=await data(`/directory/entries/${encodeURIComponent(entryRef)}/reports`,'POST',{reasonCode,description},{'Idempotency-Key':requestKey()})
    if(!record(value)||!text(value.reportRef)||value.state!=='OPEN')throw new Error('INVALID_DIRECTORY_REPORT_DTO')
    return{outcome:'CONTENT_ERROR_REPORTED',supportRef:value.reportRef,reviewTarget:'A120'}
  },
  async getLifeContentList():Promise<LifeContentListResult>{
    const value=await data('/content/life-items')
    if(!Array.isArray(value))throw new Error('INVALID_LIFE_CONTENT_LIST_DTO')
    const items=value.map(row=>{
      if(!record(row)||!text(row.contentRef)||!text(row.category)||!text(row.title)||!text(row.summary)||!text(row.publishedAt)||!text(row.updatedAt)||!text(row.validUntil))throw new Error('INVALID_LIFE_CONTENT_ITEM_DTO')
      return{contentRef:row.contentRef,contentVersion:text(row.contentVersion)?row.contentVersion:null,category:row.category as 'LIFE_REMINDER'|'HOLIDAY_EXPLANATION',title:row.title,summary:row.summary,sourceType:text(row.sourceType)?row.sourceType:null,jurisdiction:text(row.jurisdiction)?row.jurisdiction:null,applicableAudience:text(row.applicableAudience)?row.applicableAudience:null,publishedAt:row.publishedAt,updatedAt:row.updatedAt,effectiveFrom:row.publishedAt,effectiveTo:row.validUntil,freshnessState:'CURRENT' as const,coverState:'NOT_CONFIGURED' as const,coverRef:null}
    })
    return{requestRef:null,viewState:items.length?'READY':'EMPTY',projectCode:items.length?'LIFE_CONTENT_LIST_READY':'LIFE_CONTENT_LIST_EMPTY',schemaVersion:'LIFE_CONTENT_READ_V1',visibilityRuleVersion:null,items,retryClass:'NONE',nextReadAt:null}
  },
  async getLifeContentDetail(contentRef:string,contentVersion:string|null):Promise<LifeContentDetailResult>{
    if(!contentRef)throw new Error('LIFE_CONTENT_READ_KEY_REQUIRED')
    const query=contentVersion?`?contentVersion=${encodeURIComponent(contentVersion)}`:''
    const row=await data(`/content/life-items/${encodeURIComponent(contentRef)}${query}`)
    if(!record(row)||row.contentRef!==contentRef||!text(row.category)||!text(row.title)||!text(row.summary)||!text(row.bodyText)||!text(row.publishedAt)||!text(row.updatedAt)||!text(row.validUntil))throw new Error('INVALID_LIFE_CONTENT_DETAIL_DTO')
    const item={contentRef,contentVersion:text(row.contentVersion)?row.contentVersion:null,category:row.category as 'LIFE_REMINDER'|'HOLIDAY_EXPLANATION',title:row.title,summary:row.summary,body:row.bodyText,sourceType:text(row.sourceType)?row.sourceType:text(row.sourceLabel)?row.sourceLabel:null,jurisdiction:text(row.jurisdiction)?row.jurisdiction:null,applicableAudience:text(row.applicableAudience)?row.applicableAudience:null,publishedAt:row.publishedAt,updatedAt:row.updatedAt,effectiveFrom:row.publishedAt,effectiveTo:row.validUntil,freshnessState:'CURRENT' as const,coverState:'NOT_CONFIGURED' as const,coverRef:null}
    return{requestRef:null,viewState:'READY',projectCode:'LIFE_CONTENT_DETAIL_READY',schemaVersion:'LIFE_CONTENT_READ_V1',visibilityRuleVersion:null,contentRef,contentVersion:item.contentVersion,item,retryClass:'NONE',nextReadAt:null}
  },
  async getTemporalOverview():Promise<TemporalOverviewReadResponse>{
    const response=await request('/home/temporal-overview')
    const value=parseAcceptedProjectEnvelope(response.body)
    if(!record(value)||!text(value.serverTime)||!record(value.dhaka)||!record(value.beijing))throw new Error('INVALID_TEMPORAL_OVERVIEW_DTO')
    const clock=(source:Record<string,unknown>,cityCode:'DHAKA'|'BEIJING',displayName:string,zoneId:'Asia/Dhaka'|'Asia/Shanghai')=>{if(!text(source.date)||!text(source.localTime))throw new Error('INVALID_TEMPORAL_CLOCK_DTO');return{cityCode,displayName,zoneId,localDate:source.date,localTime:source.localTime.slice(0,5),availabilityState:'AVAILABLE' as const}}
    const holiday=(source:Record<string,unknown>,countryCode:'CN'|'BD')=>{if(!text(source.date))throw new Error('INVALID_TEMPORAL_HOLIDAY_DTO');const ready=['WORK_DAY','REST_DAY','HOLIDAY'].includes(String(source.dayType))&&text(source.ruleRef)&&text(source.sourceLabel)&&text(source.ruleVersion)&&text(source.effectiveFrom)&&text(source.effectiveUntil);if(!ready)return{countryCode,localDate:source.date,state:'READ_ERROR' as const,holidayId:null,name:null,note:null,sourceType:null,sourceCoverageDate:null,effectiveFrom:null,effectiveTo:null,version:null};const confirmed=source.dayType!=='WORK_DAY';return{countryCode,localDate:source.date,state:confirmed?'CONFIRMED_HOLIDAY' as const:'NO_HOLIDAY_CONFIRMED' as const,holidayId:confirmed?source.ruleRef:null,name:confirmed?(source.dayType==='REST_DAY'?'休息日':text(source.holidayName)?source.holidayName:'节假日'):null,note:null,sourceType:source.sourceLabel,sourceCoverageDate:source.date,effectiveFrom:source.effectiveFrom,effectiveTo:source.effectiveUntil,version:source.ruleVersion}}
    const china=holiday(value.beijing,'CN'),bangladesh=holiday(value.dhaka,'BD'),ready=china.state!=='READ_ERROR'&&bangladesh.state!=='READ_ERROR'
    const cacheControl=record(response.headers)?Object.entries(response.headers).find(([key])=>key.toLowerCase()==='cache-control')?.[1]:null
    return{body:{requestRef:'TEMPORAL-OVERVIEW',projectCode:ready?'TEMPORAL_OVERVIEW_READY':'TEMPORAL_OVERVIEW_PARTIAL',schemaVersion:'TEMPORAL_OVERVIEW_V1',referenceInstant:value.serverTime,generatedAt:value.serverTime,timeZoneRuleVersion:'IANA',clockStaleAfterSeconds:300,clockState:'BOTH_AVAILABLE',clocks:{dhaka:clock(value.dhaka,'DHAKA','达卡','Asia/Dhaka'),beijing:clock(value.beijing,'BEIJING','北京','Asia/Shanghai')},holidayRuleVersion:ready?`${china.version}:${bangladesh.version}`:'UNAVAILABLE',holidays:{china,bangladesh},retryClass:ready?'NONE':'USER_INITIATED_READ_ONLY'},statusCode:response.statusCode,cacheControl:typeof cacheControl==='string'?cacheControl:null}
  },
}
