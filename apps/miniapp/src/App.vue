<script setup lang="ts"></script>

<template><slot /></template>

<style>
@import "./styles/shared.css";

page {
  --brand: #2357a5;
  --brand-strong: #173e79;
  --surface: #ffffff;
  --background: #f4f6f9;
  --text: #172033;
  --muted: #697487;
  --line: #e5eaf0;
  --success: #17875b;
  --unknown: #a76500;
  --risk: #bd4242;
  min-height: 100%;
  background: var(--background);
  color: var(--text);
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  font-size: 28rpx;
}

view, text, image, button, input { box-sizing: border-box; }
button::after { border: 0; }
</style>
