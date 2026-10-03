<template>
  <div class="page">
    <header class="navbar">
      <div class="nav-inner">
        <div class="logo" @click="$router.push('/')">🏞️ <span>知行山水</span></div>
        <div class="spacer"></div>
        <el-button round @click="$router.push('/')">← 返回景点列表</el-button>
      </div>
    </header>

    <main class="orders">
      <h2>🎫 我的订单</h2>

      <el-empty v-if="!loading && list.length === 0" description="还没有订单，去景点详情页预订门票吧" />

      <div class="order-list" v-loading="loading">
        <div class="order-card" v-for="o in list" :key="o.id">
          <img :src="o.image" class="o-img" :alt="o.attractionName" />
          <div class="o-main">
            <div class="o-name">{{ o.attractionName }}</div>
            <div class="o-meta">订单号 {{ o.orderNo }}</div>
            <div class="o-meta">{{ o.visitDate }} 入园 · {{ o.quantity }} 张 · 单价 ¥{{ o.unitPrice }}</div>
            <div class="o-meta" v-if="o.paidAt">支付时间 {{ o.paidAt }}</div>
          </div>
          <div class="o-side">
            <div class="o-amount">¥{{ Number(o.totalAmount).toFixed(2) }}</div>
            <el-tag :type="statusType(o.status)" effect="plain" round>{{ o.statusText }}</el-tag>
            <div class="o-actions" v-if="o.status === 0">
              <el-button type="success" size="small" round @click="goPay(o)">去支付</el-button>
              <el-button size="small" round @click="doCancel(o)">取消</el-button>
            </div>
          </div>
        </div>
      </div>

      <div class="pager" v-if="total > query.size">
        <el-pagination layout="prev, pager, next" :total="total"
                       :page-size="query.size" v-model:current-page="query.page" @current-change="load" />
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const list = ref([])
const total = ref(0)
const loading = ref(true)
const query = reactive({ page: 1, size: 5 })

const statusType = (s) => ({ 0: 'warning', 1: 'success', 2: 'info', 3: 'info' }[s] || 'info')

const load = async () => {
  loading.value = true
  try {
    const d = await api.orders({ page: query.page, size: query.size })
    list.value = d.list
    total.value = d.total
  } catch (e) { /* 拦截器已提示 */ } finally { loading.value = false }
}

/** 待支付订单直接唤起支付弹窗 */
const goPay = async (o) => {
  try {
    await ElMessageBox.confirm(
      `确认为订单 ${o.orderNo} 支付 ¥${Number(o.totalAmount).toFixed(2)}？（模拟支付，不产生真实扣款）`,
      '模拟支付', { confirmButtonText: '确认支付', cancelButtonText: '再想想', type: 'warning' })
  } catch (e) { return }
  try {
    await api.payOrder(o.id)
    ElMessage.success('支付成功')
    load()
  } catch (e) { /* 拦截器已提示 */ }
}

const doCancel = async (o) => {
  try {
    await ElMessageBox.confirm(`确定取消订单 ${o.orderNo} 吗？`, '提示', { type: 'warning' })
  } catch (e) { return }
  try {
    await api.cancelOrder(o.id)
    ElMessage.success('已取消')
    load()
  } catch (e) { /* 拦截器已提示 */ }
}

onMounted(load)
</script>

<style scoped>
.navbar { background: #fff; border-bottom: 1px solid #e8eaf0; }
.nav-inner { max-width: 980px; margin: 0 auto; display: flex; align-items: center; padding: 12px 16px; }
.logo { font-size: 20px; font-weight: 700; cursor: pointer; }
.spacer { flex: 1; }

.orders { max-width: 980px; margin: 20px auto 60px; padding: 0 16px; }
.orders h2 { font-size: 22px; color: #1c2b3a; margin-bottom: 18px; font-weight: 500; }

.order-card { display: flex; gap: 16px; background: #fff; border-radius: 12px; padding: 14px;
  margin-bottom: 12px; box-shadow: 0 2px 10px rgba(15,52,96,.06); align-items: center; }
.o-img { width: 120px; height: 80px; object-fit: cover; border-radius: 8px; flex-shrink: 0; }
.o-main { flex: 1; min-width: 0; }
.o-name { font-size: 16px; font-weight: 600; color: #1c2b3a; margin-bottom: 6px; }
.o-meta { font-size: 12px; color: #8a97a6; margin-top: 3px; }
.o-side { text-align: right; flex-shrink: 0; display: flex; flex-direction: column; gap: 8px; align-items: flex-end; }
.o-amount { color: #ff6700; font-size: 20px; font-weight: 700; }
.o-actions { display: flex; gap: 8px; }
.pager { display: flex; justify-content: center; margin-top: 20px; }
</style>
