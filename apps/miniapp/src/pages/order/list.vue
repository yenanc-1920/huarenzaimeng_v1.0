<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import AppHeader from '../../components/AppHeader.vue'
import { api } from '../../api/client'
import { loadAuthorizedOrderList } from '../../domain/order-list'
import type { OrderSummary } from '../../domain/types'
const orders=ref<OrderSummary[]>([]),loading=ref(false),error=ref(''),needsAccess=ref(false)
const goHome=()=>uni.reLaunch({url:'/pages/index/index'}),goRecovery=()=>uni.navigateTo({url:'/pages/order/recovery'})
async function load(){orders.value=[];error.value='';loading.value=true;const loaded=await loadAuthorizedOrderList(uni,current=>api.getOrders(current));orders.value=loaded.orders;needsAccess.value=loaded.session.role!=='BUYER';if(loaded.outcome==='ERROR')error.value='暂时无法读取订单，请重新登录或稍后再试。';loading.value=false}
onShow(load)
const open=(orderRef:string)=>uni.navigateTo({url:`/pages/order/detail?orderRef=${encodeURIComponent(orderRef)}`})
const status=(code:OrderSummary['stateCode'])=>({AWAITING_PAYMENT:'等待付款',PAYMENT_PROCESSING:'正在确认付款',PAID_AWAITING_TOPUP:'已付款，等待充值',TOPUP_PROCESSING:'正在充值',TOPUP_RESULT_UNKNOWN:'充值结果待确认',DELIVERED:'已到账',CONFIRMED_NOT_DELIVERED:'未到账',REFUND_PROCESSING:'退款处理中',REFUNDED:'已退款',DELIVERY_REFUND_CONFLICT_REVIEW:'结果核对中',SUPPORT_REVIEW:'客服处理中'}[code])
</script>
<template><view class="page" data-page-root="order-list"><AppHeader title="我的订单" left="首页" right="找回订单" @left="goHome" @right="goRecovery"/><view class="content"><view v-if="needsAccess" class="card"><text class="card-title">登录后查看订单</text><text class="copy">请先登录；若更换设备或历史订单未显示，可使用找回订单。</text><button class="primary" @click="goRecovery">找回订单</button></view><view v-else-if="loading" class="card">正在读取订单…</view><view v-else-if="error" class="card"><text>{{error}}</text><button @click="load">重新加载</button></view><button v-for="item in orders" :key="item.orderRef" class="card order" @click="open(item.orderRef)"><text class="card-title">充值订单</text><text>{{status(item.stateCode)}}</text><text class="copy">更新时间：{{item.updatedAt}}</text></button><view v-if="!needsAccess&&!loading&&!error&&!orders.length" class="card">暂无订单</view></view></view></template>
<style src="../../styles/shared.css"></style><style scoped>.order{width:100%;text-align:left}.order text{display:block;margin-top:8rpx}</style>
