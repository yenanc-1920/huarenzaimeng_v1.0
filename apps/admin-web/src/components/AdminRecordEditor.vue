<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { transitionAdminObject, updateAdminObject, type AdminResource } from '../api/admin-command'
type Item = Record<string, unknown> & { version: number; state: string }
type Option = { value: string; label: string }
type Field = { key: string; label: string; kind?: 'text'|'textarea'|'date'|'datetime'|'select'; options?: Option[] }
const props = defineProps<{ resource: AdminResource; objectRef: string; item: Item; editable?: boolean }>()
const emit = defineEmits<{ changed: [] }>()
const reason = ref('业务资料维护'), message = ref(''), busy = ref(false)
const form = reactive<Record<string, string>>({})
const text = (value: unknown) => value == null ? '' : String(value)
const localTime = (value: unknown) => text(value).replace('Z','').slice(0,16)
const resourceLabel: Record<string,string> = { 'directory-entries':'黄页机构', holidays:'节假日', news:'生活资讯', products:'商品', 'product-mappings':'目录映射', 'price-versions':'价格版本', channels:'渠道' }
const options = (values: Array<[string,string]>): Option[] => values.map(([value,label]) => ({ value,label }))
const fields = computed<Field[]>(() => props.resource === 'directory-entries' ? [
  {key:'cityCode',label:'所属城市编码'},{key:'category',label:'机构分类'},{key:'displayName',label:'机构名称'},{key:'summary',label:'机构简介',kind:'textarea'},{key:'localAddress',label:'当地地址'},{key:'phone',label:'联系电话'},{key:'sourceLabel',label:'信息来源'},{key:'validUntil',label:'有效期',kind:'date'}]
  : props.resource === 'holidays' ? [{key:'displayName',label:'节假日名称'},{key:'startDate',label:'开始日期',kind:'date'},{key:'endDate',label:'结束日期',kind:'date'},{key:'weekendDays',label:'周休日期'},{key:'sourceLabel',label:'信息来源'},{key:'effectiveUntil',label:'规则有效期',kind:'date'}]
  : props.resource === 'news' ? [{key:'category',label:'资讯分类'},{key:'title',label:'资讯标题'},{key:'summary',label:'内容摘要',kind:'textarea'},{key:'bodyText',label:'资讯正文',kind:'textarea'},{key:'sourceLabel',label:'信息来源'},{key:'editor',label:'责任编辑'},{key:'validUntil',label:'展示有效期',kind:'date'}]
  : props.resource === 'products' ? [{key:'countryCode',label:'国家编码'},{key:'displayName',label:'商品名称'},{key:'benefitText',label:'权益说明',kind:'textarea'},{key:'denominationBdt',label:'面值（BDT）'},{key:'dataAllowanceMb',label:'流量（MB）'},{key:'voiceMinutes',label:'语音分钟数'},{key:'smsCount',label:'短信条数'},{key:'validityText',label:'有效期说明'},{key:'providerCode',label:'供应商编码'},{key:'providerSku',label:'供应商商品编码'},{key:'channelPriority',label:'渠道优先级'},{key:'phoneRule',label:'号码规则'},{key:'saleStartAt',label:'销售开始时间',kind:'datetime'},{key:'saleEndAt',label:'销售结束时间',kind:'datetime'}]
  : props.resource === 'product-mappings' ? [{key:'catalogBatchRef',label:'目录批次'},{key:'rawSkuName',label:'供应商商品名称'},{key:'rawBenefitText',label:'供应商权益说明',kind:'textarea'},{key:'supplierCost',label:'供应商成本'},{key:'settlementCurrency',label:'结算币种'},{key:'supplierAvailability',label:'供应状态',kind:'select',options:options([['AVAILABLE','可用'],['UNAVAILABLE','不可用'],['UNKNOWN','待确认']])},{key:'catalogSyncedAt',label:'目录记录时间',kind:'datetime'},{key:'normalizedType',label:'商品类型',kind:'select',options:options([['BALANCE','余额'],['DATA','流量'],['BUNDLE','套餐']])},{key:'normalizedOperator',label:'运营商'},{key:'mappingState',label:'映射状态',kind:'select',options:options([['MAPPED','已映射'],['PENDING','待映射'],['FAILED','映射失败']])},{key:'mappingFailureReason',label:'未映射原因'}]
  : props.resource === 'price-versions' ? [{key:'finalAmountCny',label:'人民币售价'},{key:'supplierCost',label:'供应商成本'},{key:'settlementCurrency',label:'结算币种'},{key:'fxSource',label:'汇率来源'},{key:'fxSnapshotRef',label:'汇率快照编号'},{key:'fxDirection',label:'换算方向'},{key:'fxRate',label:'采用汇率'},{key:'fxUpdatedAt',label:'汇率更新时间',kind:'datetime'},{key:'fxValidUntil',label:'汇率有效期',kind:'datetime'},{key:'bufferRate',label:'汇率缓冲比例'},{key:'markupRate',label:'平台上浮比例'},{key:'wechatFeeRate',label:'微信支付费率'},{key:'taxRate',label:'税费比例'},{key:'minimumMarginRate',label:'最低毛利率'},{key:'roundingRule',label:'售价取整方式',kind:'select',options:options([['ROUND_HALF_UP','四舍五入'],['ROUND_UP','向上取整'],['ROUND_DOWN','向下取整']])},{key:'promotionBearer',label:'优惠承担方',kind:'select',options:options([['PLATFORM','平台承担'],['SUPPLIER','供应商承担'],['SHARED','共同承担']])},{key:'pricingScope',label:'适用范围'},{key:'effectiveFrom',label:'生效时间',kind:'datetime'},{key:'effectiveUntil',label:'失效时间',kind:'datetime'}] : [])
function reset() {
  const source: Record<string,unknown> = { ...props.item, cityCode: props.item.cityRef, displayName: props.item.name ?? props.item.displayName ?? props.item.title, sourceLabel: props.item.sourceRef, supplierCost: props.resource === 'price-versions' ? props.item.priceSupplierCost : props.item.supplierCost, settlementCurrency: props.resource === 'price-versions' ? props.item.priceSettlementCurrency : props.item.settlementCurrency }
  for (const field of fields.value) form[field.key] = field.kind === 'datetime' ? localTime(source[field.key]) : text(source[field.key])
}
watch(() => [props.objectRef, props.item.version, props.resource], reset, { immediate: true })
const heading = computed(() => text(props.item.name ?? props.item.displayName ?? props.item.title) || `${resourceLabel[props.resource] ?? '记录'}维护`)
async function perform(action: 'update' | 'submit' | 'publish' | 'unpublish' | 'enable' | 'disable') {
  if (!reason.value.trim()) { message.value = '请填写操作原因'; return }
  busy.value = true; message.value = ''
  try {
    if (action === 'update') {
      const body: Record<string, unknown> = { expectedVersion: props.item.version, reason: reason.value }
      for (const field of fields.value) body[field.key] = field.kind === 'datetime' && form[field.key] ? new Date(form[field.key]).toISOString() : form[field.key]
      if (props.resource === 'products') { for (const key of ['denominationBdt','dataAllowanceMb','voiceMinutes','smsCount']) body[key] = form[key] ? Number(form[key]) : null; body.channelPriority = Number(form.channelPriority) }
      if (props.resource === 'product-mappings') body.supplierCost = Number(form.supplierCost)
      if (props.resource === 'price-versions') for (const key of ['finalAmountCny','supplierCost','fxRate','bufferRate','markupRate','wechatFeeRate','taxRate','minimumMarginRate']) body[key] = Number(form[key])
      await updateAdminObject(props.resource, props.objectRef, body)
    } else await transitionAdminObject(props.resource, props.objectRef, action, props.item.version, reason.value)
    message.value = '操作成功，页面数据即将刷新'; emit('changed')
  } catch (error) { message.value = error instanceof Error ? error.message : '操作失败' } finally { busy.value = false }
}
</script>
<template>
  <article class="card command-card record-editor">
    <div class="card-head"><div><p class="eyebrow">{{ resourceLabel[resource] }}维护</p><h2>{{ heading }}</h2></div><span class="status-strip">{{ item.state }}</span></div>
    <div v-if="editable" class="admin-form-grid"><label v-for="field in fields" :key="field.key"><span>{{ field.label }}</span><textarea v-if="field.kind === 'textarea'" v-model="form[field.key]" /><select v-else-if="field.kind === 'select'" v-model="form[field.key]"><option value="">请选择</option><option v-for="option in field.options" :key="option.value" :value="option.value">{{ option.label }}</option></select><input v-else v-model="form[field.key]" :type="field.kind === 'date' ? 'date' : field.kind === 'datetime' ? 'datetime-local' : 'text'" /></label></div>
    <p v-else class="scope-banner compact">当前记录缺少完整编辑数据，本页只开放状态操作，避免覆盖未知内容。</p>
    <label class="reason-field"><span>操作说明</span><input v-model="reason" /></label>
    <div class="command-actions"><button v-if="editable" :disabled="busy || item.state !== 'DRAFT'" @click="perform('update')">保存</button><template v-if="!['products','price-versions'].includes(resource)"><button :disabled="busy" @click="perform('submit')">提交审核</button><button :disabled="busy" @click="perform('publish')">发布</button><button :disabled="busy" @click="perform('unpublish')">下架</button></template><template v-else><button :disabled="busy" @click="perform('enable')">启用</button><button :disabled="busy" @click="perform('disable')">停用</button></template></div>
    <p v-if="message" class="command-feedback">{{ message }}</p>
  </article>
</template>
