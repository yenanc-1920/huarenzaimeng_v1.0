<script setup lang="ts">
const mockOrders = [
  { orderRef: 'SYN-O-001', state: 'TOPUP_REVIEW', total: '¥10.00', next: '等待证据或联系支持' },
  { orderRef: 'SYN-O-002', state: 'COMPLETED', total: '¥20.00', next: '无' }
]
</script>

<template>
  <main>
    <header><strong>华人在孟</strong><span>运营后台 · Mock骨架</span></header>
    <section class="notice">仅展示合成数据；未连接微信支付、Reloadly、MySQL或云平台。</section>
    <section class="panel">
      <h1>订单恢复工作台</h1>
      <table><thead><tr><th>订单号</th><th>状态</th><th>用户实付总价</th><th>下一动作</th></tr></thead>
        <tbody><tr v-for="order in mockOrders" :key="order.orderRef"><td>{{ order.orderRef }}</td><td>{{ order.state }}</td><td>{{ order.total }}</td><td>{{ order.next }}</td></tr></tbody>
      </table>
    </section>
  </main>
</template>
