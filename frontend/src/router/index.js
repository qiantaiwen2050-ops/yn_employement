import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  {
    path: '/login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true },
  },
  {
    path: '/enterprise',
    component: () => import('@/layouts/RoleLayout.vue'),
    meta: { roles: ['enterprise'] },
    children: [
      { path: '',         redirect: '/enterprise/home' },
      { path: 'home',     name: 'ent-home',     component: () => import('@/views/enterprise/Home.vue') },
      { path: 'filing',   name: 'ent-filing',   component: () => import('@/views/enterprise/Filing.vue') },
      { path: 'report',   name: 'ent-report',   component: () => import('@/views/enterprise/Report.vue') },
      { path: 'history',  name: 'ent-history',  component: () => import('@/views/enterprise/History.vue') },
      { path: 'notice',   name: 'ent-notice',   component: () => import('@/views/enterprise/Notice.vue') },
      { path: 'password', name: 'ent-password', component: () => import('@/views/ChangePassword.vue') },
    ],
  },
  {
    path: '/city',
    component: () => import('@/layouts/RoleLayout.vue'),
    meta: { roles: ['city'] },
    children: [
      { path: '',         redirect: '/city/home' },
      { path: 'home',     name: 'city-home',     component: () => import('@/views/city/Home.vue') },
      { path: 'review',   name: 'city-review',   component: () => import('@/views/city/Review.vue') },
      { path: 'notice',   name: 'city-notice',   component: () => import('@/views/city/Notice.vue') },
      { path: 'password', name: 'city-password', component: () => import('@/views/ChangePassword.vue') },
    ],
  },
  {
    path: '/province',
    component: () => import('@/layouts/RoleLayout.vue'),
    meta: { roles: ['province'] },
    children: [
      { path: '',             redirect: '/province/home' },
      { path: 'home',         name: 'prov-home',         component: () => import('@/views/province/Home.vue') },
      { path: 'period',       name: 'prov-period',       component: () => import('@/views/province/Period.vue') },
      { path: 'filing-audit', name: 'prov-filing',       component: () => import('@/views/province/FilingAudit.vue') },
      { path: 'report',       name: 'prov-report',       component: () => import('@/views/province/ReportManagement.vue') },
      { path: 'aggregation',  name: 'prov-aggregation',  component: () => import('@/views/province/Aggregation.vue') },
      { path: 'sampling',     name: 'prov-sampling',     component: () => import('@/views/province/Sampling.vue') },
      { path: 'multidim',     name: 'prov-multidim',     component: () => import('@/views/province/Multidim.vue') },
      { path: 'compare',      name: 'prov-compare',      component: () => import('@/views/province/Compare.vue') },
      { path: 'trend',        name: 'prov-trend',        component: () => import('@/views/province/Trend.vue') },
      { path: 'user',         name: 'prov-user',         component: () => import('@/views/province/UserManagement.vue') },
      { path: 'role',         name: 'prov-role',         component: () => import('@/views/province/RoleManagement.vue') },
      { path: 'monitor',      name: 'prov-monitor',      component: () => import('@/views/province/SystemMonitor.vue') },
      { path: 'log',          name: 'prov-log',          component: () => import('@/views/province/OperationLog.vue') },
      { path: 'notice',       name: 'prov-notice',       component: () => import('@/views/city/Notice.vue') },
      { path: 'nation',       name: 'prov-nation',       component: () => import('@/views/province/NationApi.vue') },
      { path: 'password',     name: 'prov-password',     component: () => import('@/views/ChangePassword.vue') },
    ],
  },
  { path: '/', redirect: '/login' },
  { path: '/:pathMatch(.*)*', redirect: '/login' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const userStore = useUserStore()
  if (to.meta.public) {
    if (to.path === '/login' && userStore.isLogin) {
      return userStore.homePath
    }
    return true
  }
  if (!userStore.isLogin) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.roles && !to.meta.roles.includes(userStore.userType)) {
    return userStore.homePath
  }
  return true
})

export default router
