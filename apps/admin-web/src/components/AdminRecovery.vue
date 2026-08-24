<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { recoverAdmin, recoveryStatus } from '../api/admin-auth'

const status=ref<'LOADING'|'AVAILABLE'|'CLOSED'|'RESET'>('LOADING')
const username=ref('yenanc'),password=ref(''),confirmation=ref(''),recoveryToken=ref(''),submitting=ref(false),errorMessage=ref('')
onMounted(async()=>{status.value=await recoveryStatus()})
async function submit(){
  if(password.value!==confirmation.value){errorMessage.value='两次输入的密码不一致';return}
  submitting.value=true;errorMessage.value=''
  try{await recoverAdmin(username.value,password.value,recoveryToken.value);status.value='RESET'}
  catch(error){errorMessage.value=error instanceof Error&&error.message==='WEAK_PASSWORD'?'密码至少16位，不能包含用户名且不能过于简单':'恢复未完成，请确认恢复码和有效时间'}
  finally{password.value='';confirmation.value='';recoveryToken.value='';submitting.value=false}
}
</script>
<template><main class="login-required initialization-page"><section class="login-card" aria-live="polite"><p class="eyebrow">运营后台</p><h1>重置管理员密码</h1><p v-if="status==='LOADING'">正在确认恢复状态……</p><template v-else-if="status==='AVAILABLE'"><p>恢复入口仅在服务端批准的短时窗口内可用。成功后旧会话全部失效。</p><form class="login-form" @submit.prevent="submit"><label>用户名<input v-model.trim="username" autocomplete="username" required maxlength="32"></label><label>新密码<input v-model="password" type="password" autocomplete="new-password" required minlength="16" maxlength="128"></label><label>确认新密码<input v-model="confirmation" type="password" autocomplete="new-password" required minlength="16" maxlength="128"></label><label>一次性恢复码<input v-model="recoveryToken" type="password" autocomplete="off" required></label><p v-if="errorMessage" class="form-error" role="alert">{{errorMessage}}</p><button class="primary" type="submit" :disabled="submitting">{{submitting?'重置中':'重置密码'}}</button></form></template><template v-else-if="status==='RESET'"><h2>密码已重置</h2><p>账号锁定已清除，旧会话已撤销。</p><a class="primary button-link" href="/admin/login">返回登录</a></template><template v-else><h2>恢复入口未开放</h2><p>请先由运维人员开启短时恢复窗口。</p><a href="/admin/login">返回登录</a></template></section></main></template>
