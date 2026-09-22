<template>
  <div v-loading="loading">
    <!-- 景点总数 -->
    <el-row :gutter="16">
      <el-col :span="8">
        <el-card class="stat-card">
          <div class="stat-num">{{ stats.total ?? '-' }}</div>
          <div class="stat-label">已发布景点总数</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card class="stat-card">
          <div class="stat-num">{{ provinceCount }}</div>
          <div class="stat-label">覆盖省份数</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card class="stat-card">
          <div class="stat-num">{{ stats.byLevel?.filter(i => i.count > 0).length ?? '-' }}</div>
          <div class="stat-label">涵盖的景区等级数</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="10">
        <el-card header="各等级景点分布统计">
          <div ref="levelChart" style="height: 360px"></div>
        </el-card>
      </el-col>
      <el-col :span="14">
        <el-card header="各省景点数量统计">
          <div ref="provinceChart" style="height: 360px"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import api from '../../api'

const loading = ref(true)
const stats = ref({})
const levelChart = ref()
const provinceChart = ref()
let levelInstance, provinceInstance

const provinceCount = computed(() => stats.value.byProvince?.filter(i => i.count > 0).length ?? 0)

function render() {
  // 等级分布：饼图
  levelInstance = echarts.init(levelChart.value)
  levelInstance.setOption({
    tooltip: { trigger: 'item', formatter: '{b} 景区：{c} 个（{d}%）' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie', radius: ['35%', '62%'],
      label: { formatter: '{b}\n{c} 个' },
      data: stats.value.byLevel.map(i => ({ name: i.level + '级', value: i.count }))
    }]
  })

  // 各省数量：柱状图
  provinceInstance = echarts.init(provinceChart.value)
  provinceInstance.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 20, bottom: 70, top: 20 },
    xAxis: { type: 'category', data: stats.value.byProvince.map(i => i.province),
             axisLabel: { rotate: 45, interval: 0 } },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{ type: 'bar', barMaxWidth: 28, itemStyle: { color: '#409eff' },
               data: stats.value.byProvince.map(i => i.count),
               label: { show: true, position: 'top' } }]
  })
}

function onResize() { levelInstance?.resize(); provinceInstance?.resize() }

onMounted(async () => {
  try {
    stats.value = await api.stats()
    render()
    window.addEventListener('resize', onResize)
  } finally { loading.value = false }
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  levelInstance?.dispose()
  provinceInstance?.dispose()
})
</script>

<style scoped>
.stat-card { text-align: center; }
.stat-num { font-size: 40px; font-weight: 800; color: #1f6feb; }
.stat-label { color: #888; margin-top: 6px; }
</style>
