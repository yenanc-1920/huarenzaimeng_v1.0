<script setup lang="ts">
import { computed, getCurrentInstance } from 'vue'
defineProps<{ left?: string; right?: string }>()
const emit = defineEmits<{ left: []; right: [] }>()
const instance = getCurrentInstance()
const hasLeftHandler = computed(() => !!instance?.vnode.props?.onLeft)
const hasRightHandler = computed(() => !!instance?.vnode.props?.onRight)
const system = uni.getSystemInfoSync()
const statusBarHeight = system.statusBarHeight || 20
let navigationHeight = 44
// #ifdef MP-WEIXIN
const menu = uni.getMenuButtonBoundingClientRect()
navigationHeight = Math.max(44, (menu.top - statusBarHeight) * 2 + menu.height)
// #endif
const safeStyle = `padding-top:${statusBarHeight}px;height:${statusBarHeight + navigationHeight}px`
const rowStyle = `height:${navigationHeight}px`
</script>

<template>
  <view class="header" :style="safeStyle"><view class="header-row" :style="rowStyle">
    <button v-if="left && hasLeftHandler" class="side" :aria-label="left" @click="emit('left')">{{ left }}</button><view v-else class="side spacer" />
    <view class="brand"><image src="/static/logo.png" mode="aspectFill" /><text>华人在孟</text></view>
    <button v-if="right && hasRightHandler" class="side right" :aria-label="right" @click="emit('right')">{{ right }}</button><view v-else class="side spacer" />
  </view>
  </view>
</template>

<style scoped>
.header{width:100%;padding-left:30rpx;padding-right:30rpx;background:#fff;border-bottom:1rpx solid var(--line)}.header-row{display:flex;align-items:center;justify-content:space-between}.brand{display:flex;align-items:center;color:var(--brand-strong);font-weight:800}.brand image{width:46rpx;height:46rpx;margin-right:12rpx;border-radius:12rpx}.side{width:108rpx;padding:10rpx 0;margin:0;background:transparent;font-size:24rpx;line-height:1.4;color:var(--muted);text-align:left}.right{text-align:right}.spacer{height:1rpx}
</style>
