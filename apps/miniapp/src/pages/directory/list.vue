<script setup lang="ts">
import { onMounted, ref } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import { api } from '../../api/client'
import type { DirectorySummary } from '../../domain/types'

const items = ref<DirectorySummary[]>([])
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    items.value = await api.getDirectory()
  } catch {
    items.value = []
    error.value = '暂时无法读取信息，请稍后重试。'
  } finally {
    loading.value = false
  }
}

function open(item: DirectorySummary) {
  const contentRef = encodeURIComponent(item.contentRef)
  const contentVersion = encodeURIComponent(item.contentVersion)
  uni.navigateTo({ url: `/pages/directory/detail?contentRef=${contentRef}&contentVersion=${contentVersion}` })
}

onMounted(load)
</script>

<template>
  <view class="page">
    <AppHeader title="实用信息" left="首页" @left="uni.reLaunch({url:'/pages/index/index'})" />
    <view class="content">
      <view class="heading" role="heading" aria-level="1">在孟实用信息</view>
      <text class="desc">仅展示当前具备发布资格的自营静态信息，使用前请再次确认。</text>
      <view class="disclaimer">信息不构成推荐、官方认证或对第三方服务的担保。</view>
      <view v-if="loading" class="card state" role="status">正在加载有效信息…</view>
      <view v-else-if="error" class="card state" role="alert">
        <text class="card-title">加载失败</text>
        <text class="copy">{{ error }}</text>
        <button class="retry" @click="load">重新加载</button>
      </view>
      <template v-else>
        <button v-for="item in items" :key="item.contentRef" class="card item" :aria-label="`查看信息 ${item.title}`" @click="open(item)">
          <view class="avatar">孟</view>
          <view class="item-copy">
            <text class="card-title">{{ item.title }}</text>
            <text class="copy">{{ item.summary }}</text>
            <text class="meta">{{ item.category }} · 更新于 {{ item.updatedAt }}</text>
          </view>
          <text class="arrow">›</text>
        </button>
      </template>
      <view v-if="!loading&&!error&&!items.length" class="card empty" role="status">
        <text class="card-title">暂无有效信息</text>
        <text class="copy">过期、投诉待查、下架或状态未知的内容不会在这里展示。</text>
      </view>
    </view>
  </view>
</template>

<style src="../../styles/shared.css"></style>
<style scoped>
.disclaimer{margin-top:20rpx;padding:20rpx 24rpx;border-radius:22rpx;background:#fff8f1;color:#71411c;font-size:23rpx;line-height:1.6}.item{width:100%;display:flex;align-items:center;text-align:left}.avatar{width:80rpx;height:80rpx;border-radius:22rpx;background:#fff3e8;color:#9a531d;font-weight:900;display:flex;align-items:center;justify-content:center;flex:none}.item-copy{flex:1;min-width:0;margin-left:20rpx}.meta{display:block;margin-top:8rpx;color:var(--muted);font-size:22rpx;line-height:1.5}.arrow{font-size:42rpx;color:var(--muted);margin-left:16rpx;flex:none}.state,.empty{text-align:center;color:var(--muted)}.retry{display:block;margin:20rpx auto 0;padding:16rpx 28rpx;border:1rpx solid var(--line);border-radius:20rpx;color:var(--brand);font-weight:700}
</style>
