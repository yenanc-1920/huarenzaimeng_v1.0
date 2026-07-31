<script setup lang="ts">
import { computed, getCurrentInstance } from 'vue'
defineProps<{ left?: string; right?: string }>()
const emit = defineEmits<{ left: []; right: [] }>()
const instance = getCurrentInstance()
const hasLeftHandler = computed(() => !!instance?.vnode.props?.onLeft)
const hasRightHandler = computed(() => !!instance?.vnode.props?.onRight)
</script>

<template>
  <view class="header">
    <button v-if="left && hasLeftHandler" class="side" :aria-label="left" @click="emit('left')">{{ left }}</button><view v-else class="side spacer" />
    <view class="brand"><image src="/static/logo.png" mode="aspectFill" /><text>华人在孟</text></view>
    <button v-if="right && hasRightHandler" class="side right" :aria-label="right" @click="emit('right')">{{ right }}</button><view v-else class="side spacer" />
  </view>
</template>

<style scoped>
.header{height:112rpx;padding:0 32rpx;background:#fff;border-bottom:1rpx solid var(--line);display:flex;align-items:center;justify-content:space-between}.brand{display:flex;align-items:center;gap:14rpx;color:var(--brand-strong);font-weight:800}.brand image{width:56rpx;height:56rpx;border-radius:14rpx}.side{width:110rpx;padding:12rpx 0;margin:0;background:transparent;font-size:24rpx;line-height:1.4;color:var(--muted);text-align:left}.right{text-align:right}.spacer{height:1rpx}
</style>
