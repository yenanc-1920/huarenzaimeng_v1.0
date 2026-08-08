<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppHeader from '../../components/AppHeader.vue'
import StatusNotice from '../../components/StatusNotice.vue'
import { api } from '../../api/client'
import { readP014OriginalWriteIdentity, type P014FactCode, type P014FactState, type P014Projection } from '../../api/p014-topup-contract'
import { readSessionProjection } from '../../domain/session'

const orderRef=ref('')
const projection=ref<P014Projection|null>(null)
const loading=ref(false)
const error=ref('')
const queryRecoveryAvailable=ref(false)
let readGeneration=0

const messageByCode:Record<string,{title:string;description:string;mark:string}>={
  PAYMENT_CONFIRMED_READY_FOR_TOPUP:{title:'已付款，可以继续充值',description:'当前仅显示已确认的项目进度；本页不会发起真实充值。',mark:'待'},
  PAYMENT_CONFIRMED_TOPUP_QUALIFICATION_CHECKING:{title:'正在核验充值资格',description:'已付款，号码或充值资格正在核验；不会自动继续真实充值。',mark:'核'},
  PAYMENT_CONFIRMATION_CHECKING_NO_AUTO_TOPUP:{title:'正在核验支付确认',description:'支付确认正在核验；不会自动继续真实充值。',mark:'核'},
  TOPUP_FACT_CONFLICT_UNDER_REVIEW:{title:'充值事实正在复核',description:'充值事实存在冲突，正在人工复核。',mark:'!'},
  DELIVERY_EVIDENCE_UNDER_REVIEW:{title:'到账证据正在核对',description:'到账证据正在核对，其他事实尚未闭合。',mark:'!'},
  TOPUP_DELIVERED:{title:'充值已到账',description:'上游处理、到账和账务闭包均已确认。',mark:'✓'},
  TOPUP_RESULT_PENDING_CONFIRMATION:{title:'充值结果长期待确认',description:'充值结果长期待确认，请主动查询或联系支持。',mark:'?'},
  UPSTREAM_CONFIRMED_DELIVERY_ABSENT:{title:'权威确认尚未到账',description:'上游已确认处理，但权威结果明确尚未到账。',mark:'!'},
  TOPUP_PROCESSING_DELIVERY_UNCONFIRMED:{title:'正在充值',description:'正在充值，到账仍待确认。',mark:'…'},
}
const factLabel:Record<P014FactCode,string>={PAYMENT:'收款确认',UPSTREAM_DEBIT:'上游处理',DELIVERY:'到账结果',ACCOUNTING_CLOSURE:'账务闭包'}
const factStateLabel:Record<P014FactState,string>={NOT_OBSERVED:'尚未观察到权威结果',UNKNOWN:'无法确认',CONFIRMED:'已确认',CONFLICT:'存在冲突',ABSENT_CONFIRMED:'权威确认未到账/未交付'}
const responsibilityLabel:Record<P014Projection['progressSummary']['responsibilityCode'],string>={SYSTEM_RECHECK:'系统继续核对',SUPPORT_REVIEW:'支持人员复核',ACCOUNTING_REVIEW:'账务人员复核',NONE:'无需额外复核'}
const copy=computed(()=>projection.value?messageByCode[projection.value.progressSummary.userMessageCode]:null)
const completed=computed(()=>projection.value?.stateCode==='DELIVERED')
const actionCodes=computed(()=>new Set(projection.value?.allowedActions.map(action=>action.actionCode)??[]))
const canRefresh=computed(()=>actionCodes.value.has('REFRESH_ORDER_PROJECTION'))
const canQueryOriginal=computed(()=>actionCodes.value.has('QUERY_ORIGINAL_TOPUP')||queryRecoveryAvailable.value)
const supportRef=computed(()=>actionCodes.value.has('OPEN_SUPPORT')&&projection.value?.progressSummary.supportRef?projection.value.progressSummary.supportRef:null)
const canSafeLeave=computed(()=>actionCodes.value.has('SAFE_LEAVE'))
const amount=computed(()=>{
  const snapshot=projection.value?.priceSnapshotSummary
  return snapshot?`${snapshot.currency} ${(snapshot.totalMinor/100).toFixed(2)}`:'—'
})
const faceValue=computed(()=>{
  const snapshot=projection.value?.priceSnapshotSummary
  return snapshot?`${snapshot.targetCurrency} ${(snapshot.targetFaceValueMinor/100).toFixed(2)}`:'—'
})
const unknownItems=computed(()=>projection.value?.progressSummary.unknownItems.map(code=>factLabel[code]).join('、')||'无')
const formatTime=(value:string|null)=>value??'未提供'

function withdraw(){projection.value=null}
async function refresh(){
  if(!orderRef.value)return
  const generation=++readGeneration
  withdraw();queryRecoveryAvailable.value=false;error.value='';loading.value=true
  try{
    const response=await api.getP014Progress(orderRef.value)
    if(generation!==readGeneration)return
    if(response.outcome!=='ACCEPTED'||response.projectCode!=='TOPUP_PROGRESS_READ'||!response.currentProjection)throw new Error('P014_PROGRESS_UNAVAILABLE')
    projection.value=response.currentProjection
    queryRecoveryAvailable.value=response.currentProjection.allowedActions.some(action=>action.actionCode==='QUERY_ORIGINAL_TOPUP')
  }catch{
    if(generation!==readGeneration)return
    withdraw();queryRecoveryAvailable.value=false;error.value='暂时无法安全显示充值进度，请稍后主动刷新。'
  }finally{if(generation===readGeneration)loading.value=false}
}

async function queryOriginalResult(){
  if(!canQueryOriginal.value)return
  const identity=readP014OriginalWriteIdentity(uni,orderRef.value,readSessionProjection(uni))
  if(!identity){withdraw();queryRecoveryAvailable.value=false;error.value='原充值请求信息缺失或无法校验，当前不会重新提交。';return}
  const generation=++readGeneration
  withdraw();queryRecoveryAvailable.value=false;error.value='';loading.value=true
  try{
    const response=await api.getP014TopupResult(orderRef.value,identity)
    if(generation!==readGeneration)return
    if(response.outcome==='ACCEPTED'&&response.projectCode==='TOPUP_INTENT_RESULT_FOUND'&&response.currentProjection){projection.value=response.currentProjection;queryRecoveryAvailable.value=response.currentProjection.allowedActions.some(action=>action.actionCode==='QUERY_ORIGINAL_TOPUP');return}
    withdraw()
    queryRecoveryAvailable.value=response.outcome==='UNKNOWN'&&response.projectCode==='TOPUP_INTENT_RESULT_UNKNOWN'&&response.retryClass==='SAME_ACTION_QUERY_ONLY'
    error.value=response.outcome==='UNKNOWN'?'充值结果仍无法确认，请等待后再次主动查询。':response.projectCode==='TOPUP_INTENT_RESULT_REJECTED'?'原充值请求已被拒绝，当前不会重新提交。':'暂时无法查询原充值请求。'
  }catch{
    if(generation!==readGeneration)return
    withdraw();queryRecoveryAvailable.value=true;error.value='查询中断，当前不会自动重试或重新提交。'
  }finally{if(generation===readGeneration)loading.value=false}
}

function openSupport(){if(supportRef.value)uni.navigateTo({url:`/pages/support/case?supportRef=${encodeURIComponent(supportRef.value)}`})}
function leave(){uni.navigateBack({fail:()=>uni.reLaunch({url:'/pages/index/index'})})}
onLoad(query=>{orderRef.value=typeof query?.orderRef==='string'?decodeURIComponent(query.orderRef):'';refresh()})
</script>

<template>
  <view class="page" data-page-root="p014-progress" data-write-eligibility="0">
    <AppHeader title="充值进度" left="订单" @left="leave" />
    <view class="content result-page">
      <view v-if="loading" class="card state-card" role="status" aria-live="polite">
        <view class="heading" role="heading" aria-level="1">正在读取最新进度</view>
        <text class="desc">读取期间不会继续显示旧结果。</text>
      </view>
      <StatusNotice v-else-if="error" tone="unknown" title="暂时无法显示">{{error}}</StatusNotice>
      <template v-else-if="projection && copy">
        <text class="result-mark" :class="{success:completed}">{{copy.mark}}</text>
        <view class="heading" role="heading" aria-level="1">{{copy.title}}</view>
        <text class="desc">{{copy.description}}</text>
        <view class="card summary">
          <view class="kv"><text>充值号码</text><text>{{projection.priceSnapshotSummary.maskedRecipientNumber}}</text></view>
          <view class="kv"><text>运营商</text><text>{{projection.priceSnapshotSummary.operatorDisplayName}}</text></view>
          <view class="kv"><text>商品</text><text>{{projection.priceSnapshotSummary.productDisplayName}}</text></view>
          <view class="kv"><text>面额</text><text>{{faceValue}}</text></view>
          <view class="kv"><text>订单总价</text><text>{{amount}}</text></view>
          <view class="kv"><text>价格显示版本</text><text>{{projection.priceSnapshotSummary.displayVersion}}</text></view>
          <view class="kv"><text>价格有效至</text><text>{{projection.priceSnapshotSummary.expiresAt}}</text></view>
        </view>
        <view class="card fact-card" aria-label="充值事实时间线">
          <view class="section-title">事实进展</view>
          <view v-for="fact in projection.factTimeline" :key="fact.factCode" class="fact-row">
            <view><text class="fact-name">{{factLabel[fact.factCode]}}</text><text>{{factStateLabel[fact.state]}}</text></view>
            <small>发生时间：{{formatTime(fact.occurredAt)}}；观察时间：{{formatTime(fact.observedAt)}}</small>
          </view>
          <view class="kv"><text>仍无法确认</text><text>{{unknownItems}}</text></view>
          <view class="kv"><text>当前责任</text><text>{{responsibilityLabel[projection.progressSummary.responsibilityCode]}}</text></view>
          <view class="kv"><text>更新时间</text><text>{{projection.progressSummary.updatedAt}}</text></view>
          <view v-if="projection.progressSummary.nextReviewPoint" class="kv"><text>下一复核点</text><text>{{projection.progressSummary.nextReviewPoint}}</text></view>
        </view>
      </template>
      <StatusNotice v-else tone="unknown" title="暂时无法显示">未取得可安全展示的充值进度。</StatusNotice>
    </view>
    <view class="action">
      <button v-if="!loading && canQueryOriginal" class="primary" @click="queryOriginalResult">查询原请求结果</button>
      <button v-if="!loading && canRefresh" class="secondary" @click="refresh">刷新订单进度</button>
      <button v-if="!loading && supportRef" class="secondary" @click="openSupport">联系支持</button>
      <button v-if="canSafeLeave || error || !projection" class="secondary" @click="leave">安全返回</button>
    </view>
  </view>
</template>

<style src="../../styles/shared.css"></style>
<style scoped>
.result-page{text-align:center;padding-top:70rpx}.result-mark{display:block;width:144rpx;height:144rpx;line-height:144rpx;margin:auto;border-radius:50%;background:#fff0d4;font-size:54rpx}.result-mark.success{background:#ddf3e9;color:var(--success);font-size:76rpx;font-weight:900}.result-page .heading{margin-top:26rpx}.summary,.fact-card{text-align:left;margin-top:36rpx}.state-card{text-align:center}.section-title{font-size:34rpx;font-weight:800;margin-bottom:16rpx}.fact-row{padding:20rpx 0;border-bottom:1rpx solid var(--line)}.fact-row>view{display:flex;justify-content:space-between;gap:20rpx}.fact-name{font-weight:700}.fact-row small{display:block;color:var(--muted);margin-top:8rpx;overflow-wrap:anywhere}
</style>
