<script setup lang="ts">
import { ref, watch } from 'vue'
import { createAdminOrderDetailController } from '../api/admin-read-controller'
import type { P021AdminReadState, P021AdminRequestedRole } from '../api/admin-order-detail'

const props = defineProps<{ orderRef: string; role: P021AdminRequestedRole }>()
const state = ref<P021AdminReadState>({ status: 'LOADING', data: null, message: '正在读取订单详情' })
const controller = createAdminOrderDetailController((value) => { state.value = value })
const refresh = () => controller.refresh(props.orderRef, props.role)
watch(() => [props.orderRef, props.role] as const, refresh, { immediate: true })
const formatTime = (value: string | null) => value ? value.replace('T', ' ') : '时间待确认'
</script>

<template>
  <section class="card p021-admin-detail" data-page-id="UX-P021-ADMIN" data-readonly="true" :data-read-state="state.status" :data-detail-count="state.status==='READY'?1:0" :data-role="state.status==='READY'?state.data.role:role">
    <div class="card-head"><div><p class="eyebrow">订单只读详情</p><h2>{{ orderRef }}</h2></div><button class="state-action" :disabled="state.status === 'LOADING'" @click="refresh">重新读取</button></div>
    <div v-if="state.status === 'LOADING'" class="empty-panel" role="status" aria-live="polite">正在读取，旧详情已撤销</div>
    <div v-else-if="state.status === 'ACCESS_DENIED'" class="empty-panel" role="status" aria-live="polite">当前无法查看该订单，不显示字段或对象是否存在</div>
    <div v-else-if="state.status === 'UNAVAILABLE'" class="empty-panel" role="alert">{{ state.message }}，旧详情已清空</div>
    <template v-else>
      <dl class="p021-admin-summary" :data-visible-field-keys="state.data.role==='CS'?'orderRef,stateCode,maskedTarget,confirmedItems,unknownItems,responsibilityCode,updatedAt,nextReviewPoint,timeline':'orderRef,stateCode,totalMinor,currency,targetValueDisplay,targetCurrency,confirmedItems,unknownItems,responsibilityCode,updatedAt,nextReviewPoint,timeline'">
        <div><dt>当前状态</dt><dd>{{ state.data.stateCode }}</dd></div>
        <div v-if="state.data.role === 'CS'"><dt>用户摘要</dt><dd>{{ state.data.maskedTarget }}</dd></div>
        <template v-else><div><dt>金额摘要</dt><dd>{{ state.data.currency }} {{ state.data.totalMinor }}（最小单位）</dd></div><div><dt>目标面值</dt><dd>{{ state.data.targetValueDisplay }}</dd></div></template>
        <div><dt>当前责任</dt><dd>{{ state.data.responsibilityCode }}</dd></div>
        <div><dt>更新时间</dt><dd>{{ formatTime(state.data.updatedAt) }}</dd></div>
        <div><dt>下一复核点</dt><dd>{{ formatTime(state.data.nextReviewPoint) }}</dd></div>
      </dl>
      <div class="fact-pair"><div><span>已确认</span><strong>{{ state.data.confirmedItems.join('、') || '暂无' }}</strong></div><div><span>待确认</span><strong>{{ state.data.unknownItems.join('、') || '暂无' }}</strong></div></div>
      <section class="timeline" aria-label="订单事实时间线"><ol role="list"><li v-for="item in state.data.timeline" :key="item.timelineItemRef" role="listitem"><strong>{{ item.stateCode }}</strong><span>{{ formatTime(item.occurredAt) }}</span></li></ol></section>
      <div class="safe-note warning"><strong>仅提供只读查询</strong><p>页面不会创建支付、充值、退款、调账或资金确认动作。</p></div>
    </template>
  </section>
</template>
