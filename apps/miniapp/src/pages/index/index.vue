<script setup lang="ts">
import { ref } from 'vue'

const phone = ref('')
const state = ref<'READY'|'QUOTE_MOCK'>('READY')
const quote = () => { state.value = 'QUOTE_MOCK' }
</script>

<template>
  <view class="page">
    <view class="brand"><image src="/static/logo.png" mode="aspectFill"/><view><text class="title">华人在孟</text><text class="sub">孟加拉生活与充值服务</text></view></view>
    <view class="warning">当前为合成数据Mock，不会发起支付或真实充值。</view>
    <view class="card">
      <text class="label">孟加拉手机号</text>
      <input v-model="phone" placeholder="请输入测试号码" maxlength="14" />
      <button @click="quote">获取Mock报价</button>
    </view>
    <view v-if="state==='QUOTE_MOCK'" class="card result">
      <text>测试套餐</text><text class="price">充值总价 ¥10.00</text><text>已包含成本、服务费与利润</text>
    </view>
  </view>
</template>

<style scoped>
.page{padding:32rpx}.brand{display:flex;align-items:center;gap:20rpx}.brand image{width:96rpx;height:96rpx;border-radius:24rpx}.title,.sub{display:block}.title{font-size:40rpx;font-weight:700}.sub{color:#667085}.warning,.card{margin-top:28rpx;padding:28rpx;border-radius:24rpx;background:#fff}.warning{background:#fff1d6;color:#8a5400}.label{display:block;margin-bottom:16rpx;font-weight:600}input{padding:22rpx;background:#f6f7fb;border-radius:16rpx}button{margin-top:24rpx;background:#e84b2c;color:#fff}.result text{display:block;margin-bottom:12rpx}.price{font-size:34rpx;font-weight:700}
</style>
