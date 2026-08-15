<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppHeader from '../../components/AppHeader.vue'
import StatusNotice from '../../components/StatusNotice.vue'
import { api, apiRuntime } from '../../api/client'
import { paymentIntentApi } from '../../api/payment-intent-client'
import { toOrderProjection, toQuoteSnapshot } from '../../api/project-contract'
import { parseOrderCreationResult, type OrderCreationProjection, type OrderCreationResult } from '../../api/order-creation-contract'
import { buildP013PaymentSummary, canCreateLocalSyntheticPaymentIntent, readPaymentIntentWriteIdentity, storePaymentIntentWriteIdentity } from '../../api/payment-intent-contract'
import { fromProjection, fromQuote, samePriceSnapshot } from '../../domain/price-snapshot'
import { validateOrderSnapshot } from '../../domain/snapshot-flow'
import { getOrCreateCommand } from '../../domain/command-identity'
import { buildP013AcceptedPageProjection, buildP013MissingWriteIdentityPageProjection, buildP013QueryErrorPageProjection, buildP013QueryPageProjection, buildP013RejectedPageProjection, createPaymentIntentWriteLatch, executePaymentIntentAttempt, executePaymentIntentReadOnlyQuery } from '../../domain/payment-intent-flow'
import { readSessionProjection } from '../../domain/session'
import type { OrderProjection } from '../../domain/types'

type ViewState='READY'|'SUBMITTING'|'CREATED'|'REPLAYED'|'REJECTED'|'UNKNOWN'|'NETWORK_ERROR'
const p=ref<OrderProjection|null>(null),created=ref<OrderCreationResult|null>(null),projection=ref<OrderCreationProjection|null>(null)
const orderRef=ref(''),message=ref(''),viewState=ref<ViewState>('READY'),writeLocked=ref(false)
const writeLatch=createPaymentIntentWriteLatch()
const frozenPriceSnapshot=computed(()=>created.value?.currentProjection.priceSnapshot||null)
const currentSnapshot=computed(()=>projection.value?fromQuote(toQuoteSnapshot(projection.value.priceSnapshot)):p.value?fromProjection(p.value):null)
const snapshotReady=computed(()=>!!currentSnapshot.value&&validateOrderSnapshot(uni,orderRef.value,currentSnapshot.value)
  &&(!frozenPriceSnapshot.value||samePriceSnapshot(currentSnapshot.value,fromQuote(toQuoteSnapshot(frozenPriceSnapshot.value)))))
const paymentSummary=computed(()=>snapshotReady.value&&frozenPriceSnapshot.value&&projection.value
  ?buildP013PaymentSummary(frozenPriceSnapshot.value,projection.value.priceSnapshot):null)
const canSubmit=computed(()=>!writeLocked.value&&viewState.value==='READY'&&!!paymentSummary.value&&!!projection.value&&!!frozenPriceSnapshot.value
  &&canCreateLocalSyntheticPaymentIntent(orderRef.value,readSessionProjection(uni),projection.value,frozenPriceSnapshot.value))
const showQuery=computed(()=>viewState.value==='CREATED'||viewState.value==='REPLAYED'||viewState.value==='UNKNOWN'||viewState.value==='NETWORK_ERROR')
const showLatestOrder=computed(()=>viewState.value==='REJECTED')

function setFailure(error:unknown){
  const code=error instanceof Error?error.message:String(error)
  if(code==='NETWORK_ERROR'){viewState.value='NETWORK_ERROR';message.value='网络中断，结果暂时无法确认；当前尚未付款，只能查询原请求的最新结果或离开本页。'}
  else if(code.includes('UNKNOWN')||code==='INVALID_ENVELOPE'){viewState.value='UNKNOWN';message.value='结果暂时无法确认；当前尚未付款，不会更换请求或再次提交。'}
  else if(code==='PAYMENT_INTENT_NOT_AVAILABLE'||code==='IDEMPOTENCY_CONFLICT'){
    const rejected=buildP013RejectedPageProjection(code)
    viewState.value=rejected.viewState;message.value=rejected.message
  }
  else{viewState.value='REJECTED';message.value='当前访问、报价或订单状态不满足继续条件；尚未付款，请查询最新订单或离开本页。'}
}

async function refresh(forceLatest=false){
  if(!orderRef.value)return
  message.value=''
  try{
    if(created.value&&!forceLatest){projection.value=created.value.currentProjection;return}
    if(apiRuntime.mode!=='BUILTIN_MOCK'){
      const core=await api.getCoreProjection(orderRef.value)
      projection.value={orderRef:core.orderRef,quoteRef:core.quoteRef,stateCode:core.orderState as OrderCreationProjection['stateCode'],
        priceSnapshot:core.priceSnapshot,projectionVersion:core.projectionVersion,aggregateVersion:core.aggregateVersion,allowedActions:core.allowedActions}
      p.value=toOrderProjection(core)
    }else p.value=await api.getProjection(orderRef.value)
  }catch{message.value='订单状态暂时无法读取；当前不会继续付款。'}
}

async function preparePayment(){
  if(writeLocked.value||viewState.value!=='READY'||!canSubmit.value||!projection.value||!frozenPriceSnapshot.value)return
  if(!writeLatch.tryLock())return
  writeLocked.value=true;viewState.value='SUBMITTING';message.value=''
  const session=readSessionProjection(uni)
  const identity=getOrCreateCommand(uni,`payment-intent:${orderRef.value}`)
  try{storePaymentIntentWriteIdentity(uni,orderRef.value,identity,session)}catch(error){setFailure(error);return}
  const attempt=await executePaymentIntentAttempt('M2-UI-029-P013-INTENT-ACCEPTED-NOT-PAID',{
    createPaymentIntent:()=>paymentIntentApi.create(orderRef.value,identity,projection.value!,frozenPriceSnapshot.value!),
    acceptProjection:(result)=>{
      const accepted=buildP013AcceptedPageProjection(result)
      projection.value=result.currentProjection
      viewState.value=accepted.viewState
      message.value=accepted.message
    },
    navigate:()=>undefined,
    automaticRetry:()=>undefined,
  })
  if(attempt.error)setFailure(attempt.error)
}

async function queryOriginalResult(){
  if(!projection.value||!frozenPriceSnapshot.value)return
  const identity=readPaymentIntentWriteIdentity(uni,orderRef.value,readSessionProjection(uni))
  if(!identity){
    const page=buildP013MissingWriteIdentityPageProjection()
    viewState.value=page.viewState;message.value=page.message
    return
  }
  const query=await executePaymentIntentReadOnlyQuery('M2-UI-031-P013-UNKNOWN-OR-NETWORK',
    ()=>paymentIntentApi.query(orderRef.value,identity,projection.value!,frozenPriceSnapshot.value!))
  try{
    if(query.error)throw query.error
    const result=query.result!
    const page=buildP013QueryPageProjection(result)
    if(result.outcome==='FOUND')projection.value=result.currentProjection
    viewState.value=page.viewState;message.value=page.message
  }catch{
    const page=buildP013QueryErrorPageProjection()
    viewState.value=page.viewState;message.value=page.message
  }
}

const money=(minor:number,currency:string)=>`${currency==='CNY'?'¥':currency==='BDT'?'৳':currency} ${(minor/100).toFixed(2)} ${currency}`
const dateTime=(value:string)=>new Date(value).toLocaleString('zh-CN',{hour12:false})
onLoad(query=>{
  orderRef.value=typeof query?.orderRef==='string'?decodeURIComponent(query.orderRef):''
  try{
    const cached=parseOrderCreationResult(uni.getStorageSync(`orderCreation:${orderRef.value}`))
    if(cached.resourceRef!==orderRef.value)throw new Error('ORDER_REF_MISMATCH')
    created.value=cached;projection.value=cached.currentProjection
  }catch{uni.removeStorageSync(`orderCreation:${orderRef.value}`)}
  refresh()
})
</script>

<template>
  <view class="page">
    <AppHeader title="准备付款" left="返回" @left="uni.navigateBack()"/>
    <view class="content">
      <text class="step">订单已建立</text>
      <view class="heading" role="heading" aria-level="1">尚未付款</view>
      <text class="desc">本地演示不会发起真实付款；只有订单、报价和访问状态一致时才能准备付款。</text>
      <StatusNotice v-if="created" tone="success" :title="created.projectCode==='ORDER_CREATED'?'订单已建立':'已找到原订单'">请核对下方付款摘要。</StatusNotice>
      <StatusNotice v-if="message" :tone="viewState==='CREATED'||viewState==='REPLAYED'?'success':viewState==='UNKNOWN'||viewState==='NETWORK_ERROR'?'unknown':'risk'" :title="viewState==='CREATED'||viewState==='REPLAYED'?'正在确认付款资格':viewState==='UNKNOWN'||viewState==='NETWORK_ERROR'?'查询最新结果':'暂时不能继续'">{{message}}</StatusNotice>
      <StatusNotice v-if="orderRef&&!paymentSummary" tone="risk" title="付款摘要无法确认">订单与冻结报价缺失或不一致，当前不会提交付款准备请求。</StatusNotice>
      <view v-if="paymentSummary" class="summary">
        <view><text>应付总价</text><text>{{money(paymentSummary.totalAmountMinor,paymentSummary.currency)}}</text></view>
        <view><text>充值号码</text><text>{{paymentSummary.maskedPhone}}</text></view>
        <view><text>运营商标识</text><text>{{paymentSummary.operatorCode}}</text></view>
        <view><text>商品标识</text><text>{{paymentSummary.productCode}}</text></view>
        <view><text>面额标识</text><text>{{paymentSummary.denominationRef}}</text></view>
        <view><text>支持集 / 目录版本</text><text>v{{paymentSummary.supportedOperatorSetVersion}} / v{{paymentSummary.catalogVersion}}</text></view>
        <view><text>报价有效期</text><text>{{dateTime(paymentSummary.expiresAt)}}</text></view>
      </view>
    </view>
    <view class="action">
      <button v-if="!writeLocked" class="primary" :disabled="!canSubmit" @click="preparePayment">准备付款</button>
      <button v-if="showQuery" class="primary" @click="queryOriginalResult">查询最新结果</button>
      <button v-if="showLatestOrder" class="secondary" @click="refresh(true)">查询最新订单</button>
    </view>
  </view>
</template>
<style src="../../styles/shared.css"></style>
<style scoped>.summary{margin-top:24rpx;padding:16rpx 30rpx;background:#fff;border-radius:36rpx}.summary view{display:flex;justify-content:space-between;gap:24rpx;padding:24rpx 0;border-bottom:1rpx solid var(--line)}.summary view:last-child{border:0}.summary text:first-child{color:var(--muted)}.summary text:last-child{font-weight:700;text-align:right;overflow-wrap:anywhere}</style>
