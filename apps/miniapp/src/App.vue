<script setup lang="ts"></script>

<template><slot /></template>

<style>
page {
  --brand: #2457a6;
  --brand-strong: #173f7c;
  --surface: #ffffff;
  --background: #f3f6fa;
  --text: #182333;
  --muted: #5f6b7a;
  --line: #d8e0ea;
  --success: #237a57;
  --unknown: #a86400;
  --risk: #b23a3a;
  min-height: 100%;
  background: var(--background);
  color: var(--text);
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  font-size: 28rpx;
}

view, text, image, button, input { box-sizing: border-box; }
button::after { border: 0; }
</style>
