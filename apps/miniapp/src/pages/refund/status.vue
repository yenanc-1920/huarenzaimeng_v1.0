<script setup lang="ts">
import{computed,ref}from'vue'
import{onLoad}from'@dcloudio/uni-app'
import AppHeader from'../../components/AppHeader.vue'
import StatusNotice from'../../components/StatusNotice.vue'
import{api}from'../../api/client'
import type{PaymentView,RefundView,TopupView}from'../../api/formal-transaction-contract'

const orderRef=ref(''),refundRef=ref(''),payment=ref<PaymentView|null>(null),topup=ref<TopupView|null>(null),refund=ref<RefundView|null>(null),loading=ref(false),error=ref('')
const paymentLabels:Record<string,string>={NEW:'待支付',PREPAY_CREATED:'待支付',PROCESSING:'支付处理中',PAID:'支付成功',CLOSED:'已关闭',REFUND_PROCESSING:'退款处理中',REFUNDED:'已退款',REJECTED:'支付失败',UNKNOWN:'支付结果待确认'}
const topupLabels:Record<string,string>={RESERVED:'已锁定权益',SUBMITTED:'已提交充值',PROCESSING:'充值处理中',DELIVERED:'充值已到账',REJECTED:'充值失败',UNKNOWN:'充值结果待确认',RECONCILIATION_REQUIRED:'需要人工复核'}
const refundLabels:Record<string,string>={PENDING:'退款处理中',SUCCEEDED:'退款成功',REJECTED:'退款失败',UNKNOWN:'退款结果待确认'}
const paymentLabel=computed(()=>paymentLabels[payment.value?.state||'UNKNOWN']||'暂时无法确认')
const topupLabel=computed(()=>topup.value?topupLabels[topup.value.state]||'暂时无法确认':'未提交充值')
const refundLabel=computed(()=>refundRef.value?(refundLabels[refund.value?.state||'UNKNOWN']||'暂时无法确认'):'未提供退款申请编号')
const canQueryRefund=computed(()=>refund.value?.state==='UNKNOWN'&&!!refundRef.value)
const lateConflict=computed(()=>topup.value?.state==='DELIVERED'&&(refund.value?.state==='PENDING'||refund.value?.state==='SUCCEEDED'))

async function refresh(){
  if(!orderRef.value){error.value='原交易编号缺失，当前无法查询原交易。';return}
  loading.value=true;error.value=''
  try{
    payment.value=await api.getPaymentStatus(orderRef.value)
    try{topup.value=await api.getTopupStatus(orderRef.value)}catch{topup.value=null}
    if(refundRef.value){try{refund.value=await api.getRefundStatus(orderRef.value,refundRef.value)}catch{refund.value=null}}
  }catch{payment.value=null;topup.value=null;refund.value=null;error.value='暂时无法读取原交易状态，请稍后主动查询。'}finally{loading.value=false}
}
async function queryRefund(){if(!canQueryRefund.value)return;loading.value=true;error.value='';try{refund.value=await api.queryRefundOriginal(orderRef.value,refundRef.value)}catch{error.value='原退款结果暂时无法确认，当前不会再次申请退款。'}finally{loading.value=false}}
onLoad(q=>{orderRef.value=typeof q?.orderRef==='string'?decodeURIComponent(q.orderRef):'';refundRef.value=typeof q?.refundRef==='string'?decodeURIComponent(q.refundRef):'';void refresh()})
</script>

<template>
  <view class="page" data-page-id="UX-P015" data-write-eligibility="0">
    <AppHeader title="交易与退款状态" left="订单" @left="uni.navigateBack()"/>
    <view class="content">
      <view class="heading" role="heading" aria-level="1">交易状态</view>
      <text class="desc">这里只读取原交易的支付、充值和退款状态，不会创建新的交易动作。</text>
      <StatusNotice v-if="loading" tone="unknown" title="正在读取原交易">旧结果已收起，请稍候。</StatusNotice>
      <StatusNotice v-else-if="error" tone="unknown" title="暂时无法确认">{{error}}</StatusNotice>
      <StatusNotice v-if="lateConflict" tone="risk" title="订单需要人工协助">充值与退款记录同时存在，我们正在核对，不会重复充值或自动退款。</StatusNotice>
      <view v-if="payment" class="card">
        <view class="kv"><text>订单号</text><text>{{orderRef}}</text></view>
        <view class="kv"><text>微信支付</text><text>{{paymentLabel}}</text></view>
        <view class="kv"><text>充值到账</text><text>{{topupLabel}}</text></view>
        <view class="kv"><text>退款状态</text><text>{{refundLabel}}</text></view>
        <view v-if="refundRef" class="kv"><text>退款申请编号</text><text>{{refundRef}}</text></view>
      </view>
      <StatusNotice v-if="payment" tone="unknown" title="只读状态说明">状态未知时不会重复充值，也不会自动退款；请查询原交易或打开微信客服。</StatusNotice>
    </view>
    <view class="action">
      <button v-if="canQueryRefund" class="primary" :disabled="loading" @click="queryRefund">查询原退款结果</button>
      <button class="primary" :disabled="loading||!orderRef" @click="refresh">查询原交易</button>
      <button class="secondary" open-type="contact">打开微信客服</button>
    </view>
  </view>
</template>
<style src="../../styles/shared.css"></style>
<style scoped>.card{margin-top:28rpx}.kv{display:flex;justify-content:space-between;gap:20rpx;padding:20rpx 0;border-bottom:1rpx solid var(--line)}.kv:last-child{border:0}.kv text:first-child{color:var(--muted)}.kv text:last-child{text-align:right;font-weight:700;overflow-wrap:anywhere}</style>
