<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppHeader from '../../components/AppHeader.vue'
import StatusNotice from '../../components/StatusNotice.vue'
import { api } from '../../api/client'
import type { PaymentView, TopupView } from '../../api/formal-transaction-contract'
import { paymentIsTrustedPaid, topupMayQueryOriginal } from '../../api/formal-transaction-contract'
import { getOrCreateCommand } from '../../domain/command-identity'

const orderRef=ref(''),payment=ref<PaymentView|null>(null),topup=ref<TopupView|null>(null),loading=ref(false),error=ref(''),channelDisabled=ref(false)
const paymentPaid=computed(()=>!!payment.value&&paymentIsTrustedPaid(payment.value))
const canSubmit=computed(()=>paymentPaid.value&&!topup.value&&!channelDisabled.value)
const canQuery=computed(()=>!!topup.value&&topupMayQueryOriginal(topup.value)&&!channelDisabled.value)
const paymentLabels:Record<string,string>={PAID:'支付成功',REFUNDED:'已退款',UNKNOWN:'支付结果待确认',PROCESSING:'支付处理中',PREPAY_CREATED:'待支付',NEW:'待支付',CLOSED:'已关闭',REFUND_PROCESSING:'退款处理中',REJECTED:'支付失败'}
const topupLabels:Record<string,string>={RESERVED:'已锁定充值权益',SUBMITTED:'已提交充值',PROCESSING:'充值处理中',DELIVERED:'充值已到账',REJECTED:'充值失败',UNKNOWN:'充值结果待确认',RECONCILIATION_REQUIRED:'需要人工复核'}
const paymentLabel=computed(()=>paymentLabels[payment.value?.state||'UNKNOWN']||'暂时无法确认')
const topupLabel=computed(()=>topupLabels[topup.value?.state||'UNKNOWN']||'暂时无法确认')

async function refresh(){
  if(!orderRef.value)return
  loading.value=true;error.value='';channelDisabled.value=false
  try{
    payment.value=await api.getPaymentStatus(orderRef.value)
    try{topup.value=await api.getTopupStatus(orderRef.value)}catch{topup.value=null}
  }catch{payment.value=null;topup.value=null;error.value='订单交易状态暂时无法读取，请稍后刷新。'}finally{loading.value=false}
}
async function submit(){
  if(!canSubmit.value)return
  loading.value=true;error.value=''
  try{
    const identity=getOrCreateCommand(uni,`topup-submit:${orderRef.value}`)
    topup.value=await api.submitTopup(orderRef.value,identity.commandId)
  }catch(caught){
    channelDisabled.value=caught instanceof Error&&caught.message==='TRANSACTION_CHANNEL_DISABLED'
    error.value=channelDisabled.value?'充值通道当前不可用，本次没有向运营商提交充值。':'充值提交结果暂时无法确认；当前不会自动重试或重复提交。'
  }finally{loading.value=false}
}
async function queryOriginal(){
  if(!canQuery.value)return
  loading.value=true;error.value=''
  try{topup.value=await api.queryTopupOriginal(orderRef.value)}catch(caught){channelDisabled.value=caught instanceof Error&&caught.message==='TRANSACTION_CHANNEL_DISABLED';error.value=channelDisabled.value?'充值通道当前不可用，请稍后查询原交易。':'原充值结果暂时无法确认，当前不会重新提交。'}finally{loading.value=false}
}
const leave=()=>uni.navigateBack({fail:()=>uni.reLaunch({url:'/pages/index/index'})})
onLoad(query=>{orderRef.value=typeof query?.orderRef==='string'?decodeURIComponent(query.orderRef):'';void refresh()})
</script>

<template>
  <view class="page" data-page-root="p014-progress" data-submit-source="trusted-paid-order-only">
    <AppHeader title="充值进度" left="订单" @left="leave"/>
    <view class="content">
      <view class="heading" role="heading" aria-level="1">充值进度</view>
      <StatusNotice v-if="loading" tone="unknown" title="正在读取">正在查询原订单状态，期间不会重复支付或重复充值。</StatusNotice>
      <StatusNotice v-else-if="error" tone="unknown" title="交易状态待确认">{{error}}</StatusNotice>
      <view v-if="payment" class="card">
        <view class="kv"><text>订单号</text><text>{{orderRef}}</text></view>
        <view class="kv"><text>支付状态</text><text>{{paymentLabel}}</text></view>
        <view v-if="topup" class="kv"><text>充值状态</text><text>{{topupLabel}}</text></view>
        <view v-if="topup?.providerRef" class="kv"><text>供应商受理号</text><text>{{topup.providerRef}}</text></view>
      </view>
      <StatusNotice v-if="payment&&!paymentPaid" tone="unknown" title="尚未确认付款">只有后端确认支付为成功终态后才会开放充值；当前不会提交充值。</StatusNotice>
      <StatusNotice v-else-if="paymentPaid&&!topup" tone="unknown" title="等待充值提交">付款已确认。充值通道未配置或尚未提交时，不会自动生成充值结果。</StatusNotice>
      <StatusNotice v-if="topup?.state==='UNKNOWN'" tone="unknown" title="充值结果待确认">只能查询原交易，不能再次提交充值。</StatusNotice>
    </view>
    <view class="action">
      <button v-if="canSubmit" class="primary" :disabled="loading" @click="submit">提交充值</button>
      <button v-if="canQuery" class="primary" :disabled="loading" @click="queryOriginal">查询原充值结果</button>
      <button class="secondary" :disabled="loading" @click="refresh">刷新状态</button>
      <button class="secondary" open-type="contact">打开微信客服</button>
      <button class="secondary" @click="leave">返回</button>
    </view>
  </view>
</template>

<style src="../../styles/shared.css"></style>
<style scoped>.card{margin-top:28rpx}.kv{display:flex;justify-content:space-between;gap:20rpx;padding:20rpx 0;border-bottom:1rpx solid var(--line)}.kv:last-child{border:0}.kv text:first-child{color:var(--muted)}.kv text:last-child{text-align:right;font-weight:700;overflow-wrap:anywhere}</style>
