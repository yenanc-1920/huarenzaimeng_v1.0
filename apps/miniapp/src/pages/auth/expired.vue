<script setup lang="ts">
import { ref } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import { executeWechatDevelopmentSignIn } from '../../api/auth-entry-contract'
import { callProjectApi } from '../../api/wechat-development-transport'
import { clearBuyerSessionToken, readBuyerSessionToken } from '../../api/buyer-session-token'
import { storeBuyerSessionProjection } from '../../domain/session'
import { requestWechatOneTimeCode } from '../../api/wechat-one-time-code'

const loading=ref(false),error=ref('')
async function signIn(){
  if(loading.value)return
  loading.value=true;error.value=''
  try{
    const code=await requestWechatOneTimeCode(wx)
    await executeWechatDevelopmentSignIn(code,`WXDEV-${Date.now().toString(36)}`)
    const token=readBuyerSessionToken()
    if(!token)throw new Error('BUYER_SESSION_REQUIRED')
    const response=await callProjectApi('/buyer-api/v1/session','GET',undefined,token.token)
    if(response.statusCode!==200)throw new Error('BUYER_SESSION_PROJECTION_UNAVAILABLE')
    storeBuyerSessionProjection(uni,response.data)
    uni.reLaunch({url:'/pages/index/index'})
  }catch{clearBuyerSessionToken();error.value='微信登录暂时不可用，请稍后重试。'}finally{loading.value=false}
}
</script>
<template><view class="page" data-page-id="UX-P022"><AppHeader left="关闭" @left="uni.reLaunch({url:'/pages/index/index'})"/><view class="content center"><view class="identity">微</view><view class="heading" role="heading" aria-level="1">微信快捷登录</view><text class="desc">登录后可查看自己的订单并继续充值服务。找回历史订单仅在重新登录后订单仍未显示时使用。</text><text v-if="error" class="risk">{{error}}</text></view><view class="action"><button class="primary" :disabled="loading" @click="signIn">{{loading?'登录中…':'微信快捷登录'}}</button><button class="secondary" @click="uni.navigateTo({url:'/pages/order/recovery'})">找回历史订单</button></view></view></template><style src="../../styles/shared.css"></style><style scoped>.center{text-align:center;padding-top:100rpx}.identity{width:120rpx;height:120rpx;border-radius:36rpx;background:#07c160;color:#fff;font-size:46rpx;font-weight:900;display:flex;align-items:center;justify-content:center;margin:20rpx auto}.risk{display:block;color:var(--risk);margin-top:24rpx}</style>
