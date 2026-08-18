<script setup lang="ts">
import { computed,ref } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import { executeWechatDevelopmentSignIn } from '../../api/auth-entry-contract'
import { callProjectApi } from '../../api/wechat-development-transport'
import { clearBuyerSessionToken,readBuyerSessionToken } from '../../api/buyer-session-token'
import { storeBuyerSessionProjection } from '../../domain/session'
import { requestWechatOneTimeCode } from '../../api/wechat-one-time-code'
import { agreementsAccepted,officialPrivacyGranted,sessionConsentCommand,type LoginPrivacyState } from '../../domain/login-privacy-state'
import { readOrCreateBuyerGuestRef } from '../../domain/buyer-guest-ref'

const loading=ref(false),error=ref('')
const userAgreementAccepted=ref(false),privacyPolicyAccepted=ref(false)
const privacyState=ref<LoginPrivacyState>('UNSEEN')
const canRequestOfficialPrivacy=computed(()=>agreementsAccepted({userAgreementAccepted:userAgreementAccepted.value,privacyPolicyAccepted:privacyPolicyAccepted.value}))

function updateAgreementSelection(event:{detail?:{value?:unknown}}){
  const values=Array.isArray(event?.detail?.value)?event.detail.value:[]
  userAgreementAccepted.value=values.includes('USER_AGREEMENT')
  privacyPolicyAccepted.value=values.includes('PRIVACY_POLICY')
  privacyState.value=canRequestOfficialPrivacy.value?'AGREEMENTS_ACCEPTED':'UNSEEN'
  error.value=''
}

function beginOfficialPrivacyAuthorization(){
  if(!canRequestOfficialPrivacy.value){privacyState.value='UNSEEN';error.value='请先阅读并同时同意《用户协议》和《隐私政策》。';return}
  privacyState.value='OFFICIAL_PRIVACY_PENDING';error.value=''
}

async function onOfficialPrivacyAuthorized(event:unknown){
  if(privacyState.value!=='OFFICIAL_PRIVACY_PENDING'||!canRequestOfficialPrivacy.value||!officialPrivacyGranted(event)){
    privacyState.value=canRequestOfficialPrivacy.value?'AGREEMENTS_ACCEPTED':'UNSEEN'
    error.value='未完成微信官方隐私授权。你仍可匿名浏览，交易功能需要完成授权和登录。'
    return
  }
  privacyState.value='OFFICIAL_PRIVACY_GRANTED'
  await signIn()
}

async function signIn(){
  if(loading.value||privacyState.value!=='OFFICIAL_PRIVACY_GRANTED')return
  const consent=sessionConsentCommand({userAgreementAccepted:userAgreementAccepted.value,privacyPolicyAccepted:privacyPolicyAccepted.value})
  if(!consent){privacyState.value='UNSEEN';return}
  loading.value=true;error.value='';privacyState.value='LOGIN_CODE_PENDING'
  try{
    const code=await requestWechatOneTimeCode(wx)
    await executeWechatDevelopmentSignIn(code,`LOGIN-${Date.now().toString(36)}`,readOrCreateBuyerGuestRef(uni),consent)
    const token=readBuyerSessionToken()
    if(!token)throw new Error('BUYER_SESSION_REQUIRED')
    const response=await callProjectApi('/buyer-api/v1/session','GET',undefined,token.token)
    if(response.statusCode!==200)throw new Error('BUYER_SESSION_PROJECTION_UNAVAILABLE')
    storeBuyerSessionProjection(uni,response.data)
    privacyState.value='SESSION_ACTIVE'
    uni.reLaunch({url:'/pages/index/index'})
  }catch(cause){
    clearBuyerSessionToken()
    privacyState.value='AGREEMENTS_ACCEPTED'
    error.value=cause instanceof Error&&cause.message==='BUYER_ACCOUNT_CLOSURE_PENDING'?'账号注销申请正在处理中，当前不能重新登录。':cause instanceof Error&&cause.message==='BUYER_CONSENT_REQUIRED'?'协议版本已更新，请重新阅读后再试。':'微信登录暂时不可用，请稍后重试。'
  }finally{loading.value=false}
}
</script>
<template><view class="page" data-page-id="UX-P022"><AppHeader left="关闭" @left="uni.reLaunch({url:'/pages/index/index'})"/><view class="content center"><view class="identity">微</view><view class="heading" role="heading" aria-level="1">微信快捷登录</view><text class="desc">登录后可查看自己的订单并继续充值服务。拒绝协议或微信官方隐私授权不影响公开内容浏览，但无法使用交易功能。</text><checkbox-group class="agreements" @change="updateAgreementSelection"><label class="agreement-row"><checkbox value="USER_AGREEMENT" :checked="userAgreementAccepted" color="#195bb8"/><text>我已阅读并同意</text><text class="protocol-link" @click.stop="uni.navigateTo({url:'/pages/legal/user-agreement'})">《用户协议》</text></label><label class="agreement-row"><checkbox value="PRIVACY_POLICY" :checked="privacyPolicyAccepted" color="#195bb8"/><text>我已阅读并同意</text><text class="protocol-link" @click.stop="uni.navigateTo({url:'/pages/legal/privacy-policy'})">《隐私政策》</text></label></checkbox-group><text v-if="error" class="risk">{{error}}</text></view><view class="action"><button v-if="canRequestOfficialPrivacy" class="primary" open-type="agreePrivacyAuthorization" :disabled="loading" @click="beginOfficialPrivacyAuthorization" @agreeprivacyauthorization="onOfficialPrivacyAuthorized">{{loading?'登录中…':'微信隐私授权并登录'}}</button><button v-else class="primary" disabled @click="beginOfficialPrivacyAuthorization">请先同意两项协议</button><button class="secondary" @click="uni.reLaunch({url:'/pages/index/index'})">暂不登录，继续浏览</button></view></view></template>
<style src="../../styles/shared.css"></style><style scoped>.center{text-align:center;padding-top:58rpx}.identity{width:120rpx;height:120rpx;border-radius:36rpx;background:#07c160;color:#fff;font-size:46rpx;font-weight:900;display:flex;align-items:center;justify-content:center;margin:20rpx auto}.agreements{margin-top:38rpx;padding:24rpx 26rpx;border-radius:24rpx;background:#fff;text-align:left}.agreement-row{min-height:72rpx;display:flex;align-items:center;font-size:23rpx}.agreement-row checkbox{margin-right:12rpx;transform:scale(.82)}.protocol-link{color:var(--brand);font-weight:700}.risk{display:block;color:var(--risk);margin-top:24rpx;line-height:1.55}</style>
