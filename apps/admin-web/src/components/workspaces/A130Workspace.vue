<script setup lang="ts">
import type { AdminPageProjection } from '../../domain/admin'

type Projection = Extract<AdminPageProjection, { pageId: 'A130' }>
defineProps<{ projection: Projection }>()
</script>

<template>
  <section class="work-grid two-column" data-page-id="UX-A130" data-readonly="true">
    <template v-if="projection.role === 'CONTENT'">
      <article class="card list-card">
        <div class="card-head"><div><p class="eyebrow">商品目录</p><h2>商品与运营商资料</h2></div><span class="count">{{ projection.items.length }} 条记录</span></div>
        <div v-if="projection.items.length === 0" class="empty-panel">当前没有可查看的目录内容</div>
        <div v-else class="table-scroll" tabindex="0" aria-label="目录内容列表，可横向滚动"><div class="data-table catalog-table" role="table" aria-label="内容职责目录只读数据"><div class="table-header" role="row"><span role="columnheader">项目</span><span role="columnheader">分类</span><span role="columnheader">内容状态</span><span role="columnheader">可售复核</span></div><div v-for="item in projection.items" :key="item.catalogRef" class="table-row" role="row"><span role="cell"><b>{{ item.displayName }}</b><small>{{ item.operatorLabel }}</small></span><span role="cell">{{ item.categoryLabel }}</span><span role="cell">{{ item.contentStatusLabel }}</span><span role="cell">{{ item.saleReviewLabel }}</span></div></div></div>
      </article>
      <aside class="card detail-card"><div class="card-head"><div><p class="eyebrow">内容资料</p><h2>{{ projection.items[0]?.displayName ?? '当前没有目录详情' }}</h2></div><span class="readonly">只读</span></div><dl v-if="projection.items[0]"><div><dt>运营商</dt><dd>{{ projection.items[0].operatorLabel }}</dd></div><div><dt>非财务分类</dt><dd>{{ projection.items[0].categoryLabel }}</dd></div><div><dt>内容状态</dt><dd>{{ projection.items[0].contentStatusLabel }}</dd></div><div><dt>复核状态</dt><dd>{{ projection.items[0].saleReviewLabel }}</dd></div></dl><div class="safe-note"><strong>职责字段互斥</strong><p>本投影不接收面额、币种、成本、利润、价格组成或支持批次；写操作资格为 0。</p></div></aside>
    </template>
    <template v-else>
      <article class="card list-card">
        <div class="card-head"><div><p class="eyebrow">商品目录</p><h2>价格与有效期只读复核</h2></div><span class="count">{{ projection.items.length }} 条记录</span></div>
        <div v-if="projection.items.length === 0" class="empty-panel">当前没有可查看的财务候选</div>
        <div v-else class="table-scroll" tabindex="0" aria-label="财务目录列表，可横向滚动"><div class="data-table catalog-finance-table" role="table" aria-label="财务职责目录只读数据"><div class="table-header" role="row"><span role="columnheader">项目</span><span role="columnheader">面额</span><span role="columnheader">有效期</span><span role="columnheader">支持批次</span></div><div v-for="item in projection.items" :key="item.catalogRef" class="table-row" role="row"><span role="cell"><b>{{ item.displayName }}</b><small>{{ item.financeReviewLabel }}</small></span><span role="cell">{{ item.denominationLabel }}</span><span role="cell">{{ item.validityLabel }}</span><span role="cell">{{ item.supportBatchLabel }}</span></div></div></div>
      </article>
      <aside class="card detail-card"><div class="card-head"><div><p class="eyebrow">财务候选</p><h2>{{ projection.items[0]?.displayName ?? '当前没有财务详情' }}</h2></div><span class="readonly">只读</span></div><dl v-if="projection.items[0]"><div><dt>面额</dt><dd>{{ projection.items[0].denominationLabel }}</dd></div><div><dt>币种</dt><dd>{{ projection.items[0].currencyLabel }}</dd></div><div><dt>价格与成本候选</dt><dd>{{ projection.items[0].priceCostCandidateLabel }}</dd></div><div><dt>有效期</dt><dd>{{ projection.items[0].validityLabel }}</dd></div><div><dt>支持批次</dt><dd>{{ projection.items[0].supportBatchLabel }}</dd></div></dl><div class="safe-note"><strong>职责字段互斥</strong><p>本投影不接收来源、权利、展示文案或非财务分类；写操作与正式价格资格均为 0。</p></div></aside>
    </template>
  </section>
</template>
