<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppHeader from '../../components/AppHeader.vue'
import StatusNotice from '../../components/StatusNotice.vue'
import { api } from '../../api/client'
import type { ReleaseOrderProjection } from '../../api/formal-transaction-contract'
import { readSessionProjection } from '../../domain/session'

const orderRef=ref(''),projection=ref<ReleaseOrderProjection|null>(null),loading=ref(false),error=ref('')
const paymentLabel=computed(()=>projection.value?.paymentState||'暂时无法确认')
const topupLabel=computed(()=>projection.value?.deliveryState||projection.value?.upstreamDebitState||'暂时无法确认')

async function refresh(){
  if(!orderRef.value)return
  loading.value=true;error.value='';projection.value=null
  try{
    if(readSessionProjection(uni).role!=='BUYER')throw new Error('BUYER_SESSION_REQUIRED')
    projection.value=await api.getP014Progress(orderRef.value)
  }catch{error.value='订单进度暂时无法读取，请登录后稍后刷新。'}finally{loading.value=false}
}
const leave=()=>uni.navigateBack({fail:()=>uni.reLaunch({url:'/pages/index/index'})})
onLoad(query=>{orderRef.value=typeof query?.orderRef==='string'?decodeURIComponent(query.orderRef):'';void refresh()})
</script>

<template>
  <view class="page" data-page-id="UX-P014" data-page-root="p014-progress" data-submit-source="trusted-paid-order-only">
    <AppHeader title="充值进度" left="订单" @left="leave"/>
    <view class="content">
      <view class="heading" role="heading" aria-level="1">充值进度</view>
      <StatusNotice v-if="loading" tone="unknown" title="正在读取">正在查询原订单状态，期间不会重复支付或重复充值。</StatusNotice>
      <StatusNotice v-else-if="error" tone="unknown" title="交易状态待确认">{{error}}</StatusNotice>
      <view v-if="projection" class="card">
        <view class="kv"><text>订单号</text><text>{{orderRef}}</text></view>
        <view class="kv"><text>支付状态</text><text>{{paymentLabel}}</text></view>
        <view class="kv"><text>充值状态</text><text>{{topupLabel}}</text></view>
        <view class="kv"><text>退款状态</text><text>{{projection.refundState}}</text></view>
      </view>
      <StatusNotice v-if="projection" tone="unknown" title="只读进度">本页仅查询服务端订单状态；不会发起支付、充值、退款或自动重试。</StatusNotice>
    </view>
    <view class="action">
      <button class="secondary" :disabled="loading" @click="refresh">刷新状态</button>
      <button class="secondary" open-type="contact">打开微信客服</button>
      <button class="secondary" @click="leave">返回</button>
    </view>
  </view>
</template>

<style src="../../styles/shared.css"></style>
<style scoped>.card{margin-top:28rpx}.kv{display:flex;justify-content:space-between;gap:20rpx;padding:20rpx 0;border-bottom:1rpx solid var(--line)}.kv:last-child{border:0}.kv text:first-child{color:var(--muted)}.kv text:last-child{text-align:right;font-weight:700;overflow-wrap:anywhere}</style>
