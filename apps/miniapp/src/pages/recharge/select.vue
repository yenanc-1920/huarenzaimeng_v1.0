<script setup lang="ts">
import { computed, ref } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import StatusNotice from '../../components/StatusNotice.vue'
import { api } from '../../api/client'
import type { CatalogItem, EligibilityResult } from '../../domain/types'

const phone = ref('')
const loading = ref(false)
const error = ref('')
const eligibility = ref<EligibilityResult | null>(null)
const products = ref<CatalogItem[]>([])
const selected = ref<CatalogItem | null>(null)
const canContinue = computed(() => eligibility.value?.outcome === 'ELIGIBLE' && !!selected.value)

async function check() {
  loading.value = true; error.value = ''; eligibility.value = null; products.value = []; selected.value = null
  try {
    eligibility.value = await api.checkEligibility(phone.value)
    if (eligibility.value.outcome === 'ELIGIBLE') {
      products.value = await api.getCatalog(); selected.value = products.value.find((item) => item.available) || null
    }
  } catch { error.value = '读取失败，请稍后安全刷新。' } finally { loading.value = false }
}

function next() {
  if (!canContinue.value || !selected.value || !eligibility.value) return
  uni.setStorageSync('rechargeSelection', { maskedPhone: eligibility.value.maskedPhone, productRef: selected.value.productRef })
  uni.navigateTo({ url: '/pages/recharge/quote' })
}
</script>

<template><view class="page"><AppHeader left="← 返回" right="帮助" @left="uni.navigateBack()"/><view class="content"><text class="step">第 1–2 步，共 3 步</text><text class="heading">号码与商品</text><text class="desc">检查结果可信且商品有效后才能继续。测试号码以 00 结尾可演示 A1 UNKNOWN。</text><view class="field"><text class="field-label">孟加拉手机号码</text><input v-model="phone" type="number" maxlength="14" placeholder="请输入测试号码" /></view><button class="check" :disabled="loading || phone.length < 4" @click="check">{{ loading ? '检查中…' : '检查号码' }}</button><StatusNotice v-if="eligibility?.outcome === 'ELIGIBLE'" title="号码适用性已可信通过">仍需选择当前有效的商品版本。</StatusNotice><StatusNotice v-if="eligibility?.outcome === 'UNKNOWN'" tone="unknown" title="A1：适用性暂时无法确认">尚未收款；只能刷新、修正或安全退出，支付资格为 0。</StatusNotice><StatusNotice v-if="error" tone="risk" title="读取错误">{{ error }}</StatusNotice><view v-if="products.length" class="card"><text class="card-title">选择商品/套餐</text><view v-for="item in products" :key="item.productRef" class="product" @click="selected=item"><view class="radio" :class="{on:selected?.productRef===item.productRef}"/><view><text class="card-title">{{ item.name }}</text><text class="copy">目标面值 BDT {{ (item.faceValue.minor / 100).toFixed(2) }} · {{ item.catalogVersion }}</text></view></view></view><view class="tag-row"><text class="tag">加载中</text><text class="tag">空目录</text><text class="tag risk">读取错误</text><text class="tag unknown">A1 UNKNOWN</text></view></view><view class="action"><button class="primary" :disabled="!canContinue" @click="next">检查完成后继续</button><text v-if="!canContinue" class="reason">禁用原因：等待可信适用性与完整商品版本。</text></view></view></template>

<style src="../../styles/shared.css"></style><style scoped>
.field{margin-top:24rpx;padding:20rpx;border:1rpx solid #bcc8d6;border-radius:20rpx;background:#fff}.field-label{display:block;font-size:22rpx;color:var(--muted);margin-bottom:8rpx}.check{margin-top:16rpx;background:#fff;color:var(--brand);border:1rpx solid var(--brand)}.product{display:flex;gap:20rpx;align-items:flex-start;margin-top:24rpx}.radio{width:34rpx;height:34rpx;border:3rpx solid var(--line);border-radius:50%}.radio.on{border:10rpx solid var(--brand)}
</style>
