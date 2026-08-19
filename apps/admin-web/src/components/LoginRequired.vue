<script setup lang="ts">
import { ref } from 'vue'
import { loginAdmin } from '../api/admin-auth'

const emit = defineEmits<{ authenticated: [] }>()
const username = ref('')
const password = ref('')
const submitting = ref(false)
const errorMessage = ref('')

async function submit() {
  submitting.value = true
  errorMessage.value = ''
  try {
    await loginAdmin(username.value, password.value)
    password.value = ''
    emit('authenticated')
  } catch {
    password.value = ''
    errorMessage.value = '用户名或密码不正确'
  } finally { submitting.value = false }
}
</script>

<template>
  <section class="login-required" data-visual-frame-id="B001" data-read-state="UNAUTHENTICATED" role="alert" aria-live="assertive">
    <div class="login-card">
      <img src="/src/assets/login-lock.svg" alt="">
      <p class="eyebrow">运营后台</p>
      <h1>请先登录</h1>
      <p>登录后，系统将由服务端确认账号、职责和可查看范围。</p>
      <form class="login-form" @submit.prevent="submit">
        <label>用户名<input v-model.trim="username" name="username" autocomplete="username" required maxlength="32"></label>
        <label>密码<input v-model="password" name="password" type="password" autocomplete="current-password" required maxlength="128"></label>
        <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
        <button class="primary" type="submit" :disabled="submitting">{{ submitting ? '登录中' : '登录' }}</button>
      </form>
      <p class="login-note">后台不开放自行注册，也不能在页面中选择或扩大账号职责。</p>
    </div>
  </section>
</template>
