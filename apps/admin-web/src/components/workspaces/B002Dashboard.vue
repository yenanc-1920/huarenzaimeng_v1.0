<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { loadAdminPage } from '../../api/admin-read'
import type { AdminDataMode, AdminPageProjection, PageId } from '../../domain/admin'

const props = defineProps<{ mode: AdminDataMode | null }>()
const emit = defineEmits<{ navigate: [pageId: PageId] }>()
const pageIds: PageId[] = ['A100', 'A110', 'A120', 'A121', 'A122', 'A130', 'A140']
const projections = ref<Partial<Record<PageId, AdminPageProjection>>>({})
const unavailable = ref<PageId[]>([])
const loading = ref(false)

const count = (pageId: PageId) => projections.value[pageId]?.items.length
const metrics = computed(() => [
  { label: '待处理客服案件', value: count('A100'), hint: '当前客服案件', pageId: 'A100' as PageId },
  { label: '交易差异', value: count('A110'), hint: '当前待核对差异', pageId: 'A110' as PageId },
  { label: '待审核内容', value: count('A120'), hint: '当前审核队列', pageId: 'A120' as PageId },
  { label: '当前可售商品', value: count('A130'), hint: '当前商品资料', pageId: 'A130' as PageId },
])
const supplierCatalogFreshness = computed(() => {
  const page = projections.value.A130
  if (!page || page.pageId !== 'A130') return undefined
  const values = page.items.map(item => item.catalogSyncedAt).filter((value): value is string => Boolean(value)).sort()
  return values.at(-1)
})
const holidayRuleVersion = computed(() => {
  const page = projections.value.A122
  if (!page || page.pageId !== 'A122') return undefined
  const versions = page.items.filter(item => item.objectType === 'HOLIDAY').map(item => item.version)
  return versions.length ? Math.max(...versions) : undefined
})

async function refreshDashboard() {
  if (!props.mode || loading.value) return
  loading.value = true
  const results = await Promise.all(pageIds.map(async pageId => ({ pageId, state: await loadAdminPage(props.mode!, pageId) })))
  const next: Partial<Record<PageId, AdminPageProjection>> = {}
  const failed: PageId[] = []
  for (const { pageId, state } of results) {
    if (state.status === 'READY') next[pageId] = state.data
    else failed.push(pageId)
  }
  projections.value = next
  unavailable.value = failed
  loading.value = false
}

onMounted(refreshDashboard)
</script>

<template>
  <section class="dashboard" data-page-id="UX-B002" data-visual-frame-id="B002" data-data-origin="PROJECT_API_PROXY">
    <div class="dashboard-welcome">
      <div><p class="eyebrow">运营概览</p><h1>今天也要稳稳地把服务做好</h1><p>所有数字均来自当前环境；未读取到的数据不会使用替代值。</p></div>
      <button class="secondary-action" type="button" :disabled="loading" @click="refreshDashboard">{{ loading ? '读取中' : '刷新数据' }}</button>
    </div>

    <div class="metric-grid" aria-label="运营指标">
      <button v-for="metric in metrics" :key="metric.label" class="metric-card" type="button" @click="emit('navigate', metric.pageId)">
        <span>{{ metric.label }}</span><strong>{{ metric.value === undefined ? '—' : metric.value }}</strong><small>{{ metric.hint }}</small>
      </button>
    </div>

    <div class="dashboard-grid">
      <article class="dashboard-card transaction-card">
        <div class="dashboard-card-head"><div><p class="eyebrow">今日交易概览</p><h2>支付、充值与退款</h2></div><span class="readonly">当前统计</span></div>
        <div class="transaction-summary">
          <div><span>支付确认</span><strong>—</strong><small>数据暂不可用</small></div>
          <div><span>充值完成</span><strong>—</strong><small>数据暂不可用</small></div>
          <div><span>退款处理中</span><strong>—</strong><small>数据暂不可用</small></div>
        </div>
        <div class="chart-unavailable"><span aria-hidden="true">⌁</span><div><strong>趋势图暂未开放</strong><p>当前没有可展示的趋势数据。</p></div></div>
      </article>

      <article class="dashboard-card dependency-card">
        <div class="dashboard-card-head"><div><p class="eyebrow">业务依赖</p><h2>关键配置与队列</h2></div></div>
        <ul>
          <li><span class="status-dot muted"></span><div><strong>微信支付服务</strong><small>— · 状态暂不可用</small></div></li>
          <li><span class="status-dot" :class="{ muted: !supplierCatalogFreshness }"></span><div><strong>供应商目录新鲜度</strong><small>{{ supplierCatalogFreshness ?? '— · 数据暂不可用' }}</small></div></li>
          <li><span class="status-dot" :class="{ warning: unavailable.includes('A120') }"></span><div><strong>内容审核队列</strong><small>{{ count('A120') === undefined ? '— · 数据暂不可用' : `${count('A120')} 条待处理资料` }}</small></div></li>
          <li><span class="status-dot" :class="{ muted: holidayRuleVersion === undefined }"></span><div><strong>节假日规则版本</strong><small>{{ holidayRuleVersion === undefined ? '— · 数据暂不可用' : `版本 ${holidayRuleVersion}` }}</small></div></li>
        </ul>
      </article>
    </div>
  </section>
</template>
