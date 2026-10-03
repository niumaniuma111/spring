<template>
  <div class="page-card">
    <div class="toolbar">
      <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px" @change="load">
        <el-option label="待支付" :value="0" />
        <el-option label="已支付" :value="1" />
      </el-select>
      <el-input v-model="query.keyword" placeholder="订单号 / 用户名 / 昵称" clearable
                style="width: 240px" @keyup.enter="load" @clear="load" />
      <el-button @click="load">查询</el-button>
      <div class="spacer"></div>
      <el-tag type="info" effect="plain">有效订单 {{ total }}</el-tag>
      <el-tag type="warning" effect="plain">待支付 {{ stats.s0 }}</el-tag>
      <el-tag type="success" effect="plain">已支付 {{ stats.s1 }}</el-tag>
    </div>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="orderNo" label="订单号" width="190" />
      <el-table-column label="购买用户" width="150">
        <template #default="{ row }">
          <div>{{ row.nickname || row.username }}</div>
          <div class="sub">{{ row.username }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="attractionName" label="景点" min-width="160" show-overflow-tooltip />
      <el-table-column label="票数" width="60">
        <template #default="{ row }">{{ row.quantity }}张</template>
      </el-table-column>
      <el-table-column label="单价" width="80">
        <template #default="{ row }">¥{{ row.unitPrice }}</template>
      </el-table-column>
      <el-table-column label="总额" width="90">
        <template #default="{ row }">
          <span class="amount">¥{{ Number(row.totalAmount).toFixed(2) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="visitDate" label="游玩日期" width="105" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="{ 0: 'warning', 1: 'success', 2: 'info', 3: 'info' }[row.status]" effect="plain" round>
            {{ row.statusText }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="下单时间" width="165">
        <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="支付时间" width="165">
        <template #default="{ row }">{{ row.paidAt ? fmt(row.paidAt) : '—' }}</template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination layout="total, prev, pager, next" :total="total"
                     :page-size="query.size" v-model:current-page="query.page" @current-change="load" />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import api from '../../api'

const list = ref([])
const total = ref(0)
const stats = ref({ s0: 0, s1: 0, total: 0 })
const loading = ref(true)
const query = reactive({ page: 1, size: 10, keyword: '', status: null })

const fmt = (s) => s ? String(s).replace('T', ' ').slice(0, 19) : ''

const load = async () => {
  loading.value = true
  try {
    const d = await api.adminOrders({ page: query.page, size: query.size,
      keyword: query.keyword || undefined, status: query.status ?? undefined })
    list.value = d.list
    total.value = d.total
  } catch (e) { /* 拦截器已提示 */ } finally { loading.value = false }
}

const loadStats = async () => { try { stats.value = await api.adminOrderStats() } catch (e) {} }

onMounted(() => { load(); loadStats() })
</script>

<style scoped>
.page-card { background: #fff; border-radius: 10px; padding: 18px; }
.toolbar { display: flex; gap: 12px; align-items: center; margin-bottom: 14px; flex-wrap: wrap; }
.spacer { flex: 1; }
.amount { color: #ff6700; font-weight: 600; }
.sub { color: #a0a6ad; font-size: 12px; }
.pager { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>
