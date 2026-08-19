<script setup lang="ts">
defineProps<{ left?: string; right?: string; title?: string; touchSafe?: boolean }>()
const emit = defineEmits<{ left: []; right: [] }>()
function readHeaderMetrics() {
  let statusBarHeight = 20
  let navigationHeight = 44
  try {
    const system = uni.getSystemInfoSync?.()
    if (typeof system?.statusBarHeight === 'number' && Number.isFinite(system.statusBarHeight)) statusBarHeight = system.statusBarHeight
    // #ifdef MP-WEIXIN
    const menu = uni.getMenuButtonBoundingClientRect?.()
    if (menu && Number.isFinite(menu.top) && Number.isFinite(menu.height)) {
      navigationHeight = Math.max(44, (menu.top - statusBarHeight) * 2 + menu.height)
    }
    // #endif
  } catch {
    // Keep a stable custom-header frame when platform metrics are temporarily unavailable.
  }
  return { statusBarHeight, navigationHeight }
}

const { statusBarHeight, navigationHeight } = readHeaderMetrics()
const safeStyle = `padding-top:${statusBarHeight}px;height:${statusBarHeight + navigationHeight}px`
const rowStyle = `height:${navigationHeight}px`
</script>

<template>
  <view class="header" :style="safeStyle"><view class="header-row" :style="rowStyle">
    <button v-if="left" class="side" :class="{'touch-safe':touchSafe}" :aria-label="left" @click="emit('left')">{{ left }}</button><view v-else class="side spacer" />
    <view v-if="title" class="page-title">{{ title }}</view>
    <view v-else class="brand"><image src="/static/logo.png" mode="aspectFill" /><text>华人在孟</text></view>
    <button v-if="right" class="side right" :class="{'touch-safe':touchSafe}" :aria-label="right" @click="emit('right')">{{ right }}</button><view v-else class="side spacer" />
  </view>
  </view>
</template>

<style scoped>
.header{width:100%;padding-left:30rpx;padding-right:30rpx;background:#fff;border-bottom:1rpx solid var(--line)}.header-row{display:flex;align-items:center;justify-content:space-between}.brand{display:flex;align-items:center;color:var(--brand-strong);font-weight:800}.brand image{width:46rpx;height:46rpx;margin-right:12rpx;border-radius:12rpx}.page-title{font-size:32rpx;font-weight:800;color:var(--text)}.side{width:108rpx;padding:10rpx 0;margin:0;background:transparent;font-size:24rpx;line-height:1.4;color:var(--muted);text-align:left}.side.touch-safe{min-height:44px;display:flex;align-items:center}.side.right.touch-safe{justify-content:flex-end}.right{text-align:right}.spacer{height:1rpx}
</style>
