<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppHeader from '../../components/AppHeader.vue'
import StatusNotice from '../../components/StatusNotice.vue'
import { v2PublicApi as api } from '../../api/v2-public-client'
import type { DirectoryItem } from '../../domain/types'

const directoryCategoryLabel=(category:string):string=>({LIFE_SERVICE:'生活服务',MEDICAL:'医疗服务'}[category]||'其他服务')

const entryRef=ref('')
const item=ref<DirectoryItem|null>(null)
const loading=ref(true)
const error=ref('')
const reportError=ref('')
const showDial=ref(false)
const showReport=ref(false)
const reportReason=ref('INCORRECT_INFO')
const reportNote=ref('')
const reported=ref(false)
const reporting=ref(false)
const reportRef=ref('')
const navigateBack=()=>uni.navigateBack()

async function load(){
  loading.value=true
  item.value=null
  error.value=''
  try{item.value=await api.getDirectoryDetail(entryRef.value)}
  catch{error.value='黄页详情暂时无法读取，请稍后重试。'}
  finally{loading.value=false}
}
const confirmDial=()=>{if(item.value?.phone)showDial.value=true}
const dial=()=>{showDial.value=false;if(item.value?.phone)uni.makePhoneCall({phoneNumber:item.value.phone})}
const report=async()=>{
  if(reporting.value||reported.value)return
  reporting.value=true;reportError.value=''
  try{const result=await api.reportDirectoryError(entryRef.value,reportReason.value,reportNote.value.trim());reportRef.value=result.supportRef;reported.value=true;showReport.value=false}
  catch{reportError.value='问题反馈暂时无法提交，请稍后重试。'}
  finally{reporting.value=false}
}
onLoad(query=>{entryRef.value=typeof query?.entryRef==='string'?decodeURIComponent(query.entryRef):'';void load()})
</script>

<template>
  <view class="page" data-page-id="UX-P041">
    <AppHeader title="黄页详情" left="返回黄页" @left="navigateBack"/>
    <view class="content">
      <view v-if="loading" class="card">正在读取黄页详情…</view>
      <StatusNotice v-else-if="error" tone="risk" title="暂时无法读取">{{error}}</StatusNotice>
      <template v-else-if="item">
        <view class="directory-hero">
          <text class="hero-label">便民黄页</text>
          <view class="heading">{{item.displayName}}</view>
          <text class="hero-summary">{{item.summary}}</text>
        </view>
        <view class="card detail-card">
          <view class="kv"><text>城市</text><text>{{item.cityName}}</text></view>
          <view class="kv"><text>分类</text><text>{{directoryCategoryLabel(item.category)}}</text></view>
          <view class="kv"><text>当地地址</text><text>{{item.localAddress}}</text></view>
          <view class="kv"><text>联系电话</text><text>{{item.phone}}</text></view>
          <view class="kv"><text>信息来源</text><text>{{item.sourceLabel||'来源暂未提供'}}</text></view>
          <view class="kv"><text>最近核验</text><text>{{item.verifiedAt}}</text></view>
        </view>
        <button class="primary call-button" @click="confirmDial">拨打 {{item.phone}}</button>
        <view class="service-note">电话和地址由平台维护。如发现信息变化，欢迎提交反馈。</view>
        <StatusNotice v-if="reportError" tone="risk" title="反馈提交失败">{{reportError}}</StatusNotice>
        <StatusNotice v-if="reported" tone="success" title="反馈已提交">报告编号：{{reportRef}}</StatusNotice>
        <button class="report-trigger" :disabled="reported||reporting" @click="showReport=true">{{reported?'反馈已提交':reporting?'正在提交…':'信息有误'}}</button>
        <view v-if="showReport" class="dialog report-dialog">
         <view class="card report">
          <text class="card-title">反馈信息问题</text>
          <picker :range="['信息不准确','电话无法接通','地址已变更','商户已停业','其他问题']" @change="reportReason=['INCORRECT_INFO','PHONE_INVALID','ADDRESS_INVALID','CLOSED','OTHER'][Number($event.detail.value)]">
            <view class="field">选择问题类型</view>
          </picker>
          <textarea v-model="reportNote" maxlength="500" placeholder="请补充说明（选填）"/>
          <view class="dialog-actions"><button :disabled="reporting" @click="showReport=false">取消</button><button :disabled="reported||reporting" @click="report">{{reporting?'正在提交…':'提交反馈'}}</button></view>
         </view>
        </view>
      </template>
      <view v-if="showDial" class="dialog">
        <view><text class="card-title">确认拨打 {{item?.phone}}？</text><view><button @click="showDial=false">取消</button><button @click="dial">拨打</button></view></view>
      </view>
    </view>
  </view>
</template>

<style src="../../styles/shared.css"></style>
<style scoped>
.directory-hero{padding:34rpx;border-radius:38rpx;background:linear-gradient(145deg,#0d4fa8,#2d74cf);color:#fff;box-shadow:0 20rpx 46rpx rgba(17,75,148,.2)}
.hero-label{font-size:22rpx;font-weight:800;opacity:.8}.directory-hero .heading{margin:12rpx 0 10rpx;color:#fff}.hero-summary{font-size:26rpx;line-height:1.65;opacity:.92}
.detail-card{margin-top:26rpx}.kv{display:flex;justify-content:space-between;gap:24rpx;padding:22rpx 0;border-bottom:1rpx solid var(--line)}.kv:last-child{border:0}.kv text:first-child{color:var(--muted)}.kv text:last-child{max-width:68%;font-weight:700;text-align:right;word-break:break-all}
.call-button{margin-top:24rpx}.service-note{margin:20rpx 4rpx;color:var(--muted);font-size:22rpx;line-height:1.6}.report{margin-top:22rpx}.report textarea,.field{box-sizing:border-box;width:100%;margin-top:18rpx;padding:20rpx;border:1rpx solid var(--line);border-radius:18rpx;background:#fff}.report button{margin-top:18rpx}
.report-trigger{margin-top:16rpx;color:var(--brand);font-weight:800}.report-dialog .report{width:78%;margin:0}.dialog-actions{display:grid!important;grid-template-columns:1fr 1fr}.dialog-actions button{margin-top:18rpx}
.dialog{position:fixed;z-index:20;inset:0;display:flex;align-items:center;justify-content:center;background:rgba(0,0,0,.35)}.dialog>view{width:70%;padding:36rpx;border-radius:28rpx;background:#fff}.dialog view view{display:flex;gap:16rpx;margin-top:24rpx}.dialog button{flex:1}
</style>
