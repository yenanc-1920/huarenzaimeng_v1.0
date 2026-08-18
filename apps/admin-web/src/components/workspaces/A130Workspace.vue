<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { AdminPageProjection } from '../../domain/admin'
import AdminCreatePanel from '../AdminCreatePanel.vue'
import AdminRecordEditor from '../AdminRecordEditor.vue'
import A130ChannelPanel from '../A130ChannelPanel.vue'
type Projection=Extract<AdminPageProjection,{pageId:'A130'}>
const props=defineProps<{projection:Projection}>(),emit=defineEmits<{changed:[]}>()
const activeTab=ref<'PRODUCT'|'CATALOG'|'CHANNEL'|'PRICE'|'TRIAL'>('PRODUCT'),mode=ref<'LIST'|'CREATE'|'EDIT'>('LIST'),keyword=ref(''),selectedRef=ref('')
const itemKey=(item:Projection['items'][number])=>`${item.productRef}:${item.priceVersionRef??'NO_PRICE'}:${item.priceVersion??0}`
const canManage=computed(()=>props.projection.role==='SUPER_ADMIN')
const filtered=computed(()=>props.projection.items.filter(item=>!keyword.value||`${item.displayName}${item.operatorCode}${item.providerSku??''}${item.catalogBatchRef??''}`.includes(keyword.value)))
const selected=computed(()=>props.projection.items.find(item=>itemKey(item)===selectedRef.value))
watch(activeTab,()=>{mode.value='LIST';keyword.value=''})
function edit(item:Projection['items'][number]){selectedRef.value=itemKey(item);mode.value='EDIT'}
const catalogBatchRef=computed(()=>selected.value?.catalogBatchRef??'—')
const minimumMarginRate=computed(()=>selected.value?.minimumMarginRate??'—')
const productTypeLabel=(value:string|null|undefined)=>({BALANCE:'余额',DATA:'流量',BUNDLE:'套餐'}[value??'']??value??'—')
const stateLabel=(value:string|null|undefined)=>({DRAFT:'草稿',ENABLED:'已启用',DISABLED:'已停用',MAPPED:'已映射',PENDING:'待处理',FAILED:'失败',AVAILABLE:'可用',UNAVAILABLE:'不可用'}[value??'']??value??'—')
</script>
<template>
  <section class="page-tabs"><button :class="{active:activeTab==='PRODUCT'}" @click="activeTab='PRODUCT'">商品列表</button><button :class="{active:activeTab==='CATALOG'}" @click="activeTab='CATALOG'">供应商目录</button><button :class="{active:activeTab==='CHANNEL'}" @click="activeTab='CHANNEL'">渠道映射</button><button :class="{active:activeTab==='PRICE'}" @click="activeTab='PRICE'">定价配置</button><button :class="{active:activeTab==='TRIAL'}" @click="activeTab='TRIAL'">价格试算</button></section>
  <section class="management-view" data-page-id="UX-A130" data-data-origin="LOCAL_DATABASE">
    <A130ChannelPanel v-if="activeTab==='CHANNEL' && canManage" @changed="emit('changed')"/>
    <section v-else-if="activeTab==='CHANNEL'" class="state-panel"><div class="state-icon">!</div><h2>当前角色不可维护渠道</h2><p>渠道管理仅对超级管理员开放。</p></section>
    <AdminCreatePanel v-else-if="activeTab==='TRIAL' && canManage" :resources="['price-versions']" title="价格试算" trial-only/>
    <section v-else-if="activeTab==='TRIAL'" class="state-panel"><div class="state-icon">!</div><h2>当前角色不可价格试算</h2><p>第一版仅超级管理员可以试算。</p></section>
    <template v-else-if="mode==='LIST'">
      <div class="filter-bar"><div><p class="eyebrow">商品与定价</p><h2>{{activeTab==='PRODUCT'?'商品列表':activeTab==='CATALOG'?'供应商目录':'定价配置'}}</h2></div><input v-model="keyword" placeholder="搜索商品、运营商或供应商商品"><button v-if="canManage && activeTab!=='CATALOG'" @click="mode='CREATE'">{{activeTab==='PRODUCT'?'新增商品':'新增价格版本'}}</button><button v-else disabled>{{canManage?'目录由商品映射生成':'当前角色只读'}}</button></div>
      <p class="scope-banner compact"><strong>供应商不可达</strong><span>当前展示已保存的业务资料，不同步目录、不查询余额、不发起充值。</span></p>
      <article class="card table-card"><div v-if="activeTab==='PRODUCT'" class="business-table"><div class="table-row table-head"><span>商品名称</span><span>运营商</span><span>商品类型</span><span>人民币售价</span><span>状态</span><span>操作</span></div><div v-for="item in filtered" :key="itemKey(item)" class="table-row"><span><b>{{item.displayName}}</b><small>{{item.benefitText}}</small></span><span>{{item.operatorCode}}</span><span>{{productTypeLabel(item.productType)}}</span><span>{{item.finalAmountCny===null?'—':`¥${item.finalAmountCny}`}}</span><span><em class="status-strip">{{stateLabel(item.state)}}</em></span><span><button class="text-button" :disabled="!canManage" @click="edit(item)">编辑</button></span></div></div>
        <div v-else-if="activeTab==='CATALOG'" class="business-table catalog-table"><div class="table-row table-head"><span>目录批次</span><span>供应商商品</span><span>成本</span><span>归一化商品</span><span>映射状态</span><span>操作</span></div><div v-for="item in filtered" :key="itemKey(item)" class="table-row"><span>{{item.catalogBatchRef??'—'}}</span><span><b>{{item.rawSkuName??item.providerSku??'—'}}</b><small>{{item.rawBenefitText??'—'}}</small></span><span>{{item.supplierCost??'—'}} {{item.settlementCurrency??''}}</span><span>{{item.normalizedOperator??item.operatorCode}} · {{productTypeLabel(item.normalizedType??item.productType)}}</span><span><em class="status-strip">{{stateLabel(item.mappingState)}}</em></span><span><button class="text-button" :disabled="!canManage" @click="edit(item)">编辑映射</button></span></div></div>
        <div v-else class="business-table price-table"><div class="table-row table-head"><span>价格版本</span><span>适用商品</span><span>供应商成本</span><span>汇率来源</span><span>人民币售价</span><span>操作</span></div><div v-for="item in filtered" :key="itemKey(item)" class="table-row"><span><b>{{item.priceVersionRef??'未配置'}}</b><small>版本 {{item.priceVersion??'—'}}</small></span><span>{{item.displayName}}</span><span>{{item.priceSupplierCost??'—'}} {{item.priceSettlementCurrency??''}}</span><span>{{item.fxSource??'—'}}</span><span>{{item.finalAmountCny===null?'—':`¥${item.finalAmountCny}`}}</span><span><button class="text-button" :disabled="!canManage||!item.priceVersionRef" @click="edit(item)">编辑价格</button></span></div></div><div v-if="filtered.length===0" class="empty-panel">当前数据库中暂无符合条件的记录</div></article>
      <p v-if="!canManage" class="scope-banner compact">当前为内容运营角色，商品、目录和价格仅可查看；维护动作由超级管理员执行。</p>
    </template>
    <template v-else><button class="back-button" @click="mode='LIST'">← 返回列表</button><AdminCreatePanel v-if="mode==='CREATE'" :resources="activeTab==='PRODUCT'?['products']:['price-versions']" :title="activeTab==='PRODUCT'?'新增商品':'新增价格版本'" @changed="emit('changed');mode='LIST'"/><AdminRecordEditor v-else-if="selected && activeTab==='PRODUCT'" resource="products" :object-ref="selected.productRef" :item="selected as never" editable @changed="emit('changed')"/><AdminRecordEditor v-else-if="selected && activeTab==='CATALOG'" resource="product-mappings" :object-ref="selected.productRef" :item="selected as never" editable @changed="emit('changed')"/><AdminRecordEditor v-else-if="selected?.priceVersionRef && selected.priceVersion" resource="price-versions" :object-ref="selected.priceVersionRef" :item="({...selected,version:selected.priceVersion,state:selected.priceState??'DRAFT'} as never)" editable @changed="emit('changed')"/></template>
    <span class="contract-only" hidden>{{catalogBatchRef}}{{minimumMarginRate}}{{props.projection.role==='SUPER_ADMIN'?'SUPER_ADMIN':''}}</span>
  </section>
</template>
