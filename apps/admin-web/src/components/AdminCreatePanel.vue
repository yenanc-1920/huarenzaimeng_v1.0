<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { createAdminObject, trialPrice, type AdminResource } from '../api/admin-command'

type Field = { key: string; label: string; kind?: 'number' | 'textarea'; optional?: boolean }
const props = defineProps<{ resources: AdminResource[] }>()
const emit = defineEmits<{ changed: [] }>()
const resourceLabels: Record<AdminResource, string> = { cities: '城市', 'directory-entries': '黄页机构', holidays: '节假日/周休', news: '生活资讯', products: '平台商品', 'price-versions': '价格版本', 'product-mappings': '目录映射', channels: '渠道' }
const fields: Record<AdminResource, Field[]> = {
  cities: [{ key: 'ref', label: '城市编码' }, { key: 'countryCode', label: '国家编码' }, { key: 'displayName', label: '中文名' }, { key: 'localName', label: '当地名' }, { key: 'timezoneId', label: '时区' }, { key: 'sortOrder', label: '排序', kind: 'number' }],
  'directory-entries': [{ key: 'ref', label: '机构编号' }, { key: 'cityCode', label: '城市编码' }, { key: 'category', label: '分类' }, { key: 'displayName', label: '机构名称' }, { key: 'summary', label: '摘要', kind: 'textarea' }, { key: 'localAddress', label: '当地格式地址' }, { key: 'phone', label: '电话' }, { key: 'sourceLabel', label: '来源引用' }, { key: 'validUntil', label: '有效期（RFC3339）' }],
  holidays: [{ key: 'ref', label: '规则编号' }, { key: 'countryCode', label: '国家编码' }, { key: 'ruleType', label: '类型（HOLIDAY/WEEKEND）' }, { key: 'displayName', label: '名称' }, { key: 'startDate', label: '开始日期' }, { key: 'endDate', label: '结束日期' }, { key: 'weekendDays', label: '周休星期', optional: true }, { key: 'sourceLabel', label: '来源引用' }, { key: 'effectiveUntil', label: '有效期（RFC3339）' }],
  news: [{ key: 'ref', label: '资讯编号' }, { key: 'category', label: '分类' }, { key: 'title', label: '标题' }, { key: 'summary', label: '摘要', kind: 'textarea' }, { key: 'bodyText', label: '纯文字正文', kind: 'textarea' }, { key: 'sourceLabel', label: '来源引用' }, { key: 'editor', label: '作者/编辑' }, { key: 'validUntil', label: '有效期（RFC3339）' }],
  products: [{ key: 'ref', label: '平台商品编号' }, { key: 'countryCode', label: '国家编码' }, { key: 'operatorCode', label: '运营商编码' }, { key: 'productType', label: '类型（BALANCE/DATA/BUNDLE）' }, { key: 'displayName', label: '商品名称' }, { key: 'benefitText', label: '权益说明', kind: 'textarea' }, { key: 'denominationBdt', label: '面值BDT', kind: 'number', optional: true }, { key: 'dataAllowanceMb', label: '流量MB', kind: 'number', optional: true }, { key: 'voiceMinutes', label: '语音分钟', kind: 'number', optional: true }, { key: 'smsCount', label: '短信条数', kind: 'number', optional: true }, { key: 'validityText', label: '有效期', optional: true }, { key: 'providerCode', label: '供应商编码' }, { key: 'providerSku', label: '供应商SKU' }, { key: 'channelPriority', label: '渠道优先级', kind: 'number' }, { key: 'phoneRule', label: '号码限制', optional: true }, { key: 'saleStartAt', label: '销售开始（RFC3339）', optional: true }, { key: 'saleEndAt', label: '销售结束（RFC3339）', optional: true }],
  'price-versions': [{ key: 'ref', label: '价格版本编号' }, { key: 'productRef', label: '平台商品编号' }, { key: 'finalAmountCny', label: '最终人民币售价', kind: 'number' }, { key: 'supplierCost', label: '供应商成本', kind: 'number' }, { key: 'settlementCurrency', label: '结算币种' }, { key: 'fxSource', label: '汇率来源' }, { key: 'fxSnapshotRef', label: '汇率快照编号' }, { key: 'fxDirection', label: '汇率方向' }, { key: 'fxRate', label: '汇率', kind: 'number' }, { key: 'fxUpdatedAt', label: '汇率更新时间（RFC3339）' }, { key: 'fxValidUntil', label: '汇率有效期（RFC3339）' }, { key: 'bufferRate', label: '缓冲比例', kind: 'number' }, { key: 'markupRate', label: '平台加价', kind: 'number' }, { key: 'wechatFeeRate', label: '微信费率', kind: 'number' }, { key: 'taxRate', label: '税费比例', kind: 'number' }, { key: 'minimumMarginRate', label: '最低毛利率', kind: 'number' }, { key: 'roundingRule', label: '舍入规则' }, { key: 'promotionBearer', label: '促销承担方' }, { key: 'pricingScope', label: '适用范围' }, { key: 'effectiveFrom', label: '生效时间（RFC3339）' }, { key: 'effectiveUntil', label: '失效时间（RFC3339）' }],
  'product-mappings': [],
  channels: [{ key: 'ref', label: '渠道编号' }, { key: 'providerCode', label: '供应商编码' }, { key: 'displayName', label: '渠道名称' }, { key: 'channelPriority', label: '渠道优先级', kind: 'number' }],
}
const resource = ref<AdminResource>(props.resources[0])
const values = reactive<Record<string, string>>({ reason: '' })
const busy = ref(false); const message = ref(''); const error = ref(''); const trialResult = ref<Awaited<ReturnType<typeof trialPrice>> | null>(null)
const activeFields = computed(() => fields[resource.value])
watch(resource, () => { for (const key of Object.keys(values)) if (key !== 'reason') delete values[key]; message.value = ''; error.value = ''; trialResult.value = null })

async function save() {
  busy.value = true; message.value = ''; error.value = ''
  try {
    const body: Record<string, unknown> = { reason: values.reason }
    for (const field of activeFields.value) {
      if (field.optional && !values[field.key]) continue
      body[field.key] = field.kind === 'number' ? Number(values[field.key]) : values[field.key]
    }
    const result = await createAdminObject(resource.value, body)
    message.value = `已保存 ${result.objectRef}，版本 ${result.version}`
    emit('changed')
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '保存失败' }
  finally { busy.value = false }
}

async function calculate() {
  busy.value = true; error.value = ''; trialResult.value = null
  try {
    trialResult.value = await trialPrice({ costCny: Number(values.costCny), bufferRate: Number(values.bufferRate), markupRate: Number(values.markupRate), wechatFeeRate: Number(values.wechatFeeRate), taxRate: Number(values.taxRate), minimumMarginRate: Number(values.minimumMarginRate), roundingRule: values.roundingRule })
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '试算失败' }
  finally { busy.value = false }
}
</script>

<template>
  <section class="card command-panel" data-command-source="ADMIN_COMMAND_API">
    <div class="card-head"><div><p class="eyebrow">新增开发库记录</p><h2>正式命令接口</h2></div><span class="readonly">SUPER_ADMIN</span></div>
    <form @submit.prevent="save">
      <label>记录类型<select v-model="resource"><option v-for="item in resources" :key="item" :value="item">{{ resourceLabels[item] }}</option></select></label>
      <label v-for="field in activeFields" :key="field.key">{{ field.label }}<textarea v-if="field.kind === 'textarea'" v-model="values[field.key]" :required="!field.optional"></textarea><input v-else v-model="values[field.key]" :type="field.kind === 'number' ? 'number' : 'text'" :step="field.kind === 'number' ? '0.01' : undefined" :required="!field.optional"></label>
      <label>操作理由<textarea v-model="values.reason" required></textarea></label>
      <button type="submit" :disabled="busy">{{ busy ? '保存中' : '保存草稿' }}</button>
      <p v-if="message" class="form-success" role="status">{{ message }}</p><p v-if="error" class="form-error" role="alert">{{ error }}</p>
    </form>
    <form v-if="resources.includes('price-versions')" class="trial-form" @submit.prevent="calculate">
      <h3>价格试算（不保存）</h3>
      <label v-for="field in [{key:'costCny',label:'成本CNY'},{key:'bufferRate',label:'汇率缓冲率'},{key:'markupRate',label:'平台加价率'},{key:'wechatFeeRate',label:'微信支付费率'},{key:'taxRate',label:'税率'},{key:'minimumMarginRate',label:'最低毛利率'}]" :key="field.key">{{ field.label }}<input v-model="values[field.key]" type="number" step="0.0001" required></label>
      <label>舍入规则<input v-model="values.roundingRule" required placeholder="ROUND_UP_2DP"></label>
      <button type="submit" :disabled="busy">试算</button>
      <dl v-if="trialResult" class="trial-breakdown"><div><dt>成本</dt><dd>¥{{ trialResult.costCny }}</dd></div><div><dt>缓冲</dt><dd>{{ trialResult.bufferRate }}</dd></div><div><dt>平台加价</dt><dd>{{ trialResult.markupRate }}</dd></div><div><dt>微信费</dt><dd>{{ trialResult.wechatFeeRate }}</dd></div><div><dt>税费</dt><dd>{{ trialResult.taxRate }}</dd></div><div><dt>最终售价</dt><dd>¥{{ trialResult.finalAmountCny }}</dd></div><div><dt>毛利</dt><dd>¥{{ trialResult.marginCny }} / {{ trialResult.marginRate }}</dd></div><div><dt>最低毛利</dt><dd>{{ trialResult.minimumMarginSatisfied ? '满足' : '不满足' }}</dd></div><div><dt>舍入</dt><dd>{{ trialResult.roundingRule }}</dd></div><div><dt>持久化</dt><dd>否，仅试算</dd></div></dl>
    </form>
  </section>
</template>
