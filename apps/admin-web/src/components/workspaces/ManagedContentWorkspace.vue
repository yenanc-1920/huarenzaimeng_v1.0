<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { AdminPageProjection, ManagedContentSummary, ManagedRuleOrNewsSummary } from '../../domain/admin'
import AdminCreatePanel from '../AdminCreatePanel.vue'
import AdminRecordEditor from '../AdminRecordEditor.vue'
type Projection = Extract<AdminPageProjection, { pageId: 'A121' | 'A122' }>
const props = defineProps<{ projection: Projection }>()
const emit = defineEmits<{ changed: [] }>()
const itemRef = (item: ManagedContentSummary | ManagedRuleOrNewsSummary) => 'entryRef' in item ? item.entryRef : item.objectRef
const selectedRef = ref(props.projection.items[0] ? itemRef(props.projection.items[0] as never) : '')
watch(() => props.projection, value => { selectedRef.value = value.items[0] ? itemRef(value.items[0] as never) : '' })
const selected = computed(() => props.projection.items.find(item => itemRef(item as never) === selectedRef.value) as ManagedContentSummary | ManagedRuleOrNewsSummary | undefined)
</script>

<template>
  <section class="work-grid two-column" :data-page-id="projection.pageId === 'A121' ? 'UX-A121' : 'UX-A122'" data-data-origin="LOCAL_DATABASE">
    <article class="card list-card">
      <div class="card-head"><div><p class="eyebrow">{{ projection.pageId === 'A121' ? '黄页与城市' : '节假日、周休与资讯' }}</p><h2>版本化内容</h2></div><span class="count">{{ projection.items.length }} 条</span></div>
      <div v-if="projection.items.length === 0" class="empty-panel">当前没有可查看记录</div>
      <div v-else class="record-list" role="list">
        <button v-for="item in projection.items" :key="itemRef(item as never)" class="record-row" :class="{ selected: selectedRef === itemRef(item as never) }" @click="selectedRef = itemRef(item as never)">
          <span v-if="'entryRef' in item"><b>{{ item.name }}</b><small>{{ item.cityName }} · {{ item.category }}</small></span>
          <span v-else><b>{{ item.title }}</b><small>{{ item.objectType }} · {{ item.category }}</small></span><em>{{ item.state }}</em>
        </button>
      </div>
    </article>
    <aside class="card detail-card">
      <div class="card-head"><div><p class="eyebrow">结构化详情</p><h2>{{ selected ? ('entryRef' in selected ? selected.name : selected.title) : '暂无详情' }}</h2></div><span class="readonly">无图片</span></div>
      <dl v-if="selected && 'entryRef' in selected">
        <div><dt>城市</dt><dd>{{ selected.cityName }}（{{ selected.cityRef }}）</dd></div><div><dt>当地地址</dt><dd>{{ selected.localAddress }}</dd></div>
        <div><dt>电话</dt><dd>{{ selected.phone }}</dd></div><div><dt>来源</dt><dd>{{ selected.sourceRef }}</dd></div>
        <div><dt>核验时间</dt><dd>{{ selected.verifiedAt }}</dd></div><div><dt>有效期</dt><dd>{{ selected.validUntil }}</dd></div>
      </dl>
      <dl v-else-if="selected">
        <div><dt>类型</dt><dd>{{ selected.objectType }}</dd></div><div><dt>分类</dt><dd>{{ selected.category }}</dd></div>
        <div><dt>摘要</dt><dd>{{ selected.summary ?? '未填写' }}</dd></div><div><dt>正文</dt><dd>{{ selected.bodyText ?? '未填写' }}</dd></div>
        <div><dt>来源</dt><dd>{{ selected.sourceRef }}</dd></div><div><dt>编辑</dt><dd>{{ selected.editor ?? '未填写' }}</dd></div>
        <div><dt>发布时间</dt><dd>{{ selected.publishAt ?? '未发布' }}</dd></div><div><dt>有效期</dt><dd>{{ selected.validUntil }}</dd></div>
      </dl>
      <div class="safe-note"><strong>新增与编辑边界</strong><p>保存、发布与下架使用正式版本命令；V1不提供图片上传、素材选择或图片预览。</p></div>
    </aside>
  </section>
  <AdminCreatePanel :resources="projection.pageId === 'A121' ? ['cities', 'directory-entries'] : ['holidays', 'news']" @changed="emit('changed')" />
  <AdminRecordEditor v-if="selected" :resource="'entryRef' in selected ? 'directory-entries' : selected.objectType === 'HOLIDAY' ? 'holidays' : 'news'" :object-ref="itemRef(selected)" :item="selected as never" editable @changed="emit('changed')" />
</template>
