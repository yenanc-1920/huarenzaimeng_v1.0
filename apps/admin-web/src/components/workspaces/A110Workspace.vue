<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { AdminPageProjection } from '../../domain/admin'
type Projection = Extract<AdminPageProjection, { pageId: 'A110' }>
const props = defineProps<{ projection: Projection }>()
const emit = defineEmits<{ navigate: [pageId: 'A100' | 'A140'] }>()
const selectedRef = ref(props.projection.items[0]?.reconciliationRef ?? '')
watch(() => props.projection, value => { selectedRef.value = value.items[0]?.reconciliationRef ?? '' })
const selected = computed(() => props.projection.items.find(item => item.reconciliationRef === selectedRef.value) ?? props.projection.items[0])
</script>
<template>
  <section class="work-grid two-column" data-page-id="UX-A110" data-data-origin="LOCAL_DATABASE">
    <article class="card list-card"><div class="card-head"><div><p class="eyebrow">交易差异</p><h2>支付、充值与退款差异</h2></div><span class="readonly">{{ projection.role }} 只读</span></div>
      <div v-if="projection.items.length === 0" class="empty-panel">当前授权范围没有交易差异</div>
      <div v-else class="record-list"><button v-for="item in projection.items" :key="item.reconciliationRef" class="record-row" :class="{ selected: selectedRef === item.reconciliationRef }" @click="selectedRef=item.reconciliationRef"><span><b>{{ item.reconciliationRef }}</b><small>{{ item.orderRef }} · {{ item.differenceType }}</small></span><em>{{ item.state }}</em></button></div>
    </article>
    <aside class="card detail-card"><div class="card-head"><div><p class="eyebrow">差异事实</p><h2>{{ selected?.reconciliationRef ?? '暂无差异' }}</h2></div><span class="readonly">不改写终态</span></div>
      <dl v-if="selected"><div><dt>订单引用</dt><dd>{{ selected.orderRef }}</dd></div><div><dt>差异类型</dt><dd>{{ selected.differenceType }}</dd></div><div><dt>差异金额</dt><dd>{{ selected.amount }} {{ selected.currency }}</dd></div><div><dt>状态</dt><dd>{{ selected.state }}</dd></div><div><dt>负责人</dt><dd>{{ selected.ownerRef ?? '未分配' }}</dd></div><div><dt>发现时间</dt><dd>{{ selected.discoveredAt }}</dd></div><div><dt>更新时间</dt><dd>{{ selected.updatedAt }}</dd></div></dl>
      <div class="safe-note"><strong>只读边界</strong><p>仅查看开发库交易差异事实；禁止重充、退款、调账或手工修改支付与充值终态。</p></div>
      <div class="command-actions"><button @click="emit('navigate','A100')">查看客服案件</button><button @click="emit('navigate','A140')">查看订单事实</button></div>
    </aside>
  </section>
</template>
