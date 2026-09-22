<template>
  <div>
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="按用户名查询" clearable style="width: 220px"
                @keyup.enter="load" @clear="load" />
      <el-button type="primary" @click="load">查询</el-button>
    </div>

    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="nickname" label="昵称" />
      <el-table-column prop="role" label="角色" width="90" align="center" />
      <el-table-column prop="createdAt" label="注册时间" width="180">
        <template #default="{ row }">{{ (row.createdAt || '').replace('T', ' ').slice(0, 19) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="110">
        <template #default="{ row }">
          <el-button v-if="row.status === 1" link type="danger" @click="toggle(row, 0)">禁用</el-button>
          <el-button v-else link type="success" @click="toggle(row, 1)">启用</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination background layout="prev, pager, next, total" :total="total" :page-size="query.size"
                     :current-page="query.page" @current-change="(p) => { query.page = p; load() }" />
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../../api'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, keyword: '' })

async function load() {
  loading.value = true
  try {
    const data = await api.users(query)
    list.value = data.list
    total.value = Number(data.total)
  } finally { loading.value = false }
}

function toggle(row, status) {
  const action = status === 0 ? '禁用' : '启用'
  ElMessageBox.confirm(`确认${action}用户「${row.username}」吗？${status === 0 ? '禁用后该用户将不能登录前台。' : ''}`, '操作确认', { type: 'warning' })
    .then(async () => { await api.changeUserStatus(row.id, status); ElMessage.success(`${action}成功`); load() })
    .catch(() => {})
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; margin-bottom: 16px; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
