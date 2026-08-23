<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import BottomNav from '../../components/BottomNav.vue'
import { v2PublicApi as api } from '../../api/v2-public-client'
import type { LifeContentCategory, LifeContentReadState, LifeContentSummary } from '../../domain/types'
import { executeLifeContentListRead, navigateToLifeContentDetail, type LifeContentListPageState, type LifeContentReadTrigger } from '../../domain/life-content-page-executor'

type ListViewState = LifeContentReadState | 'LOADING' | 'READ_ERROR'
type Topic = 'ALL' | LifeContentCategory
const state=ref<ListViewState>('LOADING')
const items=ref<LifeContentSummary[]>([])
const topic=ref<Topic>('ALL')
const visibleItems=computed(()=>topic.value==='ALL'?items.value:items.value.filter((item)=>item.category===topic.value))
const pageState:LifeContentListPageState={
  get viewState(){return state.value},set viewState(value){state.value=value},
  get items(){return items.value},set items(value){items.value=value},
  get topic(){return topic.value},set topic(value){topic.value=value},
}

const categoryLabel=(category:LifeContentCategory)=>category==='LIFE_REMINDER'?'生活提醒':'节假日说明'
const updatedLabel=(instant:string)=>{
  const date=new Date(instant)
  return `${date.getUTCFullYear()}-${String(date.getUTCMonth()+1).padStart(2,'0')}-${String(date.getUTCDate()).padStart(2,'0')}`
}

async function loadList(trigger:LifeContentReadTrigger='FIRST_ENTRY'){
  await executeLifeContentListRead(pageState,{getList:()=>api.getLifeContentList()},trigger)
}

function openDetail(item:LifeContentSummary){
  navigateToLifeContentDetail(item,(url)=>uni.navigateTo({url}))
}

onMounted(()=>loadList('FIRST_ENTRY'))
</script>

<template>
  <view class="page life-page" data-page-id="UX-P042" data-page-root="life-content-list" :data-visible-state="state">
<AppHeader />
    <view class="content life-content">
      <view class="intro">
        <text class="eyebrow">生活资讯</text>
        <view class="heading" role="heading" aria-level="1">在孟生活，简单了解</view>
        <text class="desc">查看生活提醒与节假日说明。</text>
      </view>

      <view v-if="state==='READY'" class="topic-tabs" role="tablist" aria-label="资讯类别">
        <button :class="{active:topic==='ALL'}" role="tab" :aria-selected="topic==='ALL'" @click="topic='ALL'">全部</button>
        <button :class="{active:topic==='HOLIDAY_EXPLANATION'}" role="tab" :aria-selected="topic==='HOLIDAY_EXPLANATION'" @click="topic='HOLIDAY_EXPLANATION'">节假日说明</button>
        <button :class="{active:topic==='LIFE_REMINDER'}" role="tab" :aria-selected="topic==='LIFE_REMINDER'" @click="topic='LIFE_REMINDER'">生活提醒</button>
      </view>

      <view v-if="state==='LOADING'" class="state-card loading" role="status" data-state="LOADING" data-retry-visible="false">
        <view class="state-symbol" aria-hidden="true" />
        <view><text class="state-title">正在加载生活资讯</text><text class="state-copy">请稍候。</text></view>
      </view>
      <view v-else-if="state==='EMPTY'" class="state-card" role="status" data-state="EMPTY" data-retry-visible="false">
        <text class="state-symbol">○</text><view><text class="state-title">暂无已发布内容</text><text class="state-copy">稍后再来看看。</text></view>
      </view>
      <view v-else-if="state==='UNDER_REVIEW'" class="state-card" role="status" data-state="UNDER_REVIEW" data-retry-visible="false">
        <text class="state-symbol">⋯</text><view><text class="state-title">内容正在复核</text><text class="state-copy">当前不在列表中展示。</text></view>
      </view>
      <view v-else-if="state==='EXPIRED'" class="state-card" role="status" data-state="EXPIRED" data-retry-visible="false">
        <text class="state-symbol">⌛</text><view><text class="state-title">内容已过期</text><text class="state-copy">不继续展示旧内容。</text></view>
      </view>
      <view v-else-if="state==='REMOVED'" class="state-card" role="status" data-state="UNPUBLISHED" data-retry-visible="false">
        <text class="state-symbol">—</text><view><text class="state-title">内容已下架</text><text class="state-copy">请返回查看其他资讯。</text></view>
      </view>
      <view v-else-if="state==='UNKNOWN'" class="state-card unknown" role="status" data-state="UNKNOWN" data-retry-visible="true">
        <text class="state-symbol">?</text><view><text class="state-title">暂时无法确认内容状态</text><text class="state-copy">不会猜测内容是否仍可查看。</text><button class="readonly-retry" @click="loadList('USER_RETRY')">重新读取</button></view>
      </view>
      <view v-else-if="state==='READ_ERROR'" class="state-card error" role="alert" data-state="READ_ERROR" data-retry-visible="true">
        <text class="state-symbol">!</text><view><text class="state-title">暂时无法读取</text><text class="state-copy">没有把读取失败当作暂无内容。</text><button class="readonly-retry" @click="loadList('USER_RETRY')">重新读取</button></view>
      </view>

      <view v-else class="story-list" aria-label="生活资讯列表">
        <view v-if="!visibleItems.length" class="filter-empty" role="status" data-state="FILTER_EMPTY" data-retry-visible="false">
          <text class="state-title">此类别暂无内容</text>
          <text class="state-copy">可以查看其他类别。</text>
        </view>
        <button v-for="item in visibleItems" :key="`${item.contentRef}:${item.contentVersion}`" class="story-card" :aria-label="`查看${categoryLabel(item.category)}：${item.title}`" @click="openDetail(item)">
          <view class="story-copy"><text class="story-kind">{{categoryLabel(item.category)}}</text><text class="story-title">{{item.title}}</text><text class="story-summary">{{item.summary}}</text><text class="story-meta">适用：{{item.applicableAudience||'适用对象暂未提供'}} · {{updatedLabel(item.updatedAt)}}</text></view>
          <text class="chevron" aria-hidden="true">›</text>
        </button>
        <text class="source-note">打开详情后会再次确认内容是否仍可查看。</text>
      </view>
    </view>
    <BottomNav active="news"/>
  </view>
</template>

<style src="../../styles/shared.css"></style>
<style scoped>
.life-content{padding-top:40rpx}.eyebrow{display:inline-block;min-height:50rpx;padding:8rpx 18rpx;border-radius:999rpx;background:#edf3fc;color:var(--brand);font-size:20rpx;font-weight:800}.intro .heading{margin:16rpx 0 8rpx;font-size:48rpx}.topic-tabs{display:flex;gap:16rpx;margin-top:36rpx;overflow-x:auto;padding-bottom:6rpx}.topic-tabs button{flex:none;min-height:44px;padding:14rpx 24rpx;display:flex;align-items:center;justify-content:center;box-sizing:border-box;border:1rpx solid var(--line);border-radius:999rpx;background:#fff;color:var(--muted);font-size:22rpx;line-height:1;white-space:nowrap}.topic-tabs button.active{border-color:#e6b486;background:#fff3e8;color:#824715;font-weight:800}.story-list{margin-top:26rpx}.filter-empty{margin-bottom:20rpx;padding:30rpx 24rpx;border:1rpx dashed var(--line);border-radius:28rpx;background:#fff;text-align:center}.story-card{width:100%;min-height:196rpx;margin-bottom:20rpx;padding:28rpx;display:flex;align-items:center;border:1rpx solid var(--line);border-radius:32rpx;background:#fff;text-align:left}.story-copy{flex:1;min-width:0}.story-kind,.story-title,.story-summary,.story-meta{display:block}.story-kind{color:#8a4a17;font-size:20rpx;font-weight:800}.story-title{margin-top:8rpx;font-size:30rpx;line-height:1.4;font-weight:800;word-break:break-all}.story-summary{margin-top:8rpx;color:var(--muted);font-size:23rpx;line-height:1.45;display:-webkit-box;overflow:hidden;-webkit-box-orient:vertical;-webkit-line-clamp:2}.story-meta{margin-top:12rpx;color:var(--muted);font-size:20rpx}.chevron{flex:none;margin-left:16rpx;color:#8c98a8;font-size:48rpx}.source-note{display:block;margin:28rpx 6rpx 0;color:var(--muted);font-size:22rpx;line-height:1.55}.state-card{display:flex;align-items:flex-start;gap:20rpx;margin-top:28rpx;padding:24rpx;border:1rpx solid var(--line);border-radius:26rpx;background:#fff}.state-card>view:last-child{flex:1;min-width:0}.state-symbol{width:54rpx;height:54rpx;display:flex;align-items:center;justify-content:center;flex:none;border-radius:50%;background:#eef1f5;color:var(--muted);font-size:24rpx;font-weight:900}.state-title,.state-copy{display:block}.state-title{font-size:27rpx;line-height:1.4;font-weight:800}.state-copy{margin-top:6rpx;color:var(--muted);font-size:22rpx;line-height:1.5}.state-card.unknown{border-color:#e7c98f;background:#fffaf0}.state-card.error{border-color:#efc8c8;background:#fff9f9}.loading .state-symbol{background:linear-gradient(90deg,#e9edf2 25%,#f7f9fb 50%,#e9edf2 75%);background-size:200% 100%;animation:shimmer 1.4s linear infinite}.readonly-retry{min-height:44px;margin-top:14rpx;padding:14rpx 24rpx;border:1rpx solid var(--line);border-radius:24rpx;background:#fff;color:var(--brand);font-size:22rpx;font-weight:800}@keyframes shimmer{to{background-position:-200% 0}}
@media (prefers-reduced-motion:reduce){.loading .state-symbol{animation:none}}
</style>
