<script setup lang="ts">
import type { AdminPageProjection } from '../../domain/admin'

type Projection = Extract<AdminPageProjection, { pageId: 'A100' }>
defineProps<{ projection: Projection }>()
</script>

<template>
  <section class="work-grid three-column" data-page-id="UX-A100" data-readonly="true">
    <article class="card list-card">
        <div class="card-head"><div><p class="eyebrow">客服案件</p><h2>需要跟进的问题</h2></div><span class="count">{{ projection.items.length }} 条记录</span></div>
      <div v-if="projection.items.length === 0" class="empty-panel">当前没有可查看的客服案件</div>
      <div v-else class="record-list" role="list" aria-label="客服案件列表">
        <div v-for="item in projection.items" :key="item.supportRef" class="record-row" role="listitem">
          <span><b>{{ item.supportRef }}</b><small>{{ item.subjectLabel }}</small></span><em>{{ item.statusLabel }}</em>
        </div>
      </div>
    </article>
    <article class="card detail-card">
      <div class="card-head"><div><p class="eyebrow">案件详情</p><h2>{{ projection.items[0]?.subjectLabel ?? '当前没有案件详情' }}</h2></div><span class="readonly">只读</span></div>
      <template v-if="projection.items[0]">
        <div class="fact-pair"><div><strong>已确认</strong><span>案件引用：{{ projection.items[0].supportRef }}</span><span>当前状态：{{ projection.items[0].statusLabel }}</span></div><div><strong>仍待确认</strong><span>充值结果：尚未确认</span><span>{{ projection.items[0].nextReviewLabel }}</span></div></div>
        <dl><div><dt>脱敏号码</dt><dd>{{ projection.items[0].maskedPhone }}</dd></div><div><dt>备注状态</dt><dd>{{ projection.items[0].noteStatusLabel }}</dd></div><div><dt>升级状态</dt><dd>{{ projection.items[0].escalationStatusLabel }}</dd></div></dl>
      </template>
      <div class="safe-note"><strong>写操作资格为 0</strong><p>这里只展示备注与升级的只读状态，不追加备注、不升级案件，也不修改付款、充值、退款或到账事实。</p></div>
    </article>
    <aside class="card timeline">
      <div class="card-head"><h2>案件时间线</h2></div>
      <ol><li><b>案件已登记</b><span>时间以受信记录为准</span></li><li><b>继续核对</b><span>{{ projection.items[0]?.nextReviewLabel ?? '下一复核点尚未确定' }}</span></li><li class="muted"><b>等待新记录</b><span>不显示未发生结果</span></li></ol>
    </aside>
  </section>
</template>
