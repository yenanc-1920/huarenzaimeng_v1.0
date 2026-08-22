<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppHeader from '../../components/AppHeader.vue'
import StatusNotice from '../../components/StatusNotice.vue'
import { api } from '../../api/client'
import type { ReleaseOrderProjection } from '../../api/formal-transaction-contract'
import { readSessionProjection } from '../../domain/session'

const orderRef=ref(''),projection=ref<ReleaseOrderProjection|null>(null),viewState=ref<'LOADING'|'READY'|'NOT_AVAILABLE'|'READ_ERROR'>('LOADING')
const stateCopy:Record<string,{title:string;description:string}>={
  AWAITING_PAYMENT:{title:'等待付款',description:'订单信息已确认，当前尚未付款。'},
  PAYMENT_PROCESSING:{title:'正在确认付款',description:'付款结果仍在确认，不会自动重试或推进充值。'},
  PAID_AWAITING_TOPUP:{title:'已付款，等待充值',description:'收款已确认，到账结果尚未确认。'},
  TOPUP_PROCESSING:{title:'正在充值',description:'充值正在处理中，当前尚未确认到账。'},
  TOPUP_RESULT_UNKNOWN:{title:'充值结果待确认',description:'结果仍无法确认，请勿重新付款或发起新的充值。'},
  DELIVERED:{title:'已到账',description:'到账结果已由当前订单投影确认。'},
  CONFIRMED_NOT_DELIVERED:{title:'已确认未到账',description:'充值结果确认尚未到账，请打开微信客服了解后续处理。'},
  REFUND_PROCESSING:{title:'退款处理中',description:'退款状态只读展示，不会创建或重发退款。'},
  REFUNDED:{title:'已退款',description:'退款结果已记录，页面不展示账本或第三方交易信息。'},
  DELIVERY_REFUND_CONFLICT_REVIEW:{title:'到账与退款核对中',description:'到账与退款记录正在核对，当前结果以订单最新进度为准。'},
  SUPPORT_REVIEW:{title:'客服处理中',description:'当前由支持人员继续核对，不承诺未批准的完成时间。'},
  PAYMENT_CONFIRMED:{title:'已付款',description:'付款已确认，后续充值状态以订单最新结果为准。'},
  TOPUP_REVIEW:{title:'充值处理中',description:'充值结果正在核对，请勿重复提交。'},
  COMPLETED:{title:'已完成',description:'当前订单已完成。'},
}
const currentCopy=computed(()=>projection.value?stateCopy[projection.value.orderState]??{title:'订单处理中',description:'请以当前订单状态为准。'}:null)
const money=(minor:number,currency:string)=>`${currency==='CNY'?'¥':currency==='BDT'?'৳':currency} ${(minor/100).toFixed(2)}`

async function load(){
  projection.value=null;viewState.value='LOADING'
  if(!orderRef.value||readSessionProjection(uni).role!=='BUYER'){viewState.value='NOT_AVAILABLE';return}
  try{const current=await api.getOrderDetail(orderRef.value);if(current.orderRef!==orderRef.value){viewState.value='NOT_AVAILABLE';return};projection.value=current;viewState.value='READY'}
  catch{viewState.value='READ_ERROR'}
}
function safeBack(){uni.navigateBack({fail:()=>uni.reLaunch({url:'/pages/order/list'})})}
onLoad(query=>{
  orderRef.value=typeof query?.orderRef==='string'?decodeURIComponent(query.orderRef):''
  void load()
})
</script>

<template>
  <view class="page" data-page-root="order-detail" data-page-id="UX-P021" :data-view-state="viewState" :data-projection-version="projection?.projectionVersion??0" :data-detail-count="projection?1:0" data-write-eligibility="0">
    <AppHeader title="订单详情" left="订单" @left="safeBack"/>
    <view class="content">
      <view v-if="viewState==='LOADING'" data-status-panel="LOADING"><StatusNotice tone="unknown" title="正在读取订单详情">正在读取当前账号的最新订单状态。</StatusNotice></view>
      <view v-else-if="viewState==='NOT_AVAILABLE'" data-status-panel="NOT_AVAILABLE"><StatusNotice tone="risk" title="当前无法安全显示订单详情">请登录后从订单列表进入；这里不会确认订单是否存在。</StatusNotice></view>
      <view v-else-if="viewState==='READ_ERROR'" data-status-panel="READ_ERROR"><StatusNotice tone="risk" title="暂时无法读取">请稍后重新读取或返回订单列表。</StatusNotice></view>

      <view v-if="viewState==='READY'&&projection&&currentCopy" data-detail-content="1">
        <view class="status-card" data-status-card="READY" role="status" aria-live="polite" aria-labelledby="p021-status-title">
          <view id="p021-status-title" class="heading" role="heading" aria-level="1">{{currentCopy.title}}</view>
          <text>{{currentCopy.description}}</text>
        </view>
        <view class="card price-card" data-price-summary="1" aria-label="订单摘要">
          <view class="section-title">订单信息</view>
          <view class="kv"><text>订单号</text><text>{{projection.orderRef}}</text></view>
          <view class="kv"><text>报价号</text><text>{{projection.quoteRef}}</text></view>
          <view class="kv"><text>订单金额</text><text>{{money(projection.totalAmountMinor,projection.currency)}}</text></view>
        </view>
        <view class="card facts-card" data-fact-summary="1">
          <view class="section-title">处理情况</view>
          <text class="fact-line">支付：{{projection.paymentState}}</text>
          <text class="fact-line">充值：{{projection.upstreamDebitState}}</text>
          <text class="fact-line">到账：{{projection.deliveryState}}</text>
          <text class="fact-line">退款：{{projection.refundState}}</text>
          <text class="fact-line">下一动作：{{projection.nextAction}}</text>
        </view>
      </view>
    </view>
    <view class="action" data-action-container="readonly">
      <button v-if="viewState!=='LOADING'" data-action-code="REFRESH_ORDER_DETAIL" class="secondary" @click="load">重新读取</button>
      <button v-if="viewState==='READY'" data-action-code="OPEN_SUPPORT" class="secondary" open-type="contact">打开微信客服</button>
      <button data-action-code="SAFE_BACK" class="primary" role="button" tabindex="0" aria-label="返回订单" @click="safeBack">返回订单</button>
    </view>
  </view>
</template>

<style src="../../styles/shared.css"></style>
<style scoped>
.status-card,.card{margin-top:24rpx;padding:34rpx;border-radius:32rpx;background:#fff}.status-card{background:#fff7e9}.heading{font-size:42rpx;font-weight:900}.status-card>text{display:block;margin-top:12rpx;color:var(--muted);line-height:1.6}.section-title{font-weight:900;margin-bottom:18rpx}.kv{display:flex;justify-content:space-between;gap:24rpx;padding:18rpx 0;border-bottom:1rpx solid var(--line)}.kv:last-child{border-bottom:0}.kv text:first-child{color:var(--muted)}.kv text:last-child{text-align:right;font-weight:700;overflow-wrap:anywhere}.fact-line{display:block;margin-top:14rpx;line-height:1.55}.unknown{color:var(--unknown)}.timeline-item{display:flex;align-items:flex-start;padding:18rpx 0}.dot{width:22rpx;height:22rpx;margin:8rpx 24rpx 0 0;border-radius:50%;background:var(--brand);flex:none}.timeline-title,.timeline-time{display:block}.timeline-title{font-weight:800}.timeline-time{margin-top:6rpx;color:var(--muted);font-size:23rpx}.action button{min-height:88rpx}.action button:focus-visible,.action uni-button:focus-visible{outline:4rpx solid #155eef;outline-offset:4rpx}.content{overflow-x:hidden}
</style>
