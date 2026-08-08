<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import AppHeader from '../../components/AppHeader.vue'
import { api } from '../../api/client'
import type { DirectoryDetailResult, DirectoryItem } from '../../domain/types'

type ViewState = DirectoryDetailResult['outcome'] | 'LOADING' | 'ERROR'
const item = ref<DirectoryItem | null>(null)
const state = ref<ViewState>('LOADING')
const contentRef = ref('')
const contentVersion = ref(0)
const reporting = ref(false)
const reportState = ref<'IDLE' | 'CONTENT_ERROR_REPORTED' | 'ERROR'>('IDLE')

async function loadDetail() {
  item.value = null
  reportState.value = 'IDLE'
  if (!contentRef.value || !contentVersion.value) {
    state.value = 'EMPTY'
    return
  }
  state.value = 'LOADING'
  try {
    const result = await api.getDirectoryDetail(contentRef.value, contentVersion.value)
    state.value = result.outcome
    item.value = result.outcome === 'READY' ? result.item : null
  } catch {
    state.value = 'ERROR'
  }
}

async function reportError() {
  if (state.value !== 'READY' || !item.value || reporting.value) return
  reporting.value = true
  reportState.value = 'IDLE'
  try {
    const result = await api.reportDirectoryError(item.value.contentRef, item.value.contentVersion, '用户报告信息可能有误')
    reportState.value = result.outcome
  } catch {
    reportState.value = 'ERROR'
  } finally {
    reporting.value = false
  }
}

onLoad((query) => {
  contentRef.value = typeof query?.contentRef === 'string' ? decodeURIComponent(query.contentRef) : ''
  const rawVersion = typeof query?.contentVersion === 'string' ? Number(decodeURIComponent(query.contentVersion)) : 0
  contentVersion.value = Number.isSafeInteger(rawVersion) && rawVersion > 0 ? rawVersion : 0
})
onShow(loadDetail)
</script>

<template>
  <view class="page">
    <AppHeader title="信息详情" left="信息列表" @left="uni.navigateBack()" />
    <view class="content">
      <view class="heading" role="heading" aria-level="1">{{ state==='READY' ? item?.title : '信息详情' }}</view>
      <view v-if="state==='LOADING'" class="card state-card" role="status">正在确认最新展示资格…</view>
      <view v-else-if="state==='EMPTY'" class="card state-card" role="status"><text class="card-title">暂时没有内容</text><text class="copy">链接缺少有效内容引用，或该内容不存在。</text></view>
      <view v-else-if="state==='EXPIRED'" class="card state-card" role="status"><text class="card-title">这条信息已过期</text><text class="copy">过期内容已停止作为有效信息展示，正在等待重新核验。</text></view>
      <view v-else-if="state==='COMPLAINT_PENDING'" class="card state-card" role="status"><text class="card-title">这条信息投诉待查</text><text class="copy">核对完成前不可见，也不代表相关服务有效或无效。</text></view>
      <view v-else-if="state==='REVIEW'" class="card state-card" role="status"><text class="card-title">这条信息正在复核</text><text class="copy">复核完成前不会作为有效详情展示。</text></view>
      <view v-else-if="state==='REMOVED'" class="card state-card" role="status"><text class="card-title">这条信息已下架</text><text class="copy">当前不可见，请返回查看其他有效信息。</text></view>
      <view v-else-if="state==='UNKNOWN'" class="card state-card" role="status"><text class="card-title">状态暂时无法确认</text><text class="copy">当前不会把未知状态当作有效内容，请稍后重试。</text><button class="retry" @click="loadDetail">重新加载</button></view>
      <view v-else-if="state==='ERROR'" class="card state-card" role="alert"><text class="card-title">加载失败</text><text class="copy">暂时无法从项目 API Mock 重读内容资格。</text><button class="retry" @click="loadDetail">重新加载</button></view>
      <template v-else>
        <view class="eligible-tag">当前具备展示资格</view>
        <view class="card">
          <view class="kv"><text>信息类别</text><text>{{ item?.category }}</text></view>
          <view class="kv"><text>核验范围</text><text>{{ item?.verificationScope }}</text></view>
          <view class="kv"><text>来源类别</text><text>{{ item?.sourceType }}</text></view>
          <view class="kv"><text>核验日期</text><text>{{ item?.verifiedAt }}</text></view>
          <view class="kv"><text>更新时间</text><text>{{ item?.updatedAt }}</text></view>
        </view>
        <view class="notice">核验仅覆盖上列范围，不等于官方认证、推荐、持续准确或对第三方服务的担保。联系或使用前请再次确认。</view>
        <button class="refresh" :disabled="reporting" @click="loadDetail">刷新状态</button>
        <button class="report" :disabled="reporting||reportState==='CONTENT_ERROR_REPORTED'" @click="reportError">{{reporting?'正在提交…':reportState==='CONTENT_ERROR_REPORTED'?'已报告信息错误':'报告信息错误'}}</button>
        <view v-if="reportState==='CONTENT_ERROR_REPORTED'" class="report-result" role="status">已进入 A120 内容复核候选。</view>
        <view v-else-if="reportState==='ERROR'" class="report-result error" role="alert">报告失败，请稍后重试。</view>
      </template>
    </view>
    <view class="action"><button class="secondary" @click="uni.navigateBack()">返回列表</button></view>
  </view>
</template>

<style src="../../styles/shared.css"></style>
<style scoped>
.eligible-tag{display:inline-block;margin-top:8rpx;padding:10rpx 18rpx;border-radius:999rpx;background:#e4f4ec;color:var(--success);font-size:22rpx;font-weight:700}.notice{margin-top:24rpx;padding:24rpx;border-radius:22rpx;background:#fff8f1;color:#71411c;line-height:1.6}.state-card{text-align:center}.retry,.refresh,.report{display:block;margin:20rpx auto 0;padding:16rpx 28rpx;border:1rpx solid var(--line);border-radius:20rpx;color:var(--brand);font-weight:700}.report-result{margin-top:16rpx;text-align:center;color:var(--success);font-size:24rpx}.report-result.error{color:var(--risk)}
</style>
