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
</style>
