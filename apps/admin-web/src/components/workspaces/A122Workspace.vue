<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { AdminPageProjection } from '../../domain/admin'
import AdminCreatePanel from '../AdminCreatePanel.vue'
import AdminRecordEditor from '../AdminRecordEditor.vue'
type Projection = Extract<AdminPageProjection, { pageId:'A122' }>
const props=defineProps<{projection:Projection}>(), emit=defineEmits<{changed:[]}>()
const activeTab=ref<'HOLIDAY'|'NEWS'>('HOLIDAY'), mode=ref<'LIST'|'CREATE'|'EDIT'>('LIST'), keyword=ref(''), selectedRef=ref('')
const visibleItems=computed(()=>props.projection.items.filter(item=>item.objectType===activeTab.value && (!keyword.value || `${item.title}${item.category}${item.summary??''}`.includes(keyword.value))))
const selected=computed(()=>props.projection.items.find(item=>item.objectRef===selectedRef.value))
watch(activeTab,()=>{mode.value='LIST';keyword.value=''})
function edit(refValue:string){selectedRef.value=refValue;mode.value='EDIT'}
const stateLabel=(state:string)=>({DRAFT:'草稿',SUBMITTED:'待审核',PUBLISHED:'已发布',UNPUBLISHED:'已下架'}[state]??state)
const businessDate=(value:string|null|undefined)=>{if(!value)return '—';const date=new Date(value);return Number.isNaN(date.getTime())?value:new Intl.DateTimeFormat('zh-CN',{year:'numeric',month:'2-digit',day:'2-digit'}).format(date)}
</script>
<template>
  <section class="page-tabs"><button :class="{active:activeTab==='HOLIDAY'}" @click="activeTab='HOLIDAY'">周休与节假日</button><button :class="{active:activeTab==='NEWS'}" @click="activeTab='NEWS'">生活资讯</button></section>
  <section class="management-view" data-page-id="UX-A122" data-data-origin="LOCAL_DATABASE">
    <template v-if="mode==='LIST'">
      <div class="filter-bar"><div><p class="eyebrow">内容运营</p><h2>{{ activeTab==='HOLIDAY'?'周休与节假日':'生活资讯' }}</h2></div><input v-model="keyword" :placeholder="activeTab==='HOLIDAY'?'搜索名称或分类':'搜索标题、分类或摘要'"><button @click="mode='CREATE'">新增{{activeTab==='HOLIDAY'?'日历规则':'资讯'}}</button></div>
      <article class="card table-card"><div class="business-table"><div class="table-row table-head"><span>{{activeTab==='HOLIDAY'?'规则名称':'资讯标题'}}</span><span>分类</span><span>{{activeTab==='HOLIDAY'?'适用日期':'内容摘要'}}</span><span>信息来源</span><span>状态</span><span>操作</span></div><div v-for="item in visibleItems" :key="item.objectRef" class="table-row"><span><b>{{item.title}}</b></span><span>{{item.category}}</span><span>{{activeTab==='HOLIDAY'?`${businessDate(item.startDate??item.effectiveFrom)} 至 ${businessDate(item.endDate??item.effectiveUntil)}`:(item.summary??'—')}}</span><span>{{item.sourceRef}}</span><span><em class="status-strip">{{stateLabel(item.state)}}</em></span><span><button class="text-button" @click="edit(item.objectRef)">编辑</button></span></div><div v-if="visibleItems.length===0" class="empty-panel">当前数据库中暂无符合条件的{{activeTab==='HOLIDAY'?'日历规则':'资讯'}}</div></div></article>
      <p class="scope-banner compact">V1 采用纯文字内容，不上传图片资源。</p>
    </template>
    <template v-else><button class="back-button" @click="mode='LIST'">← 返回列表</button><AdminCreatePanel v-if="mode==='CREATE'" :resources="activeTab==='HOLIDAY'?['holidays']:['news']" :title="activeTab==='HOLIDAY'?'新增日历规则':'新增生活资讯'" @changed="emit('changed');mode='LIST'"/><AdminRecordEditor v-else-if="selected" :resource="selected.objectType==='HOLIDAY'?'holidays':'news'" :object-ref="selected.objectRef" :item="selected as never" editable @changed="emit('changed')"/></template>
  </section>
</template>
