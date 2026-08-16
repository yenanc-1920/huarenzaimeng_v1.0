<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { AdminPageProjection } from '../../domain/admin'
import AdminCreatePanel from '../AdminCreatePanel.vue'
import AdminRecordEditor from '../AdminRecordEditor.vue'
import A130ChannelPanel from '../A130ChannelPanel.vue'
type Projection = Extract<AdminPageProjection, { pageId: 'A130' }>
const props = defineProps<{ projection: Projection }>()
const emit = defineEmits<{ changed: [] }>()
const itemKey = (item: Projection['items'][number]) => `${item.productRef}:${item.priceVersionRef ?? 'NO_PRICE'}:${item.priceVersion ?? 0}`
const selectedRef = ref(props.projection.items[0] ? itemKey(props.projection.items[0]) : '')
watch(() => props.projection, value => { selectedRef.value = value.items[0] ? itemKey(value.items[0]) : '' })
const selected = computed(() => props.projection.items.find(item => itemKey(item) === selectedRef.value) ?? props.projection.items[0])
const canManage = computed(() => props.projection.role === 'SUPER_ADMIN')
</script>

<template>
  <section class="work-grid two-column" data-page-id="UX-A130" data-data-origin="LOCAL_DATABASE">
    <article class="card list-card">
      <div class="card-head"><div><p class="eyebrow">平台商品</p><h2>商品、目录映射与价格</h2></div><span class="count">{{ projection.items.length }} 条</span></div>
      <div class="scope-banner compact"><strong>供应商不可达</strong><span>当前只使用开发库预置商品和价格，不同步目录、不查询余额、不发起充值。</span></div>
      <div v-if="projection.items.length === 0" class="empty-panel">开发库中暂无商品</div>
      <div v-else class="record-list" role="list"><button v-for="item in projection.items" :key="itemKey(item)" class="record-row" :class="{ selected: selectedRef === itemKey(item) }" @click="selectedRef = itemKey(item)"><span><b>{{ item.displayName }}</b><small>{{ item.operatorCode }} · {{ item.productType }} · {{ item.priceVersionRef ?? '无价格版本' }}</small></span><em>{{ item.finalAmountCny === null ? '未定价' : `¥${item.finalAmountCny}` }}</em></button></div>
    </article>
    <aside class="card detail-card">
      <div class="card-head"><div><p class="eyebrow">商品配置</p><h2>{{ selected?.displayName ?? '暂无商品' }}</h2></div><span class="readonly">{{ canManage ? 'SUPER_ADMIN 可维护' : 'CONTENT 文案只读' }}</span></div>
      <dl v-if="selected">
        <div><dt>平台商品</dt><dd>{{ selected.productRef }}</dd></div><div><dt>权益</dt><dd>{{ selected.benefitText }}</dd></div>
        <div><dt>国家</dt><dd>{{ selected.countryCode }}</dd></div><div><dt>结构化权益</dt><dd>流量 {{ selected.dataAllowanceMb ?? '—' }} MB / 语音 {{ selected.voiceMinutes ?? '—' }} 分钟 / 短信 {{ selected.smsCount ?? '—' }} 条</dd></div>
        <div><dt>渠道优先级</dt><dd>{{ selected.channelPriority }}</dd></div><div><dt>号码限制</dt><dd>{{ selected.phoneRule ?? '未限制' }}</dd></div>
        <div><dt>销售时间</dt><dd>{{ selected.saleStartAt ?? '未设置' }} 至 {{ selected.saleEndAt ?? '未设置' }}</dd></div>
        <div><dt>有效期</dt><dd>{{ selected.validityText ?? '未填写' }}</dd></div><div><dt>人民币售价</dt><dd>{{ selected.finalAmountCny === null ? '未配置' : `¥${selected.finalAmountCny}` }}</dd></div>
        <div><dt>价格版本</dt><dd>{{ selected.priceVersion ?? '未配置' }}</dd></div><div><dt>销售状态</dt><dd>{{ selected.state }}</dd></div>
        <div><dt>供应商映射</dt><dd>{{ selected.providerCode }} / {{ selected.providerSku }}</dd></div><div><dt>价格状态</dt><dd>{{ selected.priceState ?? '未配置' }}</dd></div>
      </dl>
      <div class="safe-note warning"><strong>启用规则</strong><p>只有SUPER_ADMIN能试算并启用本地商品和价格版本；外部供应商保持不可达。历史订单使用下单快照，不随当前配置追溯变化。</p></div>
    </aside>
  </section>
  <section v-if="selected" class="work-grid two-column pricing-breakdown">
    <article class="card detail-card"><div class="card-head"><div><p class="eyebrow">目录映射事实</p><h2>开发库预置记录</h2></div><span class="readonly">不冒充供应商同步</span></div><dl>
      <div><dt>目录批次</dt><dd>{{ selected.catalogBatchRef ?? '当前数据模型未配置' }}</dd></div><div><dt>原始 SKU</dt><dd>{{ selected.rawSkuName ?? '当前数据模型未配置' }}</dd></div>
      <div><dt>原始权益</dt><dd>{{ selected.rawBenefitText ?? '当前数据模型未配置' }}</dd></div><div><dt>供应商成本</dt><dd>{{ selected.supplierCost ?? '当前数据模型未配置' }}</dd></div>
      <div><dt>结算币种</dt><dd>{{ selected.settlementCurrency ?? '当前数据模型未配置' }}</dd></div><div><dt>供应商可用性</dt><dd>{{ selected.supplierAvailability ?? '当前数据模型未配置' }}</dd></div>
      <div><dt>目录记录时间</dt><dd>{{ selected.catalogSyncedAt ?? '当前数据模型未配置' }}</dd></div><div><dt>归一化类型</dt><dd>{{ selected.normalizedType ?? '当前数据模型未配置' }}</dd></div>
      <div><dt>归一化运营商</dt><dd>{{ selected.normalizedOperator ?? '当前数据模型未配置' }}</dd></div><div><dt>映射状态</dt><dd>{{ selected.mappingState ?? '当前数据模型未配置' }}</dd></div>
      <div><dt>映射失败原因</dt><dd>{{ selected.mappingFailureReason ?? '无' }}</dd></div>
    </dl></article>
    <article class="card detail-card"><div class="card-head"><div><p class="eyebrow">当前价格版本</p><h2>{{ selected.priceVersionRef ?? '未配置价格' }}</h2></div><span class="readonly">版本 {{ selected.priceVersion ?? '—' }}</span></div><dl>
      <div><dt>成本/币种</dt><dd>{{ selected.priceSupplierCost ?? '未配置' }} {{ selected.priceSettlementCurrency ?? '' }}</dd></div><div><dt>汇率方向</dt><dd>{{ selected.fxDirection ?? '未配置' }}</dd></div>
      <div><dt>汇率/来源</dt><dd>{{ selected.fxRate ?? '未配置' }} / {{ selected.fxSource ?? '未配置' }}</dd></div><div><dt>汇率更新时间</dt><dd>{{ selected.fxUpdatedAt ?? '未配置' }}</dd></div>
      <div><dt>汇率有效期</dt><dd>{{ selected.fxValidUntil ?? '未配置' }}</dd></div><div><dt>缓冲比例</dt><dd>{{ selected.bufferRate ?? '未配置' }}</dd></div>
      <div><dt>平台加价</dt><dd>{{ selected.markupRate ?? '未配置' }}</dd></div><div><dt>微信费率</dt><dd>{{ selected.wechatFeeRate ?? '未配置' }}</dd></div>
      <div><dt>税费比例</dt><dd>{{ selected.taxRate ?? '未配置' }}</dd></div><div><dt>最低毛利</dt><dd>{{ selected.minimumMarginRate ?? '未配置' }}</dd></div>
      <div><dt>舍入规则</dt><dd>{{ selected.roundingRule ?? '未配置' }}</dd></div><div><dt>促销承担</dt><dd>{{ selected.promotionBearer ?? '未配置' }}</dd></div>
      <div><dt>适用范围</dt><dd>{{ selected.pricingScope ?? '未配置' }}</dd></div><div><dt>最终售价</dt><dd>{{ selected.finalAmountCny === null ? '未配置' : `¥${selected.finalAmountCny}` }}</dd></div>
    </dl></article>
  </section>
  <AdminCreatePanel v-if="canManage" :resources="['products', 'price-versions']" @changed="emit('changed')" />
  <AdminRecordEditor v-if="canManage && selected" resource="products" :object-ref="selected.productRef" :item="selected as never" editable @changed="emit('changed')" />
  <AdminRecordEditor v-if="canManage && selected" resource="product-mappings" :object-ref="selected.productRef" :item="selected as never" editable @changed="emit('changed')" />
  <AdminRecordEditor v-if="canManage && selected?.priceVersionRef && selected.priceVersion" resource="price-versions" :object-ref="selected.priceVersionRef" :item="({ ...selected, version: selected.priceVersion, state: selected.priceState ?? 'DRAFT' } as never)" editable @changed="emit('changed')" />
  <A130ChannelPanel v-if="canManage" @changed="emit('changed')" />
</template>
