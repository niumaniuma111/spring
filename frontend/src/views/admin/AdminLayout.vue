<template>
  <el-container class="layout">
    <el-aside width="200px" class="aside">
      <div class="brand">⚙️ 景区管理后台</div>
      <el-menu :default-active="$route.path" router background-color="#232b3a" text-color="#cfd6e4"
               active-text-color="#409eff">
        <el-menu-item index="/admin/attractions">🏛️ 景点管理</el-menu-item>
        <el-menu-item index="/admin/regions">🗺️ 省市管理</el-menu-item>
        <el-menu-item index="/admin/users">👥 用户管理</el-menu-item>
        <el-menu-item index="/admin/stats">📊 数据统计</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="spacer"></div>
        <el-tag effect="plain">{{ admin.nickname }}</el-tag>
        <el-button link type="danger" @click="logout">退出</el-button>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()
const admin = JSON.parse(localStorage.getItem('user') || '{}')

function logout() {
  localStorage.removeItem('user')
  router.push('/admin/login')
}
</script>

<style scoped>
.layout { height: 100vh; }
.aside { background: #232b3a; }
.brand { color: #fff; font-weight: 700; padding: 20px 16px; font-size: 16px; }
.aside :deep(.el-menu) { border-right: none; }
.header { background: #fff; display: flex; align-items: center; gap: 12px; box-shadow: 0 1px 4px rgba(0,0,0,.08); }
.spacer { flex: 1; }
.main { background: #f5f7fa; }
</style>
