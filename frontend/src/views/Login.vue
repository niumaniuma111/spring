<template>
  <div class="auth-wrap">
    <el-card class="auth-card">
      <h2 class="title">🏞️ 游遍中国 · 登录</h2>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="登录密码" :prefix-icon="Lock"
                    @keyup.enter="submit" />
        </el-form-item>
        <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="submit">登 录</el-button>
        <div class="tip">
          还没有账号？<el-button link type="primary" @click="$router.push('/register')">去注册</el-button>
          <span class="hint">登录后才能浏览景点内容</span>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import api from '../api'

const router = useRouter()
const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    const data = await api.login(form)
    localStorage.setItem('user', JSON.stringify(data))
    ElMessage.success('登录成功，欢迎回来！')
    router.push('/')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-wrap { min-height: 100vh; display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #e0f2ff 0%, #e8fff5 100%); }
.auth-card { width: 400px; padding: 12px 8px; }
.title { text-align: center; margin-bottom: 24px; color: #1f6feb; }
.tip { text-align: center; margin-top: 14px; color: #888; }
.hint { margin-left: 8px; font-size: 12px; color: #aaa; }
</style>
