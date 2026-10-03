<template>
  <div class="page" v-loading="loading">
    <header class="navbar">
      <div class="nav-inner">
        <div class="logo" @click="$router.push('/')">🏞️ <span>知行山水</span></div>
        <div class="spacer"></div>
        <el-button round @click="$router.push('/')">← 返回景点列表</el-button>
      </div>
    </header>

    <main class="detail" v-if="a">
      <!-- 沉浸式头图 -->
      <div class="hero-wrap">
        <img :src="a.image" :alt="a.name" class="hero" />
        <div class="hero-mask"></div>
        <div class="hero-caption">
          <div class="hero-title-row">
            <h1>{{ a.name }}</h1>
            <el-tag :type="levelTagType(a.level)" size="large" effect="dark" round>{{ a.level }}级景区</el-tag>
          </div>
          <div class="hero-chips">
            <span class="chip">📍 {{ a.provinceName }} · {{ a.cityName }}</span>
            <span class="chip">★ 综合评分 {{ a.rating }}</span>
            <span class="chip">🔥 浏览 {{ a.views }} 次</span>
          </div>
        </div>
      </div>

      <div class="content">
        <!-- 信息卡 -->
        <div class="info-cards">
          <div class="info-card">
            <div class="info-icon">📍</div>
            <div class="info-body">
              <div class="info-label">详细地址</div>
              <div class="info-value">{{ a.address }}</div>
            </div>
          </div>
          <div class="info-card">
            <div class="info-icon">🕐</div>
            <div class="info-body">
              <div class="info-label">开放时间</div>
              <div class="info-value">{{ a.openTime }}</div>
            </div>
          </div>
          <div class="info-card">
            <div class="info-icon">🎫</div>
            <div class="info-body">
              <div class="info-label">参考门票</div>
              <div class="info-value price">{{ a.ticketPrice > 0 ? '¥' + a.ticketPrice + ' 起' : '免费开放' }}</div>
            </div>
          </div>
          <div class="info-card" v-if="weather">
            <div class="info-icon">🌤️</div>
            <div class="info-body">
              <div class="info-label">当地天气（{{ weather.city }}）</div>
              <div class="info-value">{{ weather.text }}</div>
            </div>
          </div>
        </div>

        <!-- 景点介绍 -->
        <section class="intro">
          <h3><i></i>景点介绍</h3>
          <p>{{ a.description }}</p>
        </section>

        <!-- 周边推荐 -->
        <section class="nearby">
          <h3><i></i>周边推荐 <span class="nearby-sub">基于景点坐标 3km 内 · 按距离排序</span></h3>
          <div class="nearby-tabs">
            <el-button size="small" :type="nearbyType === 'hotel' ? 'primary' : ''" round @click="loadNearby('hotel')">🏨 酒店住宿</el-button>
            <el-button size="small" :type="nearbyType === 'food' ? 'primary' : ''" round @click="loadNearby('food')">🍜 美食餐饮</el-button>
          </div>
          <div v-loading="nearbyLoading" style="min-height: 60px">
            <div class="poi-row" v-for="(p, idx) in nearbyList" :key="idx">
              <span class="poi-dist">{{ p.distance }}m</span>
              <div class="poi-main">
                <div class="poi-name">{{ p.name }}</div>
                <div class="poi-addr">{{ p.address || '地址详情以地图为准' }}</div>
              </div>
              <span class="poi-tel" v-if="p.tel">☎ {{ p.tel }}</span>
            </div>
            <el-empty v-if="!nearbyLoading && nearbyList.length === 0" description="周边 3km 内暂无相关地点" :image-size="60" />
          </div>
        </section>

        <!-- 门票预订 -->
        <section class="booking" v-if="a.ticketPrice > 0">
          <h3><i></i>门票预订</h3>
          <div class="booking-row">
            <div class="booking-item">
              <div class="booking-label">游玩日期</div>
              <el-date-picker v-model="visitDate" type="date" value-format="YYYY-MM-DD"
                              :disabled-date="(d) => d.getTime() < Date.now() - 86400000"
                              placeholder="选择日期" style="width: 160px" />
            </div>
            <div class="booking-item">
              <div class="booking-label">票数（每单限5张）</div>
              <el-input-number v-model="quantity" :min="1" :max="5" />
            </div>
            <div class="booking-item booking-total">
              <div class="booking-label">合计</div>
              <div class="total-price">¥{{ (a.ticketPrice * quantity).toFixed(2) }}</div>
            </div>
            <el-button type="danger" size="large" round :loading="booking" @click="book">立即预订</el-button>
          </div>
          <p class="booking-tip">预订后保留 15 分钟支付时间，超时订单自动关闭</p>
        </section>

        <div class="back-row">
          <el-button type="primary" round plain @click="$router.push('/')">← 返回景点列表</el-button>
        </div>
      </div>
    </main>

    <!-- 模拟支付弹窗 -->
    <el-dialog v-model="payVisible" title="模拟支付" width="380px" :close-on-click-modal="false">
      <div class="pay-body" v-if="currentOrder">
        <div class="pay-row"><span>订单号</span><b>{{ currentOrder.orderNo }}</b></div>
        <div class="pay-row"><span>景点</span><b>{{ currentOrder.attractionName }}</b></div>
        <div class="pay-row"><span>票数</span><b>{{ currentOrder.quantity }} 张 · {{ currentOrder.visitDate }} 入园</b></div>
        <div class="pay-qr">
          <img :src="`/api/orders/qrcode?orderNo=${currentOrder.orderNo}&amount=${Number(currentOrder.totalAmount).toFixed(2)}`"
               alt="支付二维码" />
          <span>📱 请使用手机「扫一扫」扫码支付<span class="qr-sub">（演示二维码，扫码可查看订单信息）</span></span>
        </div>
        <div class="pay-amount">¥{{ Number(currentOrder.totalAmount).toFixed(2) }}</div>
        <p class="pay-tip">本项目为演示环境，点击下方按钮即视为支付成功，不产生真实扣款</p>
      </div>
      <template #footer>
        <el-button round @click="payVisible = false">稍后支付</el-button>
        <el-button type="success" round :loading="paying" @click="confirmPay">确认支付</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const route = useRoute()
const router = useRouter()
const a = ref(null)
const loading = ref(true)

// 预订/支付状态
const visitDate = ref('')
const quantity = ref(1)
const booking = ref(false)
const payVisible = ref(false)
const paying = ref(false)
const currentOrder = ref(null)
const weather = ref(null)
const nearbyType = ref('hotel')
const nearbyList = ref([])
const nearbyLoading = ref(false)

function levelTagType(level) {
  return { '5A': 'danger', '4A': 'warning', '3A': 'success', '2A': 'info', '1A': 'info' }[level] || 'info'
}

/** 下单：成功后弹出模拟支付框 */
const book = async () => {
  if (!visitDate.value) { ElMessage.warning('请选择游玩日期'); return }
  booking.value = true
  try {
    currentOrder.value = await api.createOrder({
      attractionId: a.value.id, quantity: quantity.value, visitDate: visitDate.value
    })
    payVisible.value = true
  } catch (e) { /* 拦截器已提示 */ } finally { booking.value = false }
}

/** 模拟支付：成功后跳转"我的订单" */
const confirmPay = async () => {
  paying.value = true
  try {
    await api.payOrder(currentOrder.value.id)
    payVisible.value = false
    await ElMessageBox.alert(
      `订单 ${currentOrder.value.orderNo} 支付成功！可在"我的订单"中查看。`,
      '支付成功', { confirmButtonText: '查看我的订单', type: 'success' })
    router.push('/orders')
  } catch (e) { /* 拦截器已提示 */ } finally { paying.value = false }
}

async function loadNearby(type) {
  nearbyType.value = type
  nearbyLoading.value = true
  try {
    nearbyList.value = await api.nearby(a.value.id, type)
  } catch (e) { nearbyList.value = [] } finally { nearbyLoading.value = false }
}

onMounted(async () => {
  try {
    a.value = await api.attractionDetail(route.params.id)
    // 默认游玩日期=明天
    const t = new Date(Date.now() + 86400000)
    visitDate.value = t.toISOString().slice(0, 10)
    // 当地天气（失败静默隐藏卡片）
    api.weather(a.value.id).then(w => {
      const live = (w.lives && w.lives[0]) || {}
      const casts = (w.forecasts || []).slice(1, 4)
        .map(c => `${c.date.slice(5)} ${c.dayweather}${c.nighttemp}~${c.daytemp}℃`).join('，')
      weather.value = {
        city: live.city || '',
        text: `${live.weather || ''} ${live.temperature || ''}℃ ${live.winddirection || ''}风${live.windpower || ''}级`
              + (casts ? `｜未来：${casts}` : '')
      }
    }).catch(() => {})
    // 周边推荐（默认酒店）
    loadNearby('hotel')
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.navbar { background: rgba(255,255,255,.92); backdrop-filter: blur(8px); box-shadow: 0 1px 4px rgba(0,0,0,.06); }
.nav-inner { max-width: 980px; margin: 0 auto; display: flex; align-items: center; padding: 12px 16px; }
.logo { font-size: 20px; font-weight: 700; cursor: pointer;
  background: linear-gradient(90deg, #1f6feb, #0aa37f); -webkit-background-clip: text; background-clip: text; color: transparent; }
.spacer { flex: 1; }

.detail { max-width: 980px; margin: 20px auto 60px; padding: 0 16px; }

/* ---------- 沉浸式头图 ---------- */
.hero-wrap { position: relative; border-radius: 16px; overflow: hidden; height: 400px;
  box-shadow: 0 8px 30px rgba(15,52,96,.18); }
.hero { width: 100%; height: 100%; object-fit: cover; display: block; }
.hero-mask { position: absolute; inset: 0;
  background: linear-gradient(180deg, rgba(6,32,58,.05) 30%, rgba(6,32,58,.78) 100%); }
.hero-caption { position: absolute; left: 26px; right: 26px; bottom: 20px; color: #fff; }
.hero-title-row { display: flex; align-items: center; gap: 14px; }
.hero-title-row h1 { font-size: 32px; letter-spacing: 1px; text-shadow: 0 2px 14px rgba(0,0,0,.45); }
.hero-chips { display: flex; gap: 10px; margin-top: 12px; flex-wrap: wrap; }
.chip { background: rgba(255,255,255,.18); backdrop-filter: blur(6px); border: 1px solid rgba(255,255,255,.35);
  border-radius: 999px; padding: 4px 14px; font-size: 13px; }

/* ---------- 内容区 ---------- */
.content { background: #fff; border-radius: 16px; padding: 26px; margin-top: 18px;
  box-shadow: 0 2px 14px rgba(15,52,96,.08); }

.info-cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }
.info-card { display: flex; gap: 12px; align-items: flex-start; background: #f6f9fc;
  border-radius: 12px; padding: 14px 16px; }
.info-icon { font-size: 24px; line-height: 1.2; }
.info-label { color: #8a97a6; font-size: 12px; margin-bottom: 4px; }
.info-value { color: #1c2b3a; font-size: 14px; font-weight: 600; line-height: 1.55; }
.price { color: #ff6700; font-size: 17px; font-weight: 700; }

.intro { margin-top: 26px; }
.intro h3, .booking h3 { font-size: 18px; color: #1c2b3a; display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.intro h3 i, .booking h3 i { display: inline-block; width: 5px; height: 18px; border-radius: 3px;
  background: linear-gradient(180deg, #1f6feb, #0aa37f); }
.intro p { line-height: 2; color: #4e5d6c; background: #fafcfe; border-radius: 12px; padding: 18px 20px; }

/* ---------- 门票预订 ---------- */
.booking { margin-top: 26px; }
.booking-row { display: flex; align-items: flex-end; gap: 22px; flex-wrap: wrap;
  background: #fafcfe; border-radius: 12px; padding: 18px 20px; }
.booking-label { color: #8a97a6; font-size: 12px; margin-bottom: 6px; }
.booking-total { margin-left: auto; text-align: right; }
.total-price { color: #ff6700; font-size: 26px; font-weight: 700; line-height: 1.2; }
.booking-tip { color: #b0b8c1; font-size: 12px; margin-top: 10px; }

/* ---------- 周边推荐 ---------- */
.nearby { margin-top: 26px; }
.nearby h3 { font-size: 18px; color: #1c2b3a; display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.nearby h3 i { display: inline-block; width: 5px; height: 18px; border-radius: 3px;
  background: linear-gradient(180deg, #1f6feb, #0aa37f); }
.nearby-sub { font-size: 12px; color: #b0b8c1; font-weight: 400; }
.nearby-tabs { margin-bottom: 10px; display: flex; gap: 8px; }
.poi-row { display: flex; align-items: center; gap: 14px; padding: 10px 14px;
  border-bottom: 1px dashed #e8eaf0; }
.poi-row:last-child { border-bottom: none; }
.poi-dist { color: #1f6feb; font-size: 13px; font-weight: 600; width: 70px; flex-shrink: 0; }
.poi-main { flex: 1; min-width: 0; }
.poi-name { font-size: 14px; color: #1c2b3a; font-weight: 500; }
.poi-addr { font-size: 12px; color: #8a97a6; margin-top: 2px; }
.poi-tel { color: #8a97a6; font-size: 12px; flex-shrink: 0; }

/* ---------- 支付弹窗 ---------- */
.pay-body { text-align: left; }
.pay-row { display: flex; justify-content: space-between; gap: 12px; padding: 8px 0;
  border-bottom: 1px dashed #e8eaf0; font-size: 13px; color: #606266; }
.pay-row b { color: #1c2b3a; font-weight: 500; text-align: right; }
.pay-amount { text-align: center; font-size: 32px; font-weight: 700; color: #ff6700; margin: 18px 0 6px; }
.pay-qr { display: flex; flex-direction: column; align-items: center; gap: 8px;
  margin: 14px 0 4px; padding: 14px; background: #fff; border: 1px solid #e8eaf0; border-radius: 10px; }
.pay-qr img { width: 170px; height: 170px; image-rendering: pixelated; }
.pay-qr span { font-size: 13px; color: #4e5d6c; text-align: center; }
.pay-qr .qr-sub { display: block; font-size: 11px; color: #b0b8c1; margin-top: 2px; }
.pay-tip { text-align: center; color: #b0b8c1; font-size: 12px; }

.back-row { margin-top: 24px; text-align: center; }
</style>
