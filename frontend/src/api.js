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
  stats: () => api.get('/admin/stats')
}
