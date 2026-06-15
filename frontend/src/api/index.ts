/**
 * API 请求模块。
 *
 * <p>封装所有与后端的 HTTP 通信，提供类型安全的 API 调用函数。</p>
 *
 * <h3>核心机制：</h3>
 * <ul>
 *   <li><b>Axios 实例</b> — 统一配置 baseURL (/api) 和超时时间 (30s)</li>
 *   <li><b>请求拦截器</b> — 自动从 auth store 读取 JWT Token 并附加到 Authorization 请求头</li>
 *   <li><b>响应拦截器</b> — 统一处理 401 错误（Token 过期/无效），自动登出并跳转登录页</li>
 * </ul>
 *
 * <h3>API 分组：</h3>
 * <ul>
 *   <li><b>Auth</b> — 登录/注册/个人信息/修改密码</li>
 *   <li><b>Admin</b> — 用户管理（列表/启用禁用/删除/重置密码）</li>
 *   <li><b>Projects</b> — Compose 项目查询和刷新</li>
 *   <li><b>Containers</b> — 容器 CRUD 和生命周期操作</li>
 *   <li><b>Logs</b> — 历史日志查询（REST）</li>
 *   <li><b>Stats</b> — 资源使用统计</li>
 *   <li><b>File System</b> — 容器文件系统浏览</li>
 *   <li><b>Images</b> — 镜像 Tag 查询和服务更新</li>
 *   <li><b>Health</b> — 系统健康检查</li>
 *   <li><b>Audit</b> — 操作审计日志</li>
 *   <li><b>WebSocket</b> — 日志和 Stats 实时推送 URL 生成</li>
 * </ul>
 *
 * @see stores/auth.ts — Token 来源
 * @see router/index.ts — 401 后的页面跳转
 */
import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

/**
 * 全局 Axios 实例。
 * baseURL 设为 /api，开发时由 Vite proxy 转发到后端 localhost:8080，
 * 生产时由 Spring Boot 直接处理。
 */
const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

// ==================== 请求拦截器 ====================
// 在每个请求发送前自动附加 JWT Token 到 Authorization header
api.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

// ==================== 响应拦截器 ====================
// 统一处理 401 Unauthorized 错误：清除本地认证状态并跳转登录页
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      const auth = useAuthStore()
      auth.logout()         // 清除 Token 和用户信息
      router.push('/login') // 跳转到登录页
    }
    return Promise.reject(error)
  }
)

// ==================== Auth 认证 API ====================

/** 用户登录 */
export const authLogin = (username: string, password: string) =>
  api.post('/auth/login', { username, password })
/** 用户注册 */
export const authRegister = (username: string, password: string, nickname?: string) =>
  api.post('/auth/register', { username, password, nickname })
/** 获取当前用户信息 */
export const authProfile = () => api.get('/auth/profile')
/** 修改密码 */
export const authChangePassword = (oldPassword: string, newPassword: string) =>
  api.post('/auth/change-password', { oldPassword, newPassword })

// ==================== Admin 用户管理 API ====================

/** 获取所有用户列表（管理员） */
export const adminListUsers = () => api.get('/auth/admin/users')
/** 切换用户启用/禁用状态（管理员） */
export const adminToggleUser = (id: number) => api.post(`/auth/admin/users/${id}/toggle`)
/** 删除用户（管理员） */
export const adminDeleteUser = (id: number) => api.delete(`/auth/admin/users/${id}`)
/** 重置用户密码（管理员） */
export const adminResetPassword = (id: number, newPassword: string) =>
  api.post(`/auth/admin/users/${id}/reset-password`, { newPassword })

// ==================== Projects 项目 API ====================

/** 获取所有 Compose 项目列表 */
export const getProjects = () => api.get('/projects')
/** 获取指定项目详情 */
export const getProject = (name: string) => api.get(`/projects/${name}`)
/** 刷新项目信息 */
export const refreshProject = (name: string) => api.post(`/projects/${name}/refresh`)

// ==================== Containers 容器 API ====================

/** 获取所有容器列表 */
export const getContainers = () => api.get('/containers')
/** 获取指定容器详情 */
export const getContainer = (id: string) => api.get(`/containers/${id}`)
/** 获取容器 Docker inspect 原始数据 */
export const inspectContainer = (id: string) => api.get(`/containers/${id}/inspect`)
/** 重启容器 */
export const restartContainer = (id: string) => api.post(`/containers/${id}/restart`)
/** 停止容器 */
export const stopContainer = (id: string) => api.post(`/containers/${id}/stop`)
/** 启动容器 */
export const startContainer = (id: string) => api.post(`/containers/${id}/start`)

// ==================== Logs 日志 API ====================

/**
 * 获取容器历史日志（REST 方式）。
 * @param id    容器 ID
 * @param tail  返回最近 N 行（默认 200）
 * @param since 返回指定 Unix 时间戳之后的日志
 */
export const getLogs = (id: string, tail = 200, since?: number) =>
  api.get(`/containers/${id}/logs`, { params: { tail, since }, responseType: 'text' })

// ==================== Stats 资源统计 API ====================

/** 获取单个容器的 Stats 快照 */
export const getStats = (id: string) => api.get(`/containers/${id}/stats`)
/** 获取所有运行中容器的 Stats 汇总 */
export const getAllStats = () => api.get('/stats/summary')

// ==================== File System 文件系统 API ====================

/** 列出容器内目录的文件和子目录 */
export const listDirectory = (id: string, path = '/') =>
  api.get(`/containers/${id}/fs`, { params: { path } })
/** 读取容器内文件内容 */
export const readFile = (id: string, path: string) =>
  api.get(`/containers/${id}/fs/file`, { params: { path } })

// ==================== Images 镜像 API ====================

/** 查询镜像可用的 Tag（通过 Docker Hub API） */
export const getImageTags = (imageName: string) => api.get(`/images/${imageName}/tags`)
/** 更新服务的镜像版本（异步任务，返回 taskId） */
export const updateService = (projectName: string, serviceName: string, data: any) =>
  api.post(`/projects/${projectName}/services/${serviceName}/update`, data)
/** 查询镜像更新任务的执行状态 */
export const getTaskStatus = (taskId: string) => api.get(`/tasks/${taskId}/status`)

// ==================== Health 健康检查 API ====================

/** 获取系统和 Docker 连接状态 */
export const getHealth = () => api.get('/health')

// ==================== Hosts 宿主机 API ====================

/** 获取所有宿主机列表 */
export const getHosts = () => api.get('/hosts')
/** 获取宿主机详情 */
export const getHost = (id: number) => api.get(`/hosts/${id}`)
/** 添加宿主机 */
export const addHost = (data: any) => api.post('/hosts', data)
/** 更新宿主机 */
export const updateHost = (id: number, data: any) => api.put(`/hosts/${id}`, data)
/** 删除宿主机 */
export const deleteHost = (id: number) => api.delete(`/hosts/${id}`)
/** 测试宿主机连接 */
export const testHostConnection = (id: number) => api.post(`/hosts/${id}/test`)
/** 获取宿主机资源指标（含磁盘分区详情） */
export const getHostMetrics = (id: number) => api.get(`/hosts/${id}/metrics`)
/** 获取所有宿主机资源指标汇总 */
export const getAllHostMetrics = () => api.get('/hosts/metrics')

// ==================== Alerts 告警 API ====================

/** 获取所有告警规则 */
export const getAlertRules = () => api.get('/alerts/rules')
/** 添加告警规则 */
export const addAlertRule = (data: any) => api.post('/alerts/rules', data)
/** 更新告警规则 */
export const updateAlertRule = (id: number, data: any) => api.put(`/alerts/rules/${id}`, data)
/** 删除告警规则 */
export const deleteAlertRule = (id: number) => api.delete(`/alerts/rules/${id}`)
/** 获取告警记录列表 */
export const getAlertRecords = (limit = 50) => api.get('/alerts/records', { params: { limit } })
/** 按规则查询告警记录 */
export const getAlertRecordsByRule = (ruleId: number, limit = 50) =>
  api.get(`/alerts/records/rule/${ruleId}`, { params: { limit } })
/** 测试钉钉通知 */
export const testNotification = (data: any) => api.post('/alerts/test-notification', data)

// ==================== Audit 审计日志 API ====================

/** 获取操作审计日志列表 */
export const getAuditLogs = (limit = 50) => api.get('/audit', { params: { limit } })

// ==================== WebSocket URL 生成 ====================

/**
 * 生成容器日志 WebSocket 连接 URL。
 * 自动根据当前页面协议选择 ws:// 或 wss://。
 *
 * @param containerId 容器 ID
 * @returns WebSocket URL（如 ws://localhost:8080/ws/logs/abc123）
 */
export const getLogWsUrl = (containerId: string) => {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws/logs/${containerId}`
}

/**
 * 生成容器 Stats WebSocket 连接 URL。
 *
 * @param containerId 容器 ID
 * @returns WebSocket URL（如 ws://localhost:8080/ws/stats/abc123）
 */
export const getStatsWsUrl = (containerId: string) => {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws/stats/${containerId}`
}

export default api
