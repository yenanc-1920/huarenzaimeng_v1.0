<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import StatusNotice from '../../components/StatusNotice.vue'
import { api } from '../../api/client'
import type { CatalogItem, CatalogProjection, EligibilityResult, RechargeSelection } from '../../domain/types'
const eligibility=ref<Extract<EligibilityResult,{outcome:'ELIGIBLE'}>|null>(null),catalog=ref<CatalogProjection|null>(null),selected=ref<CatalogItem|null>(null),loading=ref(true),error=ref(''),activeType=ref<CatalogItem['productType']>('BALANCE'),supportedOperatorNames=ref<string[]>([])
const tabs=[{value:'BALANCE',label:'余额'},{value:'DATA',label:'流量'},{value:'BUNDLE',label:'套餐'}] as const
const items=computed(()=>catalog.value?.operatorQualification==='SUPPORTED'?catalog.value.items:[])
const visibleItems=computed(()=>items.value.filter(item=>item.productType===activeType.value))
const supportedOperators=computed(()=>supportedOperatorNames.value.length?supportedOperatorNames.value.join('、'):'暂时无法读取')
const typeLabel:Record<NonNullable<CatalogItem['productType']>,string>={BALANCE:'余额',DATA:'流量',BUNDLE:'套餐'}
const money=(value:number|undefined)=>`¥ ${(value??0).toFixed(2)}`
async function load(){loading.value=true;error.value='';selected.value=null;supportedOperatorNames.value=[];try{const stored=uni.getStorageSync('rechargeEligibility');if(!stored||stored.outcome!=='ELIGIBLE')throw new Error('ELIGIBILITY_REQUIRED');eligibility.value=stored;const [operators,currentCatalog]=await Promise.all([api.getSupportedOperators(),api.getCatalog(stored.operatorCode)]);supportedOperatorNames.value=operators.map(item=>item.displayName);catalog.value=currentCatalog}catch{eligibility.value=null;catalog.value=null;error.value='号码或可售商品信息已失效，请返回重新识别。'}finally{loading.value=false}}
function choose(item:CatalogItem){selected.value=item}
function next(){if(!selected.value||!eligibility.value||catalog.value?.operatorQualification!=='SUPPORTED'||!selected.value.productType||!selected.value.displayName||!selected.value.benefitText||!selected.value.priceVersionRef)return;const value:RechargeSelection={recipientPhone:eligibility.value.recipientPhone,maskedPhone:eligibility.value.maskedPhone,operatorCode:eligibility.value.operatorCode,operatorName:eligibility.value.operatorName,productRef:selected.value.productRef,denominationRef:selected.value.denominationRef,itemKind:selected.value.itemKind,faceValue:selected.value.faceValue,productType:selected.value.productType,displayName:selected.value.displayName,benefitText:selected.value.benefitText,validityText:selected.value.validityText??null,priceVersionRef:selected.value.priceVersionRef,supportedOperatorSetVersion:catalog.value.supportedOperatorSetVersion,catalogVersion:catalog.value.catalogVersion};uni.setStorageSync('rechargeSelection',value);uni.navigateTo({url:'/pages/recharge/quote'})}
onMounted(load)
</script>
<template>
  <view class="page" data-page-id="UX-P011" data-page-root="recharge-products">
    <AppHeader title="选择商品" left="返回" @left="uni.navigateBack()"/>
    <view class="content">
      <text class="step">第 2 步，共 3 步</text>
      <view class="heading" role="heading" aria-level="1">选择充值商品</view>
      <view class="supported-catalog"><text>当前支持运营商</text><strong>{{supportedOperators}}</strong></view>
      <view class="number-context">
        <view><text>当前充值号码</text><strong>{{eligibility?.maskedPhone||'待确认'}}</strong></view>
        <view><text>识别运营商</text><strong>{{eligibility?.operatorName||'待确认'}}</strong></view>
      </view>
      <text class="support-hint">以下仅显示当前号码所属运营商的正式可售商品</text>
      <view class="product-tabs" role="tablist"><button v-for="tab in tabs" :key="tab.value" :class="{active:activeType===tab.value}" @click="activeType=tab.value"><text>{{tab.label}}</text></button></view>
      <StatusNotice v-if="error" tone="risk" title="暂时不能选择">{{error}}</StatusNotice>
      <view v-else-if="loading" class="card state">正在读取可售商品…</view>
      <StatusNotice v-else-if="catalog?.operatorQualification!=='SUPPORTED'" tone="risk" title="当前没有可售商品">该运营商暂未配置可用渠道和价格，请返回修改号码。</StatusNotice>
      <view v-else class="product-list">
        <button v-for="item in visibleItems" :key="`${item.productRef}:${item.denominationRef}`" class="product-card" :class="{selected:selected?.productRef===item.productRef&&selected?.denominationRef===item.denominationRef}" @click="choose(item)">
          <view class="product-main"><view class="product-heading"><text class="product-type">{{typeLabel[item.productType!]}}</text><text class="product-name">{{item.displayName}}</text></view><text class="product-benefit">{{item.benefitText}}</text><text v-if="item.validityText" class="product-validity">有效期 {{item.validityText}}</text></view>
          <view class="product-price"><small>售价</small><strong>{{money(item.finalAmountCny)}}</strong></view>
        </button>
        <view v-if="!visibleItems.length" class="card state">该分类暂无可售商品</view>
      </view>
    </view>
    <view class="action"><button class="primary" :disabled="!selected" @click="next">确认商品</button></view>
  </view>
</template>
<style src="../../styles/shared.css"></style><style scoped>.supported-catalog{margin-top:24rpx;padding:22rpx 24rpx;border-radius:24rpx;background:#eaf2ff}.supported-catalog text,.supported-catalog strong{display:block}.supported-catalog text{color:var(--muted);font-size:21rpx}.supported-catalog strong{margin-top:8rpx;color:var(--brand-strong);line-height:1.5}.product-tabs{margin-top:30rpx;padding:7rpx;display:grid;grid-template-columns:repeat(3,1fr);gap:6rpx;border-radius:24rpx;background:#e8eef7}.product-tabs button{min-height:86rpx;padding:15rpx 8rpx;display:flex;align-items:center;justify-content:center;border-radius:19rpx;color:var(--muted)}.product-tabs button text{font-weight:800}.product-tabs .active{background:#fff;color:var(--brand);box-shadow:0 6rpx 16rpx rgba(23,52,95,.08)}.product-list{margin-top:22rpx}.product-card{width:100%;min-height:164rpx;margin-top:18rpx;padding:28rpx;display:flex;align-items:center;justify-content:space-between;gap:20rpx;border:2rpx solid transparent;border-radius:30rpx;background:#fff;text-align:left}.product-main{min-width:0;flex:1}.product-heading{display:flex;align-items:center;gap:12rpx}.product-type{flex:none;padding:5rpx 12rpx;border-radius:12rpx;background:#eaf2ff;color:var(--brand);font-size:19rpx;font-weight:800}.product-name,.product-benefit,.product-validity,.product-price small{display:block}.product-name{font-size:28rpx;font-weight:800}.product-benefit{margin-top:14rpx;color:#1e304a;font-size:29rpx;font-weight:800;line-height:1.45}.product-validity{margin-top:9rpx;color:var(--muted);font-size:21rpx}.product-price{flex:none;text-align:right}.product-price small{color:var(--muted);font-size:19rpx}.product-price strong{display:block;margin-top:5rpx;color:#e06b2d;font-size:34rpx;white-space:nowrap}.product-card.selected{border:4rpx solid var(--brand);background:#edf4ff}.state{text-align:center}</style>
