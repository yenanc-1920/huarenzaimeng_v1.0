<script setup lang="ts">
withDefaults(defineProps<{ tone?: 'info' | 'success' | 'unknown' | 'risk'; title: string }>(), { tone: 'info' })
</script>

<template><view class="notice" :class="tone" role="status" aria-live="polite"><view class="mark">{{ tone === 'info' ? 'i' : tone === 'success' ? '✓' : '!' }}</view><view><text class="title">{{ title }}</text><text class="body"><slot /></text></view></view></template>

<style scoped>
.notice{display:flex;gap:16rpx;padding:22rpx;border-radius:22rpx;margin-top:22rpx;border:1rpx solid #ccdfee;background:#edf3fc;color:var(--brand-strong)}.notice.success{background:#edf8f2;border-color:#a8d8bd;color:#246a45}.notice.unknown{background:#fff7e8;border-color:#e2bd7e;color:#744500}.notice.risk{background:#fdeaea;border-color:#efb8b8;color:var(--risk)}.mark{width:40rpx;height:40rpx;border-radius:50%;background:currentColor;color:#fff;display:flex;align-items:center;justify-content:center;flex:none;font-weight:800}.title,.body{display:block}.title{font-weight:800}.body{font-size:24rpx;line-height:1.55;margin-top:4rpx}
</style>
