<script setup lang="ts">
import { ref } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import { executeWechatDevelopmentSignIn } from '../../api/auth-entry-contract'
import { callProjectApi } from '../../api/wechat-development-transport'
import { readBuyerSessionToken } from '../../api/buyer-session-token'
import { storeBuyerSessionProjection } from '../../domain/session'

const loading=ref(false),error=ref('')
async function signIn(){
  if(loading.value)return
  loading.value=true;error.value=''
  try{
    const code=await new Promise<string>((resolve,reject)=>wx.login({success:r=>r.code?resolve(r.code):reject(new Error('WX_LOGIN_CODE_MISSING')),fail:()=>reject(new Error('WX_LOGIN_FAILED'))}))
    await executeWechatDevelopmentSignIn(code,`WXDEV-${Date.now().toString(36)}`)
    const token=readBuyerSessionToken()
    if(!token)throw new Error('BUYER_SESSION_REQUIRED')
    const response=await callProjectApi('/buyer-api/v1/session','GET',undefined,token.token)
    if(response.statusCode!==200)throw new Error('BUYER_SESSION_PROJECTION_UNAVAILABLE')
    storeBuyerSessionProjection(uni,response.data)
    uni.reLaunch({url:'/pages/index/index'})
  }catch(caught){error.value=caught instanceof Error?caught.message:'登录失败'}finally{loading.value=false}
}
</script>
<template><view class="page"><AppHeader left="关闭" @left="uni.reLaunch({url:'/pages/index/index'})"/><view class="content center"><view class="identity">⌁</view><view class="heading" role="heading" aria-level="1">微信快捷登录</view><text class="desc">确认完成前保持GUEST；登录成功后建立当前开发会话。恢复历史订单仍使用独立入口。</text><text v-if="error" class="risk">{{error}}</text></view><view class="action"><button class="primary" :disabled="loading" @click="signIn">{{loading?'登录中…':'微信快捷登录'}}</button><button class="secondary" @click="uni.navigateTo({url:'/pages/order/recovery'})">恢复订单访问</button></view></view></template><style src="../../styles/shared.css"></style><style scoped>.center{text-align:center}.identity{width:120rpx;height:120rpx;border-radius:36rpx;background:#edf3fc;color:var(--brand);font-size:50rpx;display:flex;align-items:center;justify-content:center;margin:20rpx auto}.risk{display:block;color:var(--danger);margin-top:24rpx}</style>
