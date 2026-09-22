<template>
  <div class="page" v-loading="loading">
    <header class="navbar">
      <div class="nav-inner">
        <div class="logo" @click="$router.push('/')">🏞️ <span>游遍中国</span></div>
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
        </div>

        <!-- 景点介绍 -->
        <section class="intro">
          <h3><i></i>景点介绍</h3>
          <p>{{ a.description }}</p>
        </section>

        <div class="back-row">
          <el-button type="primary" round plain @click="$router.push('/')">← 返回景点列表</el-button>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '../api'

const route = useRoute()
const a = ref(null)
const loading = ref(true)

function levelTagType(level) {
  return { '5A': 'danger', '4A': 'warning', '3A': 'success', '2A': 'info', '1A': 'info' }[level] || 'info'
}

onMounted(async () => {
  try {
    a.value = await api.attractionDetail(route.params.id)
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
.intro h3 { font-size: 18px; color: #1c2b3a; display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.intro h3 i { display: inline-block; width: 5px; height: 18px; border-radius: 3px;
  background: linear-gradient(180deg, #1f6feb, #0aa37f); }
.intro p { line-height: 2; color: #4e5d6c; background: #fafcfe; border-radius: 12px; padding: 18px 20px; }

.back-row { margin-top: 24px; text-align: center; }
</style>
