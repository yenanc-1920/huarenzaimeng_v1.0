<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import AppHeader from '../../components/AppHeader.vue'
import StatusNotice from '../../components/StatusNotice.vue'
import { api } from '../../api/client'
import { performRecovery, performRecoveryPoll, readPendingRecoveryCaseRef } from '../../domain/session'
import type { RecoveryResult } from '../../domain/types'
const orderRef=ref(''),verificationCode=ref(''),loading=ref(false),error=ref(''),result=ref<RecoveryResult|null>(null),pendingCaseRef=ref<string|null>(null)
const navigateBack=()=>uni.navigateBack(),openSupport=()=>uni.navigateTo({url:'/pages/support/case'})
onShow(()=>{pendingCaseRef.value=readPendingRecoveryCaseRef(uni)})
function consume(value:RecoveryResult){result.value=value;pendingCaseRef.value=readPendingRecoveryCaseRef(uni);if(value.outcome==='RECOVERED')uni.reLaunch({url:'/pages/order/list'})}
async function submit(){loading.value=true;error.value='';try{consume(await performRecovery(uni,()=>api.recoverOrder(orderRef.value,verificationCode.value)))}catch{error.value='暂时无法确认找回结果，请稍后查询。'}finally{loading.value=false}}
async function poll(){loading.value=true;error.value='';try{consume(await performRecoveryPoll(uni,ref=>api.getRecoveryCase(ref)))}catch{error.value='暂时无法读取找回结果，请稍后再试。'}finally{loading.value=false}}
</script>
<template><view class="page"><AppHeader title="找回订单" left="返回" @left="navigateBack"/><view class="content"><view class="heading">找回历史订单</view><text class="desc">请输入下单时保存的订单号和校验码。</text><template v-if="!pendingCaseRef"><view class="field"><text>订单号</text><input v-model="orderRef" placeholder="请输入订单号"/></view><view class="field"><text>校验码</text><input v-model="verificationCode" password placeholder="请输入校验码"/></view></template><StatusNotice v-else tone="unknown" title="正在确认找回结果">请查询当前请求，无需重复提交。</StatusNotice><StatusNotice v-if="error" tone="unknown" title="暂时无法确认">{{error}}</StatusNotice><StatusNotice v-if="result?.outcome==='REJECTED'" tone="risk" title="未能找回订单">请检查输入，或联系客服协助处理。</StatusNotice></view><view class="action"><button v-if="pendingCaseRef" class="primary" :disabled="loading" @click="poll">查询结果</button><button v-else class="primary" :disabled="loading||!orderRef||!verificationCode" @click="submit">提交找回</button><button class="secondary" @click="openSupport">联系客服</button></view></view></template>
<style src="../../styles/shared.css"></style><style scoped>.field{margin-top:24rpx;padding:24rpx;border-radius:22rpx;background:#fff}.field text{display:block;margin-bottom:12rpx;color:var(--muted)}</style>
