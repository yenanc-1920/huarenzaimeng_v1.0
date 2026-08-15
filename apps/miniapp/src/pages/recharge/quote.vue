<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import StatusNotice from '../../components/StatusNotice.vue'
import { api, apiRuntime } from '../../api/client'
import { ProjectApiError } from '../../api/project-envelope'
import { toQuoteSnapshot } from '../../api/project-contract'
import { parseRechargeSelection, selectionMatchesCatalog } from '../../api/topup-recovery-contract'
import { fromQuote, isCompleteSnapshot, samePriceSnapshot } from '../../domain/price-snapshot'
import { freezeOrderSnapshot } from '../../domain/snapshot-flow'
import { getOrCreateCommand } from '../../domain/command-identity'
import { createOrderCreationNavigationLedger, executeOrderCreationAttempt } from '../../domain/order-creation-flow'
import type { QuoteSnapshot, RechargeSelection } from '../../domain/types'

const selection=ref<RechargeSelection|null>(null),quote=ref<QuoteSnapshot|null>(null),loading=ref(true),error=ref(''),creationPending=ref(false)
const navigationLedger=createOrderCreationNavigationLedger()
const snapshotReady=computed(()=>!!quote.value&&!!selection.value&&quote.value.valid&&isCompleteSnapshot(fromQuote(quote.value))
  &&Date.parse(quote.value.validUntil)>Date.now()
  &&quote.value.supportedOperatorSetVersion===selection.value.supportedOperatorSetVersion
  &&quote.value.catalogVersion===selection.value.catalogVersion
  &&quote.value.productRef===selection.value.productRef&&quote.value.denominationRef===selection.value.denominationRef)

function invalidateSelection(message:string){selection.value=null;quote.value=null;uni.removeStorageSync('rechargeSelection');error.value=message}
async function currentSelectionIsValid(current:RechargeSelection){const catalog=await api.getCatalog(current.operatorCode);return selectionMatchesCatalog(current,catalog)}

onMounted(async()=>{
  try{
    const current=parseRechargeSelection(uni.getStorageSync('rechargeSelection'))
    if(!await currentSelectionIsValid(current)){invalidateSelection('支持集或目录已更新，请返回重新选择。');return}
    selection.value=current;quote.value=await api.createQuote(current)
  }catch{invalidateSelection('缺少有效的预设项目，或当前版本已变化，请返回重新选择。')}finally{loading.value=false}
})

async function createOrder(){
  if(!quote.value||!selection.value||!snapshotReady.value)return
  loading.value=true;error.value=''
  try{
    if(!await currentSelectionIsValid(selection.value)){invalidateSelection('可选项目已更新，请重新选择/确认。');return}
    let orderRef=quote.value.orderRef,confirmedSnapshot=fromQuote(quote.value)
    if(apiRuntime.mode!=='BUILTIN_MOCK'){
      const command=getOrCreateCommand(uni,`create-order:${quote.value.priceSnapshotRef}`)
      const attempt=await executeOrderCreationAttempt('P012-RUNTIME',{
        createOrder:()=>api.createOrder(quote.value!.priceSnapshotRef,command.commandId,command.idempotencyKey),
        acceptProjection:(result)=>{
          const serverQuote=toQuoteSnapshot(result.currentProjection.priceSnapshot)
          if(!samePriceSnapshot(fromQuote(quote.value!),fromQuote(serverQuote)))throw new ProjectApiError('ORDER_PRICE_SNAPSHOT_MISMATCH')
          orderRef=result.resourceRef;confirmedSnapshot=fromQuote(serverQuote)
          uni.setStorageSync(`orderCreation:${orderRef}`,result);freezeOrderSnapshot(uni,orderRef,confirmedSnapshot)
        },
        navigate:(createdOrderRef)=>new Promise<void>((resolve,reject)=>uni.navigateTo({url:`/pages/payment/status?orderRef=${encodeURIComponent(createdOrderRef)}`,success:()=>resolve(),fail:()=>reject(new Error('NAVIGATION_FAILED'))})),
        automaticRetry:()=>undefined,
      },navigationLedger)
      if(attempt.error)throw attempt.error
      creationPending.value=false
      if(attempt.evidence.navigationCount===0)error.value='已找到原订单，未重复创建或重复打开页面；可从“我的订单”继续查看。'
      return
    }
    if(!orderRef)throw new Error('ORDER_REF_MISSING')
    freezeOrderSnapshot(uni,orderRef,confirmedSnapshot)
    creationPending.value=false
    if(navigationLedger.has(orderRef))return
    uni.navigateTo({url:`/pages/payment/status?orderRef=${encodeURIComponent(orderRef)}`,success:()=>navigationLedger.add(orderRef),fail:()=>{
      error.value='订单已创建，但页面暂时无法打开；请从“我的订单”继续查看。'
    }})
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
</script>
<template><view class="page"><AppHeader title="确认充值" left="返回" @left="uni.navigateBack()"/><view class="content"><view class="progress"><text/><text/><text class="active"/></view><text class="step">第 3 步，共 3 步</text><view class="heading" role="heading" aria-level="1">请核对充值信息</view><StatusNotice v-if="error" :tone="creationPending?'unknown':'risk'" :title="creationPending?'订单结果待确认':'暂时不能继续'">{{error}}</StatusNotice><view v-if="quote" class="summary"><view><text>充值号码</text><text>{{quote.maskedPhone}}</text></view><view><text>运营商</text><text>{{quote.operatorName}}</text></view><view><text>预设项目</text><text>{{quote.productName}}</text></view><view><text>到账金额</text><text>{{money(quote.faceValue.minor)}} {{quote.faceValue.currency}}</text></view><view><text>支持集 / 目录版本</text><text>v{{quote.supportedOperatorSetVersion}} / v{{quote.catalogVersion}}</text></view></view><view v-if="quote" class="total-card"><text>应付总价</text><strong>{{displayMoney(quote.total)}}</strong><small>已包含全部费用</small></view><text class="notice">请确认号码和预设项目无误；支持集、目录或价格变化后必须返回重选。</text><StatusNotice v-if="quote&&!snapshotReady" tone="risk" title="可选项目已更新">请返回重新选择/确认后再继续。</StatusNotice></view><view class="action"><button class="primary" :disabled="!snapshotReady||loading" @click="createOrder">{{loading?'正在确认…':creationPending?'查询订单创建结果':quote?`确认并创建订单 ${displayMoney(quote.total)}`:'确认并创建订单'}}</button></view></view></template>
<style src="../../styles/shared.css"></style><style scoped>.progress{display:flex;margin-bottom:20rpx}.progress text{height:8rpx;border-radius:8rpx;background:#d8dee7;flex:1;margin-right:12rpx}.progress text:last-child{margin-right:0}.progress .active{background:var(--brand)}.summary{margin-top:28rpx;padding:16rpx 30rpx;background:#fff;border-radius:36rpx}.summary view{display:flex;justify-content:space-between;padding:28rpx 0;border-bottom:1rpx solid var(--line)}.summary view:last-child{border:0}.summary text:first-child{color:var(--muted)}.summary text:last-child{font-weight:800;text-align:right}.total-card{margin-top:24rpx;padding:36rpx;background:#edf4ff;border-radius:36rpx}.total-card text,.total-card small{display:block;color:var(--muted)}.total-card strong{display:block;font-size:60rpx;color:var(--brand-strong);margin:12rpx 0}.notice{display:block;margin-top:24rpx;padding:24rpx;border-radius:24rpx;background:#fff7e9;color:var(--unknown);font-size:24rpx;line-height:1.6}</style>
