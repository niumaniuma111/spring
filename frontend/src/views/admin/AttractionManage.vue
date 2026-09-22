<template>
  <div>
    <!-- 条件查询栏 -->
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="按景点名称查询" clearable style="width: 200px" @keyup.enter="load" @clear="load" />
      <el-select v-model="query.provinceId" placeholder="所属省份" clearable style="width: 140px" @change="onProvinceChange">
        <el-option v-for="p in provinces" :key="p.id" :label="p.name" :value="p.id" />
      </el-select>
      <el-select v-model="query.cityId" placeholder="所属城市" clearable style="width: 140px" :disabled="!query.provinceId" @change="load">
        <el-option v-for="c in cities" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-select v-model="query.level" placeholder="景点等级" clearable style="width: 120px" @change="load">
        <el-option v-for="lv in levels" :key="lv" :label="lv" :value="lv" />
      </el-select>
      <el-button type="primary" @click="load">查询</el-button>
      <div class="spacer"></div>
      <el-button type="success" @click="openAdd">＋ 新增景点</el-button>
    </div>

    <!-- 列表 -->
    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column label="图片" width="90">
        <template #default="{ row }"><img :src="row.image" style="width: 64px; height: 44px; object-fit: cover; border-radius: 4px" /></template>
      </el-table-column>
      <el-table-column prop="name" label="景点名称" min-width="160" />
      <el-table-column label="所属省市" width="120">
        <template #default="{ row }">{{ provinceName(row.provinceId) }} / {{ cityName(row.cityId) }}</template>
      </el-table-column>
      <el-table-column prop="level" label="等级" width="70" align="center">
        <template #default="{ row }"><el-tag :type="levelTagType(row.level)" size="small" effect="dark">{{ row.level }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="ticketPrice" label="门票" width="90" align="right">
        <template #default="{ row }">{{ row.ticketPrice > 0 ? '¥' + row.ticketPrice : '免费' }}</template>
      </el-table-column>
      <el-table-column prop="rating" label="评分" width="70" align="center" />
      <el-table-column prop="views" label="浏览" width="80" align="center" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">修改</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination background layout="prev, pager, next, total" :total="total" :page-size="query.size"
                     :current-page="query.page" @current-change="(p) => { query.page = p; load() }" />
    </div>

    <!-- 新增/修改弹窗 -->
    <el-dialog v-model="dialog.visible" :title="dialog.isEdit ? '修改景点' : '新增景点'" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="景点名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="所属省份" prop="provinceId">
          <el-select v-model="form.provinceId" style="width: 200px" @change="onFormProvinceChange">
            <el-option v-for="p in provinces" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属城市" prop="cityId">
          <el-select v-model="form.cityId" style="width: 200px">
            <el-option v-for="c in formCities" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="景点等级" prop="level">
          <el-select v-model="form.level" style="width: 200px">
            <el-option v-for="lv in levels" :key="lv" :label="lv" :value="lv" />
          </el-select>
        </el-form-item>
        <el-form-item label="景点图片" prop="image">
          <el-select v-model="form.image" style="width: 200px">
            <el-option v-for="i in 6" :key="i" :label="'示例图 ' + i" :value="'/img/scenic' + i + '.svg'" />
          </el-select>
          <img v-if="form.image" :src="form.image" style="margin-left: 12px; width: 90px; height: 60px; object-fit: cover; border-radius: 4px" />
        </el-form-item>
        <el-form-item label="景点介绍" prop="description"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="详细地址" prop="address"><el-input v-model="form.address" /></el-form-item>
        <el-form-item label="开放时间" prop="openTime"><el-input v-model="form.openTime" /></el-form-item>
        <el-form-item label="门票价格" prop="ticketPrice">
          <el-input-number v-model="form.ticketPrice" :min="0" :max="9999" :precision="2" />
          <span style="margin-left: 8px; color: #999">元（0 表示免费）</span>
        </el-form-item>
        <el-form-item label="综合评分" prop="rating">
          <el-input-number v-model="form.rating" :min="0" :max="5" :precision="1" :step="0.1" />
        </el-form-item>
      </el-form>
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

const levels = ['5A', '4A', '3A', '2A', '1A']
const loading = ref(false)
const list = ref([])
const total = ref(0)
const provinces = ref([])
const cities = ref([])
const formCities = ref([])
const formRef = ref()

const query = reactive({ page: 1, size: 10, keyword: '', provinceId: null, cityId: null, level: '' })
const dialog = reactive({ visible: false, isEdit: false })
const emptyForm = () => ({ id: null, name: '', provinceId: null, cityId: null, level: '', image: '/img/scenic1.svg',
  description: '', address: '', openTime: '', ticketPrice: 0, rating: 4.0 })
const form = reactive(emptyForm())

const rules = {
  name: [{ required: true, message: '请输入景点名称', trigger: 'blur' }],
  provinceId: [{ required: true, message: '请选择省份', trigger: 'change' }],
  cityId: [{ required: true, message: '请选择城市', trigger: 'change' }],
  level: [{ required: true, message: '请选择等级', trigger: 'change' }]
}

const provinceName = (id) => provinces.value.find(p => p.id === id)?.name || id
const cityName = (id) => allCities.value.find(c => c.id === id)?.name || id
const allCities = ref([])

async function load() {
  loading.value = true
  try {
    const data = await api.adminAttractions(query)
    list.value = data.list
    total.value = Number(data.total)
  } finally { loading.value = false }
}

async function onProvinceChange(pid) {
  query.cityId = null
  cities.value = pid ? await api.cities(pid) : []
  load()
}

function openAdd() {
  dialog.isEdit = false
  Object.assign(form, emptyForm())
  formCities.value = []
  dialog.visible = true
}

async function openEdit(row) {
  dialog.isEdit = true
  const detail = await api.adminAttraction(row.id)
  Object.assign(form, detail)
  formCities.value = await api.cities(detail.provinceId)
  dialog.visible = true
}

async function onFormProvinceChange(pid) {
  form.cityId = null
  formCities.value = pid ? await api.cities(pid) : []
}

async function save() {
  await formRef.value.validate()
  if (dialog.isEdit) await api.updateAttraction(form.id, form)
  else await api.addAttraction(form)
  ElMessage.success(dialog.isEdit ? '修改成功，前台已实时更新' : '新增成功')
  dialog.visible = false
  load()
}

function remove(row) {
  ElMessageBox.confirm(`确认删除景点「${row.name}」吗？删除后前台不再展示。`, '删除确认', { type: 'warning' })
    .then(async () => { await api.deleteAttraction(row.id); ElMessage.success('删除成功'); load() })
    .catch(() => {})
}

function levelTagType(level) {
  return { '5A': 'danger', '4A': 'warning', '3A': 'success', '2A': 'info', '1A': 'info' }[level] || 'info'
}

onMounted(async () => {
  provinces.value = await api.provinces()
  for (const p of provinces.value) allCities.value.push(...(await api.cities(p.id)))
  load()
})
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; }
.spacer { flex: 1; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
