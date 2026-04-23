<template>
  <el-container class="layout">
    <el-aside :width="collapse ? '64px' : '220px'" class="aside">
      <div class="logo">
        <span v-if="!collapse">{{ brandTitle }}</span>
        <span v-else>YN</span>
      </div>
      <el-scrollbar>
        <el-menu :default-active="activePath" :default-openeds="defaultOpeneds" :collapse="collapse" router
                 background-color="#1e3c72" text-color="#cfd8e6" active-text-color="#fff" :collapse-transition="false">
          <template v-for="item in menu" :key="item.path || item.title">
            <!-- group with children -->
            <el-sub-menu v-if="item.children && item.children.length" :index="item.title">
              <template #title>
                <el-icon><component :is="item.icon" /></el-icon>
                <span>{{ item.title }}</span>
              </template>
              <el-menu-item v-for="c in item.children" :key="c.path" :index="c.path">
                <el-icon><component :is="c.icon" /></el-icon>
                <template #title>{{ c.name }}</template>
              </el-menu-item>
            </el-sub-menu>
            <!-- leaf -->
            <el-menu-item v-else :index="item.path">
              <el-icon><component :is="item.icon" /></el-icon>
              <template #title>{{ item.name }}</template>
            </el-menu-item>
          </template>
        </el-menu>
      </el-scrollbar>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="collapse = !collapse"><Fold v-if="!collapse" /><Expand v-else /></el-icon>
          <span class="page-title">{{ pageTitle }}</span>
        </div>
        <div class="header-right">
          <el-tag :type="roleTagType" effect="dark" round>{{ roleLabel }}</el-tag>
          <el-dropdown @command="onCommand">
            <span class="user-info">
              <el-icon><UserFilled /></el-icon>
              {{ userStore.info?.realName || userStore.info?.username }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout" :icon="SwitchButton">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main"><router-view /></el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessageBox, ElMessage } from 'element-plus'
import { SwitchButton } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const collapse = ref(false)

const ROLE_MENUS = {
  enterprise: {
    title: '企业端',
    tag: 'success',
    label: '企业用户',
    items: [
      { path: '/enterprise/home',     name: '工作台',     icon: 'House' },
      { path: '/enterprise/filing',   name: '企业备案',   icon: 'Document' },
      { path: '/enterprise/report',   name: '数据填报',   icon: 'EditPen' },
      { path: '/enterprise/history',  name: '历史查询',   icon: 'Tickets' },
      { path: '/enterprise/notice',   name: '通知浏览',   icon: 'Bell' },
      { path: '/enterprise/password', name: '修改密码',   icon: 'Lock' },
    ],
  },
  city: {
    title: '市级管理',
    tag: 'warning',
    label: '市级用户',
    items: [
      { path: '/city/home',     name: '工作台',   icon: 'House' },
      { path: '/city/review',   name: '数据审核', icon: 'DocumentChecked' },
      { path: '/city/notice',   name: '通知发布', icon: 'Bell' },
      { path: '/city/password', name: '修改密码', icon: 'Lock' },
    ],
  },
  province: {
    title: '省级管理',
    tag: 'danger',
    label: '省级用户',
    items: [
      { path: '/province/home', name: '工作台', icon: 'House' },
      { title: '业务管理', icon: 'Files', children: [
        { path: '/province/filing-audit', name: '备案审核', icon: 'DocumentChecked' },
        { path: '/province/report',       name: '报表管理', icon: 'Document' },
      ]},
      { title: '汇总分析', icon: 'DataAnalysis', children: [
        { path: '/province/aggregation', name: '数据汇总', icon: 'DataAnalysis' },
        { path: '/province/sampling',    name: '取样分析', icon: 'PieChart' },
        { path: '/province/multidim',    name: '多维分析', icon: 'Grid' },
        { path: '/province/compare',     name: '对比分析', icon: 'TrendCharts' },
        { path: '/province/trend',       name: '趋势分析', icon: 'DataLine' },
      ]},
      { title: '系统管理', icon: 'Setting', children: [
        { path: '/province/period',  name: '调查期管理', icon: 'Calendar' },
        { path: '/province/user',    name: '用户管理',   icon: 'User' },
        { path: '/province/role',    name: '角色管理',   icon: 'UserFilled' },
        { path: '/province/monitor', name: '系统监控',   icon: 'Monitor' },
        { path: '/province/log',     name: '操作日志',   icon: 'List' },
      ]},
      { title: '通知与上报', icon: 'Connection', children: [
        { path: '/province/notice',  name: '通知发布', icon: 'Bell' },
        { path: '/province/nation',  name: '国家接口', icon: 'Promotion' },
      ]},
      { path: '/province/password', name: '修改密码', icon: 'Lock' },
    ],
  },
}

const cfg = computed(() => ROLE_MENUS[userStore.userType] || ROLE_MENUS.enterprise)
const brandTitle = computed(() => cfg.value.title)
const roleTagType = computed(() => cfg.value.tag)
const roleLabel = computed(() => cfg.value.label)
const menu = computed(() => cfg.value.items)
const activePath = computed(() => route.path)
// Open all groups by default so submenu items are reachable in one click
const defaultOpeneds = computed(() => menu.value.filter(m => m.children).map(m => m.title))

const pageTitle = computed(() => {
  for (const m of menu.value) {
    if (m.path === route.path) return m.name
    if (m.children) {
      const found = m.children.find(c => c.path === route.path)
      if (found) return found.name
    }
  }
  return '工作台'
})

async function onCommand(cmd) {
  if (cmd === 'logout') {
    try {
      await ElMessageBox.confirm('确定退出系统吗？', '提示', { type: 'warning' })
      userStore.logout()
      ElMessage.success('已退出')
      router.replace('/login')
    } catch {}
  }
}
</script>

<style scoped>
.layout { height: 100vh; }
.aside { background: #1e3c72; transition: width .25s; overflow: hidden; }
.aside :deep(.el-scrollbar) { height: calc(100vh - 56px); }
.logo { height: 56px; display: flex; align-items: center; justify-content: center; color: #fff; font-weight: 600; letter-spacing: 1px; background: #15305c; }
.aside :deep(.el-menu) { border-right: none; }
.header { background: #fff; border-bottom: 1px solid #e8e8e8; display: flex; align-items: center; justify-content: space-between; }
.header-left { display: flex; align-items: center; gap: 18px; }
.collapse-btn { font-size: 20px; cursor: pointer; color: #555; }
.page-title { font-size: 16px; color: #333; font-weight: 500; }
.header-right { display: flex; align-items: center; gap: 16px; }
.user-info { display: flex; align-items: center; gap: 6px; cursor: pointer; color: #333; }
.main { background: #f0f2f5; padding: 20px; }
</style>
