<template>
  <div class="page">
    <header class="navbar">
      <div class="nav-inner">
        <div class="logo" @click="$router.push('/')">🏞️ <span>知行山水</span></div>
        <div class="spacer"></div>
        <el-button round @click="$router.push('/')">← 返回景点列表</el-button>
      </div>
    </header>

    <div class="map-wrap">
      <div class="map-tip" v-if="ready">🗺️ 点击标记查看景点卡片，滚轮缩放聚合</div>
      <div class="map-error" v-if="error">
        <el-result icon="warning" title="地图加载失败" :sub-title="error">
          <template #extra>
            <el-button round @click="$router.push('/')">返回景点列表</el-button>
          </template>
        </el-result>
      </div>
      <div id="amap-container" class="amap-container" v-show="!error"></div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api'

const router = useRouter()
const ready = ref(false)
const error = ref('')

/** 动态加载高德 JS API（key 从后端下发，不硬编码） */
function loadAmap(cfg) {
  return new Promise((resolve, reject) => {
    if (window.AMap) return resolve(window.AMap)
    // 安全密钥必须在 JS API 加载前配置
    window._AMapSecurityConfig = { securityJsCode: cfg.securityCode }
    const s = document.createElement('script')
    s.src = `https://webapi.amap.com/maps?v=2.0&key=${cfg.jsKey}&plugin=AMap.MarkerCluster`
    s.onload = () => resolve(window.AMap)
    s.onerror = () => reject(new Error('脚本加载失败'))
    document.head.appendChild(s)
  })
}

const levelColor = { '5A': '#e24b4a', '4A': '#ef9f27', '3A': '#639922' }

onMounted(async () => {
  try {
    const cfg = await api.amapConfig()
    if (!cfg.jsKey) { error.value = '地图服务未配置'; return }
    const AMap = await loadAmap(cfg)
    const map = new AMap.Map('amap-container', {
      zoom: 5, center: [104.0, 35.5], mapStyle: 'amap://styles/whitesmoke', viewMode: '2D'
    })
    const points = await api.mapPoints()
    if (!points.length) { error.value = '暂无带坐标的景点数据'; return }

    // 点位数据（坐标→景点 反查表：MarkerCluster 的 renderMarker 里 getExtData 不可靠，
    // 直接用 marker 坐标反查，避免 spot 丢失导致 InfoWindow 收到 NaN 坐标而卡死）
    const spotByPos = new Map()
    const clusterData = points.map(p => {
      const lng = Number(p.lng), lat = Number(p.lat)
      spotByPos.set(lng.toFixed(6) + ',' + lat.toFixed(6), p)
      return { lnglat: [lng, lat] }
    })

    const dotHtml = (p) => `<div style="width:16px;height:16px;border-radius:50% 50% 50% 0;transform:rotate(-45deg);
        background:${levelColor[p.level] || '#378add'};border:2px solid #fff;
        box-shadow:0 1px 4px rgba(0,0,0,.4);cursor:pointer"></div>`

    // 单例 InfoWindow：避免每次点击 new 造成实例堆积
    let infoWindow = null
    const openInfo = (p) => {
      if (!infoWindow) infoWindow = new AMap.InfoWindow({ anchor: 'bottom-center', offset: new AMap.Pixel(0, -8) })
      infoWindow.setContent(`<div style="padding:8px 10px;font-family:sans-serif;min-width:180px">
          <b style="font-size:14px">${p.name}</b>
          <div style="margin:4px 0;font-size:12px;color:#666">
            ${p.level} · ★${p.rating} · ${p.ticketPrice > 0 ? '¥' + p.ticketPrice : '免费'} · ${p.cityName}
          </div>
          <a href="javascript:;" id="amap-detail-link" style="font-size:12px;color:#1f6feb">查看详情 →</a>
        </div>`)
      infoWindow.open(map, [Number(p.lng), Number(p.lat)])
      // SPA 内跳转（替代 <a href> 整页刷新）
      const link = document.getElementById('amap-detail-link')
      if (link) link.onclick = () => router.push(`/detail/${p.id}`)
    }

    // 聚合：缩小时同区域标记合并为带数量的气泡，解决密集区互相遮挡；
    // 放大后自动散开为单点。点击聚合点自动放大一级。
    AMap.plugin('AMap.MarkerCluster', () => {
      const cluster = new AMap.MarkerCluster(map, clusterData, {
        gridSize: 70,
        renderMarker: (ctx) => {
          // 用 marker 坐标反查景点信息（extData 在部分版本返回空）
          const pos = ctx.marker.getPosition()
          const key = pos ? Number(pos.getLng()).toFixed(6) + ',' + Number(pos.getLat()).toFixed(6) : ''
          const spot = spotByPos.get(key) || { name: '未知景点', level: '', rating: 0, ticketPrice: 0, cityName: '' }
          ctx.marker.setContent(dotHtml(spot))
          // 聚合在每次地图移动/缩放后都会重新执行 renderMarker 并复用 marker，
          // 必须先解绑旧监听再绑新监听，否则点击会触发多次 openInfo
          ctx.marker.off('click')
          ctx.marker.on('click', () => openInfo(spot))
        },
        renderClusterMarker: (ctx) => {
          const n = ctx.count
          const size = n < 10 ? 36 : n < 30 ? 44 : 52
          ctx.marker.setContent(`<div style="width:${size}px;height:${size}px;border-radius:50%;
              background:rgba(31,111,235,.85);color:#fff;display:flex;align-items:center;justify-content:center;
              font-size:13px;font-weight:600;border:3px solid rgba(255,255,255,.7);
              box-shadow:0 2px 8px rgba(0,0,0,.3);cursor:pointer">${n}</div>`)
          ctx.marker.off('click')
          ctx.marker.on('click', () => {
            map.setZoomAndCenter(map.getZoom() + 3, ctx.marker.getPosition())
          })
        }
      })
      // 注意：构造时已传入数据，此处不可再调 setData()（会触发二次渲染管线冲突导致页面卡死）
    })

    ready.value = true
  } catch (e) {
    error.value = typeof e === 'string' ? e : (e.message || '地图初始化异常，请检查高德 Key 配置')
  }
})
</script>

<style scoped>
.navbar { background: #fff; border-bottom: 1px solid #e8eaf0; }
.nav-inner { max-width: 1200px; margin: 0 auto; display: flex; align-items: center; padding: 12px 16px; }
.logo { font-size: 20px; font-weight: 700; cursor: pointer; }
.spacer { flex: 1; }
.map-wrap { position: relative; }
.amap-container { width: 100%; height: calc(100vh - 61px); }
.map-tip { position: absolute; top: 12px; left: 50%; transform: translateX(-50%); z-index: 999;
  background: rgba(255,255,255,.95); padding: 6px 16px; border-radius: 999px;
  font-size: 13px; color: #4e5d6c; box-shadow: 0 2px 8px rgba(0,0,0,.12); }
.map-error { padding-top: 80px; }
</style>
