<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import { api } from '../../api/client'
import type { DirectoryCity, DirectorySummary } from '../../domain/types'

const cities=ref<DirectoryCity[]>([]),items=ref<DirectorySummary[]>([]),cityCode=ref(''),category=ref('ALL'),loading=ref(true),error=ref('')
const categories=computed(()=>['ALL',...new Set(items.value.map(item=>item.category))])
const visibleItems=computed(()=>category.value==='ALL'?items.value:items.value.filter(item=>item.category===category.value))
const goHome=()=>uni.reLaunch({url:'/pages/index/index'})
async function load(){loading.value=true;error.value='';try{items.value=await api.getDirectory(cityCode.value||undefined)}catch{items.value=[];error.value='黄页暂时无法读取，请稍后重试。'}finally{loading.value=false}}
async function initialize(){try{cities.value=await api.getDirectoryCities();if(cities.value.length)cityCode.value=cities.value[0].cityCode}catch{error.value='城市列表暂时无法读取。'}await load()}
const chooseCity=(event:any)=>{cityCode.value=cities.value[Number(event.detail.value)]?.cityCode||'';category.value='ALL';void load()}
const open=(item:DirectorySummary)=>uni.navigateTo({url:`/pages/directory/detail?entryRef=${encodeURIComponent(item.entryRef)}`})
onMounted(initialize)
</script>
<template><view class="page" data-page-root="directory-list"><AppHeader title="孟加拉黄页" left="首页" @left="goHome"/><view class="content"><view class="heading">孟加拉黄页</view><text class="desc">按城市和分类查找已发布的生活服务信息</text><picker :range="cities" range-key="displayName" @change="chooseCity"><view class="city-filter">当前城市：{{cities.find(city=>city.cityCode===cityCode)?.displayName||'请选择城市'}}⌄</view></picker><scroll-view scroll-x class="tabs"><button v-for="tab in categories" :key="tab" :class="{active:category===tab}" @click="category=tab">{{tab==='ALL'?'全部':tab}}</button></scroll-view><view v-if="loading" class="card state">正在读取黄页…</view><view v-else-if="error" class="card state"><text>{{error}}</text><button @click="load">重新加载</button></view><button v-for="item in visibleItems" :key="item.entryRef" class="card item" @click="open(item)"><view><text class="card-title">{{item.displayName}}</text><text class="copy">{{item.summary}}</text><text class="meta">{{item.cityName}} · {{item.category}}</text></view><text>›</text></button><view v-if="!loading&&!error&&!visibleItems.length" class="card state">当前筛选条件暂无黄页信息</view></view></view></template>
<style src="../../styles/shared.css"></style><style scoped>.city-filter{margin-top:28rpx;padding:24rpx;border-radius:22rpx;background:#fff;font-weight:700}.tabs{white-space:nowrap;margin:22rpx 0}.tabs button{display:inline-block;margin-right:12rpx;padding:14rpx 24rpx;border-radius:999rpx;background:#fff;color:var(--muted)}.tabs .active{background:var(--brand);color:#fff}.item{width:100%;display:flex;justify-content:space-between;text-align:left}.item view{flex:1}.meta{display:block;margin-top:10rpx;color:var(--muted);font-size:22rpx}.state{text-align:center}</style>
