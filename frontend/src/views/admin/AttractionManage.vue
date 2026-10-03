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
        <template #default="{ row }">
          <img :src="row.image" @error="row.image = '/img/scenic/a1.jpg'"
               style="width: 64px; height: 44px; object-fit: cover; border-radius: 4px" />
        </template>
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
        <el-form-item label="景点图片">
          <div style="width: 100%">
            <div style="display: flex; align-items: center; gap: 10px; flex-wrap: wrap">
              <el-upload :action="'/api/admin/attractions/image'" name="file"
                         :headers="uploadHeaders" :show-file-list="false" accept="image/*"
                         :before-upload="beforeImgUpload" :on-success="onImgSuccess" :on-error="onImgError">
                <el-button size="small" type="primary" plain :loading="uploading">📷 上传本地图片</el-button>
              </el-upload>
              <span style="color: #b0b8c1; font-size: 12px">或选择简略图：</span>
              <img v-for="i in 6" :key="i" :src="'/img/scenic' + i + '.svg'"
                   @click="form.image = '/img/scenic' + i + '.svg'"
                   :style="{
                     width: '48px', height: '36px', objectFit: 'cover', borderRadius: '4px', cursor: 'pointer',
                     outline: form.image === '/img/scenic' + i + '.svg' ? '2px solid #1f6feb' : '1px solid #e0e4ea'
                   }" />
            </div>
            <div v-if="form.image" style="margin-top: 8px; display: flex; align-items: center; gap: 12px">
              <img :src="form.image" style="width: 90px; height: 60px; object-fit: cover; border-radius: 4px" />
              <span style="color: #999; font-size: 12px">
                {{ form.image.startsWith('/api/img/') ? '✅ 已上传本地图片' : '✅ 已选择简略图（点击缩略图可换）' }}
              </span>
            </div>
            <div v-else style="margin-top: 8px; color: #b0b8c1; font-size: 12px">
              上传或选择简略图；都不选时保存将自动分配图库随机图片
            </div>
          </div>
        </el-form-item>
        <el-form-item label="景点介绍" prop="description"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="详细地址" prop="address"><el-input v-model="form.address" placeholder="如：北京市西城区景山西街44号" /></el-form-item>
        <el-form-item label="地图定位">
          <div style="width: 100%">
            <el-button size="small" type="primary" plain :loading="locating" @click="locate">📍 按地址定位</el-button>
            <span style="margin-left: 10px; color: #999; font-size: 12px">{{ locateHint }}</span>
            <div ref="mapEl" style="margin-top: 8px; height: 280px; border-radius: 8px; overflow: hidden"></div>
          </div>
        </el-form-item>
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
import { reactive, ref, onMounted, nextTick } from 'vue'
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
const emptyForm = () => ({ id: null, name: '', provinceId: null, cityId: null, level: '', image: '',
  description: '', address: '', openTime: '', ticketPrice: 0, rating: 4.0, lng: null, lat: null })
const form = reactive(emptyForm())

// ---- 表单内地图定位/点选 ----
const mapEl = ref()
const locating = ref(false)
const mapReady = ref(false)
const locateHint = ref('点击地图任意位置选点，地址自动生成；或填地址后点"按地址定位"')
let amapObj = null, markerObj = null

async function loadAmap() {
  if (window.AMap) return window.AMap
  const cfg = await api.amapConfig()
  window._AMapSecurityConfig = { securityJsCode: cfg.securityCode }
  return new Promise((resolve, reject) => {
    const s = document.createElement('script')
    s.src = `https://webapi.amap.com/maps?v=2.0&key=${cfg.jsKey}`
    s.onload = () => resolve(window.AMap)
    s.onerror = () => reject(new Error('地图脚本加载失败'))
    document.head.appendChild(s)
  })
}

/** 初始化/复用地图（zoom 可选），并绑定"点击选点→逆地理编码→回填地址" */
async function showMapAt(lng, lat, zoom = 15) {
  const AMap = await loadAmap()
  await nextTick()
  if (!amapObj) {
    amapObj = new AMap.Map(mapEl.value, { zoom, center: [lng, lat] })
    mapReady.value = true
    amapObj.on('click', async (e) => {
      const lng2 = Number(e.lnglat.getLng().toFixed(6))
      const lat2 = Number(e.lnglat.getLat().toFixed(6))
      form.lng = lng2; form.lat = lat2
      if (markerObj) markerObj.setPosition([lng2, lat2])
      else markerObj = new AMap.Marker({ position: [lng2, lat2], map: amapObj, title: form.name })
      locateHint.value = `已选点 (${lng2}, ${lat2})，正在解析地址…`
      try {
        const g = await api.adminRegeo(lng2, lat2)
        form.address = g.address
        locateHint.value = `✅ 已选点 (${lng2}, ${lat2})，地址已自动生成，可直接修改后保存`
      } catch (err) {
        locateHint.value = `已选点 (${lng2}, ${lat2})，该位置无法解析出地址，请换个位置`
      }
    })
  } else {
    amapObj.setZoomAndCenter(zoom, [lng, lat])
  }
  if (zoom >= 15) {
    if (markerObj) markerObj.setPosition([lng, lat])
    else markerObj = new AMap.Marker({ position: [lng, lat], map: amapObj, title: form.name })
  }
}

async function locate() {
  if (!form.address) { ElMessage.warning('请先填写详细地址，或直接点击地图选点'); return }
  locating.value = true
  try {
    const city = formCities.value.find(c => c.id === form.cityId)
    const g = await api.adminGeocode(form.address, city?.name || '')
    form.lng = g.lng; form.lat = g.lat
    await showMapAt(g.lng, g.lat)
    locateHint.value = `✅ 已定位 (${g.lng}, ${g.lat})。若位置有偏差，可直接点击地图微调选点，地址会自动更新`
  } catch (e) {
    ElMessage.error(typeof e === 'string' ? e : (e.message || '定位失败，请核对地址或直接点击地图选点'))
  } finally { locating.value = false }
}

// ---- 图片上传 ----
const uploading = ref(false)
const uploadHeaders = { Authorization: 'Bearer ' + (JSON.parse(localStorage.getItem('user') || 'null')?.token || '') }

function beforeImgUpload(file) {
  if (!file.type.startsWith('image/')) { ElMessage.error('仅支持图片文件'); return false }
  if (file.size > 5 * 1024 * 1024) { ElMessage.error('图片不能超过 5MB'); return false }
  uploading.value = true
  return true
}

function onImgSuccess(resp) {
  uploading.value = false
  if (resp.code === 0 && resp.data) {
    form.image = resp.data
    ElMessage.success('图片上传成功')
  } else {
    ElMessage.error(resp.msg || '上传失败')
  }
}

function onImgError() {
  uploading.value = false
  ElMessage.error('上传失败，请重试')
}

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

async function openAdd() {
  dialog.isEdit = false
  Object.assign(form, emptyForm())
  formCities.value = []
  // 重置地图定位状态（dialog destroy-on-close 会销毁容器）
  amapObj = null; markerObj = null; mapReady.value = false
  locateHint.value = '点击地图任意位置选点，地址自动生成；或填地址后点"按地址定位"'
  dialog.visible = true
  // 新增：弹窗一打开即显示全国地图，可直接点选
  await nextTick()
  showMapAt(104.0, 35.0, 4).catch(() => {})
}

async function openEdit(row) {
  dialog.isEdit = true
  const detail = await api.adminAttraction(row.id)
  Object.assign(form, detail)
  formCities.value = await api.cities(detail.provinceId)
  amapObj = null; markerObj = null; mapReady.value = false
  dialog.visible = true
  // 已有坐标的景点：编辑时自动在地图上标出当前位置
  if (detail.lng && detail.lat) {
    locateHint.value = `✅ 当前位置 (${detail.lng}, ${detail.lat})，点击地图可改选位置，地址会自动生成`
    showMapAt(Number(detail.lng), Number(detail.lat), 15).catch(() => {})
  } else {
    locateHint.value = '该景点暂无坐标，点击地图选点或填地址定位'
    await nextTick()
    showMapAt(104.0, 35.0, 4).catch(() => {})
  }
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
