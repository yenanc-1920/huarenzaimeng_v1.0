<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import AppHeader from '../../components/AppHeader.vue'
import BottomNav from '../../components/BottomNav.vue'
import { api } from '../../api/client'
import type { ClockAuthorityState, ClockViewState, HolidayState, TemporalClock, TemporalHoliday, TemporalOverview, TemporalOverviewReadResponse } from '../../api/temporal-overview-contract'

type TemporalReadTrigger='FIRST_SHOW'|'FOREGROUND_SHOW'|'USER_REFRESH'
interface TemporalOverviewPageState {
  viewState:ClockViewState
  overview:TemporalOverview|null
  readGeneration:number
  lastAuthorityState:ClockAuthorityState|null
  lastReferenceInstant:string|null
}

// P001_INLINE_RUNTIME_AUTHORITY: keep this page's runtime validator in the page compilation unit. WeChat DevTools
// has repeatedly failed to register newly emitted page-only modules even when the
// files exist, so the home page must not depend on a new runtime chunk.
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

function parseTemporalOverview(value:unknown):TemporalOverview {
  if(!isRecord(value)||!exactKeys(value,ROOT_KEYS)||!nullableString(value.requestRef)||!nonEmpty(value.projectCode)
    ||value.schemaVersion!=='TEMPORAL_OVERVIEW_V1'||!isRfc3339(value.referenceInstant)||!isRfc3339(value.generatedAt)
    ||Date.parse(value.generatedAt)<Date.parse(value.referenceInstant)||!nullableString(value.timeZoneRuleVersion)
    ||!Number.isSafeInteger(value.clockStaleAfterSeconds)||Number(value.clockStaleAfterSeconds)<=0
    ||!CLOCK_STATES.includes(value.clockState as ClockAuthorityState)||!nullableString(value.holidayRuleVersion)
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

function hasRequiredNoStore(value:string):boolean {
  const rawDirectives=value.split(',')
  if(rawDirectives.length===0||rawDirectives.some((item)=>item.trim().length===0))return false
  const directives=new Map<string,string|null>()
  for(const raw of rawDirectives){
    const match=/^([!#$%&'*+\-.^_`|~0-9A-Za-z]+)(?:\s*=\s*("[^"\r\n]*"|[!#$%&'*+\-.^_`|~0-9A-Za-z]+))?$/.exec(raw.trim())
    if(!match)return false
    const name=match[1].toLowerCase()
    if(directives.has(name))return false
    const directiveValue=match[2]===undefined?null:match[2].replace(/^"|"$/g,'')
    directives.set(name,directiveValue)
  }
  if(directives.get('no-store')!==null||!directives.has('no-store')||directives.has('public')||directives.has('immutable'))return false
  for(const name of ['max-age','s-maxage']){
    if(!directives.has(name))continue
    const directiveValue=directives.get(name)
    if(directiveValue===null||!/^\d+$/.test(directiveValue)||Number(directiveValue)>0)return false
  }
  return true
}

function parseTemporalOverviewReadResponse(value:unknown):TemporalOverview {
  if(!isRecord(value)||!exactKeys(value,['body','statusCode','cacheControl'])||value.statusCode!==200
    ||typeof value.cacheControl!=='string'||!hasRequiredNoStore(value.cacheControl))throw new Error('INVALID_TEMPORAL_HTTP_RESPONSE')
  return parseTemporalOverview(value.body)
}

async function executeTemporalOverviewRead(state:TemporalOverviewPageState,reader:{getOverview:()=>Promise<TemporalOverviewReadResponse>},_trigger:TemporalReadTrigger){
  const previousState=state.lastAuthorityState
  const previousReference=state.lastReferenceInstant
  const generation=++state.readGeneration
  state.viewState='LOADING'
  state.overview=null
  try{
    const overview=parseTemporalOverviewReadResponse(await reader.getOverview())
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

const HOME_ROUTES = new Set(['/pages/recharge/select', '/pages/directory/list', '/pages/life-content/list'])
const temporalState=ref<ClockViewState>('LOADING')
const temporalOverview=ref<TemporalOverview|null>(null)
let temporalReadGeneration=0
let lastAuthorityState:ClockAuthorityState|null=null
let lastReferenceInstant:string|null=null
let recoveredTimer:ReturnType<typeof setTimeout>|null=null
let shownOnce=false

const temporalPageState:TemporalOverviewPageState={
  get viewState(){return temporalState.value},set viewState(value){temporalState.value=value},
  get overview(){return temporalOverview.value},set overview(value){temporalOverview.value=value},
  get readGeneration(){return temporalReadGeneration},set readGeneration(value){temporalReadGeneration=value},
  get lastAuthorityState(){return lastAuthorityState},set lastAuthorityState(value){lastAuthorityState=value},
  get lastReferenceInstant(){return lastReferenceInstant},set lastReferenceInstant(value){lastReferenceInstant=value},
}

function showNavigationFailure(url: string, error: unknown) {
  console.error('[HOME_NAVIGATION_FAILED]', { url, error })
  uni.showToast({ title: '页面暂时无法打开，请重试', icon: 'none' })
}

function go(url: string) {
  if (!HOME_ROUTES.has(url)) {
    showNavigationFailure(url, new Error('HOME_ROUTE_NOT_ALLOWED'))
    return
  }
  uni.navigateTo({
    url,
    fail(navigateError) {
      console.error('[HOME_NAVIGATE_TO_FAILED]', { url, error: navigateError })
      uni.redirectTo({
        url,
        fail: (redirectError) => showNavigationFailure(url, redirectError),
      })
    },
  })
}

async function loadTemporal(trigger:TemporalReadTrigger){
  if(recoveredTimer){clearTimeout(recoveredTimer);recoveredTimer=null}
  const generationBefore=temporalReadGeneration+1
  const result=await executeTemporalOverviewRead(temporalPageState,{getOverview:()=>api.getTemporalOverview()},trigger)
  if(result.recoveredOnce&&temporalReadGeneration===generationBefore){
    recoveredTimer=setTimeout(()=>{
      if(temporalReadGeneration===generationBefore&&temporalState.value==='RECOVERED')temporalState.value='BOTH_AVAILABLE'
      recoveredTimer=null
    },0)
  }
}

function updatedLabel(value:string){
  const match=/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(value)
  return match?`${match[2]}月${match[3]}日 ${match[4]}:${match[5]}（UTC）更新`:'更新时间暂不可用'
}

const clockStatusCopy:Record<ClockViewState,string>={
  LOADING:'正在读取两地时间',BOTH_AVAILABLE:'两地时间已更新',DHAKA_UNAVAILABLE:'达卡时间暂不可用',BEIJING_UNAVAILABLE:'北京时间暂不可用',
  BOTH_UNAVAILABLE:'两地时间暂不可用',STALE:'以下为非当前时间',RECOVERED:'时间已恢复',
}

const holidayTitle=(country:'中国'|'孟加拉',holiday:TemporalHoliday|null)=>{
  if(!holiday)return `${country}：节假日信息暂不可用`
  const copy:Record<HolidayState,string>={
    NO_HOLIDAY_CONFIRMED:'工作日',CONFIRMED_HOLIDAY:holiday.name==='休息日'?'休息日':holiday.name||'节假日',
    PENDING_CONFIRMATION:`预计为${holiday.name||'节假日'}，待官方确认`,READ_ERROR:'节假日信息读取失败',
    STALE_OR_EXPIRED:'信息已过期，当前结论不可用',UNPUBLISHED:'内容已下架',
  }
  return `${country}：${copy[holiday.state]}`
}

const holidayMeta=(holiday:TemporalHoliday|null)=>holiday&&['NO_HOLIDAY_CONFIRMED','CONFIRMED_HOLIDAY','PENDING_CONFIRMATION'].includes(holiday.state)
  ?`${holiday.sourceType||'来源未提供'} · ${updatedLabel(temporalOverview.value!.generatedAt)}`:''

onShow(()=>{
  const trigger:TemporalReadTrigger=shownOnce?'FOREGROUND_SHOW':'FIRST_SHOW'
  shownOnce=true
  return loadTemporal(trigger)
})
</script>

<template>
  <view class="page" data-page-id="UX-P001" data-page-root="home">
    <AppHeader/>
    <view class="content">
      <view class="welcome"><text>你好，欢迎回来</text><view class="heading" role="heading" aria-level="1">在孟生活，一站办妥</view></view>
      <button class="hero" @click="go('/pages/recharge/select')"><text class="eyebrow">手机充值</text><view class="hero-title" role="heading" aria-level="2">给孟加拉手机号充值</view><text class="hero-copy">输入号码，选择商品，微信付款</text><text class="hero-action">开始充值</text></button>

      <view class="temporal-card" role="region" aria-label="今日时间与节假日">
        <view class="temporal-heading"><view><text class="temporal-eyebrow">今日信息</text><text class="temporal-title">达卡与北京</text></view><button class="temporal-refresh" :disabled="temporalState==='LOADING'" @click="loadTemporal('USER_REFRESH')">刷新</button></view>
        <text class="temporal-status" role="status">{{clockStatusCopy[temporalState]}}</text>
        <view class="clock-grid">
          <view class="clock-cell"><text class="clock-city">达卡</text><text class="clock-time">{{temporalOverview?.clocks.dhaka.localTime||'--:--'}}</text><text v-if="temporalState==='STALE'" class="not-current">非当前时间</text><text v-else-if="temporalOverview?.clocks.dhaka.availabilityState==='UNAVAILABLE'" class="not-current">暂不可用</text></view>
          <view class="clock-cell"><text class="clock-city">北京</text><text class="clock-time">{{temporalOverview?.clocks.beijing.localTime||'--:--'}}</text><text v-if="temporalState==='STALE'" class="not-current">非当前时间</text><text v-else-if="temporalOverview?.clocks.beijing.availabilityState==='UNAVAILABLE'" class="not-current">暂不可用</text></view>
        </view>
        <text v-if="temporalOverview" class="temporal-updated">{{updatedLabel(temporalOverview.generatedAt)}}</text>
        <view class="holiday-list" aria-label="中国和孟加拉今日节假日">
          <view class="holiday-row"><text class="holiday-copy">{{temporalState==='LOADING'?'中国：正在读取今日节假日':holidayTitle('中国',temporalOverview?.holidays.china||null)}}</text><text v-if="holidayMeta(temporalOverview?.holidays.china||null)" class="holiday-meta">{{holidayMeta(temporalOverview?.holidays.china||null)}}</text></view>
          <view class="holiday-row"><text class="holiday-copy">{{temporalState==='LOADING'?'孟加拉：正在读取今日节假日':holidayTitle('孟加拉',temporalOverview?.holidays.bangladesh||null)}}</text><text v-if="holidayMeta(temporalOverview?.holidays.bangladesh||null)" class="holiday-meta">{{holidayMeta(temporalOverview?.holidays.bangladesh||null)}}</text></view>
        </view>
        <text class="temporal-note">时间与今日状态异常不影响充值。</text>
      </view>

      <view class="entries"><button class="card nav" @click="go('/pages/directory/list')"><text class="entry-icon orange">页</text><view><text class="card-title">孟加拉黄页</text><text class="copy">按城市查找电话与生活服务</text></view><text class="arrow">›</text></button><button class="card nav" @click="go('/pages/life-content/list')"><text class="entry-icon warm">阅</text><view><text class="card-title">生活资讯</text><text class="copy">查看生活提醒与节假日说明</text></view><text class="arrow">›</text></button></view>
    </view>
    <BottomNav active="home"/>
  </view>
</template>

<style src="../../styles/shared.css"></style>
<style scoped>
.welcome{width:100%;overflow:visible}.welcome>text{color:var(--muted);font-size:28rpx}.welcome .heading{width:100%;font-size:52rpx;line-height:1.35;min-height:70rpx;margin-top:10rpx;white-space:normal;word-break:normal}.hero{display:block;width:100%;min-width:0;margin-top:36rpx;padding:48rpx;border-radius:40rpx;background:var(--brand);color:#fff;text-align:left;box-shadow:0 20rpx 56rpx rgba(35,87,165,.22)}.eyebrow,.hero-copy,.hero-action{display:block}.eyebrow{font-size:24rpx;opacity:.82}.hero-title{font-size:44rpx;line-height:1.35;font-weight:900;margin-top:16rpx}.hero-copy{font-size:26rpx;line-height:1.5;margin-top:10rpx;opacity:.9}.hero-action{display:inline-block;width:auto;min-width:192rpx;min-height:72rpx;margin-top:44rpx;padding:16rpx 40rpx;border-radius:24rpx;background:#fff;color:var(--brand);text-align:center;font-weight:800;white-space:nowrap}
.temporal-card{margin-top:32rpx;padding:28rpx;border:1rpx solid var(--line);border-radius:32rpx;background:#fff}.temporal-heading{display:flex;align-items:center;justify-content:space-between;gap:20rpx}.temporal-heading>view{min-width:0}.temporal-eyebrow,.temporal-title,.temporal-status,.temporal-updated,.clock-city,.clock-time,.not-current,.holiday-copy,.holiday-meta,.temporal-note{display:block}.temporal-eyebrow{color:#8a4a17;font-size:20rpx;font-weight:800}.temporal-title{margin-top:4rpx;font-size:31rpx;font-weight:900}.temporal-refresh{flex:none;min-width:96rpx;min-height:44px;padding:10rpx 22rpx;border:1rpx solid var(--line);border-radius:999rpx;background:#fff;color:var(--brand);font-size:22rpx;font-weight:800}.temporal-refresh[disabled]{color:#9aa4b1;background:#f3f5f8}.temporal-status{margin-top:18rpx;color:var(--muted);font-size:22rpx}.clock-grid{display:grid;grid-template-columns:1fr 1fr;gap:16rpx;margin-top:14rpx}.clock-cell{min-width:0;padding:20rpx;border-radius:24rpx;background:#f5f8fc}.clock-city{color:var(--muted);font-size:22rpx}.clock-time{margin-top:4rpx;font-size:40rpx;line-height:1.25;font-weight:900;letter-spacing:1rpx}.not-current{margin-top:4rpx;color:#9a531d;font-size:20rpx}.temporal-updated{margin-top:12rpx;color:var(--muted);font-size:19rpx}.holiday-list{margin-top:18rpx;border-top:1rpx solid var(--line)}.holiday-row{padding:18rpx 0}.holiday-row+.holiday-row{border-top:1rpx solid var(--line)}.holiday-copy{font-size:24rpx;line-height:1.45;font-weight:750}.holiday-meta{margin-top:6rpx;color:var(--muted);font-size:19rpx;line-height:1.45}.temporal-note{margin-top:8rpx;color:var(--muted);font-size:20rpx;line-height:1.5}
.entries{margin-top:32rpx;border-radius:32rpx;overflow:hidden}.nav{width:100%;min-height:148rpx;margin:0;padding:36rpx;border-radius:0;display:flex;align-items:center;text-align:left}.nav+.nav{border-top:1rpx solid var(--line)}.entry-icon{width:76rpx;height:76rpx;line-height:76rpx;border-radius:24rpx;color:#fff;text-align:center;font-weight:800;flex:none;margin-right:24rpx}.blue{background:var(--brand)}.orange{background:#f5a623}.warm{background:#d97832}.nav view{flex:1;min-width:0}.nav .card-title{width:100%;white-space:nowrap}.nav .copy{width:100%;overflow:hidden;display:-webkit-box;-webkit-box-orient:vertical;-webkit-line-clamp:2;white-space:normal;word-break:normal}.arrow{font-size:48rpx;color:#8792a2;margin-left:20rpx;flex:none}.test-badge{display:block;width:108rpx;margin:32rpx auto 0;padding:10rpx 18rpx;border-radius:999rpx;background:#eaedf2;color:#737e8d;text-align:center;font-size:22rpx;white-space:nowrap}
</style>
