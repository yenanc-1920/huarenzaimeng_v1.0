<script setup lang="ts">
const navigateBack=()=>uni.navigateBack()
import { computed, onMounted, onUnmounted, ref } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import StatusNotice from '../../components/StatusNotice.vue'
import { api } from '../../api/client'
import { ProjectApiError } from '../../api/project-envelope'
import { parseRechargeSelection, selectionMatchesCatalog } from '../../api/topup-recovery-contract'
import { fromQuote, isCompleteSnapshot, samePriceSnapshot } from '../../domain/price-snapshot'
import { freezeOrderSnapshot } from '../../domain/snapshot-flow'
import { getOrCreateCommand } from '../../domain/command-identity'
import type { QuoteSnapshot, RechargeSelection } from '../../domain/types'

const selection=ref<RechargeSelection|null>(null),quote=ref<QuoteSnapshot|null>(null),loading=ref(true),error=ref(''),creationPending=ref(false)
const now=ref(Date.now())
const timer=setInterval(()=>{now.value=Date.now()},1000)
onUnmounted(()=>clearInterval(timer))
const snapshotReady=computed(()=>!!quote.value&&!!selection.value&&quote.value.valid&&isCompleteSnapshot(fromQuote(quote.value))
  &&Date.parse(quote.value.validUntil)>now.value
  &&quote.value.supportedOperatorSetVersion===selection.value.supportedOperatorSetVersion
  &&quote.value.catalogVersion===selection.value.catalogVersion
  &&quote.value.productRef===selection.value.productRef&&quote.value.denominationRef===selection.value.denominationRef)

function invalidateSelection(message:string){selection.value=null;quote.value=null;uni.removeStorageSync('rechargeSelection');error.value=message}
async function currentSelectionIsValid(current:RechargeSelection){const catalog=await api.getCatalog(current.operatorCode);return selectionMatchesCatalog(current,catalog)}

onMounted(async()=>{
  try{
    const current=parseRechargeSelection(uni.getStorageSync('rechargeSelection'))
    selection.value=current;quote.value=await api.createQuote(current)
  }catch(caught){
    const code=caught instanceof ProjectApiError?caught.projectCode:caught instanceof Error?caught.message:'UNKNOWN'
    if(code==='BUYER_SESSION_REQUIRED'){
      quote.value=null;error.value='请先完成微信快捷登录，再重新选择商品获取报价。'
      uni.navigateTo({url:'/pages/auth/expired'})
    }else invalidateSelection('商品信息已更新，请返回重新选择。')
  }finally{loading.value=false}
})

async function createOrder(){
  if(!quote.value||!selection.value||!snapshotReady.value)return
  loading.value=true;error.value=''
  try{
    if(!await currentSelectionIsValid(selection.value)){invalidateSelection('可选项目已更新，请重新选择/确认。');return}
    let orderRef=quote.value.orderRef,confirmedSnapshot=fromQuote(quote.value)
  {
      const command=getOrCreateCommand(uni,`create-order:${quote.value.priceSnapshotRef}`)
      const result=await api.createOrder(quote.value!.priceSnapshotRef,command.commandId,command.idempotencyKey)
      if(result.quoteRef!==quote.value!.priceSnapshotRef||result.requestRef!==command.commandId||result.orderState!=='CREATED')throw new ProjectApiError('ORDER_SNAPSHOT_BINDING_MISMATCH')
      orderRef=result.orderRef;confirmedSnapshot=fromQuote(quote.value!)
      uni.setStorageSync(`orderCreation:${orderRef}`,result);freezeOrderSnapshot(uni,orderRef,confirmedSnapshot)
      await new Promise<void>((resolve,reject)=>uni.navigateTo({url:`/pages/payment/status?orderRef=${encodeURIComponent(orderRef)}`,success:()=>resolve(),fail:()=>reject(new Error('NAVIGATION_FAILED'))}))
      creationPending.value=false
      return
    }
  }catch(caught){
    const code=caught instanceof ProjectApiError?caught.projectCode:caught instanceof Error?caught.message:'UNKNOWN'
    if(code==='ORDER_CREATION_NOT_AVAILABLE'||code==='ORDER_CREATION_PRECONDITION_INVALID'){
      invalidateSelection('报价、目录版本或当前访问已变化，请重新建立访问并重新选择；未创建付款或充值动作。')
    }else if(code==='ORDER_CREATION_BUYER_SESSION_REQUIRED'||code==='UNAUTHORIZED'){
      error.value='当前访问已失效，请先重新建立订单访问；未创建付款或充值动作。'
    }else if(code==='IDEMPOTENCY_CONFLICT'){
      error.value='订单创建请求存在冲突，当前未继续付款或充值；请返回重新核对。'
    }else if(code==='NAVIGATION_FAILED'){
      error.value='订单已创建，但页面暂时无法打开；请从“我的订单”继续查看。'
    }else{
      creationPending.value=true;error.value='订单创建结果暂时无法确认；请使用原请求查询结果，当前不会继续付款或充值。'
    }
  }finally{loading.value=false}
}
const money=(n:number)=>(n/100).toFixed(2)
const displayMoney=(value:{minor:number;currency:string})=>`${value.currency==='CNY'?'¥':value.currency==='BDT'?'৳':value.currency} ${money(value.minor)}`
const remaining=computed(()=>{
  if(!quote.value)return''
  const seconds=Math.max(0,Math.floor((Date.parse(quote.value.validUntil)-now.value)/1000))
  return seconds>0?`${Math.floor(seconds/60).toString().padStart(2,'0')}:${(seconds%60).toString().padStart(2,'0')}`:'已过期'
})
</script>
<template><view class="page" data-page-id="UX-P012"><AppHeader title="订单确认" left="返回" @left="navigateBack"/><view class="content"><view class="progress"><text/><text/><text class="active"/></view><text class="step">第 3 步，共 3 步</text><view class="heading" role="heading" aria-level="1">订单确认</view><StatusNotice v-if="loading&&!quote" tone="unknown" title="正在读取报价">正在核对商品、面值和价格，请稍候。</StatusNotice><StatusNotice v-if="error" :tone="creationPending?'unknown':'risk'" :title="creationPending?'订单结果待确认':'暂时不能继续'">{{error}}</StatusNotice><view v-if="quote" class="summary"><view><text>充值号码</text><text>{{quote.maskedPhone}}</text></view><view><text>运营商</text><text>{{selection?.operatorName||quote.operatorName}}</text></view><view><text>充值商品</text><text>{{quote.entitlement.displayName}}</text></view><view><text>面值</text><text>{{displayMoney(quote.faceValue)}}</text></view><view><text>到账内容</text><text>{{quote.entitlement.benefitText}}</text></view><view v-if="quote.entitlement.validityText"><text>有效期</text><text>{{quote.entitlement.validityText}}</text></view></view><view v-if="quote" class="total-card"><view class="quote-expiry"><text>报价剩余时间</text><strong>{{remaining}}</strong></view><text>应付金额</text><strong>{{displayMoney(quote.total)}}</strong><small>已包含全部费用</small></view><text class="notice">请确认号码、运营商和充值商品无误。报价过期后需返回重新选择。</text><StatusNotice v-if="quote&&!snapshotReady" tone="risk" title="报价已失效">请返回重新选择后再继续。</StatusNotice></view><view class="action"><button class="primary" :disabled="!snapshotReady||loading" @click="createOrder">{{loading?'正在确认…':'确认订单'}}</button></view></view></template>
<style src="../../styles/shared.css"></style><style scoped>.quote-expiry{display:flex;align-items:center;justify-content:space-between;margin-bottom:24rpx;padding-bottom:20rpx;border-bottom:1rpx solid #cfdcf0}.quote-expiry strong{margin:0;font-size:28rpx;color:var(--brand)}.progress{display:flex;margin-bottom:20rpx}.progress text{height:8rpx;border-radius:8rpx;background:#d8dee7;flex:1;margin-right:12rpx}.progress text:last-child{margin-right:0}.progress .active{background:var(--brand)}.summary{margin-top:28rpx;padding:16rpx 30rpx;background:#fff;border-radius:36rpx}.summary view{display:flex;justify-content:space-between;padding:28rpx 0;border-bottom:1rpx solid var(--line)}.summary view:last-child{border:0}.summary text:first-child{color:var(--muted)}.summary text:last-child{font-weight:800;text-align:right}.total-card{margin-top:24rpx;padding:36rpx;background:#edf4ff;border-radius:36rpx}.total-card text,.total-card small{display:block;color:var(--muted)}.total-card>strong{display:block;font-size:60rpx;color:var(--brand-strong);margin:12rpx 0}.notice{display:block;margin-top:24rpx;padding:24rpx;border-radius:24rpx;background:#fff7e9;color:var(--unknown);font-size:24rpx;line-height:1.6}</style>
