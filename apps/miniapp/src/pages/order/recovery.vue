<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import AppHeader from '../../components/AppHeader.vue'
import StatusNotice from '../../components/StatusNotice.vue'
import { api } from '../../api/client'
import { performRecovery, performRecoveryPoll, readPendingRecoveryCaseRef } from '../../domain/session'
import type { RecoveryResult } from '../../domain/types'

const orderRef=ref(''),recoveryMaterialRef=ref(''),loading=ref(false),error=ref(''),result=ref<RecoveryResult|null>(null),pendingCaseRef=ref<string|null>(null)
onShow(()=>{pendingCaseRef.value=readPendingRecoveryCaseRef(uni)})
function consume(recovered:RecoveryResult){
  result.value=recovered;pendingCaseRef.value=readPendingRecoveryCaseRef(uni)
  if(recovered.outcome==='RECOVERED')uni.reLaunch({url:'/pages/order/list'})
}
async function submit(){
  loading.value=true;error.value='';result.value=null
  try{
    consume(await performRecovery(uni,()=>api.recoverOrder(orderRef.value,recoveryMaterialRef.value)))
  }catch{error.value='暂时无法确认恢复结果；当前仍是GUEST，请保留原请求后只读重试。'}finally{loading.value=false}
}
async function poll(){
  loading.value=true;error.value='';result.value=null
  try{consume(await performRecoveryPoll(uni,(recoveryCaseRef)=>api.getRecoveryCase(recoveryCaseRef)))}
  catch{error.value='原恢复案件暂时无法读取；当前仍是GUEST，请稍后只读查询同一案件。'}finally{loading.value=false}
}
</script>
<template><view class="page"><AppHeader title="恢复订单" left="返回" @left="uni.navigateBack()"/><view class="content"><view class="heading" role="heading" aria-level="1">恢复原订单访问</view><text class="desc">提交最少必要的恢复信息。订单引用与恢复材料只用于定位请求，不单独证明身份或订单归属。</text><template v-if="!pendingCaseRef"><view class="field"><text class="label">订单引用</text><input v-model="orderRef" placeholder="请输入订单引用"/></view><view class="field"><text class="label">恢复材料引用</text><input v-model="recoveryMaterialRef" placeholder="请输入恢复材料引用"/></view></template><StatusNotice v-else tone="unknown" title="结果仍在确认">已保留原恢复案件；只能只读查询这个案件，不会更换输入或新建案件。</StatusNotice><StatusNotice v-if="loading" title="正在确认访问资格">确认完成前保持GUEST，不展示任何订单事实。</StatusNotice><StatusNotice v-if="error" tone="unknown" title="结果仍在确认">{{error}}</StatusNotice><StatusNotice v-if="result?.outcome==='REJECTED'" tone="risk" title="无法完成此次查询">当前保持GUEST；未认证、无权、不存在、他人订单或证据不足均使用这一通用结果。</StatusNotice><StatusNotice v-if="result?.outcome==='UNKNOWN'" tone="unknown" title="暂时无法确认恢复结果">当前保持GUEST，不显示订单数量、金额、号码尾号或存在性差异；后续只查询原恢复案件。</StatusNotice></view><view class="action"><button v-if="pendingCaseRef" class="primary" :disabled="loading" @click="poll">{{loading?'正在查询…':'查询原恢复案件'}}</button><button v-else class="primary" :disabled="loading||!orderRef||!recoveryMaterialRef" @click="submit">{{loading?'正在确认…':'提交恢复请求'}}</button><button class="secondary" @click="uni.navigateTo({url:'/pages/support/case'})">通用申诉</button></view></view></template>
<style src="../../styles/shared.css"></style><style scoped>.field{margin-top:28rpx;background:#fff;border:1rpx solid var(--line);padding:24rpx;border-radius:22rpx}.label{display:block;color:var(--muted);font-size:22rpx;margin-bottom:14rpx}</style>
