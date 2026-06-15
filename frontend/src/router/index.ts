/**
 * Vue Router 路由配置。
 *
 * <p>定义应用的所有前端路由及导航守卫逻辑。</p>
 *
 * <h3>路由表：</h3>
 * <ul>
 *   <li>{@code /login} — 登录/注册页（公开，无需认证）</li>
 *   <li>{@code /} — Dashboard 首页（需认证）</li>
 *   <li>{@code /projects} — Compose 项目列表（需认证）</li>
 *   <li>{@code /projects/:name} — 项目详情（需认证）</li>
 *   <li>{@code /containers/:id} — 容器详情（需认证）</li>
 *   <li>{@code /users} — 用户管理（需认证 + 需管理员角色）</li>
 * </ul>
 *
 * <h3>路由特性：</h3>
 * <ul>
 *   <li><b>History 模式</b> — URL 无 # 号，需后端配合 SPA 路由转发</li>
 *   <li><b>路由懒加载</b> — 所有视图组件使用 {@code () => import()} 按需加载</li>
 *   <li><b>Meta 标记</b> — requiresAuth 控制认证，requiresAdmin 控制管理员权限</li>
 * </ul>
 *
 * <h3>导航守卫逻辑：</h3>
 * <pre>
 * 请求路由 → 检查 requiresAuth
 *   → 未登录 → 重定向到 /login（携带 redirect 参数）
 *   → 已登录且目标是 /login → 重定向到 /（避免重复登录）
 *   → 检查 requiresAdmin
 *     → 非管理员 → 重定向到 /
 *     → 管理员 → 放行
 * </pre>
 *
 * @see stores/auth.ts — 认证状态（isLoggedIn, isAdmin）
 * @see config/WebConfig.java — 后端 SPA 路由转发配置
 */
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  // 使用 HTML5 History 模式（URL 无 # 前缀）
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),  // 路由懒加载
      meta: { requiresAuth: false },  // 标记为公开页面
    },
    {
      path: '/',
      name: 'dashboard',
      component: () => import('@/views/DashboardView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/projects',
      name: 'projects',
      component: () => import('@/views/ProjectListView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/projects/:name',
      name: 'project-detail',
      component: () => import('@/views/ProjectDetailView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/containers/:id',
      name: 'container-detail',
      component: () => import('@/views/ContainerDetailView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/hosts',
      name: 'hosts',
      component: () => import('@/views/HostManagementView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/alerts',
      name: 'alerts',
      component: () => import('@/views/AlertManagementView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/users',
      name: 'users',
      component: () => import('@/views/UserManagementView.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },  // 需要管理员权限
    },
  ],
})

/**
 * 全局前置导航守卫。
 *
 * <p>在每次路由切换前执行认证和授权检查：</p>
 * <ol>
 *   <li>如果目标路由需要认证且用户未登录 → 重定向到登录页（保存原路径用于登录后回跳）</li>
 *   <li>如果已登录用户访问登录页 → 重定向到首页</li>
 *   <li>如果目标路由需要管理员权限且用户非管理员 → 重定向到首页</li>
 *   <li>其他情况 → 正常放行</li>
 * </ol>
 */
router.beforeEach((to, _from, next) => {
  const auth = useAuthStore()

  if (to.meta.requiresAuth !== false && !auth.isLoggedIn) {
    // 未登录 → 跳转登录页，携带 redirect 参数用于登录后回跳
    next({ name: 'login', query: { redirect: to.fullPath } })
  } else if (to.name === 'login' && auth.isLoggedIn) {
    // 已登录用户访问登录页 → 直接跳转首页
    next({ name: 'dashboard' })
  } else if (to.meta.requiresAdmin && !auth.isAdmin) {
    // 非管理员访问管理员页面 → 跳转首页
    next({ name: 'dashboard' })
  } else {
    next()
  }
})

export default router
