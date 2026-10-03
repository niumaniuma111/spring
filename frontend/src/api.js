import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from './router'

const api = axios.create({ baseURL: '/api', timeout: 15000 })

api.interceptors.request.use((cfg) => {
  const user = JSON.parse(localStorage.getItem('user') || 'null')
  if (user && user.token) cfg.headers.Authorization = 'Bearer ' + user.token
  return cfg
})

api.interceptors.response.use(
  (resp) => {
    const body = resp.data
    if (body.code !== 0) {
      if (body.code === 401 || body.code === 403) {
        ElMessage.error(body.msg || '请先登录')
        const path = router.currentRoute.value.path
        if (path.startsWith('/admin')) router.push('/admin/login')
        else if (body.code === 401 && path !== '/login') router.push('/login')
      } else {
        ElMessage.error(body.msg || '请求失败')
      }
      return Promise.reject(new Error(body.msg))
    }
    return body.data
  },
  (err) => {
    ElMessage.error('网络异常，请稍后重试')
    return Promise.reject(err)
  }
)

export default {
  // ---- 游客端 ----
  register: (data) => api.post('/auth/register', data),
  login: (data) => api.post('/auth/login', data),
  checkUsername: (username) => api.get('/auth/check-username', { params: { username } }),
  attractions: (params) => api.get('/attractions', { params }),
  attractionDetail: (id) => api.get(`/attractions/${id}`),
  provinces: () => api.get('/provinces'),
  cities: (provinceId) => api.get('/cities', { params: { provinceId } }),

  // ---- 管理端 ----
  adminLogin: (data) => api.post('/admin/auth/login', data),
  adminAttractions: (params) => api.get('/admin/attractions', { params }),
  adminAttraction: (id) => api.get(`/admin/attractions/${id}`),
  addAttraction: (data) => api.post('/admin/attractions', data),
  updateAttraction: (id, data) => api.put(`/admin/attractions/${id}`, data),
  deleteAttraction: (id) => api.delete(`/admin/attractions/${id}`),
  regionTree: () => api.get('/admin/regions'),
  addProvince: (data) => api.post('/admin/regions/provinces', data),
  updateProvince: (id, data) => api.put(`/admin/regions/provinces/${id}`, data),
  deleteProvince: (id) => api.delete(`/admin/regions/provinces/${id}`),
  addCity: (data) => api.post('/admin/regions/cities', data),
  updateCity: (id, data) => api.put(`/admin/regions/cities/${id}`, data),
  deleteCity: (id) => api.delete(`/admin/regions/cities/${id}`),
  users: (params) => api.get('/admin/users', { params }),
  changeUserStatus: (id, status) => api.put(`/admin/users/${id}/status`, null, { params: { status } }),
  stats: () => api.get('/admin/stats'),

  // ---- 后台订单管理 ----
  adminOrders: (params) => api.get('/admin/orders', { params }),
  adminOrderStats: () => api.get('/admin/orders/stats'),
  adminGeocode: (address, city) => api.get('/admin/amap/geocode', { params: { address, city } }),
  adminRegeo: (lng, lat) => api.get('/admin/amap/regeo', { params: { lng, lat } }),
  nearby: (attractionId, type) => api.get('/amap/nearby', { params: { attractionId, type } }),

  // ---- AI 行程助手（会话管理走 axios；聊天走 fetch 流式，axios 不支持流式读取） ----
  createSession: (title) => api.post('/assistant/sessions', title ? { title } : {}),
  sessions: () => api.get('/assistant/sessions'),
  sessionMessages: (id) => api.get(`/assistant/sessions/${id}/messages`),
  deleteSession: (id) => api.delete(`/assistant/sessions/${id}`),

  // ---- 门票订单 ----
  createOrder: (data) => api.post('/orders', data),
  orders: (params) => api.get('/orders', { params }),
  orderDetail: (id) => api.get(`/orders/${id}`),
  payOrder: (id) => api.post(`/orders/${id}/pay`),
  cancelOrder: (id) => api.post(`/orders/${id}/cancel`),

  // ---- 高德地图 ----
  amapConfig: () => api.get('/amap/config'),
  mapPoints: () => api.get('/attractions/map'),
  weather: (attractionId) => api.get('/amap/weather', { params: { attractionId } })
}

/**
 * AI 聊天（SSE 流式）：POST + fetch ReadableStream，按 \n\n 切分事件、半包缓冲。
 * 回调：onDelta(增量文本) / onDone(messageId) / onError(错误信息)
 */
export function chatStream(sessionId, message, { onDelta, onDone, onError }) {
  const user = JSON.parse(localStorage.getItem('user') || 'null')
  return fetch('/api/assistant/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer ' + (user && user.token ? user.token : '')
    },
    body: JSON.stringify({ sessionId, message })
  }).then(async (resp) => {
    const ctype = resp.headers.get('content-type') || ''
    // 非 SSE 响应（如 401/403/参数错误返回 JSON Result）→ 统一按错误处理
    if (!ctype.includes('text/event-stream')) {
      let msg = '请求失败'
      try { const b = await resp.json(); msg = b.msg || msg } catch (ignore) {}
      onError && onError(msg)
      return
    }
    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buf = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buf += decoder.decode(value, { stream: true })
      const events = buf.split('\n\n')
      buf = events.pop()                       // 半包留到下一轮
      for (const ev of events) {
        if (!ev.trim()) continue
        const name = (ev.match(/event:\s*(\w+)/) || [])[1]
        const dataRaw = (ev.match(/data:\s*([\s\S]*)/) || [])[1] || '{}'
        let data = {}
        try { data = JSON.parse(dataRaw) } catch (ignore) {}
        if (name === 'delta') onDelta && onDelta(data.content || '')
        else if (name === 'done') onDone && onDone(data.messageId)
        else if (name === 'error') onError && onError(data.message || 'AI 服务异常')
      }
    }
  }).catch(() => onError && onError('网络异常，请稍后重试'))
}
