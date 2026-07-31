<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import StatusNotice from '../../components/StatusNotice.vue'
import { api, apiRuntime } from '../../api/client'
import { fromQuote, isCompleteSnapshot } from '../../domain/price-snapshot'
import { freezeOrderSnapshot } from '../../domain/snapshot-flow'
import { getOrCreateCommand } from '../../domain/command-identity'
import type { QuoteSnapshot } from '../../domain/types'
const quote=ref<QuoteSnapshot|null>(null),loading=ref(true),error=ref('')
const snapshotReady=computed(()=>!!quote.value&&quote.value.valid&&isCompleteSnapshot(fromQuote(quote.value))&&Date.parse(quote.value.validUntil)>Date.now())
onMounted(async()=>{const s=uni.getStorageSync('rechargeSelection')as{maskedPhone?:string;productRef?:string};if(!s?.maskedPhone||!s?.productRef){error.value='缺少有效的号码与商品选择。';loading.value=false;return}try{quote.value=await api.createQuote(s.maskedPhone,s.productRef)}catch{error.value='价格快照读取失败。'}finally{loading.value=false}})
async function createOrder(){if(!quote.value||!snapshotReady.value)return;loading.value=true;error.value='';try{let orderRef=quote.value.orderRef;if(apiRuntime.mode==='PROJECT_MOCK_API'){const command=getOrCreateCommand(uni,`create-order:${quote.value.priceSnapshotRef}`),projection=await api.createOrder(quote.value.priceSnapshotRef,command.commandId,command.idempotencyKey);orderRef=projection.orderRef;uni.setStorageSync(`coreProjection:${orderRef}`,projection)}if(!orderRef)throw new Error('ORDER_REF_MISSING');freezeOrderSnapshot(uni,orderRef,fromQuote(quote.value));uni.navigateTo({url:`/pages/payment/status?orderRef=${encodeURIComponent(orderRef)}`})}catch{error.value='创建订单未确认；保留原命令身份，可安全重试。'}finally{loading.value=false}}
const money=(n:number)=>(n/100).toFixed(2)
</script>
<template><view class="page"><AppHeader left="← 返回" @left="uni.navigateBack()"/><view class="content"><view class="heading" role="heading" aria-level="1">确认充值信息</view><StatusNotice v-if="error" tone="risk" title="暂时不能继续">{{error}}</StatusNotice><view v-if="quote" class="card"><view class="kv"><text>号码/运营商</text><text>{{quote.maskedPhone}} / {{quote.operatorName}}</text></view><view class="kv"><text>商品</text><text>{{quote.productName}}</text></view><view class="kv"><text>最终实付</text><text>{{quote.total.currency}} {{money(quote.total.minor)}}</text></view><view class="kv"><text>价格快照</text><text>{{quote.priceSnapshotRef}} / {{quote.priceVersion}} / {{quote.validUntil}}</text></view></view><StatusNotice v-if="quote&&!snapshotReady" tone="risk" title="STALE / 快照不一致">任一字段缺失、变化或过期，确认资格为 0。</StatusNotice></view><view class="action"><button class="primary" :disabled="!snapshotReady||loading" @click="createOrder">创建订单并继续</button></view></view></template>
<style src="../../styles/shared.css"></style>
