<!--
  App.vue - 应用根组件（布局框架）

  职责：
  1. 登录页不显示布局框架（侧边栏/Header），仅展示登录表单
  2. 已登录页面显示完整的后台布局：
     - 左侧：可折叠侧边栏（Logo + 导航菜单 + 版本号）
     - 顶部：Header（折叠按钮 + 面包屑 + Docker状态 + 时间 + 全屏 + 用户下拉菜单）
     - 中间：路由视图（带 page-fade 过渡动画）
  3. 全局修改密码弹窗

  核心功能：
  - Docker 连接状态检测（每 60 秒轮询 /api/health）
  - 当前时间显示（每 30 秒更新）
  - 全屏切换
  - 用户下拉菜单（修改密码 / 退出登录）
  - 侧边栏菜单高亮（基于当前路由路径匹配）
  - 面包屑导航（基于路由 name 动态生成）
-->
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { getHealth } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

// ===== 布局状态 =====
const isCollapse = ref(false)       // 侧边栏是否折叠
const isFullscreen = ref(false)     // 是否全屏模式
const currentTime = ref('')         // Header 显示的当前时间
const dockerStatus = ref<'checking' | 'up' | 'down'>('checking')  // Docker 连接状态
const dockerVersion = ref('')       // Docker 版本号
let timeTimer: ReturnType<typeof setInterval> | null = null    // 时间更新定时器
let healthTimer: ReturnType<typeof setInterval> | null = null  // 健康检查定时器

/** 是否为登录页（登录页不显示布局框架） */
const isLoginPage = computed(() => route.path === '/login')

/** 当前激活的菜单项（基于路由路径匹配） */
const activeMenu = computed(() => {
  const path = route.path
  if (path.startsWith('/projects')) return '/projects'
  if (path.startsWith('/containers')) return '/containers'
  if (path.startsWith('/users')) return '/users'
  return path
})

/** 面包屑导航项（根据路由 name 动态生成层级） */
const breadcrumbItems = computed(() => {
  const items: { title: string; path?: string }[] = [{ title: '首页', path: '/' }]
  const name = route.name as string
  if (name === 'projects') items.push({ title: 'Compose 项目' })
  else if (name === 'project-detail') {
    items.push({ title: 'Compose 项目', path: '/projects' })
    items.push({ title: route.params.name as string })
  } else if (name === 'container-detail') {
    items.push({ title: '容器详情' })
  } else if (name === 'users') {
    items.push({ title: '用户管理' })
  }
  return items
})

/** 侧边栏菜单点击处理 */
const handleMenuSelect = (index: string) => {
  router.push(index)
}

// ===== 修改密码弹窗 =====
const showPasswordDialog = ref(false)
const passwordForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })

/** 提交密码修改 */
const handleChangePassword = async () => {
  if (passwordForm.value.newPassword.length < 6) {
    ElMessage.warning('新密码至少6位')
    return
  }
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    ElMessage.warning('两次密码不一致')
    return
  }
  try {
    await auth.changePassword(passwordForm.value.oldPassword, passwordForm.value.newPassword)
    ElMessage.success('密码修改成功')
    showPasswordDialog.value = false
    passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error || '修改失败')
  }
}

/** 退出登录（带确认弹窗） */
const handleLogout = () => {
  ElMessageBox.confirm('确认退出登录?', '提示', { type: 'warning' }).then(() => {
    auth.logout()
    router.push('/login')
  }).catch(() => {})
}

/** 用户下拉菜单命令处理 */
const handleCommand = (command: string) => {
  if (command === 'password') showPasswordDialog.value = true
  else if (command === 'logout') handleLogout()
}

/** 切换浏览器全屏模式 */
const toggleFullscreen = () => {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen()
    isFullscreen.value = true
  } else {
    document.exitFullscreen()
    isFullscreen.value = false
  }
}

/** 更新 Header 显示的时间 */
const updateTime = () => {
  currentTime.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

/** 检查 Docker 连接状态（调用 /api/health 端点） */
const checkDockerHealth = async () => {
  try {
    const res = await getHealth()
    const data = res.data
    if (data.docker?.status === 'UP') {
      dockerStatus.value = 'up'
      dockerVersion.value = data.docker.version || ''
    } else {
      dockerStatus.value = 'down'
    }
  } catch {
    dockerStatus.value = 'down'
  }
}

// ===== 生命周期 =====
onMounted(() => {
  // 已登录时：同步用户资料 + 启动 Docker 健康检查（每 60 秒）
  if (auth.isLoggedIn) {
    auth.fetchProfile()
    checkDockerHealth()
    healthTimer = setInterval(checkDockerHealth, 60000)
  }
  // 启动时间更新（每 30 秒）
  updateTime()
  timeTimer = setInterval(updateTime, 30000)

  document.addEventListener('fullscreenchange', () => {
    isFullscreen.value = !!document.fullscreenElement
  })
})
</script>

<template>
  <!-- Login page: no layout -->
  <router-view v-if="isLoginPage" />

  <!-- Main layout -->
  <el-container v-else class="app-container">
    <el-aside :width="isCollapse ? '64px' : '240px'" class="app-aside">
      <!-- Logo -->
      <div class="logo-area">
        <div class="logo-icon">
          <el-icon :size="24"><Monitor /></el-icon>
        </div>
        <transition name="fade-text">
          <span v-show="!isCollapse" class="logo-text">Docker Manager</span>
        </transition>
      </div>

      <!-- Menu -->
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        :collapse-transition="false"
        background-color="transparent"
        text-color="rgba(255,255,255,0.65)"
        active-text-color="#fff"
        @select="handleMenuSelect"
        router
        class="sidebar-menu"
      >
        <el-menu-item index="/">
          <el-icon><DataAnalysis /></el-icon>
          <template #title>Dashboard</template>
        </el-menu-item>
        <el-menu-item index="/projects">
          <el-icon><Folder /></el-icon>
          <template #title>Compose 项目</template>
        </el-menu-item>
        <el-menu-item v-if="auth.isAdmin" index="/users">
          <el-icon><UserFilled /></el-icon>
          <template #title>用户管理</template>
        </el-menu-item>
      </el-menu>

      <!-- Sidebar footer -->
      <div v-show="!isCollapse" class="sidebar-footer">
        <span class="sidebar-version">v1.0.0</span>
      </div>
    </el-aside>

    <el-container>
      <!-- Header -->
      <el-header class="app-header" height="56px">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="isCollapse = !isCollapse">
            <component :is="isCollapse ? 'Expand' : 'Fold'" />
          </el-icon>
          <el-breadcrumb separator="/" class="header-breadcrumb">
            <el-breadcrumb-item v-for="item in breadcrumbItems" :key="item.title"
                                :to="item.path ? { path: item.path } : undefined">
              {{ item.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-right">
          <el-tooltip :content="dockerStatus === 'up' ? `Docker 已连接 (v${dockerVersion})` : 'Docker 未连接'" placement="bottom">
            <div class="docker-status" :class="dockerStatus">
              <span class="status-dot"></span>
              <span class="status-text" v-if="!isCollapse">Docker</span>
            </div>
          </el-tooltip>
          <span class="header-time">{{ currentTime }}</span>
          <el-tooltip content="全屏" placement="bottom">
            <el-icon class="header-icon" @click="toggleFullscreen">
              <component :is="isFullscreen ? 'Aim' : 'FullScreen'" />
            </el-icon>
          </el-tooltip>
          <el-dropdown trigger="click" @command="handleCommand" class="user-dropdown">
            <div class="user-info">
              <el-avatar :size="32" class="user-avatar">
                {{ auth.nickname?.charAt(0)?.toUpperCase() || 'U' }}
              </el-avatar>
              <span class="user-name">{{ auth.nickname || auth.username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled class="user-info-item">
                  <div class="dropdown-user-info">
                    <el-avatar :size="36" class="user-avatar">
                      {{ auth.nickname?.charAt(0)?.toUpperCase() || 'U' }}
                    </el-avatar>
                    <div class="dropdown-user-text">
                      <div class="dropdown-username">{{ auth.nickname || auth.username }}</div>
                      <div class="dropdown-role">
                        <el-tag :type="auth.isAdmin ? 'danger' : 'info'" size="small" round effect="dark">
                          {{ auth.isAdmin ? 'ADMIN' : 'USER' }}
                        </el-tag>
                      </div>
                    </div>
                  </div>
                </el-dropdown-item>
                <el-dropdown-item divided command="password">
                  <el-icon><Lock /></el-icon> 修改密码
                </el-dropdown-item>
                <el-dropdown-item command="logout">
                  <el-icon><SwitchButton /></el-icon> 退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- Main content -->
      <el-main class="app-main">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>

  <!-- Change Password Dialog -->
  <el-dialog v-model="showPasswordDialog" title="修改密码" width="420">
    <el-form :model="passwordForm" label-width="80px">
      <el-form-item label="原密码">
        <el-input v-model="passwordForm.oldPassword" type="password" show-password />
      </el-form-item>
      <el-form-item label="新密码">
        <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="至少6位" />
      </el-form-item>
      <el-form-item label="确认密码">
        <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showPasswordDialog = false">取消</el-button>
      <el-button type="primary" @click="handleChangePassword">确认修改</el-button>
    </template>
  </el-dialog>
</template>

<style>
/* Global styles moved to main.css */
</style>

<style scoped>
.app-container {
  height: 100vh;
}

/* === Sidebar === */
.app-aside {
  background: linear-gradient(180deg, #001529 0%, #002140 100%);
  transition: width 0.28s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.15);
}

.logo-area {
  height: 56px;
  display: flex;
  align-items: center;
  padding: 0 16px;
  gap: 10px;
  flex-shrink: 0;
}

.logo-icon {
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, #409EFF, #66b1ff);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.logo-text {
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  white-space: nowrap;
  letter-spacing: 0.5px;
}

.sidebar-menu {
  border-right: none !important;
  flex: 1;
  padding: 8px;
}

.sidebar-menu :deep(.el-menu-item) {
  border-radius: 8px;
  margin-bottom: 4px;
  height: 44px;
  line-height: 44px;
}

.sidebar-menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.08) !important;
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(135deg, #409EFF, #337ecc) !important;
  color: #fff !important;
  font-weight: 600;
}

.sidebar-footer {
  padding: 16px;
  text-align: center;
  flex-shrink: 0;
}

.sidebar-version {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.3);
}

/* === Header === */
.app-header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  color: #606266;
  transition: color 0.2s;
}

.collapse-btn:hover {
  color: #409EFF;
}

.header-breadcrumb {
  font-size: 14px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.docker-status {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 500;
  cursor: default;
  transition: all 0.3s;
}

.docker-status .status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}

.docker-status.up {
  background: rgba(103, 194, 58, 0.1);
  color: #67C23A;
}

.docker-status.up .status-dot {
  background: #67C23A;
  box-shadow: 0 0 6px rgba(103, 194, 58, 0.4);
  animation: pulse-green 2s infinite;
}

.docker-status.down {
  background: rgba(245, 108, 108, 0.1);
  color: #F56C6C;
}

.docker-status.down .status-dot {
  background: #F56C6C;
}

.docker-status.checking {
  background: rgba(144, 147, 153, 0.1);
  color: #909399;
}

.docker-status.checking .status-dot {
  background: #909399;
  animation: pulse-gray 1.5s infinite;
}

.status-text {
  font-size: 12px;
}

@keyframes pulse-green {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

@keyframes pulse-gray {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

.header-time {
  font-size: 13px;
  color: #86909c;
  font-variant-numeric: tabular-nums;
}

.header-icon {
  font-size: 18px;
  color: #606266;
  cursor: pointer;
  padding: 6px;
  border-radius: 6px;
  transition: all 0.2s;
}

.header-icon:hover {
  background: #f0f2f5;
  color: #409EFF;
}

.user-dropdown {
  cursor: pointer;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 12px;
  border-radius: 8px;
  transition: background 0.2s;
}

.user-info:hover {
  background: #f5f7fa;
}

.user-avatar {
  background: linear-gradient(135deg, #409EFF, #337ecc);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
}

.user-name {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
}

/* Dropdown user info */
.dropdown-user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 0;
}

.dropdown-user-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.dropdown-username {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.dropdown-role {
  display: flex;
}

.user-info-item :deep(.el-dropdown-menu__item) {
  padding: 8px 16px;
}

/* === Main === */
.app-main {
  background: #f0f2f5;
  padding: 20px;
  overflow-y: auto;
}

/* === Transitions === */
.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.page-fade-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.page-fade-leave-to {
  opacity: 0;
}

.fade-text-enter-active,
.fade-text-leave-active {
  transition: opacity 0.2s;
}

.fade-text-enter-from,
.fade-text-leave-to {
  opacity: 0;
}
</style>
