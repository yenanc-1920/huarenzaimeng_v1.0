<script setup lang="ts">
import type { AdminPageProjection } from '../../domain/admin'

type Projection = Extract<AdminPageProjection, { pageId: 'A120' }>
defineProps<{ projection: Projection }>()
</script>

<template>
  <section class="work-grid two-column" data-page-id="UX-A120" data-readonly="true">
    <article class="card list-card">
        <div class="card-head"><div><p class="eyebrow">内容列表</p><h2>来源与展示资格</h2></div><span class="count">{{ projection.items.length }} 条记录</span></div>
      <div v-if="projection.items.length === 0" class="empty-panel">当前没有可查看的内容记录</div>
      <div v-else class="table-scroll" tabindex="0" aria-label="内容核验列表，可横向滚动">
        <div class="data-table content-table" role="table" aria-label="内容核验只读数据"><div class="table-header" role="row"><span role="columnheader">条目</span><span role="columnheader">核验状态</span><span role="columnheader">投诉状态</span><span role="columnheader">展示资格</span></div><div v-for="item in projection.items" :key="item.contentRef" class="table-row" role="row"><span role="cell"><b>{{ item.title }}</b><small>{{ item.sourceLabel }}</small></span><span role="cell">{{ item.reviewLabel }}</span><span role="cell">{{ item.complaintLabel }}</span><span role="cell">{{ item.visibilityLabel }}</span></div></div>
      </div>
    </article>
    <aside class="card detail-card">
      <div class="card-head"><div><p class="eyebrow">核验详情</p><h2>{{ projection.items[0]?.title ?? '当前没有核验详情' }}</h2></div><span class="readonly">只读</span></div>
      <dl v-if="projection.items[0]"><div><dt>来源类别</dt><dd>{{ projection.items[0].sourceLabel }}</dd></div><div><dt>当前责任</dt><dd>{{ projection.items[0].ownerLabel }}</dd></div><div><dt>历史版本</dt><dd>{{ projection.items[0].historyLabel }}</dd></div><div><dt>下架状态</dt><dd>{{ projection.items[0].removalLabel }}</dd></div><div><dt>展示资格</dt><dd>{{ projection.items[0].visibilityLabel }}</dd></div></dl>
      <div class="safe-note warning"><strong>写操作资格为 0</strong><p>当前只读查看责任、历史版本和下架状态；不记录复核、不发布、不下架，也不把核验写成推荐、认证或担保。</p></div>
    </aside>
  </section>
</template>
