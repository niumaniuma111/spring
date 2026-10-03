import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', name: 'list', component: () => import('./views/AttractionList.vue') },
  { path: '/login', name: 'login', component: () => import('./views/Login.vue') },
  { path: '/register', name: 'register', component: () => import('./views/Register.vue') },
  { path: '/detail/:id', name: 'detail', component: () => import('./views/AttractionDetail.vue') },
  { path: '/assistant', name: 'assistant', component: () => import('./views/AssistantChat.vue') },
  { path: '/orders', name: 'orders', component: () => import('./views/MyOrders.vue') },
  { path: '/map', name: 'map', component: () => import('./views/MapView.vue') },

  { path: '/admin/login', name: 'adminLogin', component: () => import('./views/admin/AdminLogin.vue') },
  {
    path: '/admin',
    component: () => import('./views/admin/AdminLayout.vue'),
    children: [
      { path: '', redirect: '/admin/attractions' },
      { path: 'attractions', name: 'adminAttractions', component: () => import('./views/admin/AttractionManage.vue') },
      { path: 'regions', name: 'adminRegions', component: () => import('./views/admin/RegionManage.vue') },
      { path: 'users', name: 'adminUsers', component: () => import('./views/admin/UserManage.vue') },
      { path: 'orders', name: 'adminOrders', component: () => import('./views/admin/OrderManage.vue') },
      { path: 'stats', name: 'adminStats', component: () => import('./views/admin/StatsView.vue') }
    ]
  }
]

const router = createRouter({ history: createWebHistory(), routes })

function getLocalUser() {
  return JSON.parse(localStorage.getItem('user') || 'null')
}

// 路由守卫：前台浏览（列表/详情/AI助手）与后台均需登录
router.beforeEach((to) => {
  const needLogin = to.path === '/' || to.path.startsWith('/detail') || to.path.startsWith('/assistant') || to.path.startsWith('/orders')
  if (needLogin && !getLocalUser()) return '/login'
  if (to.path.startsWith('/admin') && to.path !== '/admin/login') {
    const user = getLocalUser()
    if (!user || user.role !== 'ADMIN') return '/admin/login'
  }
})

export default router
