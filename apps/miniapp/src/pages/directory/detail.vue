<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppHeader from '../../components/AppHeader.vue'
import StatusNotice from '../../components/StatusNotice.vue'
import { api } from '../../api/client'
import type { DirectoryItem } from '../../domain/types'
const entryRef=ref(''),item=ref<DirectoryItem|null>(null),loading=ref(true),error=ref(''),showDial=ref(false),reportReason=ref('INCORRECT_INFO'),reportNote=ref(''),reported=ref(false)
const navigateBack=()=>uni.navigateBack()
async function load(){loading.value=true;error.value='';try{item.value=await api.getDirectoryDetail(entryRef.value)}catch{error.value='黄页详情暂时无法读取，请稍后重试。'}finally{loading.value=false}}
const confirmDial=()=>{if(!item.value?.phone)return;showDial.value=true}
const dial=()=>{showDial.value=false;if(item.value?.phone)uni.makePhoneCall({phoneNumber:item.value.phone})}
const report=async()=>{try{await api.reportDirectoryError(entryRef.value,reportReason.value,reportNote.value.trim());reported.value=true}catch{error.value='问题反馈暂时无法提交，请稍后重试。'}}
onLoad(query=>{entryRef.value=typeof query?.entryRef==='string'?decodeURIComponent(query.entryRef):'';void load()})
</script>
<template><view class="page"><AppHeader title="黄页详情" left="黄页列表" @left="navigateBack"/><view class="content"><view v-if="loading" class="card">正在读取黄页详情…</view><StatusNotice v-else-if="error" tone="risk" title="暂时无法读取">{{error}}</StatusNotice><template v-else-if="item"><view class="heading">{{item.displayName}}</view><text class="desc">{{item.summary}}</text><view class="card"><view class="kv"><text>城市</text><text>{{item.cityName}}</text></view><view class="kv"><text>当地地址</text><text>{{item.localAddress}}</text></view><view class="kv"><text>联系电话</text><text>{{item.phone}}</text></view><view class="kv"><text>核验日期</text><text>{{item.verifiedAt}}</text></view></view><button class="primary" @click="confirmDial">拨打电话</button><view class="card report"><text class="card-title">信息有误？</text><picker :range="['信息不准确','电话无法接通','地址已变更','商户已停业','其他问题']" @change="reportReason=['INCORRECT_INFO','PHONE_INVALID','ADDRESS_INVALID','CLOSED','OTHER'][Number($event.detail.value)]"><view class="field">选择原因</view></picker><textarea v-model="reportNote" maxlength="500" placeholder="请补充说明（选填）"/><button :disabled="reported" @click="report">{{reported?'反馈已提交':'提交反馈'}}</button></view></template><view v-if="showDial" class="dialog"><view><text class="card-title">确认拨打 {{item?.phone}}？</text><view><button @click="showDial=false">取消</button><button @click="dial">拨打</button></view></view></view></view></view></template>
<style src="../../styles/shared.css"></style><style scoped>.kv{display:flex;justify-content:space-between;gap:24rpx;padding:20rpx 0;border-bottom:1rpx solid var(--line)}.kv text:last-child{text-align:right}.report textarea,.field{margin-top:18rpx;padding:20rpx;border:1rpx solid var(--line);border-radius:18rpx;background:#fff}.dialog{position:fixed;inset:0;display:flex;align-items:center;justify-content:center;background:rgba(0,0,0,.35)}.dialog>view{width:70%;padding:36rpx;border-radius:28rpx;background:#fff}.dialog view view{display:flex;gap:16rpx;margin-top:24rpx}.dialog button{flex:1}</style>
