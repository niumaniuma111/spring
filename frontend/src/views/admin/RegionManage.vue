<template>
  <div v-loading="loading">
    <div class="toolbar">
      <el-button type="success" @click="openProvince()">＋ 新增省份</el-button>
      <el-button type="primary" :disabled="!selectedProvince" @click="openCity()">＋ 新增城市</el-button>
    </div>

    <el-row :gutter="16">
      <!-- 省份列表 -->
      <el-col :span="8">
        <el-card header="省份列表（点击查看城市）">
          <el-table :data="tree" highlight-current-row @current-change="(row) => (selectedProvince = row)" size="small">
            <el-table-column prop="name" label="省份" />
            <el-table-column label="城市数" width="80" align="center">
              <template #default="{ row }">{{ row.cities.length }}</template>
            </el-table-column>
            <el-table-column label="操作" width="130">
              <template #default="{ row }">
                <el-button link type="primary" @click.stop="openProvince(row)">修改</el-button>
                <el-button link type="danger" @click.stop="removeProvince(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 城市列表 -->
      <el-col :span="16">
        <el-card :header="selectedProvince ? `${selectedProvince.name} · 下属城市` : '请先在左侧选择省份'">
          <el-empty v-if="!selectedProvince" description="请选择省份" :image-size="60" />
          <el-table v-else :data="selectedProvince.cities" size="small">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="name" label="城市" />
            <el-table-column label="操作" width="130">
              <template #default="{ row }">
                <el-button link type="primary" @click="openCity(row)">修改</el-button>
                <el-button link type="danger" @click="removeCity(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="dialog.visible" :title="dialog.title" width="380px" destroy-on-close>
      <el-input v-model="dialog.name" placeholder="名称" />
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../../api'

const loading = ref(false)
const tree = ref([])
const selectedProvince = ref(null)
const dialog = reactive({ visible: false, title: '', type: '', id: null, name: '' })

async function load() {
  loading.value = true
  try {
    tree.value = await api.regionTree()
    // 刷新后保持选中项指向新数据
    if (selectedProvince.value) {
      selectedProvince.value = tree.value.find(p => p.id === selectedProvince.value.id) || null
    }
  } finally { loading.value = false }
}

function openProvince(row) {
  dialog.type = 'province'
  dialog.id = row?.id || null
  dialog.name = row?.name || ''
  dialog.title = row ? '修改省份' : '新增省份'
  dialog.visible = true
}

function openCity(row) {
  dialog.type = 'city'
  dialog.id = row?.id || null
  dialog.name = row?.name || ''
  dialog.title = row ? '修改城市' : `新增城市（${selectedProvince.value?.name}）`
  dialog.visible = true
}

async function save() {
  if (!dialog.name.trim()) return ElMessage.warning('名称不能为空')
  if (dialog.type === 'province') {
    if (dialog.id) await api.updateProvince(dialog.id, { name: dialog.name })
    else await api.addProvince({ name: dialog.name })
  } else {
    if (dialog.id) await api.updateCity(dialog.id, { name: dialog.name })
    else await api.addCity({ name: dialog.name, provinceId: selectedProvince.value.id })
  }
  ElMessage.success('保存成功')
  dialog.visible = false
  load()
}

function removeProvince(row) {
  ElMessageBox.confirm(`确认删除省份「${row.name}」吗？若下属城市或景点数据存在将无法删除。`, '删除确认', { type: 'warning' })
    .then(async () => { await api.deleteProvince(row.id); ElMessage.success('删除成功'); if (selectedProvince.value?.id === row.id) selectedProvince.value = null; load() })
    .catch(() => {})
}

function removeCity(row) {
  ElMessageBox.confirm(`确认删除城市「${row.name}」吗？若下属景点数据存在将无法删除。`, '删除确认', { type: 'warning' })
    .then(async () => { await api.deleteCity(row.id); ElMessage.success('删除成功'); load() })
    .catch(() => {})
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; margin-bottom: 16px; }
</style>
