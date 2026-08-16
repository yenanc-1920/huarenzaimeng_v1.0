<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { AdminPageProjection } from '../../domain/admin'
type Projection = Extract<AdminPageProjection, { pageId: 'A100' }>
const props = defineProps<{ projection: Projection }>()
const selectedRef = ref(props.projection.items[0]?.caseRef ?? '')
watch(() => props.projection, value => { selectedRef.value = value.items[0]?.caseRef ?? '' })
const selected = computed(() => props.projection.items.find(item => item.caseRef === selectedRef.value) ?? props.projection.items[0])
</script>

<template>
  <section class="work-grid two-column" data-page-id="UX-A100" data-data-origin="LOCAL_DATABASE">
    <article class="card list-card">
      <div class="card-head"><div><p class="eyebrow">客服案件</p><h2>案件列表</h2></div><span class="count">{{ projection.items.length }} 条</span></div>
      <div v-if="projection.items.length === 0" class="empty-panel">开发库中暂无客服案件</div>
      <div v-else class="record-list" role="list">
        <button v-for="item in projection.items" :key="item.caseRef" class="record-row" :class="{ selected: selectedRef === item.caseRef }" @click="selectedRef = item.caseRef">
          <span><b>{{ item.caseRef }}</b><small>{{ item.issueType }} · {{ item.sourceType }}</small></span><em>{{ item.state }}</em>
        </button>
      </div>
    </article>
    <aside class="card detail-card">
      <div class="card-head"><div><p class="eyebrow">案件跟进</p><h2>{{ selected?.caseRef ?? '暂无案件' }}</h2></div><span class="readonly">事实只读</span></div>
      <dl v-if="selected">
        <div><dt>问题类型</dt><dd>{{ selected.issueType }}</dd></div><div><dt>来源</dt><dd>{{ selected.sourceType }}</dd></div>
        <div><dt>关联订单</dt><dd>{{ selected.relatedOrderRef ?? '未关联' }}</dd></div><div><dt>优先级</dt><dd>{{ selected.priorityCode }}</dd></div>
        <div><dt>负责人</dt><dd>{{ selected.ownerRef ?? '未分配' }}</dd></div><div><dt>更新时间</dt><dd>{{ selected.updatedAt }}</dd></div>
      </dl>
      <div class="safe-note"><strong>只读边界</strong><p>本页只读取开发库案件事实，不修改订单、支付、充值或退款权威状态。</p></div>
    </aside>
  </section>
</template>
