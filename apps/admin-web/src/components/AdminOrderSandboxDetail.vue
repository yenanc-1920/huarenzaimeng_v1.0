<script setup lang="ts">
import { ref, watch } from 'vue'
import { loadAdminOrderSandbox, type AdminOrderSandboxState } from '../api/admin-order-sandbox'

const props = defineProps<{ orderRef: string }>()
const emit = defineEmits<{ close: [] }>()
const state = ref<AdminOrderSandboxState>({ status: 'LOADING', data: null, message: '正在读取订单详情' })
let generation = 0

async function refresh() {
  const own = ++generation
  state.value = { status: 'LOADING', data: null, message: '正在读取订单详情' }
  const result = await loadAdminOrderSandbox(props.orderRef)
  if (own === generation) state.value = result
}

watch(() => props.orderRef, refresh, { immediate: true })
</script>

<template>
  <section class="card admin-order-sandbox-detail" data-page-id="ADMIN-ORDER-SANDBOX" :data-read-state="state.status">
    <div class="card-head"><div><p class="eyebrow">订单与 Sandbox 充值状态</p><h2>{{ orderRef }}</h2></div><div class="detail-actions"><button class="secondary" :disabled="state.status==='LOADING'" @click="refresh">重新读取</button><button class="secondary" @click="emit('close')">返回列表</button></div></div>
    <div v-if="state.status==='LOADING'" class="empty-panel" role="status">正在读取，旧详情已撤销</div>
    <div v-else-if="state.status!=='READY'" class="empty-panel" role="alert">{{ state.message }}</div>
    <template v-else>
      <dl class="admin-order-detail-grid"><div><dt>订单状态</dt><dd>{{ state.data.stateCode }}</dd></div><div><dt>支付状态</dt><dd>{{ state.data.paymentState }}</dd></div><div><dt>交付状态</dt><dd>{{ state.data.deliveryState }}</dd></div><div><dt>退款状态</dt><dd>{{ state.data.refundState }}</dd></div><div><dt>脱敏号码</dt><dd>{{ state.data.maskedTarget }}</dd></div><div><dt>金额</dt><dd>{{ state.data.currency }} {{ (state.data.totalMinor/100).toFixed(2) }}</dd></div></dl>
      <div v-if="state.data.sandboxTopup.availability==='OBSERVED'" class="safe-note"><strong>Sandbox 状态已观察</strong><p>交易引用：{{ state.data.sandboxTopup.providerTransactionRef }}；状态：{{ state.data.sandboxTopup.providerStatusCode }}</p></div>
      <div v-else class="safe-note warning"><strong>暂无 Sandbox 充值事实</strong><p>这表示数据库中没有已观察到的 Sandbox 交易状态，不代表充值成功或失败。</p></div>
    </template>
  </section>
</template>
