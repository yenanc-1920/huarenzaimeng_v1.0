<script setup lang="ts">
import { computed, ref } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import StatusNotice from '../../components/StatusNotice.vue'
import { api } from '../../api/client'
import type { CatalogItem, CatalogProjection, EligibilityResult, RechargeSelection } from '../../domain/types'

const phone=ref(''),loading=ref(false),error=ref('')
const eligibility=ref<EligibilityResult|null>(null),catalog=ref<CatalogProjection|null>(null)
const products=ref<CatalogItem[]>([]),selected=ref<CatalogItem|null>(null)
const canContinue=computed(()=>eligibility.value?.outcome==='ELIGIBLE'&&catalog.value?.operatorQualification==='SUPPORTED'&&!!selected.value)

async function check(){
  loading.value=true;error.value='';eligibility.value=null;catalog.value=null;products.value=[];selected.value=null
  try{
    const result=await api.checkEligibility(phone.value);eligibility.value=result
    if(result.outcome==='ELIGIBLE'){
      const current=await api.getCatalog(result.operatorCode);catalog.value=current
      if(current.operatorQualification==='SUPPORTED')products.value=current.items
    }
  }catch{error.value='暂时无法检查号码或当前支持批次，请稍后重试。'}finally{loading.value=false}
}

function choose(item:CatalogItem){if(catalog.value?.operatorQualification==='SUPPORTED')selected.value=item}
function next(){
  if(!canContinue.value||!selected.value||eligibility.value?.outcome!=='ELIGIBLE'||catalog.value?.operatorQualification!=='SUPPORTED')return
  const selection:RechargeSelection={
    maskedPhone:eligibility.value.maskedPhone,operatorCode:eligibility.value.operatorCode,operatorName:eligibility.value.operatorName,
    productRef:selected.value.productRef,denominationRef:selected.value.denominationRef,itemKind:selected.value.itemKind,faceValue:selected.value.faceValue,
    supportedOperatorSetVersion:catalog.value.supportedOperatorSetVersion,catalogVersion:catalog.value.catalogVersion,
  }
  uni.setStorageSync('rechargeSelection',selection);uni.navigateTo({url:'/pages/recharge/quote'})
}
</script>
<template><view class="page" data-page-root="recharge-select" data-visible-state="READY_FOR_INPUT"><AppHeader title="手机充值" left="返回" @left="uni.navigateBack()"/><view class="content"><view class="progress"><text class="active"/><text/><text/></view><text class="step">第 1 步，共 3 步</text><view class="heading" role="heading" aria-level="1">输入充值号码</view><text class="desc">仅使用合成运营商支持集；不连接第三方识别</text><text class="input-label">手机号码</text><view class="phone-input"><text>+880</text><input v-model="phone" type="number" maxlength="14" placeholder="1712 345 678"/></view><button class="check" :disabled="loading||phone.length<4" @click="check">{{loading?'正在读取…':'检查号码和当前支持批次'}}</button>
<StatusNotice v-if="eligibility?.outcome==='UNKNOWN'" tone="unknown" title="当前无法确认，尚未收款">你可以查询最新结果、修改号码或安全退出。</StatusNotice>
<StatusNotice v-if="catalog?.operatorQualification==='UNSUPPORTED'" tone="risk" title="当前批次暂不支持该运营商">你可以修改号码或安全退出；本页不承诺后续支持时间。</StatusNotice>
<StatusNotice v-if="catalog?.operatorQualification==='UNKNOWN'" tone="unknown" title="暂时无法确认是否支持">当前支持资格不明，不能选择商品或继续。</StatusNotice>
<StatusNotice v-if="error" tone="risk" title="暂时无法检查">{{error}}</StatusNotice>
<view v-if="eligibility?.outcome==='ELIGIBLE'&&catalog?.operatorQualification==='SUPPORTED'" class="operator"><text class="operator-logo">✓</text><view><text class="card-title">{{eligibility.operatorName}}</text><text class="copy">当前批次支持，仅可选择下列预设面额或套餐</text><text class="version">支持集 v{{catalog.supportedOperatorSetVersion}} · 目录 v{{catalog.catalogVersion}}</text></view></view>
<view v-if="products.length" class="amount-section"><text class="section-title">选择预设面额或套餐</text><view class="amounts"><button v-for="item in products" :key="`${item.productRef}:${item.denominationRef}`" :class="{selected:selected?.productRef===item.productRef&&selected?.denominationRef===item.denominationRef}" @click="choose(item)"><text>{{(item.faceValue.minor/100).toFixed(0)}} {{item.faceValue.currency}}</text><small>{{item.itemKind==='PRESET_PACKAGE'?'预设套餐':'预设面额'}}</small></button></view></view></view><view class="action"><button class="primary" :disabled="!canContinue" @click="next">下一步</button></view></view></template>
<style src="../../styles/shared.css"></style><style scoped>.progress{display:flex;margin-bottom:20rpx}.progress text{height:8rpx;border-radius:8rpx;background:#d8dee7;flex:1;margin-right:12rpx}.progress text:last-child{margin-right:0}.progress .active{background:var(--brand)}.input-label{display:block;font-size:26rpx;font-weight:700;margin-top:46rpx}.phone-input{display:flex;align-items:center;margin-top:16rpx;padding:28rpx;background:#fff;border:4rpx solid var(--brand);border-radius:28rpx}.phone-input>text{padding-right:24rpx;margin-right:24rpx;border-right:1rpx solid var(--line);color:var(--muted)}.phone-input input{flex:1;font-size:34rpx}.check{display:block;width:100%;margin-top:20rpx;padding:22rpx;color:var(--brand);text-align:center}.operator{display:flex;align-items:center;margin-top:22rpx;padding:26rpx;background:#fff;border-radius:28rpx}.operator-logo{width:72rpx;height:72rpx;line-height:72rpx;margin-right:22rpx;border-radius:22rpx;background:#e7effc;color:var(--brand);text-align:center;font-weight:900}.version{display:block;margin-top:8rpx;color:var(--muted);font-size:22rpx}.section-title{display:block;font-size:30rpx;font-weight:800;margin-top:42rpx}.amounts{display:flex;flex-wrap:wrap;margin:14rpx -8rpx 0}.amounts button{width:calc(50% - 16rpx);min-height:112rpx;margin:8rpx;border:1rpx solid var(--line);border-radius:24rpx;background:#fff;text-align:center;font-weight:700}.amounts button text,.amounts button small{display:block}.amounts button small{margin-top:6rpx;color:var(--muted);font-size:20rpx}.amounts .selected{border:4rpx solid var(--brand);background:#edf4ff;color:var(--brand)}</style>
