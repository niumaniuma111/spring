<template>
  <div class="page">
    <!-- 顶部导航 -->
    <header class="navbar">
      <div class="nav-inner">
        <div class="logo" @click="$router.push('/')">🏞️ <span>游遍中国</span></div>
        <div class="spacer"></div>
        <template v-if="user">
          <el-tag type="success" effect="plain" round>您好，{{ user.nickname }}</el-tag>
          <el-button link type="danger" @click="logout">退出登录</el-button>
        </template>
        <template v-else>
          <el-button link type="primary" @click="$router.push('/login')">登录</el-button>
          <el-button type="primary" size="small" round @click="$router.push('/register')">注册</el-button>
        </template>
      </div>
    </header>

    <!-- 沉浸式横幅 -->
    <div class="banner">
      <div class="banner-inner">
        <h1>发现中国之美</h1>
        <p>收录全国 A 级旅游景区 · 按省市筛选 · 按等级查看 · 评分热度推荐</p>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-input v-model="query.keyword" placeholder="输入景点名称关键词搜索" clearable style="width: 250px"
                @keyup.enter="load" @clear="load">
        <template #append>
          <el-button @click="load">搜索</el-button>
        </template>
      </el-input>

      <el-select v-model="query.provinceId" placeholder="选择省份" clearable style="width: 150px"
                 @change="onProvinceChange" @clear="onProvinceCleared">
        <el-option v-for="p in provinces" :key="p.id" :label="p.name" :value="p.id" />
      </el-select>

      <el-select v-model="query.cityId" placeholder="选择城市" clearable style="width: 150px"
                 :disabled="!query.provinceId" @change="load" @clear="load">
        <el-option v-for="c in cities" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>

      <el-select v-model="query.level" placeholder="景点等级" clearable style="width: 130px" @change="load">
        <el-option v-for="lv in levels" :key="lv" :label="lv + '级景区'" :value="lv" />
      </el-select>

      <div class="spacer"></div>

      <el-radio-group v-model="query.sort" @change="load">
        <el-radio-button value="">默认</el-radio-button>
        <el-radio-button value="rating">综合评分排序</el-radio-button>
        <el-radio-button value="views">浏览热度排序</el-radio-button>
      </el-radio-group>

      <el-button @click="reset">重置</el-button>
    </div>

    <!-- 景点列表 -->
    <main class="list-area" v-loading="loading">
      <el-empty v-if="!loading && list.length === 0" description="没有找到符合条件的景点" />
      <div class="grid">
        <div v-for="a in list" :key="a.id" class="card" @click="$router.push('/detail/' + a.id)">
          <div class="img-wrap">
            <img :src="a.image" :alt="a.name" class="card-img" loading="lazy" />
            <el-tag class="level-badge" :type="levelTagType(a.level)" effect="dark" round>{{ a.level }}</el-tag>
            <div class="img-mask"></div>
            <div class="img-location">📍 {{ a.provinceName }} · {{ a.cityName }}</div>
          </div>
          <div class="card-body">
            <div class="card-title">
              <span class="name">{{ a.name }}</span>
            </div>
            <div class="card-bottom">
              <span class="rating">★ {{ a.rating }}<i>评分</i></span>
              <span class="views">🔥 {{ a.views }}<i>浏览</i></span>
              <span class="price">{{ a.ticketPrice > 0 ? '¥' + a.ticketPrice + ' 起' : '免费' }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <div class="pager">
        <el-pagination background layout="prev, pager, next, total" :total="total"
                       :page-size="query.size" :current-page="query.page"
                       @current-change="onPageChange" />
      </div>
    </main>

    <footer class="footer">游遍中国 · 生产实习作品 · SpringBoot + Vue 前后端分离</footer>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api'

const router = useRouter()
const user = JSON.parse(localStorage.getItem('user') || 'null')

const levels = ['5A', '4A', '3A', '2A', '1A']
const loading = ref(false)
const list = ref([])
const total = ref(0)
const provinces = ref([])
const cities = ref([])
const query = reactive({ page: 1, size: 6, keyword: '', provinceId: null, cityId: null, level: '', sort: '' })

async function load() {
  loading.value = true
  try {
    const data = await api.attractions(query)
    list.value = data.list
    total.value = Number(data.total)
  } finally {
    loading.value = false
  }
}

function onPageChange(page) { query.page = page; load() }

async function onProvinceChange(pid) {
  query.cityId = null
  if (pid) cities.value = await api.cities(pid)
  else cities.value = []
  query.page = 1
  load()
}
function onProvinceCleared() { query.cityId = null; cities.value = []; query.page = 1; load() }

function reset() {
  Object.assign(query, { page: 1, keyword: '', provinceId: null, cityId: null, level: '', sort: '' })
  cities.value = []
  load()
}

function levelTagType(level) {
  return { '5A': 'danger', '4A': 'warning', '3A': 'success', '2A': 'info', '1A': 'info' }[level] || 'info'
}

function logout() {
  localStorage.removeItem('user')
  user.value = null
  router.go(0)
}

onMounted(async () => {
  provinces.value = await api.provinces()
  load()
})
</script>

<style scoped>
/* ---------- 导航 ---------- */
.navbar { background: rgba(255,255,255,.92); backdrop-filter: blur(8px); box-shadow: 0 1px 4px rgba(0,0,0,.06);
  position: sticky; top: 0; z-index: 10; }
.nav-inner { max-width: 1120px; margin: 0 auto; display: flex; align-items: center; gap: 12px; padding: 12px 16px; }
.logo { font-size: 20px; font-weight: 700; cursor: pointer;
  background: linear-gradient(90deg, #1f6feb, #0aa37f); -webkit-background-clip: text; background-clip: text; color: transparent; }
.spacer { flex: 1; }

/* ---------- 沉浸式横幅 ---------- */
.banner { position: relative; min-height: 300px; display: flex; align-items: center;
  background: url('/img/scenic/a20.jpg') center 62% / cover no-repeat; }
.banner::before { content: ''; position: absolute; inset: 0;
  background: linear-gradient(180deg, rgba(6,32,58,.30) 0%, rgba(6,32,58,.62) 100%); }
.banner-inner { position: relative; max-width: 1120px; margin: 0 auto; padding: 0 16px; width: 100%; color: #fff; }
.banner h1 { font-size: 42px; letter-spacing: 2px; margin-bottom: 10px; text-shadow: 0 2px 14px rgba(0,0,0,.45); }
.banner p { font-size: 15px; opacity: .92; text-shadow: 0 1px 8px rgba(0,0,0,.5); }

/* ---------- 筛选栏 ---------- */
.filter-bar { max-width: 1120px; margin: -34px auto 8px; padding: 16px 18px; background: #fff; border-radius: 14px;
  display: flex; flex-wrap: wrap; gap: 12px; align-items: center; position: relative; z-index: 5;
  box-shadow: 0 6px 22px rgba(15,52,96,.10); }

/* ---------- 卡片网格 ---------- */
.list-area { max-width: 1120px; margin: 0 auto 40px; padding: 16px 16px 0; }
.grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 20px; }
.card { background: #fff; border-radius: 14px; overflow: hidden; cursor: pointer;
  transition: transform .25s ease, box-shadow .25s ease; box-shadow: 0 2px 10px rgba(15,52,96,.08); }
.card:hover { transform: translateY(-6px); box-shadow: 0 12px 28px rgba(15,52,96,.16); }
.img-wrap { position: relative; height: 200px; overflow: hidden; }
.card-img { width: 100%; height: 100%; object-fit: cover; display: block; transition: transform .5s ease; }
.card:hover .card-img { transform: scale(1.07); }
.img-mask { position: absolute; inset: 0;
  background: linear-gradient(180deg, rgba(0,0,0,.18) 0%, rgba(0,0,0,0) 34%, rgba(0,0,0,.52) 100%); }
.level-badge { position: absolute; top: 10px; left: 10px; z-index: 2; font-weight: 700; letter-spacing: 1px; }
.img-location { position: absolute; left: 12px; bottom: 9px; z-index: 2; color: #fff; font-size: 13px;
  text-shadow: 0 1px 6px rgba(0,0,0,.55); }
.card-body { padding: 13px 15px 14px; }
.card-title .name { font-weight: 600; font-size: 16px; color: #1c2b3a; }
.card-bottom { display: flex; align-items: baseline; justify-content: space-between; margin-top: 10px; }
.rating { color: #ff9800; font-weight: 700; font-size: 15px; }
.views { color: #8a97a6; font-size: 14px; }
.price { color: #ff6700; font-weight: 700; font-size: 14px; }
.rating i, .views i { font-style: normal; font-size: 12px; font-weight: 400; color: #a8b4c0; margin-left: 3px; }
.pager { display: flex; justify-content: center; margin-top: 26px; }

/* ---------- 页脚 ---------- */
.footer { text-align: center; color: #9aa7b4; font-size: 13px; padding: 26px 0 30px;
  border-top: 1px solid #eef1f4; }
</style>
