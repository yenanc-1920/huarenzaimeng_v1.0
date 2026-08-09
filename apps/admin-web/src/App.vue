<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import logoUrl from '../../miniapp/src/static/logo.png'
import { resolveAdminDataMode } from './api/admin-read'
import { createAdminReadController } from './api/admin-read-controller'
import AccessDenied from './components/AccessDenied.vue'
import AdminWorkspace from './components/AdminWorkspace.vue'
import AdminInitialization from './components/AdminInitialization.vue'
import LoginRequired from './components/LoginRequired.vue'
import ReadUnavailable from './components/ReadUnavailable.vue'
import P021AdminOrderDetail from './components/P021AdminOrderDetail.vue'
import type { AdminReadState, AdminRole, PageId } from './domain/admin'

const activePage = ref<PageId>('A120')
const isInitializationRoute = window.location.pathname.replace(/\/+$/, '') === '/admin/initialize'
const previewRole = ref<AdminRole>('CS')
const localA110VisualState = ref<{ role: AdminRole; state: AdminReadState | null } | null>(null)
const resolvedMode = resolveAdminDataMode(import.meta.env.VITE_ADMIN_DATA_MODE, import.meta.env.DEV)
const readState = ref<AdminReadState>({ status: 'LOADING', data: null, message: '正在读取只读数据' })
const readController = createAdminReadController((state) => { readState.value = state })
const p021ItEntry = (() => {
  if (!import.meta.env.DEV) return null
  const query = new URLSearchParams(window.location.search)
  const role = query.get('p021ItRole')
  const orderRef = query.get('p021ItOrderRef')
  return orderRef && (role === 'CS' || role === 'FIN' || role === 'CONTENT') ? { orderRef, role: role as 'CS' | 'FIN' | 'CONTENT' } : null
})()

if (import.meta.env.DEV) {
  void import('../scripts/a110-visual-fixture-entry').then(({ resolveA110VisualScenario }) => {
    const scenario = resolveA110VisualScenario(window.location.search)
    if (!scenario) return
    localA110VisualState.value = scenario
    previewRole.value = scenario.role
    activePage.value = 'A110'
  })
}

const pages: Array<{ id: PageId; label: string; description: string }> = [
  { id: 'A120', label: '内容核验', description: '查看来源、责任、历史版本和展示状态' },
  { id: 'A130', label: '商品与运营商', description: '按职责查看互斥的目录或财务候选字段' },
  { id: 'A140', label: '订单与退款', description: '按职责查看互斥的客服或财务只读摘要' },
]
const roleLabels: Record<AdminRole, string> = { CS: '客服职责', CONTENT: '内容职责', FIN: '财务职责' }
const activeMeta = computed(() => pages.find(({ id }) => id === activePage.value)!)
const isSynthetic = computed(() => resolvedMode === 'BUILTIN_SYNTHETIC')
const visibleRoleLabel = computed(() => {
  if (readState.value.status === 'READY') {
    const role = readState.value.data.pageId === 'A110' ? readState.value.data.roleProjection : readState.value.data.role
    return roleLabels[role]
  }
  return isSynthetic.value ? roleLabels[previewRole.value] : '受信职责待确认'
})
const showHeaderRefresh = computed(() => {
  if (activePage.value !== 'A110') return true
  if (readState.value.status === 'LOADING' || readState.value.status === 'UNAUTHENTICATED' || readState.value.status === 'ACCESS_DENIED') return false
  if (readState.value.status === 'UNAVAILABLE') return true
  const data = readState.value.data
  return data.pageId === 'A110' && data.allowedActions.includes('READ_REFRESH')
})

async function refresh() {
  if (localA110VisualState.value && activePage.value === 'A110' && resolvedMode === 'BUILTIN_SYNTHETIC') {
    readState.value = { status: 'LOADING', data: null, message: '正在读取只读数据' }
    if (localA110VisualState.value.state === null) return
    readState.value = localA110VisualState.value.state
    return
  }
  await readController.refresh({ mode: resolvedMode, pageId: activePage.value, syntheticRole: previewRole.value })
}

function safeNavigate(pageId: 'A100' | 'A140') {
  activePage.value = pageId
}

watch([activePage, () => (isSynthetic.value ? previewRole.value : 'TRUSTED_ROLE')], refresh, { immediate: true })
</script>

<template>
  <AdminInitialization v-if="isInitializationRoute" />
  <main v-else-if="p021ItEntry" class="workspace p021-it-entry" data-it-entry="P021-ADMIN" :data-it-role="p021ItEntry.role">
    <P021AdminOrderDetail :order-ref="p021ItEntry.orderRef" :role="p021ItEntry.role" />
  </main>
  <div v-else class="admin-app">
    <header class="topbar">
      <div class="brand"><img :src="logoUrl" alt="华人在孟"><div><strong>华人在孟</strong><span>运营后台只读页面</span></div></div>
      <div class="topbar-actions">
        <span class="environment">{{ isSynthetic ? '界面预览' : '只读模式' }}</span>
        <label v-if="isSynthetic">查看职责
          <select v-model="previewRole" aria-label="选择界面查看职责"><option value="CS">客服职责</option><option value="CONTENT">内容职责</option><option value="FIN">财务职责</option></select>
        </label>
        <span v-else class="trusted-role">{{ visibleRoleLabel }}</span>
      </div>
    </header>

    <div class="app-shell">
      <nav class="sidebar" aria-label="后台页面导航">
        <div class="sidebar-title">运营工作台</div>
        <button v-for="page in pages" :key="page.id" :class="['nav-item', { active: activePage === page.id }]" :aria-current="activePage === page.id ? 'page' : undefined" @click="activePage = page.id">
          <span>{{ page.label }}</span>
        </button>
        <div class="sidebar-pending"><strong>后续开放</strong><span>客服案件、悬账与冲突仍在建设中，本版本不展示占位数据。</span></div>
        <div class="sidebar-foot"><strong>当前为只读界面</strong><span>实际可查看范围需重新确认，页面暂不支持修改。</span></div>
      </nav>

      <main class="workspace">
        <section class="page-heading">
          <div><p class="eyebrow">{{ visibleRoleLabel }}</p><h1>{{ activeMeta.label }}</h1><p>{{ activeMeta.description }}</p></div>
          <div class="heading-actions"><button v-if="showHeaderRefresh" class="secondary" :disabled="readState.status === 'LOADING'" @click="refresh">{{ readState.status === 'LOADING' ? '读取中' : '重新读取' }}</button><button disabled>暂不支持修改</button></div>
        </section>

        <section class="scope-banner"><strong>查看说明</strong><span>界面预览不代表真实业务结果；读取失败会清空旧信息，不显示替代数据。</span></section>

        <section v-if="readState.status === 'LOADING'" class="state-panel" data-read-state="LOADING" role="status" aria-live="polite">
          <div class="loading-mark" aria-hidden="true"></div><h2>正在读取</h2><p>旧页面数据已撤销，请稍候。</p>
        </section>
        <LoginRequired v-else-if="readState.status === 'UNAUTHENTICATED'" @authenticated="refresh" />
        <AccessDenied v-else-if="readState.status === 'ACCESS_DENIED'" :denial-kind="readState.denialKind" />
        <ReadUnavailable v-else-if="readState.status === 'UNAVAILABLE'" :message="readState.message" @retry="refresh" />
        <template v-else>
          <p class="sr-only" role="status" aria-live="polite" :data-read-state="readState.data.items.length === 0 ? 'READY_EMPTY' : 'READY'">{{ readState.data.items.length === 0 ? '只读数据读取完成，当前没有可查看记录' : `只读数据读取完成，共 ${readState.data.items.length} 条记录` }}</p>
          <AdminWorkspace :projection="readState.data" @navigate="safeNavigate" />
        </template>
      </main>
    </div>
  </div>
</template>
