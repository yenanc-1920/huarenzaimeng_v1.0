<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import AppHeader from '../../components/AppHeader.vue'
import { api } from '../../api/client'
import { loadAuthorizedOrderList } from '../../domain/order-list'
import { readSessionProjection } from '../../domain/session'
import type { OrderSummary, ProjectSessionProjection } from '../../domain/types'

const orders=ref<OrderSummary[]>([]),loading=ref(false),error=ref('')
const session=ref<ProjectSessionProjection>(readSessionProjection(uni))
const isGuest=computed(()=>session.value.role!=='BUYER')

async function load(){
  orders.value=[];error.value='';loading.value=true
  const loaded=await loadAuthorizedOrderList(uni,(current)=>api.getOrders(current))
  session.value=loaded.session;orders.value=loaded.orders
  if(loaded.outcome==='ERROR')error.value='访问已失效，请重新建立访问。'
  loading.value=false
}
onShow(load)

const open=(ref:string)=>uni.navigateTo({url:`/pages/order/detail?orderRef=${encodeURIComponent(ref)}`})
const status=(code:OrderSummary['stateCode'])=>({AWAITING_PAYMENT:'等待付款',PAYMENT_PROCESSING:'正在确认付款',PAID_AWAITING_TOPUP:'已付款，等待充值',TOPUP_PROCESSING:'正在充值',TOPUP_RESULT_UNKNOWN:'充值结果待确认',DELIVERED:'已到账',CONFIRMED_NOT_DELIVERED:'已确认未到账',REFUND_PROCESSING:'退款处理中',REFUNDED:'已退款',DELIVERY_REFUND_CONFLICT_REVIEW:'到账与退款记录核对中',SUPPORT_REVIEW:'客服处理中'}[code])
const tone=(code:OrderSummary['stateCode'])=>code==='DELIVERED'||code==='REFUNDED'?'complete':code==='CONFIRMED_NOT_DELIVERED'||code==='DELIVERY_REFUND_CONFLICT_REVIEW'||code==='SUPPORT_REVIEW'?'attention':'processing'
const time=(value:string)=>{const d=new Date(value);return Number.isNaN(d.getTime())?'更新时间待确认':`${d.getMonth()+1}月${d.getDate()}日 ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`}
</script>
<template><view class="page" data-page-root="order-list" :data-visible-state="isGuest?'GUEST':loading?'LOADING':error?'ERROR':orders.length?'READY':'EMPTY'"><AppHeader title="我的订单" left="首页" right="找回订单" @left="uni.reLaunch({url:'/pages/index/index'})" @right="uni.navigateTo({url:'/pages/order/recovery'})"/><view class="content orders"><view v-if="isGuest" class="card access-gate" role="status"><text class="card-title">需要重新建立访问</text><text v-if="error" class="attention">{{error}}</text><text class="copy">GUEST不能直接查看历史订单。恢复成功转为BUYER后，仅显示恢复证据绑定且当前授权的订单集合。</text><text class="privacy">这里不会确认订单是否存在，也不会展示数量、金额或号码尾号。</text><button class="primary" @click="uni.navigateTo({url:'/pages/order/recovery'})">恢复订单访问</button></view><template v-else><view class="tabs"><text class="active">全部</text><text>处理中</text><text>已完成</text></view><view v-if="loading" class="card" role="status">正在读取已授权订单…</view><view v-if="error" class="card" role="alert">{{error}}<button class="secondary retry" @click="load">重新读取</button></view><button v-for="item in orders" :key="item.orderRef" class="card order" :aria-label="`查看已授权订单 ${item.orderRef}`" @click="open(item.orderRef)"><view class="order-head"><text>{{time(item.updatedAt)}}</text><text :class="tone(item.stateCode)">{{status(item.stateCode)}}</text></view><view class="order-body"><text class="operator">充</text><view><text class="phone">已授权订单</text><text class="copy">仅显示当前服务端授权集合内的状态摘要</text></view></view><text v-if="item.stateCode==='TOPUP_RESULT_UNKNOWN'" class="hint">正在确认充值结果，请勿重复充值</text><text v-if="item.stateCode==='DELIVERY_REFUND_CONFLICT_REVIEW'||item.stateCode==='SUPPORT_REVIEW'" class="hint">结果仍在核对，你可以查看详情或联系我们</text></button><view v-if="!loading&&!error&&!orders.length" class="card empty">当前授权集合没有可展示的订单；这不证明其他主体是否存在订单。</view></template></view></view></template>
<style src="../../styles/shared.css"></style><style scoped>.orders{padding-top:24rpx}.tabs{display:flex;margin:20rpx 0 28rpx;border-bottom:1rpx solid var(--line)}.tabs text{padding:18rpx 0;margin-right:46rpx;color:var(--muted)}.tabs .active{color:var(--brand);font-weight:800;border-bottom:4rpx solid var(--brand)}.access-gate{padding:42rpx 34rpx}.access-gate .copy,.privacy{display:block;margin-top:18rpx;line-height:1.65}.privacy{color:var(--muted);font-size:23rpx}.access-gate button{margin-top:30rpx}.retry{margin-top:20rpx}.order{width:100%;text-align:left}.order-head,.order-body{display:flex;align-items:center;justify-content:space-between}.order-head{color:var(--muted);font-size:23rpx}.order-body{margin-top:26rpx}.operator{width:72rpx;height:72rpx;line-height:72rpx;border-radius:22rpx;background:#e7effc;color:var(--brand);text-align:center;font-weight:900;flex:none;margin-right:20rpx}.order-body view{flex:1}.phone,.amount{display:block;font-weight:800}.processing{color:var(--unknown)}.complete{color:var(--success)}.attention{color:var(--risk)}.hint{display:block;margin-top:22rpx;padding-top:20rpx;border-top:1rpx solid var(--line);color:var(--muted);font-size:23rpx}.empty{text-align:center;color:var(--muted);padding:70rpx 30rpx}</style>
