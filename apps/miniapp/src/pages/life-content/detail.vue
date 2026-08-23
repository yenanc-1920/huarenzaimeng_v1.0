<script setup lang="ts">
const navigateBack=()=>uni.navigateBack()
import { ref } from 'vue'
import { onHide, onLoad, onShow } from '@dcloudio/uni-app'
import AppHeader from '../../components/AppHeader.vue'
import { v2PublicApi as api } from '../../api/v2-public-client'
import type { LifeContentCategory, LifeContentDetailItem, LifeContentDetailState } from '../../domain/types'
import { executeLifeContentDetailRead, revokeLifeContentDetail, type LifeContentDetailPageState, type LifeContentReadTrigger } from '../../domain/life-content-page-executor'

type DetailViewState = LifeContentDetailState | 'LOADING' | 'STALE' | 'READ_ERROR'
const state=ref<DetailViewState>('LOADING')
const item=ref<LifeContentDetailItem|null>(null)
const contentRef=ref('')
const contentVersion=ref<string|null>(null)
let readGeneration=0
const pageState:LifeContentDetailPageState={
  get viewState(){return state.value},set viewState(value){state.value=value},
  get item(){return item.value},set item(value){item.value=value},
  get readGeneration(){return readGeneration},set readGeneration(value){readGeneration=value},
}

const categoryLabel=(category:LifeContentCategory)=>category==='LIFE_REMINDER'?'生活提醒':'节假日说明'
const sourceTypeLabel=(sourceType:string|null)=>sourceType||'来源暂未提供'
const updatedLabel=(instant:string)=>{
  const date=new Date(instant)
  return `${date.getUTCMonth()+1}月${date.getUTCDate()}日更新`
}
const routeValue=(value:unknown)=>{
  if(typeof value!=='string'||!value)return''
  try{return decodeURIComponent(value)}catch{return''}
}

function revokeReady(nextState:DetailViewState){
  revokeLifeContentDetail(pageState,nextState)
}

async function loadDetail(trigger:LifeContentReadTrigger='FIRST_ENTRY'){
  await executeLifeContentDetailRead(pageState,{getDetail:(ref,version)=>api.getLifeContentDetail(ref,version)},contentRef.value,contentVersion.value,trigger)
}

onLoad((query)=>{
  contentRef.value=routeValue(query?.contentRef)
  contentVersion.value=routeValue(query?.contentVersion)||null
})
let shownOnce=false
onShow(()=>{const trigger:LifeContentReadTrigger=shownOnce?'REENTRY':'FIRST_ENTRY';shownOnce=true;return loadDetail(trigger)})
onHide(()=>revokeReady('STALE'))
</script>

<template>
  <view class="page life-page" data-page-id="UX-P043" data-authority-read-key="contentRef+contentVersion" data-reread-on="enter,return,foreground">
<AppHeader title="资讯详情" touch-safe left="返回列表" @left="navigateBack" />
    <view class="content detail-content">
      <view v-if="state==='LOADING'" class="detail-state loading" role="status" data-state="LOADING" data-body-visible="false" data-clear-old-body="true" data-retry-visible="false">
        <view class="large-state-symbol" aria-hidden="true"/><view class="state-heading" role="heading" aria-level="1">正在确认最新内容</view><text class="state-copy">旧内容已收起，请稍候。</text>
      </view>
      <view v-else-if="state==='STALE'" class="detail-state" role="status" data-state="STALE" data-body-visible="false" data-retry-visible="false">
        <text class="large-state-symbol">↻</text><view class="state-heading" role="heading" aria-level="1">内容可能已更新</view><text class="state-copy">旧内容不再显示。</text>
      </view>
      <view v-else-if="state==='UNDER_REVIEW'" class="detail-state" role="status" data-state="UNDER_REVIEW" data-body-visible="false" data-retry-visible="false">
        <text class="large-state-symbol">⋯</text><view class="state-heading" role="heading" aria-level="1">内容正在复核</view><text class="state-copy">当前无法查看正文。</text>
      </view>
      <view v-else-if="state==='EXPIRED'" class="detail-state" role="status" data-state="EXPIRED" data-body-visible="false" data-retry-visible="false">
        <text class="large-state-symbol">⌛</text><view class="state-heading" role="heading" aria-level="1">内容已过期</view><text class="state-copy">旧正文已停止展示。</text>
      </view>
      <view v-else-if="state==='REMOVED'" class="detail-state" role="status" data-state="UNPUBLISHED" data-body-visible="false" data-retry-visible="false">
        <text class="large-state-symbol">—</text><view class="state-heading" role="heading" aria-level="1">内容已下架</view><text class="state-copy">请返回查看其他资讯。</text>
      </view>
      <view v-else-if="state==='UNKNOWN'" class="detail-state unknown" role="status" data-state="UNKNOWN" data-body-visible="false" data-retry-visible="true">
        <text class="large-state-symbol">?</text><view class="state-heading" role="heading" aria-level="1">暂时无法确认内容状态</view><text class="state-copy">不会继续显示旧正文。</text><button class="readonly-retry" @click="loadDetail('USER_RETRY')">重新读取</button>
      </view>
      <view v-else-if="state==='READ_ERROR'" class="detail-state error" role="alert" data-state="READ_ERROR" data-body-visible="false" data-retry-visible="true">
        <text class="large-state-symbol">!</text><view class="state-heading" role="heading" aria-level="1">暂时无法读取</view><text class="state-copy">请重新读取，或先返回列表。</text><button class="readonly-retry" @click="loadDetail('USER_RETRY')">重新读取</button>
      </view>

      <view v-else-if="item" class="published" data-state="PUBLISHED" data-body-visible="true">
        <view class="article-hero">
          <text class="eyebrow">{{categoryLabel(item.category)}}</text>
          <view class="article-title" role="heading" aria-level="1">{{item.title}}</view>
          <text class="article-summary">{{item.summary}}</text>
          <text class="article-updated">{{updatedLabel(item.updatedAt)}}</text>
        </view>
        <view class="article-body card">
          <view class="article-meta"><text>来源：{{sourceTypeLabel(item.sourceType)}}</text><text>适用对象：{{item.applicableAudience||'暂未提供'}}</text></view>
          <text class="article-copy">{{item.body}}</text>
        </view>
      </view>
    </view>
<view class="action"><button class="primary" @click="navigateBack">返回生活资讯</button></view>
  </view>
</template>

<style src="../../styles/shared.css"></style>
<style scoped>
.detail-content{padding-top:40rpx}.life-page .action .primary{min-height:44px}.detail-state{min-height:380rpx;padding:40rpx 32rpx;display:flex;flex-direction:column;align-items:center;justify-content:center;border:1rpx solid var(--line);border-radius:32rpx;background:#fff;text-align:center}.large-state-symbol{width:90rpx;height:90rpx;margin-bottom:20rpx;display:flex;align-items:center;justify-content:center;border-radius:30rpx;background:#edf3fc;color:var(--brand);font-size:40rpx;font-weight:900}.state-heading{font-size:36rpx;line-height:1.4;font-weight:800}.state-copy{display:block;margin-top:12rpx;color:var(--muted);font-size:23rpx;line-height:1.55}.detail-state.unknown{border-color:#e7c98f;background:#fffaf0}.detail-state.error{border-color:#efc8c8;background:#fff9f9}.loading .large-state-symbol{background:linear-gradient(90deg,#e9edf2 25%,#f7f9fb 50%,#e9edf2 75%);background-size:200% 100%;animation:shimmer 1.4s linear infinite}.readonly-retry{min-width:224rpx;min-height:44px;margin-top:28rpx;padding:18rpx 28rpx;border:1rpx solid var(--brand);border-radius:24rpx;background:#fff;color:var(--brand);font-size:24rpx;font-weight:800}.article-body{margin-top:36rpx}.eyebrow{display:inline-block;min-height:50rpx;padding:8rpx 18rpx;border-radius:999rpx;background:#fff3e8;color:#8a4a17;font-size:20rpx;font-weight:800}.article-title{margin:16rpx 0 8rpx;font-size:46rpx;line-height:1.35;font-weight:800;word-break:break-all}.article-meta{display:flex;flex-wrap:wrap;gap:12rpx 24rpx;margin:16rpx 0 28rpx;color:var(--muted);font-size:21rpx}.article-copy{display:block;font-size:27rpx;line-height:1.85;white-space:pre-wrap}.article-note{margin-top:32rpx;padding:22rpx 24rpx;border-radius:24rpx;background:#fff3e8;color:#70411d;font-size:22rpx;line-height:1.55}@keyframes shimmer{to{background-position:-200% 0}}@media (prefers-reduced-motion:reduce){.loading .large-state-symbol{animation:none}}
</style>
<style scoped>
.article-hero{padding:38rpx 34rpx;border-radius:38rpx;background:linear-gradient(145deg,#0d4fa8,#2d74cf);color:#fff;box-shadow:0 20rpx 46rpx rgba(17,75,148,.2)}
.article-hero .eyebrow{background:rgba(255,255,255,.16);color:#fff}.article-hero .article-title{margin-top:18rpx;color:#fff}.article-updated{display:block;margin-top:16rpx;font-size:22rpx;opacity:.78}.article-body{margin-top:26rpx}.article-meta{margin-top:0;padding-bottom:24rpx;border-bottom:1rpx solid var(--line)}
.article-summary{display:block;margin-top:12rpx;font-size:24rpx;line-height:1.6;opacity:.9}
</style>
