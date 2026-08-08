<script setup lang="ts">
defineProps<{ message: string }>()
defineEmits<{ retry: [] }>()
</script>

<template>
  <section class="state-panel read-unavailable" data-read-state="UNAVAILABLE" data-visible-fields="0" role="alert" aria-live="assertive">
    <div class="state-icon" aria-hidden="true">i</div>
    <h2>暂时无法读取工作台数据</h2>
    <p>{{ message }}。旧信息已清空，请重新读取。</p>
    <button class="state-action" @click="$emit('retry')">重新读取</button>
  </section>
</template>
