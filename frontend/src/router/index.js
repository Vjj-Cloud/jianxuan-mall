import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/user/Login.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/user/Register.vue'),
    meta: { title: '注册' },
  },
  {
    path: '/admin/login',
    name: 'admin-login',
    component: () => import('@/views/user/Login.vue'),
    meta: { title: '管理员登录', adminLogin: true },
  },
  {
    path: '/',
    component: () => import('@/layouts/UserLayout.vue'),
    children: [
      { path: '', name: 'home', component: () => import('@/views/user/Home.vue'), meta: { title: '首页' } },
      { path: 'products', name: 'products', component: () => import('@/views/user/ProductList.vue'), meta: { title: '全部商品' } },
      { path: 'products/:id', name: 'product-detail', component: () => import('@/views/user/ProductDetail.vue'), meta: { title: '商品详情' } },
      { path: 'cart', name: 'cart', component: () => import('@/views/user/Cart.vue'), meta: { title: '购物车', requiresAuth: true } },
      { path: 'checkout', name: 'checkout', component: () => import('@/views/user/Checkout.vue'), meta: { title: '确认订单', requiresAuth: true } },
      { path: 'orders', name: 'orders', component: () => import('@/views/user/Orders.vue'), meta: { title: '我的订单', requiresAuth: true } },
    ],
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/admin/products',
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      { path: 'products', name: 'admin-products', component: () => import('@/views/admin/AdminProducts.vue'), meta: { title: '商品管理', requiresAuth: true, requiresAdmin: true } },
      { path: 'orders', name: 'admin-orders', component: () => import('@/views/admin/AdminOrders.vue'), meta: { title: '订单管理', requiresAuth: true, requiresAdmin: true } },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  const user = JSON.parse(localStorage.getItem('user') || 'null')

  if (to.meta.requiresAuth && !token) {
    const loginPath = to.meta.requiresAdmin ? '/admin/login' : '/login'
    return { path: loginPath, query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAdmin && user?.role !== 'ADMIN') {
    return { path: '/admin/login', query: { redirect: to.fullPath } }
  }
  if ((to.name === 'login' || to.name === 'admin-login') && token && user) {
    return user.role === 'ADMIN' ? '/admin/products' : '/'
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - 简选商城` : '简选商城'
})

export default router
