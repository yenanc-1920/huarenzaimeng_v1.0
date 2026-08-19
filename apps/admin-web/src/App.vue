<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import logoUrl from '../../miniapp/src/static/logo.png'
import { logoutAdmin } from './api/admin-auth'
import { loadAdminPage, resolveAdminDataMode } from './api/admin-read'
import { createAdminReadController } from './api/admin-read-controller'
import AccessDenied from './components/AccessDenied.vue'
import AdminWorkspace from './components/AdminWorkspace.vue'
import AdminInitialization from './components/AdminInitialization.vue'
import LoginRequired from './components/LoginRequired.vue'
import ReadUnavailable from './components/ReadUnavailable.vue'
import AdminOrderDetail from './components/AdminOrderDetail.vue'
import B002Dashboard from './components/workspaces/B002Dashboard.vue'
import type { AdminReadState, PageId } from './domain/admin'

type ViewId = 'B002' | PageId
const allViews: ViewId[] = ['B002', 'A100', 'A110', 'A120', 'A121', 'A122', 'A130', 'A140']
const hashView = () => { const candidate = window.location.hash.replace(/^#\/?/, '') as ViewId; return allViews.includes(candidate) ? candidate : 'B002' }
const activePage = ref<ViewId>(hashView())
const isInitializationRoute = window.location.pathname.replace(/\/+$/, '') === '/admin/initialize'
const resolvedMode = resolveAdminDataMode(import.meta.env.VITE_ADMIN_DATA_MODE)
const readState = ref<AdminReadState>({ status: 'LOADING', data: null, message: '正在读取只读数据' })
const authResolved = ref(false)
const loggingOut = ref(false)
const selectedOrderRef = ref<string | null>(null)
const icpFiling = import.meta.env.VITE_ICP_FILING?.trim() || 'ICP备案信息上线前补充'
const publicSecurityFiling = import.meta.env.VITE_PUBLIC_SECURITY_FILING?.trim() || '公安备案信息上线前补充'
const environmentLabel = import.meta.env.VITE_ENVIRONMENT_LABEL?.trim() || '运营环境'
const readController = createAdminReadController((state) => { readState.value = state })
let dashboardGeneration = 0
const pages: Array<{ id: ViewId; label: string; description: string }> = [
  { id: 'B002', label: '运营工作台', description: '查看当前环境的业务概览与服务状态' },
  { id: 'A100', label: '客服案件', description: '跟进数据库中的客服案件，不改写交易事实' },
  { id: 'A110', label: '交易差异', description: '核对支付、充值和退款差异，禁止重充和手工改终态' },
  { id: 'A120', label: '内容审核', description: '审核黄页、节假日和资讯版本，不编辑业务正文' },
  { id: 'A121', label: '黄页管理', description: '维护城市和机构的结构化文字资料，V1不含图片' },
  { id: 'A122', label: '节假日与资讯', description: '维护周休、节假日和纯文字生活资讯，V1不含图片' },
  { id: 'A130', label: '商品、渠道与定价', description: '管理持久化商品和价格；供应商同步保持不可达' },
  { id: 'A140', label: '订单与退款', description: '只读查看订单、支付、充值和退款事实' },
]
const navGroups: Array<{ label: string; views: ViewId[] }> = [
  { label: '概览', views: ['B002'] },
  { label: '服务运营', views: ['A100', 'A110', 'A140'] },
  { label: '内容运营', views: ['A120', 'A121', 'A122'] },
  { label: '商品与交易', views: ['A130'] },
]
const activeMeta = computed(() => pages.find(({ id }) => id === activePage.value)!)
const visibleRoleLabel = computed(() => { const state = readState.value; if (state.status !== 'READY') return '登录角色'; return state.data.role })
const showHeaderRefresh = computed(() => readState.value.status !== 'LOADING' && readState.value.status !== 'UNAUTHENTICATED' && readState.value.status !== 'ACCESS_DENIED')

watch(readState, (state) => {
  if (state.status === 'LOADING') return
  authResolved.value = state.status !== 'UNAUTHENTICATED'
  const expectedPath = state.status === 'UNAUTHENTICATED' ? '/admin/login' : '/'
  if (window.location.pathname !== expectedPath) window.history.replaceState(null, '', expectedPath)
})
async function authenticated() { authResolved.value = true; window.history.replaceState(null, '', `/#/${activePage.value}`); await refresh() }
async function logout() { if (loggingOut.value) return; loggingOut.value = true; try { await logoutAdmin() } finally { readState.value = { status: 'UNAUTHENTICATED', data: null, message: '登录已退出' }; authResolved.value = false; loggingOut.value = false } }
async function refresh() {
  if (activePage.value !== 'B002') { dashboardGeneration += 1; await readController.refresh({ mode: resolvedMode, pageId: activePage.value }); return }
  const generation = ++dashboardGeneration
  if (!resolvedMode) { readState.value = { status: 'UNAVAILABLE', data: null, message: '数据模式配置不受支持' }; return }
  readState.value = { status: 'LOADING', data: null, message: '正在确认工作台访问权限' }
  let denied: AdminReadState | null = null
  for (const pageId of ['A130', 'A100', 'A140'] as PageId[]) {
    const state = await loadAdminPage(resolvedMode, pageId)
    if (generation !== dashboardGeneration || activePage.value !== 'B002') return
    if (state.status === 'READY' || state.status === 'UNAUTHENTICATED') { readState.value = state; return }
    if (state.status === 'ACCESS_DENIED') denied = state
  }
  if (generation === dashboardGeneration && activePage.value === 'B002') readState.value = denied ?? { status: 'UNAVAILABLE', data: null, message: '运营工作台暂不可读取' }
}
function safeNavigate(pageId: 'A100' | 'A140') { activePage.value = pageId }
function selectOrder(orderRef: string) { selectedOrderRef.value = orderRef }
function syncHash() { activePage.value = hashView() }
onMounted(() => window.addEventListener('hashchange', syncHash))
onBeforeUnmount(() => window.removeEventListener('hashchange', syncHash))
watch(activePage, refresh, { immediate: true })
watch(activePage, view => { const next = `#/${view}`; if (window.location.hash !== next) window.history.replaceState(null, '', `/${next}`); selectedOrderRef.value = null })
</script>

<template>
  <AdminInitialization v-if="isInitializationRoute" />
  <LoginRequired v-else-if="!authResolved && readState.status === 'UNAUTHENTICATED'" class="standalone-login" @authenticated="authenticated" />
  <main v-else-if="!authResolved" class="auth-check" data-read-state="AUTH_CHECK" role="status" aria-live="polite"><img :src="logoUrl" alt="华人在孟"><div class="loading-mark" aria-hidden="true"></div><h1>正在确认登录状态</h1><p>确认完成前不会显示后台菜单或业务数据。</p></main>
  <div v-else class="admin-app">
    <header class="topbar"><div class="brand"><img :src="logoUrl" alt="华人在孟"><div><strong>华人在孟</strong><span>运营管理后台</span></div></div><div class="topbar-actions"><span class="environment">{{ environmentLabel }}</span><span class="trusted-role">{{ visibleRoleLabel }}</span><button class="logout-button" type="button" :disabled="loggingOut" @click="logout">{{ loggingOut ? '退出中' : '退出登录' }}</button></div></header>
    <div class="app-shell">
      <nav class="sidebar" aria-label="后台页面导航"><div class="sidebar-title">运营管理</div><section v-for="group in navGroups" :key="group.label" class="nav-group"><p>{{ group.label }}</p><button v-for="page in pages.filter(item => group.views.includes(item.id))" :key="page.id" :class="['nav-item', { active: activePage === page.id }]" :aria-current="activePage === page.id ? 'page' : undefined" @click="activePage = page.id"><span>{{ page.label }}</span></button></section><div class="sidebar-foot"><img :src="logoUrl" alt=""><span><strong>{{ visibleRoleLabel }}</strong><small>已安全登录</small></span></div></nav>
      <main class="workspace">
        <section v-if="activePage !== 'B002'" class="page-heading"><div><p class="eyebrow">{{ visibleRoleLabel }}</p><h1>{{ activeMeta.label }}</h1><p>{{ activeMeta.description }}</p></div><div class="heading-actions"><button v-if="showHeaderRefresh" class="secondary" :disabled="readState.status === 'LOADING'" @click="refresh">{{ readState.status === 'LOADING' ? '读取中' : '重新读取' }}</button></div></section>
        <section v-if="activePage !== 'B002'" class="scope-banner"><strong>数据说明</strong><span>页面展示当前环境业务数据。读取失败会撤销旧信息，不显示替代数据；未启用的外部服务不会被调用。</span></section>
        <section v-if="readState.status === 'LOADING'" class="state-panel" data-read-state="LOADING" role="status" aria-live="polite"><div class="loading-mark" aria-hidden="true"></div><h2>正在读取</h2><p>旧页面数据已撤销，请稍候。</p></section>
        <LoginRequired v-else-if="readState.status === 'UNAUTHENTICATED'" @authenticated="authenticated" />
        <AccessDenied v-else-if="readState.status === 'ACCESS_DENIED'" :denial-kind="readState.denialKind" />
        <ReadUnavailable v-else-if="readState.status === 'UNAVAILABLE'" :message="readState.message" @retry="refresh" />
        <template v-else><p class="sr-only" role="status" aria-live="polite" :data-read-state="readState.data.items.length === 0 ? 'READY_EMPTY' : 'READY'">{{ readState.data.items.length === 0 ? '数据读取完成，当前没有可查看记录' : `数据读取完成，共 ${readState.data.items.length} 条记录` }}</p><B002Dashboard v-if="activePage === 'B002'" :mode="resolvedMode" @navigate="activePage=$event" /><AdminOrderDetail v-else-if="activePage==='A140' && selectedOrderRef" :order-ref="selectedOrderRef" @close="selectedOrderRef=null" /><AdminWorkspace v-else :projection="readState.data" @navigate="safeNavigate" @select-order="selectOrder" @changed="refresh" /></template>
      </main>
    </div>
    <footer class="compliance-footer" aria-label="备案信息"><span>{{ icpFiling }}</span><span>{{ publicSecurityFiling }}</span></footer>
  </div>
</template>
