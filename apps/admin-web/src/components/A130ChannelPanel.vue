<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { transitionAdminObject, updateAdminObject } from '../api/admin-command'
import AdminCreatePanel from './AdminCreatePanel.vue'
type Channel = { channelRef: string; providerCode: string; displayName: string; channelPriority: number; state: string; version: number; updatedAt: string }
const emit = defineEmits<{ changed: [] }>()
const channels = ref<Channel[]>([]), error = ref(''), reason = ref('开发环境渠道维护'), busy = ref(false)
async function load() {
  try {
    const response = await fetch('/admin-read/v1/pages/A130/channels', { credentials: 'include', headers: { Accept: 'application/json' } })
    if (!response.ok) throw new Error(response.status === 403 ? '当前角色只能查看商品文案' : '渠道读取失败')
    const body = await response.json(), items = Array.isArray(body.items) ? body.items : []
    if (!items.every((item: unknown) => typeof item === 'object' && item !== null && typeof (item as Channel).channelRef === 'string' && Number.isSafeInteger((item as Channel).version))) throw new Error('渠道响应格式不符合约定')
    channels.value = items; error.value = ''
  } catch (cause) { channels.value = []; error.value = cause instanceof Error ? cause.message : '渠道读取失败' }
}
async function save(item: Channel) {
  busy.value = true
  try { await updateAdminObject('channels', item.channelRef, { expectedVersion: item.version, reason: reason.value, providerCode: item.providerCode, displayName: item.displayName, channelPriority: item.channelPriority }); await load(); emit('changed') }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '渠道保存失败' } finally { busy.value = false }
}
async function toggle(item: Channel, action: 'enable' | 'disable') {
  busy.value = true
  try { await transitionAdminObject('channels', item.channelRef, action, item.version, reason.value); await load(); emit('changed') }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '渠道状态更新失败' } finally { busy.value = false }
}
onMounted(load)
</script>
<template>
  <section class="card command-card"><div class="card-head"><div><p class="eyebrow">渠道管理</p><h2>开发库渠道与优先级</h2></div><button class="secondary" @click="load">刷新</button></div>
    <p class="scope-banner compact">不连接供应商、不触发目录同步；只维护开发库渠道记录。</p>
    <label class="reason-field"><span>操作原因</span><input v-model="reason"></label>
    <div v-for="item in channels" :key="item.channelRef" class="channel-editor"><b>{{ item.channelRef }}</b><input v-model="item.providerCode"><input v-model="item.displayName"><input v-model.number="item.channelPriority" type="number"><span>{{ item.state }} / v{{ item.version }}</span><button :disabled="busy" @click="save(item)">保存</button><button :disabled="busy" @click="toggle(item,'enable')">启用</button><button :disabled="busy" @click="toggle(item,'disable')">停用</button></div>
    <p v-if="error" class="form-error">{{ error }}</p>
  </section>
  <AdminCreatePanel :resources="['channels']" @changed="load(); emit('changed')" />
</template>
