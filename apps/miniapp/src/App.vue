<script setup lang="ts"></script>

<template><slot /></template>

<style>
@import "./styles/shared.css";

page {
  --brand: #195bb8;
  --brand-strong: #142540;
  --surface: #ffffff;
  --background: #f7f9fc;
  --text: #142540;
  --muted: #68768c;
  --line: #dde4ee;
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
.primary, .secondary, .identify, .retry, .readonly-retry, .report-trigger,
.filters button, .product-tabs button, .dialog button, .recovery-card button {
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1.2;
}
.tabs button { display: inline-flex; align-items: center; justify-content: center; line-height: 1.2; }
.side { display: flex; align-items: center; line-height: 1.2; }
.side.right { justify-content: flex-end; }
.city-filter, .item, .order, .product-card, .story-card, .service-row, .support-card { align-items: center; }
.operator-logo { width: 96rpx; height: 96rpx; margin-right: 24rpx; flex: none; border-radius: 22rpx; background: #fff; }
.life-page > .header, .directory-page > .header { display: none; }
</style>
