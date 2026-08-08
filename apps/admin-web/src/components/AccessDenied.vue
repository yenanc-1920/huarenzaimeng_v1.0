<script setup lang="ts">
const props = defineProps<{ denialKind: 'ROLE_DENIED' | 'AUTHORITY_UNAVAILABLE' }>()
const authorityUnavailable = props.denialKind === 'AUTHORITY_UNAVAILABLE'
</script>

<template>
  <section class="state-panel denied-state" data-read-state="ACCESS_DENIED" :data-denial-kind="denialKind" data-visible-fields="0" role="status" aria-live="polite">
    <div class="state-icon" aria-hidden="true">!</div>
    <h2>{{ authorityUnavailable ? '当前无法安全显示，请重新进入' : '当前无法访问此页面' }}</h2>
    <p>当前不会显示任何记录、数量或对象是否存在。</p>
  </section>
</template>
