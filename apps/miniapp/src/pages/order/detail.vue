<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppHeader from '../../components/AppHeader.vue'
import StatusNotice from '../../components/StatusNotice.vue'
import { api } from '../../api/client'
import { createP021PageState, executeP021Read } from '../../domain/order-detail-flow'
import type { P021StateCode, P021UserItemCode, P021ResponsibilityCode } from '../../api/order-detail-contract'

const state=reactive(createP021PageState()),orderRef=ref('')
const itCompleted=ref(false),itOldContentRestored=ref(false),itDelayPlan=ref('')
const itRealSession=ref('')
const projection=computed(()=>state.projection)
const stateCopy:Record<P021StateCode,{title:string;description:string}>={
  AWAITING_PAYMENT:{title:'等待付款',description:'订单信息已确认，当前尚未付款。'},
  PAYMENT_PROCESSING:{title:'正在确认付款',description:'付款结果仍在确认，不会自动重试或推进充值。'},
  PAID_AWAITING_TOPUP:{title:'已付款，等待充值',description:'收款已确认，到账结果尚未确认。'},
  TOPUP_PROCESSING:{title:'正在充值',description:'充值正在处理中，当前尚未确认到账。'},
  TOPUP_RESULT_UNKNOWN:{title:'充值结果待确认',description:'结果仍无法确认，请勿重新付款或发起新的充值。'},
  DELIVERED:{title:'已到账',description:'到账结果已由当前订单投影确认。'},
  CONFIRMED_NOT_DELIVERED:{title:'已确认未到账',description:'权威结果确认尚未到账，不推测后续结果。'},
  REFUND_PROCESSING:{title:'退款处理中',description:'退款状态只读展示，不会创建或重发退款。'},
  REFUNDED:{title:'已退款',description:'退款结果已记录，页面不展示账本或第三方交易信息。'},
  DELIVERY_REFUND_CONFLICT_REVIEW:{title:'到账与退款核对中',description:'到账与退款事实均保留，当前不作资金裁决。'},
  SUPPORT_REVIEW:{title:'客服处理中',description:'当前由支持人员继续核对，不承诺未批准的完成时间。'},
}
const itemCopy:Record<P021UserItemCode,string>={PAYMENT_CONFIRMATION:'付款确认',TOPUP_RESULT:'充值结果',DELIVERY_RESULT:'到账结果',REFUND_RESULT:'退款结果',ACCOUNTING_REVIEW:'账务复核'}
const responsibilityCopy:Record<P021ResponsibilityCode,string>={NONE:'当前无需额外处理',USER_PAYMENT:'等待你完成付款',SYSTEM_RECHECK:'系统正在重新核对',SUPPORT_REVIEW:'支持人员正在核对'}
const currentCopy=computed(()=>projection.value?stateCopy[projection.value.stateCode]:null)
const actionCodes=computed(()=>new Set(projection.value?.allowedActions.map(action=>action.actionCode)??[]))
const canRefresh=computed(()=>actionCodes.value.has('REFRESH_ORDER_DETAIL'))
const supportRef=computed(()=>actionCodes.value.has('OPEN_SUPPORT')?projection.value?.supportRef??null:null)
const money=(minor:number,currency:string)=>`${currency} ${minor}（最小单位）`
const time=(value:string|null)=>value?value.replace('T',' '):'时间待确认'
const timelineRows=computed(()=>projection.value?.timeline.map(item=>({ref:item.timelineItemRef,title:stateCopy[item.stateCode].title,time:time(item.occurredAt)}))??[])

async function load(){
  if(!orderRef.value){state.projection=null;state.viewState='NOT_AVAILABLE';return}
  try{await executeP021Read(state,uni,api,orderRef.value)}finally{if(itRealSession.value)itCompleted.value=true}
}
async function loadVisualScenario(value:unknown,parameterId?:unknown){
  if(typeof value!=='string')return false
  const fixture=await import('../../dev/p021-visual-fixtures')
  if(!fixture.isP021VisualScenarioId(value))return false
  orderRef.value='ORDER-P021-VISUAL';await fixture.runP021VisualScenario(state,value,typeof parameterId==='string'?parameterId:undefined);return true
}
async function loadItScenario(value:unknown){
  if(typeof value!=='string')return false
  const fixture=await import('../../dev/p021-it-fixtures')
  if(!fixture.isP021ItDelayPlan(value))return false
  orderRef.value='IT-P021-AWAITING';itDelayPlan.value=value
  const actual=await fixture.runP021ItPageScenario(state,value)
  itOldContentRestored.value=actual.oldContentRestored;itCompleted.value=true;return true
}
function safeBack(){uni.navigateBack({fail:()=>uni.reLaunch({url:'/pages/order/list'})})}
function openSupport(){if(supportRef.value)uni.navigateTo({url:`/pages/support/case?supportRef=${encodeURIComponent(supportRef.value)}`})}
onLoad(query=>{
  if(import.meta.env.DEV){void loadItScenario(query?.itDelayPlan).then(consumed=>consumed||loadVisualScenario(query?.visualScenario,query?.visualSubcase)).then(async consumed=>{if(!consumed){orderRef.value=typeof query?.orderRef==='string'?decodeURIComponent(query.orderRef):'';if(typeof query?.itRealSession==='string'){const fixture=await import('../../dev/p021-it-fixtures');itRealSession.value=query.itRealSession;fixture.prepareP021ItRealSession(uni,orderRef.value,query.itRealSession!=='UNAUTHENTICATED')}await load()}});return}
  orderRef.value=typeof query?.orderRef==='string'?decodeURIComponent(query.orderRef):'';load()
})
</script>

<template>
  <view class="page" data-page-root="order-detail" data-page-id="UX-P021" :data-view-state="state.viewState" :data-projection-version="projection?.projectionVersion??0" :data-detail-count="projection?1:0" :data-it-completed="itCompleted" :data-it-old-content-restored="itOldContentRestored" :data-it-delay-plan="itDelayPlan" data-write-eligibility="0">
    <AppHeader title="订单详情" left="订单" @left="safeBack"/>
    <view class="content">
      <view v-if="state.viewState==='LOADING'" data-status-panel="LOADING"><StatusNotice tone="unknown" title="正在读取订单详情" aria-label="正在读取订单详情" role="status">正在确认当前授权和最新订单状态。</StatusNotice></view>
      <view v-else-if="state.viewState==='NOT_AVAILABLE'" data-status-panel="NOT_AVAILABLE"><StatusNotice tone="risk" title="当前无法安全显示订单详情" aria-label="当前无法安全显示订单详情" role="alert">请重新建立订单访问后再试；这里不会确认订单是否存在。</StatusNotice></view>
      <view v-else-if="state.viewState==='READ_ERROR'" data-status-panel="READ_ERROR"><StatusNotice tone="risk" title="暂时无法读取" aria-label="暂时无法读取" role="alert">旧详情已撤销，你可以主动重新读取或安全返回。</StatusNotice></view>
      <view v-else-if="state.viewState==='INFORMATION_UPDATED'" data-status-panel="INFORMATION_UPDATED"><StatusNotice tone="unknown" title="订单信息已更新" aria-label="订单信息已更新" role="status">当前响应未通过版本或价格快照核对，请主动重新读取。</StatusNotice></view>

      <view v-if="state.viewState==='READY'&&projection&&currentCopy" data-detail-content="1">
        <view class="status-card" data-status-card="READY" role="status" aria-live="polite" aria-labelledby="p021-status-title">
          <view id="p021-status-title" class="heading" role="heading" aria-level="1">{{currentCopy.title}}</view>
          <text>{{currentCopy.description}}</text>
        </view>
        <view class="card price-card" data-price-summary="1" aria-label="冻结价格摘要">
          <view class="section-title">订单价格摘要</view>
          <view class="kv"><text>充值目标</text><text>{{projection.priceSnapshotSummary.maskedTarget}}</text></view>
          <view class="kv"><text>运营商</text><text>{{projection.priceSnapshotSummary.brandDisplayName}}</text></view>
<view class="kv"><text>充值商品</text><text>{{projection.priceSnapshotSummary.productDisplayName}}</text></view>
          <view class="kv"><text>目标价值</text><text>{{projection.priceSnapshotSummary.targetValueDisplay}}</text></view>
          <view class="kv"><text>最终实付</text><text>{{money(projection.priceSnapshotSummary.totalMinor,projection.priceSnapshotSummary.currency)}}</text></view>
          <view class="kv"><text>价格显示版本</text><text>{{projection.priceSnapshotSummary.displayVersion}}</text></view>
          <view class="kv"><text>价格有效至</text><text>{{time(projection.priceSnapshotSummary.validUntil)}}</text></view>
        </view>
        <view class="card facts-card" data-fact-summary="1">
          <view class="section-title">当前确认范围</view>
          <text v-if="projection.confirmedItems.length" class="fact-line">已确认：{{projection.confirmedItems.map(item=>itemCopy[item]).join('、')}}</text>
          <text v-if="projection.unknownItems.length" class="fact-line unknown">仍待确认：{{projection.unknownItems.map(item=>itemCopy[item]).join('、')}}</text>
          <text class="fact-line">当前责任：{{responsibilityCopy[projection.responsibilityCode]}}</text>
          <text class="fact-line">更新时间：{{time(projection.updatedAt)}}</text>
          <text v-if="projection.nextReviewPoint" class="fact-line">下一复核点：{{time(projection.nextReviewPoint)}}</text>
        </view>
        <view class="card timeline-card" data-timeline="1" role="list" aria-label="订单事实时间线">
          <view class="section-title">事实时间线</view>
          <view v-for="item in timelineRows" :key="item.ref" class="timeline-item" role="listitem">
            <text class="dot" aria-hidden="true"/><view><text class="timeline-title">{{item.title}}</text><text class="timeline-time">{{item.time}}</text></view>
          </view>
        </view>
      </view>
    </view>
    <view class="action" data-action-container="readonly">
      <button v-if="state.viewState!=='LOADING'&&(state.viewState!=='READY'||canRefresh)" data-action-code="REFRESH_ORDER_DETAIL" class="secondary" role="button" tabindex="0" aria-label="重新读取" @click="load">重新读取</button>
      <button v-if="state.viewState==='READY'&&supportRef" data-action-code="OPEN_SUPPORT" class="secondary" role="button" tabindex="0" aria-label="联系客服核对" @click="openSupport">联系客服核对</button>
      <button data-action-code="SAFE_BACK" class="primary" role="button" tabindex="0" aria-label="返回订单" @click="safeBack">返回订单</button>
    </view>
  </view>
</template>

<style src="../../styles/shared.css"></style>
<style scoped>
.status-card,.card{margin-top:24rpx;padding:34rpx;border-radius:32rpx;background:#fff}.status-card{background:#fff7e9}.heading{font-size:42rpx;font-weight:900}.status-card>text{display:block;margin-top:12rpx;color:var(--muted);line-height:1.6}.section-title{font-weight:900;margin-bottom:18rpx}.kv{display:flex;justify-content:space-between;gap:24rpx;padding:18rpx 0;border-bottom:1rpx solid var(--line)}.kv:last-child{border-bottom:0}.kv text:first-child{color:var(--muted)}.kv text:last-child{text-align:right;font-weight:700;overflow-wrap:anywhere}.fact-line{display:block;margin-top:14rpx;line-height:1.55}.unknown{color:var(--unknown)}.timeline-item{display:flex;align-items:flex-start;padding:18rpx 0}.dot{width:22rpx;height:22rpx;margin:8rpx 24rpx 0 0;border-radius:50%;background:var(--brand);flex:none}.timeline-title,.timeline-time{display:block}.timeline-title{font-weight:800}.timeline-time{margin-top:6rpx;color:var(--muted);font-size:23rpx}.action button{min-height:88rpx}.action button:focus-visible,.action uni-button:focus-visible{outline:4rpx solid #155eef;outline-offset:4rpx}.content{overflow-x:hidden}
</style>
