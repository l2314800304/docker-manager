/**
 * 认证状态管理 Store (Pinia)。
 *
 * <p>管理用户认证的全生命周期状态，包括：</p>
 * <ul>
 *   <li><b>Token 管理</b> — JWT Token 的存储、读取和清除</li>
 *   <li><b>用户信息</b> — 用户名、昵称、角色的响应式状态</li>
 *   <li><b>认证操作</b> — 登录、注册、登出、修改密码</li>
 *   <li><b>持久化</b> — 使用 localStorage 实现刷新后保持登录状态</li>
 * </ul>
 *
 * <h3>使用方式：</h3>
 * <pre>
 * import { useAuthStore } from '@/stores/auth'
 * const auth = useAuthStore()
 * if (auth.isLoggedIn) { ... }
 * if (auth.isAdmin) { ... }
 * </pre>
 *
 * <h3>数据流：</h3>
 * <pre>
 * 登录/注册 API → 后端签发 JWT → setAuth() 存入 ref + localStorage
 *     → axios 拦截器读取 token 自动附加 Authorization header
 *     → 路由守卫检查 isLoggedIn 决定是否跳转登录页
 * </pre>
 *
 * @see api/index.ts — axios 拦截器自动附加 Token
 * @see router/index.ts — 导航守卫检查登录状态
 */
import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import axios from 'axios'

/** 独立的 axios 实例（避免与 api/index.ts 的拦截器循环依赖） */
const api = axios.create({ baseURL: '/api', timeout: 30000 })

export const useAuthStore = defineStore('auth', () => {
  // ===== 响应式状态（从 localStorage 恢复以支持页面刷新后保持登录） =====

  /** JWT Token（用于 Authorization: Bearer <token>） */
  const token = ref(localStorage.getItem('token') || '')
  /** 用户名（登录名） */
  const username = ref(localStorage.getItem('username') || '')
  /** 用户昵称（显示用） */
  const nickname = ref(localStorage.getItem('nickname') || '')
  /** 用户角色（'ADMIN' 或 'USER'） */
  const role = ref(localStorage.getItem('role') || '')

  // ===== 计算属性 =====

  /** 是否已登录（Token 非空即为已登录） */
  const isLoggedIn = computed(() => !!token.value)
  /** 是否为管理员（角色为 ADMIN） */
  const isAdmin = computed(() => role.value === 'ADMIN')

  // ===== 内部方法 =====

  /**
   * 保存认证信息到响应式状态和 localStorage。
   * 登录和注册成功后调用。
   */
  function setAuth(data: { token: string; username: string; nickname: string }) {
    token.value = data.token
    username.value = data.username
    nickname.value = data.nickname
    localStorage.setItem('token', data.token)
    localStorage.setItem('username', data.username)
    localStorage.setItem('nickname', data.nickname)
  }

  // ===== 公开方法 =====

  /**
   * 登出：清除所有认证状态和 localStorage 数据。
   * 调用后路由守卫会自动跳转到登录页。
   */
  function logout() {
    token.value = ''
    username.value = ''
    nickname.value = ''
    role.value = ''
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    localStorage.removeItem('nickname')
    localStorage.removeItem('role')
  }

  /**
   * 用户登录。
   *
   * @param user 用户名
   * @param pass 密码
   * @returns 后端返回的 { token, username, nickname }
   * @throws 登录失败时抛出异常（由调用方捕获显示错误信息）
   */
  async function login(user: string, pass: string) {
    const res = await api.post('/auth/login', { username: user, password: pass })
    setAuth(res.data)
    return res.data
  }

  /**
   * 用户注册（注册成功后自动登录）。
   *
   * @param user 用户名
   * @param pass 密码
   * @param nick 昵称（可选）
   * @returns 后端返回的 { token, username, nickname }
   */
  async function register(user: string, pass: string, nick?: string) {
    const res = await api.post('/auth/register', { username: user, password: pass, nickname: nick })
    setAuth(res.data)
    return res.data
  }

  /**
   * 获取/刷新用户资料。
   *
   * <p>应用启动时调用（App.vue onMounted），用于从后端同步最新的
   * 昵称和角色信息（可能在其他设备上被修改）。</p>
   *
   * <p>如果请求失败（Token 过期等），自动调用 logout() 清除本地状态。</p>
   *
   * @returns 用户资料 { username, nickname, role, createdAt, lastLoginAt }
   */
  async function fetchProfile() {
    try {
      const res = await api.get('/auth/profile', {
        headers: { Authorization: `Bearer ${token.value}` }
      })
      nickname.value = res.data.nickname
      role.value = res.data.role || ''
      localStorage.setItem('nickname', res.data.nickname)
      localStorage.setItem('role', res.data.role || '')
      return res.data
    } catch {
      logout()
      return null
    }
  }

  /**
   * 修改密码。
   *
   * @param oldPassword 旧密码
   * @param newPassword 新密码
   * @returns 后端返回的 { message }
   */
  async function changePassword(oldPassword: string, newPassword: string) {
    const res = await api.post('/auth/change-password', { oldPassword, newPassword }, {
      headers: { Authorization: `Bearer ${token.value}` }
    })
    return res.data
  }

  // 导出所有状态和方法供组件使用
  return { token, username, nickname, role, isLoggedIn, isAdmin, login, register, logout, fetchProfile, changePassword }
})
