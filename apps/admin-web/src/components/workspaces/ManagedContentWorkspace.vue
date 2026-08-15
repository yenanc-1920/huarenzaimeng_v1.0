<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { AdminPageProjection } from '../../domain/admin'
type Projection = Extract<AdminPageProjection, { pageId: 'A121' | 'A122' }>
const props = defineProps<{ projection: Projection }>()
const selectedRef = ref(props.projection.items[0]?.contentRef ?? '')
watch(() => props.projection, value => { selectedRef.value = value.items[0]?.contentRef ?? '' })
const selected = computed(() => props.projection.items.find(item => item.contentRef === selectedRef.value) ?? props.projection.items[0])
</script>
<template>
  <section class="work-grid two-column" :data-page-id="projection.pageId === 'A121' ? 'UX-A121' : 'UX-A122'" data-readonly="true">
    <article class="card list-card">
      <div class="card-head"><div><p class="eyebrow">{{ projection.pageId === 'A121' ? '黄页条目' : '资讯条目' }}</p><h2>内容列表</h2></div><span class="count">{{ projection.items.length }} 条记录</span></div>
      <div v-if="projection.items.length === 0" class="empty-panel">当前没有可查看的内容记录</div>
      <div v-else class="table-scroll" tabindex="0" aria-label="内容列表">
        <div class="data-table content-table" role="table"><div class="table-header" role="row"><span role="columnheader">标题</span><span role="columnheader">类别</span><span role="columnheader">状态</span><span role="columnheader">版本</span></div><button v-for="item in projection.items" :key="item.contentRef" class="table-row" role="row" type="button" @click="selectedRef=item.contentRef"><span role="cell"><b>{{ item.title }}</b><small>{{ item.contentRef }}</small></span><span role="cell">{{ item.category }}</span><span role="cell">{{ item.statusLabel }}</span><span role="cell">V{{ item.versionLabel }}</span></button></div>
      </div>
    </article>
    <aside class="card detail-card"><div class="card-head"><div><p class="eyebrow">完整信息</p><h2>{{ selected?.title ?? '暂无详情' }}</h2></div><span class="readonly">只读</span></div>
      <template v-if="selected"><p class="content-summary">{{ selected.summary }}</p><dl><div><dt>类别</dt><dd>{{ selected.category }}</dd></div><div><dt>来源</dt><dd>{{ selected.sourceLabel }}</dd></div><div><dt>当前状态</dt><dd>{{ selected.statusLabel }}</dd></div><div><dt>核验时间</dt><dd>{{ selected.verifiedAtLabel }}</dd></div><div><dt>有效期至</dt><dd>{{ selected.validUntilLabel }}</dd></div><div><dt>最后更新</dt><dd>{{ selected.updatedAtLabel }}</dd></div></dl></template>
    </aside>
  </section>
</template>
