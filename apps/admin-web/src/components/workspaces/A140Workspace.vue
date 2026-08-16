<script setup lang="ts">
import type { AdminPageProjection } from '../../domain/admin'
type Projection = Extract<AdminPageProjection, { pageId: 'A140' }>
defineProps<{ projection: Projection }>()
defineEmits<{ selectOrder: [orderRef: string] }>()
</script>

<template>
  <section class="work-grid two-column" data-page-id="UX-A140" data-data-origin="LOCAL_DATABASE" data-readonly="true">
    <article class="card list-card">
      <div class="card-head"><div><p class="eyebrow">订单运营</p><h2>订单、支付、充值与退款</h2></div><span class="count">{{ projection.items.length }} 条</span></div>
      <div v-if="projection.items.length === 0" class="empty-panel">开发库中暂无订单记录</div>
      <div v-else class="table-scroll" tabindex="0"><div class="data-table order-table" role="table">
        <div class="table-header" role="row"><span role="columnheader">订单</span><span role="columnheader">综合展示状态</span><span role="columnheader">下单金额</span><span role="columnheader">操作</span></div>
        <div v-for="item in projection.items" :key="item.orderRef" class="table-row" role="row">
          <span role="cell"><b>{{ item.orderRef }}</b><small>{{ item.maskedPhone }}</small></span><span role="cell">{{ item.userStatusLabel }}</span>
          <span role="cell">{{ item.totalLabel }}</span><span role="cell"><button class="table-action" @click="$emit('selectOrder', item.orderRef)">查看事实详情</button></span>
        </div>
      </div></div>
    </article>
    <aside class="card detail-card">
      <div class="card-head"><div><p class="eyebrow">只读边界</p><h2>权威事实不可编辑</h2></div><span class="readonly">只读</span></div>
      <div class="safe-note warning"><strong>禁止操作</strong><p>本页不提供手工改终态、调账、退款、再次充值或切换供应商。异常进入A110，客服问题进入A100。</p></div>
    </aside>
  </section>
</template>
