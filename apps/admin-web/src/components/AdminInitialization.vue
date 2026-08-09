<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { initializationStatus, initializeAdmin } from '../api/admin-auth'

const status = ref<'LOADING' | 'AVAILABLE' | 'CLOSED' | 'CREATED'>('LOADING')
const username = ref('yenanc')
const displayName = ref('南哥')
const password = ref('')
const confirmation = ref('')
const bootstrapToken = ref('')
const submitting = ref(false)
const errorMessage = ref('')

onMounted(async () => { status.value = await initializationStatus() })

async function submit() {
  if (password.value !== confirmation.value) { errorMessage.value = '两次输入的密码不一致'; return }
  submitting.value = true; errorMessage.value = ''
  try {
    await initializeAdmin(username.value, displayName.value, password.value, bootstrapToken.value)
    password.value = ''; confirmation.value = ''; bootstrapToken.value = ''; status.value = 'CREATED'
  } catch (error) {
    password.value = ''; confirmation.value = ''; bootstrapToken.value = ''
    errorMessage.value = error instanceof Error && error.message === 'WEAK_PASSWORD'
      ? '密码至少16位，不能包含用户名且不能过于简单'
      : '初始化未完成，请确认一次性初始化码和服务状态'
  } finally { submitting.value = false }
}
</script>

<template>
  <main class="login-required initialization-page">
    <section class="login-card" aria-live="polite">
      <p class="eyebrow">运营后台</p><h1>初始化超级管理员</h1>
      <p v-if="status === 'LOADING'">正在确认初始化状态……</p>
      <template v-else-if="status === 'AVAILABLE'">
        <p>此入口只能成功使用一次。密码仅提交给当前 HTTPS 服务。</p>
        <form class="login-form" @submit.prevent="submit">
          <label>显示名称<input v-model.trim="displayName" autocomplete="name" required maxlength="40"></label>
          <label>用户名<input v-model.trim="username" autocomplete="username" required maxlength="32"></label>
          <label>密码<input v-model="password" type="password" autocomplete="new-password" required minlength="16" maxlength="128"></label>
          <label>确认密码<input v-model="confirmation" type="password" autocomplete="new-password" required minlength="16" maxlength="128"></label>
          <label>一次性初始化码<input v-model="bootstrapToken" type="password" autocomplete="off" required></label>
          <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
          <button class="primary" type="submit" :disabled="submitting">{{ submitting ? '创建中' : '创建超级管理员' }}</button>
        </form>
      </template>
      <template v-else-if="status === 'CREATED'"><h2>初始化完成</h2><p>超级管理员已创建，本入口已经关闭。</p><a class="primary button-link" href="/">进入登录页</a></template>
      <template v-else><h2>初始化入口已关闭</h2><p>系统已存在管理员，或服务端没有显式开放初始化。</p><a href="/">返回登录页</a></template>
    </section>
  </main>
</template>
