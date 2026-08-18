<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { createAdminObject, trialPrice, type AdminResource } from '../api/admin-command'

type Option = { value: string; label: string }
type Field = { key: string; label: string; kind?: 'number' | 'textarea' | 'date' | 'datetime' | 'select'; optional?: boolean; options?: Option[]; placeholder?: string }
const props = withDefaults(defineProps<{ resources: AdminResource[]; title?: string; trialOnly?: boolean }>(), { title: '', trialOnly: false })
const emit = defineEmits<{ changed: [] }>()
const resourceLabels: Record<AdminResource, string> = { cities: '城市', 'directory-entries': '黄页机构', holidays: '节假日与周休', news: '生活资讯', products: '平台商品', 'price-versions': '价格版本', 'product-mappings': '目录映射', channels: '渠道' }
const productTypes: Option[] = [{ value: 'BALANCE', label: '余额' }, { value: 'DATA', label: '流量' }, { value: 'BUNDLE', label: '套餐' }]
const fields: Record<AdminResource, Field[]> = {
  cities: [{ key: 'ref', label: '城市编号' }, { key: 'countryCode', label: '国家', placeholder: 'BD' }, { key: 'displayName', label: '城市中文名' }, { key: 'localName', label: '城市当地名称' }, { key: 'timezoneId', label: '所在时区', placeholder: 'Asia/Dhaka' }, { key: 'sortOrder', label: '展示顺序', kind: 'number' }],
  'directory-entries': [{ key: 'ref', label: '机构编号' }, { key: 'cityCode', label: '所属城市编号' }, { key: 'category', label: '黄页分类' }, { key: 'displayName', label: '机构名称' }, { key: 'summary', label: '机构简介', kind: 'textarea' }, { key: 'localAddress', label: '当地格式地址' }, { key: 'phone', label: '联系电话' }, { key: 'sourceLabel', label: '信息来源' }, { key: 'validUntil', label: '信息有效期', kind: 'datetime' }],
  holidays: [{ key: 'ref', label: '规则编号' }, { key: 'countryCode', label: '适用国家', placeholder: 'BD' }, { key: 'ruleType', label: '规则类型', kind: 'select', options: [{ value: 'HOLIDAY', label: '节假日' }, { value: 'WEEKEND', label: '周休' }] }, { key: 'displayName', label: '规则名称' }, { key: 'startDate', label: '开始日期', kind: 'date' }, { key: 'endDate', label: '结束日期', kind: 'date' }, { key: 'weekendDays', label: '周休星期', optional: true, placeholder: '例如：FRIDAY,SATURDAY' }, { key: 'sourceLabel', label: '信息来源' }, { key: 'effectiveUntil', label: '规则有效期', kind: 'datetime' }],
  news: [{ key: 'ref', label: '资讯编号' }, { key: 'category', label: '资讯分类' }, { key: 'title', label: '资讯标题' }, { key: 'summary', label: '内容摘要', kind: 'textarea' }, { key: 'bodyText', label: '资讯正文', kind: 'textarea' }, { key: 'sourceLabel', label: '信息来源' }, { key: 'editor', label: '作者或编辑' }, { key: 'validUntil', label: '内容有效期', kind: 'datetime' }],
  products: [{ key: 'ref', label: '商品编号' }, { key: 'countryCode', label: '适用国家', placeholder: 'BD' }, { key: 'operatorCode', label: '运营商' }, { key: 'productType', label: '商品类型', kind: 'select', options: productTypes }, { key: 'displayName', label: '商品名称' }, { key: 'benefitText', label: '商品权益', kind: 'textarea' }, { key: 'denominationBdt', label: '面值（BDT）', kind: 'number', optional: true }, { key: 'dataAllowanceMb', label: '流量（MB）', kind: 'number', optional: true }, { key: 'voiceMinutes', label: '语音分钟', kind: 'number', optional: true }, { key: 'smsCount', label: '短信条数', kind: 'number', optional: true }, { key: 'validityText', label: '权益有效期', optional: true }, { key: 'providerCode', label: '供应商' }, { key: 'providerSku', label: '供应商商品编号' }, { key: 'channelPriority', label: '渠道优先级', kind: 'number' }, { key: 'phoneRule', label: '号码限制', optional: true }, { key: 'saleStartAt', label: '开始销售时间', kind: 'datetime', optional: true }, { key: 'saleEndAt', label: '停止销售时间', kind: 'datetime', optional: true }],
  'price-versions': [{ key: 'ref', label: '价格版本编号' }, { key: 'productRef', label: '适用商品编号' }, { key: 'finalAmountCny', label: '人民币售价', kind: 'number' }, { key: 'supplierCost', label: '供应商成本', kind: 'number' }, { key: 'settlementCurrency', label: '结算币种' }, { key: 'fxSource', label: '汇率来源' }, { key: 'fxSnapshotRef', label: '汇率快照编号' }, { key: 'fxDirection', label: '汇率方向' }, { key: 'fxRate', label: '汇率', kind: 'number' }, { key: 'fxUpdatedAt', label: '汇率更新时间', kind: 'datetime' }, { key: 'fxValidUntil', label: '汇率有效期', kind: 'datetime' }, { key: 'bufferRate', label: '汇率缓冲比例', kind: 'number' }, { key: 'markupRate', label: '平台加价比例', kind: 'number' }, { key: 'wechatFeeRate', label: '微信支付费率', kind: 'number' }, { key: 'taxRate', label: '税费比例', kind: 'number' }, { key: 'minimumMarginRate', label: '最低毛利率', kind: 'number' }, { key: 'roundingRule', label: '舍入规则', kind: 'select', options: [{ value: 'ROUND_UP_2DP', label: '向上保留两位小数' }] }, { key: 'promotionBearer', label: '促销成本承担方', kind: 'select', options: [{ value: 'PLATFORM', label: '平台承担' }, { value: 'SUPPLIER', label: '供应商承担' }, { value: 'SHARED', label: '双方共同承担' }] }, { key: 'pricingScope', label: '适用范围' }, { key: 'effectiveFrom', label: '生效时间', kind: 'datetime' }, { key: 'effectiveUntil', label: '失效时间', kind: 'datetime' }],
  'product-mappings': [],
  channels: [{ key: 'ref', label: '渠道编号' }, { key: 'providerCode', label: '供应商' }, { key: 'displayName', label: '渠道名称' }, { key: 'channelPriority', label: '渠道优先级', kind: 'number' }],
}
const resource = ref<AdminResource>(props.resources[0])
const values = reactive<Record<string, string>>({ reason: '' })
const busy = ref(false), message = ref(''), error = ref('')
const trialResult = ref<Awaited<ReturnType<typeof trialPrice>> | null>(null)
const activeFields = computed(() => fields[resource.value])
const heading = computed(() => props.title || `新建${resourceLabels[resource.value]}`)
watch(resource, () => { for (const key of Object.keys(values)) if (key !== 'reason') delete values[key]; message.value = ''; error.value = ''; trialResult.value = null })

const fieldValue = (field: Field) => {
  const value = values[field.key]
  if (field.kind === 'number') return Number(value)
  if (field.kind === 'datetime' && value) return new Date(value).toISOString()
  return value
}
async function save() {
  busy.value = true; message.value = ''; error.value = ''
  try {
    const body: Record<string, unknown> = { reason: values.reason }
    for (const field of activeFields.value) { if (field.optional && !values[field.key]) continue; body[field.key] = fieldValue(field) }
    const result = await createAdminObject(resource.value, body)
    message.value = `保存成功，当前版本 ${result.version}`; emit('changed')
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '保存失败' }
  finally { busy.value = false }
}
async function calculate() {
  busy.value = true; error.value = ''; trialResult.value = null
  try { trialResult.value = await trialPrice({ costCny: Number(values.costCny), bufferRate: Number(values.bufferRate), markupRate: Number(values.markupRate), wechatFeeRate: Number(values.wechatFeeRate), taxRate: Number(values.taxRate), minimumMarginRate: Number(values.minimumMarginRate), roundingRule: values.roundingRule }) }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '试算失败' }
  finally { busy.value = false }
}
</script>

<template>
  <section class="card command-panel business-form" data-command-source="ADMIN_COMMAND_API">
    <div class="card-head"><div><p class="eyebrow">业务资料维护</p><h2>{{ trialOnly ? '价格试算' : heading }}</h2></div><span class="readonly">SUPER_ADMIN</span></div>
    <form v-if="!trialOnly" @submit.prevent="save">
      <label v-if="resources.length > 1">资料类型<select v-model="resource"><option v-for="item in resources" :key="item" :value="item">{{ resourceLabels[item] }}</option></select></label>
      <label v-for="field in activeFields" :key="field.key">{{ field.label }}<textarea v-if="field.kind === 'textarea'" v-model="values[field.key]" :required="!field.optional" :placeholder="field.placeholder"></textarea><select v-else-if="field.kind === 'select'" v-model="values[field.key]" :required="!field.optional"><option disabled value="">请选择</option><option v-for="option in field.options" :key="option.value" :value="option.value">{{ option.label }}</option></select><input v-else v-model="values[field.key]" :type="field.kind === 'number' ? 'number' : field.kind === 'date' ? 'date' : field.kind === 'datetime' ? 'datetime-local' : 'text'" :step="field.kind === 'number' ? '0.0001' : undefined" :required="!field.optional" :placeholder="field.placeholder"></label>
      <label class="full-field">操作原因<textarea v-model="values.reason" required placeholder="说明本次新增原因"></textarea></label>
      <div class="form-actions"><button type="submit" :disabled="busy">{{ busy ? '保存中' : '保存草稿' }}</button></div>
      <p v-if="message" class="form-success" role="status">{{ message }}</p><p v-if="error" class="form-error" role="alert">{{ error }}</p>
    </form>
    <form v-else class="trial-form" @submit.prevent="calculate">
      <h3>输入定价组成</h3>
      <label v-for="field in [{key:'costCny',label:'人民币成本'},{key:'bufferRate',label:'汇率缓冲率'},{key:'markupRate',label:'平台加价率'},{key:'wechatFeeRate',label:'微信支付费率'},{key:'taxRate',label:'税率'},{key:'minimumMarginRate',label:'最低毛利率'}]" :key="field.key">{{ field.label }}<input v-model="values[field.key]" type="number" step="0.0001" required></label>
      <label>舍入规则<select v-model="values.roundingRule" required><option value="ROUND_UP_2DP">向上保留两位小数</option></select></label>
      <div class="form-actions"><button type="submit" :disabled="busy">开始试算</button></div>
      <dl v-if="trialResult" class="trial-breakdown"><div><dt>成本</dt><dd>¥{{ trialResult.costCny }}</dd></div><div><dt>缓冲</dt><dd>{{ trialResult.bufferRate }}</dd></div><div><dt>平台加价</dt><dd>{{ trialResult.markupRate }}</dd></div><div><dt>微信费</dt><dd>{{ trialResult.wechatFeeRate }}</dd></div><div><dt>税费</dt><dd>{{ trialResult.taxRate }}</dd></div><div><dt>最终售价</dt><dd>¥{{ trialResult.finalAmountCny }}</dd></div><div><dt>毛利</dt><dd>¥{{ trialResult.marginCny }} / {{ trialResult.marginRate }}</dd></div><div><dt>最低毛利</dt><dd>{{ trialResult.minimumMarginSatisfied ? '满足' : '不满足' }}</dd></div><div><dt>舍入方式</dt><dd>向上保留两位小数</dd></div><div><dt>是否保存</dt><dd>否，仅试算</dd></div></dl>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    </form>
  </section>
</template>
