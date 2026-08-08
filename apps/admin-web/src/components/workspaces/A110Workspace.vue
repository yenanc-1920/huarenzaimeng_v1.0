<script setup lang="ts">
import { computed } from 'vue'
import type { A110PageProjection, ReconciliationFactCode, ReconciliationFactState } from '../../domain/admin'

const props = defineProps<{ projection: A110PageProjection }>()
defineEmits<{ navigate: [pageId: 'A100' | 'A140'] }>()

const factNames: Record<ReconciliationFactCode, string> = { W: '收款', U: '上游扣款', D: '到账', R: '退款', L: '本地账务' }
const factCodes: ReconciliationFactCode[] = ['W', 'U', 'D', 'R', 'L']
const factStates: Record<ReconciliationFactState, string> = {
  ABSENT_CONFIRMED: '已确认暂无', PENDING_OR_INFLIGHT: '正在确认', UNKNOWN: '尚无法确认', CONFIRMED: '已确认', CONFLICT: '存在冲突',
}
const differenceLabels: Record<string, string> = {
  MISSING: '事实缺失', DUPLICATE: '疑似重复', AMOUNT_MISMATCH: '金额不一致', CURRENCY_MISMATCH: '币种不一致',
  FACT_CONFLICT: '事实冲突', ACCOUNTING_INCOMPLETE: '本地账务未闭合', REFUND_DELIVERY_CONFLICT: '退款后出现到账冲突', UNKNOWN: '差异仍待确认',
}
const ageLabels: Record<string, string> = { CURRENT: '当前待核对', LONG_RUNNING: '较长时间未确认', UNKNOWN: '账龄尚无法确认' }
const responsibilityLabels: Record<string, string> = { FIN_REVIEW_QUEUE: '财务复核队列', CS_FOLLOW_UP: '客服跟进', CROSS_TEAM_REVIEW: '跨职责复核' }
const explanationLabels: Record<string, string> = {
  PAYMENT_RECEIVED: '已确认收到付款', DELIVERY_RESULT_PENDING: '到账结果尚未确认', LOCAL_RECORD_PENDING: '本地记录仍在核对',
  REFUND_CONFIRMED: '退款结果已确认', DELIVERY_CONFIRMED: '后续到账结果已确认', FINAL_CONCLUSION_PENDING: '最终处理结论尚未确认',
  RESULT_DIFFERENCE_PENDING: '需要进一步核对结果差异',
}
const stateCopy: Record<string, { title: string; message: string }> = {
  READY: { title: '当前差异只读摘要', message: '以下为当前可查看的信息，界面预览不代表真实业务结果。' },
  EMPTY: { title: '当前没有待显示差异', message: '仅表示当前授权范围没有记录，不代表其他范围的情况。' },
  LONG_RUNNING_UNKNOWN: { title: '部分结果较长时间仍未确认', message: '已确认和未确认事实保持分列，当前不会提前定案。' },
  ASYMMETRIC_FACTS: { title: '已知结果之间存在不一致', message: '各项事实保持独立，当前只提供查看和安全导航。' },
  REFUND_DELIVERY_CONFLICT: { title: '退款后出现新的到账结果', message: '退款和到账事实同时保留，等待跨职责复核。' },
}

const formatMoney = (amountMinor: number | null, currency: string | null) => amountMinor === null || currency === null ? '金额未确认' : `${currency} ${(amountMinor / 100).toFixed(2)}`
const factLabel = (code: ReconciliationFactCode, state: ReconciliationFactState) => `${factNames[code]}：${factStates[state]}`
const responsibilityLabel = (value: string) => responsibilityLabels[value] ?? '职责待权威确认'
const explanationLabel = (value: string) => explanationLabels[value] ?? '说明项待确认'
const currentCopy = computed(() => stateCopy[props.projection.viewState] ?? stateCopy.READY)
</script>

<template>
  <section class="work-grid" data-page-id="UX-A110" :data-view-state="projection.viewState" data-readonly="true">
    <section class="a110-state-summary" role="status" aria-live="polite">
      <div><p class="eyebrow">当前只读状态</p><h2>{{ currentCopy.title }}</h2><p>{{ currentCopy.message }}</p></div>
      <span class="readonly">只读页面</span>
    </section>

    <template v-if="projection.roleProjection === 'FIN'">
      <article class="card list-card">
        <div class="card-head"><div><p class="eyebrow">财务职责</p><h2>收款、上游扣款、到账、退款与本地账务</h2></div><span class="count">{{ projection.items.length }} 条记录</span></div>
        <div v-if="projection.items.length === 0" class="empty-panel">当前授权范围没有待显示差异</div>
        <div v-else class="table-scroll" tabindex="0" aria-label="财务悬账与冲突列表，可横向滚动">
          <div class="data-table a110-finance-table" role="table" aria-label="财务职责悬账与冲突只读数据">
            <div class="table-header" role="row"><span role="columnheader">差异引用</span><span v-for="code in factCodes" :key="code" role="columnheader">{{ code }} {{ factNames[code] }}</span><span role="columnheader">差异与账龄</span></div>
            <div v-for="item in projection.items" :key="item.reconciliationRef" class="table-row" role="row">
              <span role="cell"><b>{{ item.reconciliationRef }}</b><small>{{ item.orderRef }}</small></span>
              <span v-for="code in factCodes" :key="code" role="cell"><b>{{ factStates[item.factSummaries[code].factState] }}</b><small>{{ formatMoney(item.factSummaries[code].amountMinor, item.factSummaries[code].currency) }}</small></span>
              <span role="cell"><b>{{ item.differenceCategories.map((value) => differenceLabels[value] ?? '差异待确认').join('、') }}</b><small>{{ ageLabels[item.ageState] ?? '账龄待确认' }}</small></span>
            </div>
          </div>
        </div>
      </article>

      <div v-if="projection.items[0]" class="work-grid two-column">
        <aside class="card detail-card"><div class="card-head"><div><p class="eyebrow">财务只读详情</p><h2>{{ projection.items[0].reconciliationRef }}</h2></div><span class="readonly">只读</span></div><dl><div><dt>订单引用</dt><dd>{{ projection.items[0].orderRef }}</dd></div><div><dt>客服引用</dt><dd>{{ projection.items[0].supportRef ?? '—' }}</dd></div><div><dt>当前责任</dt><dd>{{ responsibilityLabel(projection.items[0].responsibilityCode) }}</dd></div><div><dt>下一复核点</dt><dd>{{ projection.items[0].nextReviewPoint ?? '未确定' }}</dd></div><div><dt>更新时间</dt><dd>{{ projection.items[0].updatedAt }}</dd></div></dl></aside>
        <aside class="card timeline"><div class="card-head"><div><p class="eyebrow">事实时间线</p><h2>独立事实观察</h2></div><span class="readonly">不改写事实</span></div><ol><li v-for="(event, index) in projection.items[0].timeline" :key="`${event.factCode}-${index}`"><b>{{ factLabel(event.factCode, event.factState) }}</b><span>{{ event.observedAt ?? '观察时间尚未确认' }}</span></li></ol></aside>
      </div>
    </template>

    <template v-else>
      <article class="card list-card">
        <div class="card-head"><div><p class="eyebrow">客服职责</p><h2>脱敏差异协作摘要</h2></div><span class="count">{{ projection.items.length }} 条记录</span></div>
        <div v-if="projection.items.length === 0" class="empty-panel">当前授权范围没有待显示差异</div>
        <div v-else class="table-scroll" tabindex="0" aria-label="客服悬账与冲突列表，可横向滚动"><div class="data-table a110-support-table" role="table" aria-label="客服职责悬账与冲突只读数据"><div class="table-header" role="row"><span role="columnheader">差异引用</span><span role="columnheader">用户可理解摘要</span><span role="columnheader">已确认</span><span role="columnheader">未确认</span><span role="columnheader">下一复核点</span></div><div v-for="item in projection.items" :key="item.reconciliationRef" class="table-row" role="row"><span role="cell"><b>{{ item.reconciliationRef }}</b><small>{{ item.maskedSubjectSummary }}</small></span><span role="cell">{{ item.userFacingSummary }}</span><span role="cell">{{ item.confirmedItems.map(explanationLabel).join('、') || '暂无已确认项' }}</span><span role="cell">{{ item.unconfirmedItems.map(explanationLabel).join('、') || '暂无未确认项' }}</span><span role="cell"><b>{{ item.nextReviewPoint }}</b><small>{{ item.updatedAt }}</small></span></div></div></div>
      </article>
      <aside v-if="projection.items[0]" class="card detail-card"><div class="card-head"><div><p class="eyebrow">客服只读详情</p><h2>{{ projection.items[0].userFacingSummary }}</h2></div><span class="readonly">只读</span></div><dl><div><dt>客服引用</dt><dd>{{ projection.items[0].supportRef }}</dd></div><div><dt>订单引用</dt><dd>{{ projection.items[0].orderRef }}</dd></div><div><dt>脱敏主体</dt><dd>{{ projection.items[0].maskedSubjectSummary }}</dd></div><div><dt>当前责任</dt><dd>{{ responsibilityLabel(projection.items[0].responsibilityCode) }}</dd></div><div><dt>下一复核点</dt><dd>{{ projection.items[0].nextReviewPoint ?? '未确定' }}</dd></div></dl><div class="safe-note"><strong>查看范围说明</strong><p>当前页面只显示客服处理所需摘要，不展示金额、币种、完整资金事实、账本、成本或财务时间线。</p></div></aside>
    </template>

    <section class="a110-safe-actions" aria-label="只读安全动作">
      <p>重新读取只更新当前信息；前往其他页面后将重新确认可查看范围。</p>
      <div><button v-if="projection.allowedActions.includes('NAVIGATE_A100')" class="secondary" @click="$emit('navigate', 'A100')">前往客服案件</button><button v-if="projection.allowedActions.includes('NAVIGATE_A140')" class="secondary" @click="$emit('navigate', 'A140')">前往订单与退款</button><button disabled>暂不支持复核、备注或资金修改</button></div>
    </section>
  </section>
</template>
