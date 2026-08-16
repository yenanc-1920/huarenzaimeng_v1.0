<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { transitionAdminObject, updateAdminObject, type AdminResource } from '../api/admin-command'
type Item = Record<string, unknown> & { version: number; state: string }
const props = defineProps<{ resource: AdminResource; objectRef: string; item: Item; editable?: boolean }>()
const emit = defineEmits<{ changed: [] }>()
const reason = ref('开发环境业务资料维护'), message = ref(''), busy = ref(false)
const form = reactive<Record<string, string>>({})
const text = (value: unknown) => value == null ? '' : String(value)
function reset() { Object.assign(form, { cityCode: text(props.item.cityRef), category: text(props.item.category), displayName: text(props.item.name ?? props.item.displayName ?? props.item.title), summary: text(props.item.summary), localAddress: text(props.item.localAddress), phone: text(props.item.phone), sourceLabel: text(props.item.sourceRef), validUntil: text(props.item.validUntil), startDate: text(props.item.startDate), endDate: text(props.item.endDate), weekendDays: text(props.item.weekendDays), title: text(props.item.title), bodyText: text(props.item.bodyText), editor: text(props.item.editor), countryCode: text(props.item.countryCode), benefitText: text(props.item.benefitText), denominationBdt: text(props.item.denominationBdt), dataAllowanceMb: text(props.item.dataAllowanceMb), voiceMinutes: text(props.item.voiceMinutes), smsCount: text(props.item.smsCount), validityText: text(props.item.validityText), providerCode: text(props.item.providerCode), providerSku: text(props.item.providerSku), channelPriority: text(props.item.channelPriority), phoneRule: text(props.item.phoneRule), saleStartAt: text(props.item.saleStartAt), saleEndAt: text(props.item.saleEndAt), catalogBatchRef: text(props.item.catalogBatchRef), rawSkuName: text(props.item.rawSkuName), rawBenefitText: text(props.item.rawBenefitText), supplierAvailability: text(props.item.supplierAvailability), catalogSyncedAt: text(props.item.catalogSyncedAt), normalizedType: text(props.item.normalizedType), normalizedOperator: text(props.item.normalizedOperator), mappingState: text(props.item.mappingState), mappingFailureReason: text(props.item.mappingFailureReason), finalAmountCny: text(props.item.finalAmountCny), supplierCost: text(props.resource === 'price-versions' ? props.item.priceSupplierCost : props.item.supplierCost), settlementCurrency: text(props.resource === 'price-versions' ? props.item.priceSettlementCurrency : props.item.settlementCurrency), fxSource: text(props.item.fxSource), fxSnapshotRef: text(props.item.fxSnapshotRef), fxDirection: text(props.item.fxDirection), fxRate: text(props.item.fxRate), fxUpdatedAt: text(props.item.fxUpdatedAt), fxValidUntil: text(props.item.fxValidUntil), bufferRate: text(props.item.bufferRate), markupRate: text(props.item.markupRate), wechatFeeRate: text(props.item.wechatFeeRate), taxRate: text(props.item.taxRate), minimumMarginRate: text(props.item.minimumMarginRate), roundingRule: text(props.item.roundingRule), promotionBearer: text(props.item.promotionBearer), pricingScope: text(props.item.pricingScope), effectiveFrom: text(props.item.effectiveFrom), effectiveUntil: text(props.item.effectiveUntil) }) }
watch(() => [props.objectRef, props.item.version], reset, { immediate: true })
const fields = computed(() => props.resource === 'directory-entries' ? [['cityCode','城市编码'],['category','分类'],['displayName','名称'],['summary','摘要'],['localAddress','当地地址'],['phone','电话'],['sourceLabel','来源'],['validUntil','有效期（ISO）']]
  : props.resource === 'holidays' ? [['displayName','名称'],['startDate','开始日期'],['endDate','结束日期'],['weekendDays','周休规则'],['sourceLabel','来源'],['effectiveUntil','有效期（ISO）']]
  : props.resource === 'news' ? [['category','分类'],['title','标题'],['summary','摘要'],['bodyText','正文'],['sourceLabel','来源'],['editor','编辑'],['validUntil','有效期（ISO）']]
  : props.resource === 'products'
  ? [['countryCode','国家编码'],['displayName','名称'],['benefitText','权益'],['denominationBdt','面值 BDT'],['dataAllowanceMb','流量MB'],['voiceMinutes','语音分钟'],['smsCount','短信条数'],['validityText','有效期说明'],['providerCode','供应商编码'],['providerSku','供应商 SKU'],['channelPriority','渠道优先级'],['phoneRule','号码限制'],['saleStartAt','销售开始'],['saleEndAt','销售结束']]
  : props.resource === 'product-mappings' ? [['catalogBatchRef','目录批次'],['rawSkuName','原始SKU'],['rawBenefitText','原始权益'],['supplierCost','供应商成本'],['settlementCurrency','结算币种'],['supplierAvailability','供应商可用性'],['catalogSyncedAt','目录记录时间'],['normalizedType','归一化类型'],['normalizedOperator','归一化运营商'],['mappingState','映射状态'],['mappingFailureReason','失败原因']]
  : props.resource === 'price-versions' ? [['finalAmountCny','人民币售价'],['supplierCost','供应商成本'],['settlementCurrency','结算币种'],['fxSource','汇率来源'],['fxSnapshotRef','汇率快照'],['fxDirection','汇率方向'],['fxRate','汇率'],['fxUpdatedAt','汇率更新时间（ISO）'],['fxValidUntil','汇率有效期（ISO）'],['bufferRate','缓冲比例'],['markupRate','平台加价'],['wechatFeeRate','微信费率'],['taxRate','税费比例'],['minimumMarginRate','最低毛利率'],['roundingRule','舍入规则'],['promotionBearer','促销承担方'],['pricingScope','适用范围'],['effectiveFrom','生效时间（ISO）'],['effectiveUntil','失效时间（ISO）']] : [])
async function perform(action: 'update' | 'submit' | 'publish' | 'unpublish' | 'enable' | 'disable') {
  if (!reason.value.trim()) { message.value = '请填写操作原因'; return }
  busy.value = true; message.value = ''
  try {
    if (action === 'update') {
      const body: Record<string, unknown> = { expectedVersion: props.item.version, reason: reason.value }
      for (const [key] of fields.value) body[key] = form[key]
      if (props.resource === 'products') {
        for (const key of ['denominationBdt','dataAllowanceMb','voiceMinutes','smsCount']) body[key] = form[key] ? Number(form[key]) : null
        body.channelPriority = Number(form.channelPriority)
      }
      if (props.resource === 'product-mappings') body.supplierCost = Number(form.supplierCost)
      if (props.resource === 'price-versions') for (const key of ['finalAmountCny','supplierCost','fxRate','bufferRate','markupRate','wechatFeeRate','taxRate','minimumMarginRate']) body[key] = Number(form[key])
      await updateAdminObject(props.resource, props.objectRef, body)
    } else await transitionAdminObject(props.resource, props.objectRef, action, props.item.version, reason.value)
    message.value = '操作成功，正在刷新开发库数据'; emit('changed')
  } catch (error) { message.value = error instanceof Error ? error.message : '操作失败' }
  finally { busy.value = false }
}
</script>
<template>
  <article class="card command-card">
    <div class="card-head"><div><p class="eyebrow">现有记录维护</p><h2>{{ objectRef }}</h2></div><span class="readonly">版本 {{ item.version }}</span></div>
    <div v-if="editable" class="admin-form-grid"><label v-for="field in fields" :key="field[0]"><span>{{ field[1] }}</span><textarea v-if="['summary','bodyText'].includes(field[0])" v-model="form[field[0]]" /><input v-else v-model="form[field[0]]" /></label></div>
    <p v-else class="scope-banner compact">当前读模型未返回完整编辑回填字段；本轮仅开放正式状态迁移，避免覆盖未知数据。</p>
    <label class="reason-field"><span>操作原因</span><input v-model="reason" /></label>
    <div class="command-actions">
      <button v-if="editable" :disabled="busy || item.state !== 'DRAFT'" @click="perform('update')">保存编辑</button>
      <template v-if="!['products','price-versions'].includes(resource)"><button :disabled="busy" @click="perform('submit')">提交</button><button :disabled="busy" @click="perform('publish')">发布</button><button :disabled="busy" @click="perform('unpublish')">下架</button></template>
      <template v-else><button :disabled="busy" @click="perform('enable')">启用</button><button :disabled="busy" @click="perform('disable')">停用</button></template>
    </div><p v-if="message" class="command-feedback">{{ message }}</p>
  </article>
</template>
