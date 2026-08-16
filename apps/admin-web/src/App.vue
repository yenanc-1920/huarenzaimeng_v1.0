<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import logoUrl from '../../miniapp/src/static/logo.png'
import { logoutAdmin } from './api/admin-auth'
import { resolveAdminDataMode } from './api/admin-read'
import { createAdminReadController } from './api/admin-read-controller'
import AccessDenied from './components/AccessDenied.vue'
import AdminWorkspace from './components/AdminWorkspace.vue'
import AdminInitialization from './components/AdminInitialization.vue'
import LoginRequired from './components/LoginRequired.vue'
import ReadUnavailable from './components/ReadUnavailable.vue'
import AdminOrderSandboxDetail from './components/AdminOrderSandboxDetail.vue'
import type { AdminReadState, PageId } from './domain/admin'

const activePage = ref<PageId>('A130')
const isInitializationRoute = window.location.pathname.replace(/\/+$/, '') === '/admin/initialize'
const resolvedMode = resolveAdminDataMode(import.meta.env.VITE_ADMIN_DATA_MODE)
const readState = ref<AdminReadState>({ status: 'LOADING', data: null, message: '正在读取只读数据' })
const authResolved = ref(false)
const loggingOut = ref(false)
const selectedOrderRef = ref<string | null>(null)
const icpFiling = import.meta.env.VITE_ICP_FILING?.trim() || 'ICP备案号待补充（开发样例）'
const publicSecurityFiling = import.meta.env.VITE_PUBLIC_SECURITY_FILING?.trim() || '公安备案号待补充（开发样例）'
const readController = createAdminReadController((state) => { readState.value = state })
const pages: Array<{ id: PageId; label: string; description: string }> = [
  { id: 'A100', label: '客服案件', description: '跟进本地开发库中的客服案件，不改写交易事实' },
  { id: 'A110', label: '交易差异', description: '核对支付、充值和退款差异，禁止重充和手工改终态' },
  { id: 'A120', label: '内容审核', description: '审核黄页、节假日和资讯版本，不编辑业务正文' },
  { id: 'A121', label: '黄页管理', description: '维护城市和机构的结构化文字资料，V1不含图片' },
  { id: 'A122', label: '节假日与资讯', description: '维护周休、节假日和纯文字生活资讯，V1不含图片' },
  { id: 'A130', label: '商品、渠道与定价', description: '管理本地预置商品和价格；供应商同步保持不可达' },
  { id: 'A140', label: '订单与退款', description: '只读查看订单、支付、充值和退款事实' },
]
const activeMeta = computed(() => pages.find(({ id }) => id === activePage.value)!)
const visibleRoleLabel = computed(() => {
  const state = readState.value
  if (state.status !== 'READY') return '登录角色'
  return state.data.pageId === 'A110' ? `${state.data.role} 只读投影` : state.data.role
})
const showHeaderRefresh = computed(() => {
  return readState.value.status !== 'LOADING' && readState.value.status !== 'UNAUTHENTICATED' && readState.value.status !== 'ACCESS_DENIED'
})

watch(readState, (state) => {
  if (state.status === 'LOADING') return
  authResolved.value = state.status !== 'UNAUTHENTICATED'
  const expectedPath = state.status === 'UNAUTHENTICATED' ? '/admin/login' : '/'
  if (window.location.pathname !== expectedPath) window.history.replaceState(null, '', expectedPath)
})

async function authenticated() {
  authResolved.value = true
  window.history.replaceState(null, '', '/')
  await refresh()
}

async function logout() {
  if (loggingOut.value) return
  loggingOut.value = true
  try { await logoutAdmin() }
  finally {
    readState.value = { status: 'UNAUTHENTICATED', data: null, message: '登录已退出' }
    authResolved.value = false
    loggingOut.value = false
  }
}

async function refresh() {
  await readController.refresh({ mode: resolvedMode, pageId: activePage.value })
}

function safeNavigate(pageId: 'A100' | 'A140') {
  activePage.value = pageId
}

function selectOrder(orderRef: string) {
  selectedOrderRef.value = orderRef
}

watch(activePage, refresh, { immediate: true })
</script>

<template>
  <AdminInitialization v-if="isInitializationRoute" />
  <LoginRequired v-else-if="!authResolved && readState.status === 'UNAUTHENTICATED'" class="standalone-login" @authenticated="authenticated" />
  <main v-else-if="!authResolved" class="auth-check" data-read-state="AUTH_CHECK" role="status" aria-live="polite">
    <img :src="logoUrl" alt="华人在孟">
    <div class="loading-mark" aria-hidden="true"></div>
    <h1>正在确认登录状态</h1>
    <p>确认完成前不会显示后台菜单或业务数据。</p>
  </main>
  <div v-else class="admin-app">
    <header class="topbar">
      <div class="brand"><img :src="logoUrl" alt="华人在孟"><div><strong>华人在孟</strong><span>运营后台只读页面</span></div></div>
      <div class="topbar-actions">
        <span class="environment">开发环境</span>
        <span class="data-origin">本地数据库预置数据</span>
        <span class="trusted-role">{{ visibleRoleLabel }}</span>
        <button class="logout-button" type="button" :disabled="loggingOut" @click="logout">{{ loggingOut ? '退出中' : '退出登录' }}</button>
      </div>
    </header>

    <div class="app-shell">
      <nav class="sidebar" aria-label="后台页面导航">
        <div class="sidebar-title">运营工作台</div>
        <button v-for="page in pages" :key="page.id" :class="['nav-item', { active: activePage === page.id }]" :aria-current="activePage === page.id ? 'page' : undefined" @click="activePage = page.id">
          <span>{{ page.label }}</span>
        </button>
        <div class="sidebar-pending"><strong>真实接口边界</strong><span>页面不读取 Mock 或合成数据；外部供应商目录同步、充值和余额操作当前不可达。</span></div>
        <div class="sidebar-foot"><strong>开发库操作</strong><span>新增、编辑和启停仅作用于本地开发库预置记录，并保留版本与审计。</span></div>
      </nav>

      <main class="workspace">
        <section class="page-heading">
          <div><p class="eyebrow">{{ visibleRoleLabel }}</p><h1>{{ activeMeta.label }}</h1><p>{{ activeMeta.description }}</p></div>
          <div class="heading-actions"><button v-if="showHeaderRefresh" class="secondary" :disabled="readState.status === 'LOADING'" @click="refresh">{{ readState.status === 'LOADING' ? '读取中' : '重新读取' }}</button><button disabled>暂不支持修改</button></div>
        </section>

        <section class="scope-banner"><strong>数据说明</strong><span>当前只读取本地开发数据库的持久化预置记录。读取失败会撤销旧信息，不显示替代数据；外部供应商功能保持不可达。</span></section>

        <section v-if="readState.status === 'LOADING'" class="state-panel" data-read-state="LOADING" role="status" aria-live="polite">
          <div class="loading-mark" aria-hidden="true"></div><h2>正在读取</h2><p>旧页面数据已撤销，请稍候。</p>
        </section>
        <LoginRequired v-else-if="readState.status === 'UNAUTHENTICATED'" @authenticated="authenticated" />
        <AccessDenied v-else-if="readState.status === 'ACCESS_DENIED'" :denial-kind="readState.denialKind" />
        <ReadUnavailable v-else-if="readState.status === 'UNAVAILABLE'" :message="readState.message" @retry="refresh" />
        <template v-else>
          <p class="sr-only" role="status" aria-live="polite" :data-read-state="readState.data.items.length === 0 ? 'READY_EMPTY' : 'READY'">{{ readState.data.items.length === 0 ? '只读数据读取完成，当前没有可查看记录' : `只读数据读取完成，共 ${readState.data.items.length} 条记录` }}</p>
          <AdminOrderSandboxDetail v-if="activePage==='A140' && selectedOrderRef" :order-ref="selectedOrderRef" @close="selectedOrderRef=null" />
          <AdminWorkspace v-else :projection="readState.data" @navigate="safeNavigate" @select-order="selectOrder" @changed="refresh" />
        </template>
      </main>
    </div>
    <footer class="compliance-footer" aria-label="备案信息"><span>{{ icpFiling }}</span><span>{{ publicSecurityFiling }}</span></footer>
  </div>
</template>
