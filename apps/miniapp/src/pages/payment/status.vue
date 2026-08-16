<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppHeader from '../../components/AppHeader.vue'
import StatusNotice from '../../components/StatusNotice.vue'
import { api } from '../../api/client'
import type { ProjectProjection } from '../../api/project-contract'
const orderRef=ref(''),projection=ref<ProjectProjection|null>(null),loading=ref(true),error=ref('')
const navigateBack=()=>uni.navigateBack()
async function load(){if(!orderRef.value)return;loading.value=true;error.value='';try{projection.value=await api.getCoreProjection(orderRef.value)}catch{error.value='订单状态暂时无法读取，请稍后重试。'}finally{loading.value=false}}
onLoad(query=>{orderRef.value=typeof query?.orderRef==='string'?decodeURIComponent(query.orderRef):'';void load()})
</script>
<template><view class="page"><AppHeader title="订单付款" left="返回" @left="navigateBack"/><view class="content"><view class="heading">订单已建立</view><text class="desc">请核对订单信息</text><view v-if="loading" class="card">正在读取订单…</view><StatusNotice v-else-if="error" tone="risk" title="暂时无法读取">{{error}}</StatusNotice><template v-else-if="projection"><view class="card"><view class="kv"><text>订单号</text><text>{{projection.orderRef}}</text></view><view class="kv"><text>当前状态</text><text>{{projection.orderState}}</text></view><view class="kv"><text>应付金额</text><text>¥ {{(projection.priceSnapshot.totalAmountMinor/100).toFixed(2)}}</text></view></view><StatusNotice tone="unknown" title="支付暂不可用">当前开发环境尚未配置外部支付通道，不会发起扣款或充值。</StatusNotice></template><button class="secondary" @click="load">刷新订单状态</button></view></view></template>
<style src="../../styles/shared.css"></style><style scoped>.kv{display:flex;justify-content:space-between;gap:20rpx;padding:20rpx 0;border-bottom:1rpx solid var(--line)}.kv text:last-child{text-align:right}.secondary{margin-top:24rpx}</style>
