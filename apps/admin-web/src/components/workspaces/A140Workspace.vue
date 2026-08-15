<script setup lang="ts">
import type { AdminPageProjection } from '../../domain/admin'
type Projection = Extract<AdminPageProjection, { pageId: 'A140' }>
defineProps<{ projection: Projection }>()
defineEmits<{ selectOrder: [orderRef: string] }>()
</script>

<template>
  <section class="work-grid two-column" data-page-id="UX-A140" data-readonly="true">
    <template v-if="projection.role === 'CS'">
      <article class="card list-card"><div class="card-head"><div><p class="eyebrow">授权订单</p><h2>订单与客服状态</h2></div><span class="count">{{ projection.items.length }} 条记录</span></div><div v-if="projection.items.length === 0" class="empty-panel">当前没有可查看的订单</div><div v-else class="table-scroll" tabindex="0" aria-label="客服订单列表，可横向滚动"><div class="data-table order-table" role="table" aria-label="客服职责订单只读数据"><div class="table-header" role="row"><span role="columnheader">订单</span><span role="columnheader">用户状态</span><span role="columnheader">最终实付</span><span role="columnheader">操作</span></div><div v-for="item in projection.items" :key="item.orderRef" class="table-row" role="row"><span role="cell"><b>{{ item.orderRef }}</b><small>{{ item.maskedPhone }}</small></span><span role="cell">{{ item.userStatusLabel }}</span><span role="cell">{{ item.totalLabel }}</span><span role="cell"><button class="table-action" @click="$emit('selectOrder', item.orderRef)">详情与 Sandbox 状态</button></span></div></div></div></article>
      <aside class="card detail-card"><div class="card-head"><div><p class="eyebrow">客服只读摘要</p><h2>{{ projection.items[0]?.userStatusLabel ?? '当前没有订单详情' }}</h2></div><span class="readonly">只读</span></div><dl v-if="projection.items[0]"><div><dt>订单引用</dt><dd>{{ projection.items[0].orderRef }}</dd></div><div><dt>脱敏号码</dt><dd>{{ projection.items[0].maskedPhone }}</dd></div><div><dt>最终实付</dt><dd>{{ projection.items[0].totalLabel }}</dd></div><div><dt>更新时间</dt><dd>{{ projection.items[0].updatedLabel }}</dd></div></dl><div class="safe-note warning"><strong>资金写动作资格为 0</strong><p>本投影不接收退款分层、账本明细或内部资金事实；只读刷新不会创建退款或资金动作。</p></div></aside>
    </template>
    <template v-else>
      <article class="card list-card"><div class="card-head"><div><p class="eyebrow">订单与退款</p><h2>退款与差异只读摘要</h2></div><span class="count">{{ projection.items.length }} 条记录</span></div><div v-if="projection.items.length === 0" class="empty-panel">当前没有可查看的退款或差异记录</div><div v-else class="table-scroll" tabindex="0" aria-label="财务订单列表，可横向滚动"><div class="data-table order-finance-table" role="table" aria-label="财务职责订单只读数据"><div class="table-header" role="row"><span role="columnheader">订单</span><span role="columnheader">退款分层</span><span role="columnheader">事实状态</span><span role="columnheader">账龄</span></div><div v-for="item in projection.items" :key="item.orderRef" class="table-row" role="row"><span role="cell"><b>{{ item.orderRef }}</b><small>{{ item.totalLabel }}</small></span><span role="cell">{{ item.refundLayerLabel }}</span><span role="cell">{{ item.factStatusLabel }}</span><span role="cell">{{ item.agingLabel }}</span></div></div></div></article>
      <aside class="card detail-card"><div class="card-head"><div><p class="eyebrow">财务只读摘要</p><h2>{{ projection.items[0]?.factStatusLabel ?? '当前没有财务详情' }}</h2></div><span class="readonly">只读</span></div><dl v-if="projection.items[0]"><div><dt>订单引用</dt><dd>{{ projection.items[0].orderRef }}</dd></div><div><dt>退款分层</dt><dd>{{ projection.items[0].refundLayerLabel }}</dd></div><div><dt>事实状态</dt><dd>{{ projection.items[0].factStatusLabel }}</dd></div><div><dt>账龄</dt><dd>{{ projection.items[0].agingLabel }}</dd></div></dl><div class="safe-note warning"><strong>资金写动作资格为 0</strong><p>本投影不接收手机号、会话风险键或无关身份字段；不批准退款、不执行退款、不调账、不确认事实。</p></div></aside>
    </template>
  </section>
</template>
