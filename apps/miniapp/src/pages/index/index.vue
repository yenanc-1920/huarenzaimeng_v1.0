<script setup lang="ts">
import AppHeader from '../../components/AppHeader.vue'

const HOME_ROUTES = new Set(['/pages/recharge/select', '/pages/order/list', '/pages/directory/list'])

function showNavigationFailure(url: string, error: unknown) {
  console.error('[HOME_NAVIGATION_FAILED]', { url, error })
  uni.showToast({ title: '页面暂时无法打开，请重试', icon: 'none' })
}

function go(url: string) {
  if (!HOME_ROUTES.has(url)) {
    showNavigationFailure(url, new Error('HOME_ROUTE_NOT_ALLOWED'))
    return
  }
  uni.navigateTo({
    url,
    fail(navigateError) {
      console.error('[HOME_NAVIGATE_TO_FAILED]', { url, error: navigateError })
      uni.redirectTo({
        url,
        fail: (redirectError) => showNavigationFailure(url, redirectError),
      })
    },
  })
}
</script>
<template><view class="page"><AppHeader/><view class="content"><view class="welcome"><text>你好，欢迎回来</text><view class="heading" role="heading" aria-level="1">在孟生活，一站办妥</view></view><button class="hero" @click="go('/pages/recharge/select')"><text class="eyebrow">手机充值</text><view class="hero-title" role="heading" aria-level="2">给孟加拉手机号充值</view><text class="hero-copy">输入号码，选择金额，核对后付款</text><text class="hero-action">开始充值</text></button><view class="entries"><button class="card nav" @click="go('/pages/order/list')"><text class="entry-icon blue">单</text><view><text class="card-title">我的订单</text><text class="copy">查看充值和退款进度</text></view><text class="arrow">›</text></button><button class="card nav" @click="go('/pages/directory/list')"><text class="entry-icon orange">讯</text><view><text class="card-title">实用信息</text><text class="copy">电话、办事与生活指南</text></view><text class="arrow">›</text></button></view><text class="test-badge">测试版</text></view></view></template>
<style src="../../styles/shared.css"></style><style scoped>.welcome{width:100%;overflow:visible}.welcome>text{color:var(--muted);font-size:28rpx}.welcome .heading{width:100%;font-size:52rpx;line-height:1.35;min-height:70rpx;margin-top:10rpx;white-space:normal;word-break:normal}.hero{display:block;width:100%;min-width:0;margin-top:36rpx;padding:48rpx;border-radius:40rpx;background:var(--brand);color:#fff;text-align:left;box-shadow:0 20rpx 56rpx rgba(35,87,165,.22)}.eyebrow,.hero-copy,.hero-action{display:block}.eyebrow{font-size:24rpx;opacity:.82}.hero-title{font-size:44rpx;line-height:1.35;font-weight:900;margin-top:16rpx}.hero-copy{font-size:26rpx;line-height:1.5;margin-top:10rpx;opacity:.9}.hero-action{display:inline-block;width:auto;min-width:192rpx;min-height:72rpx;margin-top:44rpx;padding:16rpx 40rpx;border-radius:24rpx;background:#fff;color:var(--brand);text-align:center;font-weight:800;white-space:nowrap}.entries{margin-top:32rpx;border-radius:32rpx;overflow:hidden}.nav{width:100%;min-height:148rpx;margin:0;padding:36rpx;border-radius:0;display:flex;align-items:center;text-align:left}.nav+.nav{border-top:1rpx solid var(--line)}.entry-icon{width:76rpx;height:76rpx;line-height:76rpx;border-radius:24rpx;color:#fff;text-align:center;font-weight:800;flex:none;margin-right:24rpx}.blue{background:var(--brand)}.orange{background:#f5a623}.nav view{flex:1;min-width:0}.nav .card-title{width:100%;white-space:nowrap}.nav .copy{width:100%;overflow:hidden;display:-webkit-box;-webkit-box-orient:vertical;-webkit-line-clamp:2;white-space:normal;word-break:normal}.arrow{font-size:48rpx;color:#8792a2;margin-left:20rpx;flex:none}.test-badge{display:block;width:108rpx;margin:32rpx auto 0;padding:10rpx 18rpx;border-radius:999rpx;background:#eaedf2;color:#737e8d;text-align:center;font-size:22rpx;white-space:nowrap}</style>
