import type { ClockAuthorityState, ClockViewState, HolidayState, TemporalClock, TemporalHoliday, TemporalOverview } from '../api/temporal-overview-contract'

export type TemporalReadTrigger='FIRST_SHOW'|'FOREGROUND_SHOW'|'USER_REFRESH'
export interface TemporalOverviewPageState {
  viewState:ClockViewState
  overview:TemporalOverview|null
  readGeneration:number
  lastAuthorityState:ClockAuthorityState|null
  lastReferenceInstant:string|null
}

const DEGRADED_STATES:ClockAuthorityState[]=['DHAKA_UNAVAILABLE','BEIJING_UNAVAILABLE','BOTH_UNAVAILABLE','STALE']
const CLOCK_STATES:ClockAuthorityState[]=['BOTH_AVAILABLE','DHAKA_UNAVAILABLE','BEIJING_UNAVAILABLE','BOTH_UNAVAILABLE','STALE']
const HOLIDAY_STATES:HolidayState[]=['NO_HOLIDAY_CONFIRMED','CONFIRMED_HOLIDAY','PENDING_CONFIRMATION','READ_ERROR','STALE_OR_EXPIRED','UNPUBLISHED']
const RETRY_CLASSES=['NONE','USER_INITIATED_READ_ONLY'] as const
const ROOT_KEYS=['requestRef','projectCode','schemaVersion','referenceInstant','generatedAt','timeZoneRuleVersion','clockStaleAfterSeconds','clockState','clocks','holidayRuleVersion','holidays','retryClass']
const CLOCK_KEYS=['cityCode','displayName','zoneId','localDate','localTime','availabilityState']
const HOLIDAY_KEYS=['countryCode','localDate','state','holidayId','name','note','sourceType','sourceCoverageDate','effectiveFrom','effectiveTo','version']

const isRecord=(value:unknown):value is Record<string,unknown>=>Boolean(value)&&typeof value==='object'&&!Array.isArray(value)
const exactKeys=(value:Record<string,unknown>,keys:string[])=>Object.keys(value).length===keys.length&&keys.every((key)=>Object.hasOwn(value,key))
const nonEmpty=(value:unknown):value is string=>typeof value==='string'&&value.trim().length>0
const nullableString=(value:unknown)=>value===null||nonEmpty(value)
const isRfc3339=(value:unknown):value is string=>nonEmpty(value)&&/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/.test(value)&&Number.isFinite(Date.parse(value))
const isDate=(value:unknown):value is string=>typeof value==='string'&&/^\d{4}-\d{2}-\d{2}$/.test(value)
const isTime=(value:unknown):value is string=>typeof value==='string'&&/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(value)

function parseClock(value:unknown,cityCode:TemporalClock['cityCode'],zoneId:TemporalClock['zoneId']):TemporalClock {
  if(!isRecord(value)||!exactKeys(value,CLOCK_KEYS)||value.cityCode!==cityCode||value.zoneId!==zoneId||!nonEmpty(value.displayName)
    ||!['AVAILABLE','UNAVAILABLE'].includes(String(value.availabilityState)))throw new Error('INVALID_TEMPORAL_CLOCK_DTO')
  const available=value.availabilityState==='AVAILABLE'
  if(available?(!isDate(value.localDate)||!isTime(value.localTime)):(value.localDate!==null||value.localTime!==null))throw new Error('INVALID_TEMPORAL_CLOCK_AVAILABILITY_DTO')
  return value as unknown as TemporalClock
}

function parseHoliday(value:unknown,countryCode:TemporalHoliday['countryCode'],referenceInstant:string):TemporalHoliday {
  if(!isRecord(value)||!exactKeys(value,HOLIDAY_KEYS)||value.countryCode!==countryCode||!isDate(value.localDate)
    ||!HOLIDAY_STATES.includes(value.state as HolidayState)||!nullableString(value.holidayId)||!nullableString(value.name)||!nullableString(value.note)
    ||!nullableString(value.sourceType)||!nullableString(value.sourceCoverageDate)||!nullableString(value.effectiveFrom)
    ||!nullableString(value.effectiveTo)||!nullableString(value.version))throw new Error('INVALID_TEMPORAL_HOLIDAY_DTO')
  if(value.sourceCoverageDate!==null&&!isDate(value.sourceCoverageDate))throw new Error('INVALID_TEMPORAL_HOLIDAY_COVERAGE_DTO')
  if(value.effectiveFrom!==null&&!isRfc3339(value.effectiveFrom))throw new Error('INVALID_TEMPORAL_HOLIDAY_PERIOD_DTO')
  if(value.effectiveTo!==null&&!isRfc3339(value.effectiveTo))throw new Error('INVALID_TEMPORAL_HOLIDAY_PERIOD_DTO')
  if(value.effectiveFrom!==null&&value.effectiveTo!==null&&Date.parse(value.effectiveFrom)>Date.parse(value.effectiveTo))throw new Error('INVALID_TEMPORAL_HOLIDAY_PERIOD_ORDER')
  const determinate=['NO_HOLIDAY_CONFIRMED','CONFIRMED_HOLIDAY','PENDING_CONFIRMATION'].includes(String(value.state))
  if(determinate&&(!nonEmpty(value.sourceType)||!isDate(value.sourceCoverageDate)||!nonEmpty(value.version)
    ||!isRfc3339(value.effectiveFrom)||!isRfc3339(value.effectiveTo)
    ||Date.parse(value.effectiveFrom)>Date.parse(referenceInstant)||Date.parse(referenceInstant)>Date.parse(value.effectiveTo)))throw new Error('INVALID_TEMPORAL_HOLIDAY_SOURCE_DTO')
  if(value.state==='NO_HOLIDAY_CONFIRMED'&&(value.holidayId!==null||value.name!==null||value.note!==null))throw new Error('INVALID_TEMPORAL_NO_HOLIDAY_DTO')
  if(value.state==='CONFIRMED_HOLIDAY'&&(!nonEmpty(value.holidayId)||!nonEmpty(value.name)))throw new Error('INVALID_TEMPORAL_CONFIRMED_HOLIDAY_DTO')
  if(value.state==='PENDING_CONFIRMATION'&&(!nonEmpty(value.name)||!nonEmpty(value.note)||!/(?:待官方确认|预计)/.test(value.note)))throw new Error('INVALID_TEMPORAL_PENDING_HOLIDAY_DTO')
  if(!determinate&&(value.holidayId!==null||value.name!==null||value.note!==null))throw new Error('INVALID_TEMPORAL_NON_CURRENT_HOLIDAY_DTO')
  if(value.state==='READ_ERROR'&&[value.sourceType,value.sourceCoverageDate,value.effectiveFrom,value.effectiveTo,value.version].some((field)=>field!==null))throw new Error('INVALID_TEMPORAL_READ_ERROR_METADATA_DTO')
  if(['STALE_OR_EXPIRED','UNPUBLISHED'].includes(String(value.state))&&(!nonEmpty(value.sourceType)||!isDate(value.sourceCoverageDate)
    ||!isRfc3339(value.effectiveFrom)||!isRfc3339(value.effectiveTo)||!nonEmpty(value.version)))throw new Error('INVALID_TEMPORAL_NON_CURRENT_METADATA_DTO')
  return value as unknown as TemporalHoliday
}

export function parseTemporalOverview(value:unknown):TemporalOverview {
  if(!isRecord(value)||!exactKeys(value,ROOT_KEYS)||!nonEmpty(value.requestRef)||!nonEmpty(value.projectCode)
    ||value.schemaVersion!=='TEMPORAL_OVERVIEW_V1'||!isRfc3339(value.referenceInstant)||!isRfc3339(value.generatedAt)
    ||Date.parse(value.generatedAt)<Date.parse(value.referenceInstant)||!nonEmpty(value.timeZoneRuleVersion)
    ||!Number.isSafeInteger(value.clockStaleAfterSeconds)||Number(value.clockStaleAfterSeconds)<=0
    ||!CLOCK_STATES.includes(value.clockState as ClockAuthorityState)||!nonEmpty(value.holidayRuleVersion)
    ||!RETRY_CLASSES.includes(value.retryClass as typeof RETRY_CLASSES[number]))throw new Error('INVALID_TEMPORAL_OVERVIEW_DTO')
  if(!isRecord(value.clocks)||!exactKeys(value.clocks,['dhaka','beijing'])||!isRecord(value.holidays)||!exactKeys(value.holidays,['china','bangladesh']))throw new Error('INVALID_TEMPORAL_OVERVIEW_COLLECTION_DTO')
  const referenceInstant=value.referenceInstant as string
  const generatedAt=value.generatedAt as string
  const dhaka=parseClock(value.clocks.dhaka,'DHAKA','Asia/Dhaka')
  const beijing=parseClock(value.clocks.beijing,'BEIJING','Asia/Shanghai')
  const availability=`${dhaka.availabilityState}:${beijing.availabilityState}`
  const expectedByState:Partial<Record<ClockAuthorityState,string>>={BOTH_AVAILABLE:'AVAILABLE:AVAILABLE',DHAKA_UNAVAILABLE:'UNAVAILABLE:AVAILABLE',BEIJING_UNAVAILABLE:'AVAILABLE:UNAVAILABLE',BOTH_UNAVAILABLE:'UNAVAILABLE:UNAVAILABLE'}
  if(expectedByState[value.clockState as ClockAuthorityState]&&expectedByState[value.clockState as ClockAuthorityState]!==availability)throw new Error('INVALID_TEMPORAL_CLOCK_STATE_DTO')
  const staleByAge=Date.parse(generatedAt)-Date.parse(referenceInstant)>Number(value.clockStaleAfterSeconds)*1000
  if((staleByAge&&value.clockState!=='STALE')||(value.clockState==='STALE'&&availability!=='UNAVAILABLE:UNAVAILABLE'))throw new Error('INVALID_TEMPORAL_CLOCK_STALENESS_DTO')
  const china=parseHoliday(value.holidays.china,'CN',referenceInstant)
  const bangladesh=parseHoliday(value.holidays.bangladesh,'BD',referenceInstant)
  if(china.sourceCoverageDate!==null&&china.sourceCoverageDate!==china.localDate)throw new Error('TEMPORAL_HOLIDAY_COVERAGE_DATE_MISMATCH')
  if(bangladesh.sourceCoverageDate!==null&&bangladesh.sourceCoverageDate!==bangladesh.localDate)throw new Error('TEMPORAL_HOLIDAY_COVERAGE_DATE_MISMATCH')
  if(beijing.localDate!==null&&china.localDate!==beijing.localDate)throw new Error('TEMPORAL_CHINA_DATE_MISMATCH')
  if(dhaka.localDate!==null&&bangladesh.localDate!==dhaka.localDate)throw new Error('TEMPORAL_BANGLADESH_DATE_MISMATCH')
  return {...value,clocks:{dhaka,beijing},holidays:{china,bangladesh}} as unknown as TemporalOverview
}

export async function executeTemporalOverviewRead(
  state:TemporalOverviewPageState,
  api:{getOverview:()=>Promise<unknown>},
  _trigger:TemporalReadTrigger,
):Promise<{recoveredOnce:boolean}> {
  const previousState=state.lastAuthorityState
  const previousReference=state.lastReferenceInstant
  const generation=++state.readGeneration
  state.viewState='LOADING'
  state.overview=null
  try{
    const overview=parseTemporalOverview(await api.getOverview())
    if(generation!==state.readGeneration)return{recoveredOnce:false}
    const recoveredOnce=overview.clockState==='BOTH_AVAILABLE'&&previousState!==null&&DEGRADED_STATES.includes(previousState)
      &&previousReference!==null&&overview.referenceInstant!==previousReference
    state.overview=overview
    state.lastAuthorityState=overview.clockState
    state.lastReferenceInstant=overview.referenceInstant
    state.viewState=recoveredOnce?'RECOVERED':overview.clockState
    return{recoveredOnce}
  }catch{
    if(generation!==state.readGeneration)return{recoveredOnce:false}
    state.overview=null
    state.lastAuthorityState='BOTH_UNAVAILABLE'
    state.viewState='BOTH_UNAVAILABLE'
    return{recoveredOnce:false}
  }
}
