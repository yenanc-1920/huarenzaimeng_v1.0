<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { AdminPageProjection } from '../../domain/admin'
import AdminCreatePanel from '../AdminCreatePanel.vue'
import AdminRecordEditor from '../AdminRecordEditor.vue'
type Projection = Extract<AdminPageProjection, { pageId: 'A121' }>
const props = defineProps<{ projection: Projection }>()
const emit = defineEmits<{ changed: [] }>()
const activeTab = ref<'DIRECTORY'|'CITY'>('DIRECTORY'), mode = ref<'LIST'|'CREATE'|'EDIT'>('LIST'), keyword = ref(''), cityFilter = ref('')
const selectedRef = ref(''), selectedCityRef = ref('')
const cities = computed(() => Array.from(new Map(props.projection.items.map(item => [item.cityRef,{ cityRef:item.cityRef, cityName:item.cityName }])).values()))
const filtered = computed(() => props.projection.items.filter(item => (!keyword.value || `${item.name}${item.category}${item.localAddress}`.includes(keyword.value)) && (!cityFilter.value || item.cityRef === cityFilter.value)))
const selected = computed(() => props.projection.items.find(item => item.entryRef === selectedRef.value))
watch(activeTab, () => { mode.value='LIST'; keyword.value=''; cityFilter.value='' })
function edit(refValue:string) { selectedRef.value=refValue; mode.value='EDIT' }
function editCity(refValue:string) { selectedCityRef.value=refValue; mode.value='EDIT' }
const stateLabel = (state:string) => ({ DRAFT:'草稿', SUBMITTED:'待审核', PUBLISHED:'已发布', UNPUBLISHED:'已下架' }[state] ?? state)
</script>
<template>
  <section class="page-tabs"><button :class="{active:activeTab==='DIRECTORY'}" @click="activeTab='DIRECTORY'">机构信息</button><button :class="{active:activeTab==='CITY'}" @click="activeTab='CITY'">城市配置</button></section>
  <section class="management-view" data-page-id="UX-A121" data-data-origin="LOCAL_DATABASE">
    <template v-if="mode==='LIST'">
      <div class="filter-bar"><div><p class="eyebrow">黄页管理</p><h2>{{ activeTab==='DIRECTORY' ? '机构信息' : '城市配置' }}</h2></div><template v-if="activeTab==='DIRECTORY'"><input v-model="keyword" placeholder="搜索机构名称、分类或地址"><select v-model="cityFilter"><option value="">全部城市</option><option v-for="city in cities" :key="city.cityRef" :value="city.cityRef">{{ city.cityName }}</option></select></template><button @click="mode='CREATE'">新增{{ activeTab==='DIRECTORY'?'机构':'城市' }}</button></div>
      <article class="card table-card"><div v-if="activeTab==='DIRECTORY'" class="business-table"><div class="table-row table-head"><span>机构名称</span><span>城市</span><span>分类</span><span>当地地址</span><span>状态</span><span>操作</span></div><div v-for="item in filtered" :key="item.entryRef" class="table-row"><span><b>{{ item.name }}</b><small>联系电话 {{ item.phone || '—' }}</small></span><span>{{ item.cityName }}</span><span>{{ item.category }}</span><span>{{ item.localAddress }}</span><span><em class="status-strip">{{ stateLabel(item.state) }}</em></span><span><button class="text-button" @click="edit(item.entryRef)">编辑</button></span></div><div v-if="filtered.length===0" class="empty-panel">当前数据库中暂无符合条件的黄页机构</div></div>
        <div v-else class="business-table city-table"><div class="table-row table-head"><span>城市名称</span><span>城市编码</span><span>机构数量</span><span>数据来源</span><span>操作</span></div><div v-for="city in cities" :key="city.cityRef" class="table-row"><span><b>{{ city.cityName }}</b></span><span>{{ city.cityRef }}</span><span>{{ projection.items.filter(item=>item.cityRef===city.cityRef).length }}</span><span>机构资料</span><span><button class="text-button" @click="editCity(city.cityRef)">编辑</button></span></div><div v-if="cities.length===0" class="empty-panel">当前数据库中暂无城市</div></div>
      </article><p class="scope-banner compact">V1 无图片资源；详细地址按当地地址结构保存，城市作为独立字段管理。</p>
    </template>
<template v-else><button class="back-button" @click="mode='LIST'">← 返回列表</button><AdminCreatePanel v-if="mode==='CREATE'" :resources="activeTab==='CITY'?['cities']:['directory-entries']" :title="activeTab==='CITY'?'新增城市':'新增黄页机构'" @changed="emit('changed');mode='LIST'"/><AdminRecordEditor v-else-if="activeTab==='DIRECTORY' && selected" resource="directory-entries" :object-ref="selected.entryRef" :item="selected as never" editable @changed="emit('changed')"/><article v-else class="card detail-card"><div class="card-head"><div><p class="eyebrow">城市维护</p><h2>{{cities.find(city=>city.cityRef===selectedCityRef)?.cityName}}</h2></div></div><div class="safe-note warning"><strong>当前仅可查看</strong><p>这条城市资料暂不支持修改。你可以返回列表新增城市，已有资料后续开放维护。</p></div></article></template>
  </section>
</template>
