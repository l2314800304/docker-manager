<script setup lang="ts">
import { ref, shallowRef, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getContainer, inspectContainer, restartContainer, stopContainer, startContainer,
  getLogWsUrl, getStatsWsUrl, listDirectory, readFile,
  updateService, getTaskStatus, getImageTags
} from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const containerId = route.params.id as string
const container = ref<any>(null)
const activeTab = ref('info')
const loading = ref(false)

// Logs - use shallowRef for large arrays (no deep reactivity needed)
const logLines = shallowRef<{streamType: string; line: string}[]>([])
const logWs = ref<WebSocket | null>(null)
const logAutoScroll = ref(true)
const logSearch = ref('')
const logContainerRef = ref<HTMLElement | null>(null)
const logConnected = ref(false)
let logReconnectTimer: ReturnType<typeof setTimeout> | null = null
let logReconnectAttempts = 0
const MAX_RECONNECT_DELAY = 30000

// Stats
const statsWs = ref<WebSocket | null>(null)
const cpuHistory = ref<number[]>([])
const memHistory = ref<number[]>([])
const netRxHistory = ref<number[]>([])
const netTxHistory = ref<number[]>([])
const timeLabels = ref<string[]>([])
const currentStats = ref<any>(null)
let statsReconnectTimer: ReturnType<typeof setTimeout> | null = null
let statsReconnectAttempts = 0

// File System - use shallowRef for potentially large arrays
const currentPath = ref('/')
const pathHistory = ref<string[]>(['/'])
const fileEntries = shallowRef<any[]>([])
const fileContent = ref<string | null>(null)
const selectedFile = ref('')
const fsLoading = ref(false)

// Image Update
const showUpdateDialog = ref(false)
const newTag = ref('')
const updateLoading = ref(false)
const updateProgress = ref('')
const availableTags = ref<string[]>([])
let updatePollTimer: ReturnType<typeof setInterval> | null = null

const fetchContainer = async () => {
  loading.value = true
  try {
    const res = await getContainer(containerId)
    container.value = res.data
  } catch (e) {
    ElMessage.error('获取容器信息失败')
  } finally {
    loading.value = false
  }
}

// === Log WebSocket with auto-reconnect ===
const connectLogWs = () => {
  if (logWs.value) {
    logWs.value.close()
    logWs.value = null
  }
  if (logReconnectTimer) {
    clearTimeout(logReconnectTimer)
    logReconnectTimer = null
  }
  logLines.value = []
  logConnected.value = false

  const url = getLogWsUrl(containerId)
  const ws = new WebSocket(url)

  ws.onopen = () => {
    logConnected.value = true
    logReconnectAttempts = 0
  }

  let pendingLines: {streamType: string; line: string}[] = []
  let flushTimer: ReturnType<typeof setTimeout> | null = null

  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)
      pendingLines.push(data)
      // Batch flush every 100ms to reduce re-renders
      if (!flushTimer) {
        flushTimer = setTimeout(() => {
          const current = logLines.value
          const merged = [...current, ...pendingLines]
          // Keep max 3000 lines
          logLines.value = merged.length > 3000 ? merged.slice(-3000) : merged
          pendingLines = []
          flushTimer = null
          if (logAutoScroll.value) {
            nextTick(() => {
              if (logContainerRef.value) {
                logContainerRef.value.scrollTop = logContainerRef.value.scrollHeight
              }
            })
          }
        }, 100)
      }
    } catch {}
  }

  ws.onclose = () => {
    if (flushTimer) { clearTimeout(flushTimer); flushTimer = null }
    logConnected.value = false
    // Auto-reconnect with exponential backoff
    if (activeTab.value === 'logs') {
      const delay = Math.min(1000 * Math.pow(2, logReconnectAttempts), MAX_RECONNECT_DELAY)
      logReconnectAttempts++
      logReconnectTimer = setTimeout(() => connectLogWs(), delay)
    }
  }

  ws.onerror = () => { logConnected.value = false }

  logWs.value = ws
}

// === Stats WebSocket with auto-reconnect ===
const connectStatsWs = () => {
  if (statsWs.value) {
    statsWs.value.close()
    statsWs.value = null
  }
  if (statsReconnectTimer) {
    clearTimeout(statsReconnectTimer)
    statsReconnectTimer = null
  }

  const url = getStatsWsUrl(containerId)
  const ws = new WebSocket(url)

  ws.onopen = () => { statsReconnectAttempts = 0 }

  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)
      currentStats.value = data

      const now = new Date().toLocaleTimeString()
      cpuHistory.value.push(data.cpu?.percent || 0)
      memHistory.value.push(data.memory?.percent || 0)
      netRxHistory.value.push(data.network?.rxBytesPerSec || 0)
      netTxHistory.value.push(data.network?.txBytesPerSec || 0)
      timeLabels.value.push(now)

      // Keep last 60 data points
      const max = 60
      if (cpuHistory.value.length > max) {
        cpuHistory.value.shift()
        memHistory.value.shift()
        netRxHistory.value.shift()
        netTxHistory.value.shift()
        timeLabels.value.shift()
      }
    } catch {}
  }

  ws.onclose = () => {
    // Auto-reconnect with exponential backoff
    if (activeTab.value === 'stats') {
      const delay = Math.min(1000 * Math.pow(2, statsReconnectAttempts), MAX_RECONNECT_DELAY)
      statsReconnectAttempts++
      statsReconnectTimer = setTimeout(() => connectStatsWs(), delay)
    }
  }

  statsWs.value = ws
}

// === File System ===
const loadDirectory = async (path: string) => {
  fsLoading.value = true
  fileContent.value = null
  try {
    const res = await listDirectory(containerId, path)
    fileEntries.value = res.data
    currentPath.value = path
    if (!pathHistory.value.includes(path)) {
      pathHistory.value.push(path)
    }
  } catch (e) {
    ElMessage.error('读取目录失败')
  } finally {
    fsLoading.value = false
  }
}

const openFile = async (entry: any) => {
  if (entry.type === 'directory') {
    loadDirectory(entry.path)
  } else {
    try {
      const res = await readFile(containerId, entry.path)
      fileContent.value = res.data.content
      selectedFile.value = entry.path
    } catch {
      ElMessage.error('读取文件失败')
    }
  }
}

const navigateUp = () => {
  const parts = currentPath.value.split('/').filter(Boolean)
  parts.pop()
  loadDirectory('/' + parts.join('/') || '/')
}

// === Image Update ===
const openUpdateDialog = async () => {
  if (!container.value) return
  const imageName = container.value.image?.split(':')[0]
  if (imageName) {
    try {
      const res = await getImageTags(imageName)
      availableTags.value = res.data
    } catch {
      availableTags.value = []
    }
  }
  newTag.value = ''
  updateProgress.value = ''
  showUpdateDialog.value = true
}

const doUpdate = async () => {
  if (!newTag.value || !container.value) return
  updateLoading.value = true
  updateProgress.value = '提交更新请求...'

  // Clean up any existing poll timer
  if (updatePollTimer) {
    clearInterval(updatePollTimer)
    updatePollTimer = null
  }

  try {
    const imageName = container.value.image?.split(':')[0]
    const currentTag = container.value.image?.split(':')[1] || 'latest'

    const res = await updateService(container.value.projectName, container.value.serviceName, {
      image: imageName,
      currentTag,
      newTag: newTag.value,
      autoRestart: true,
      rollbackOnFailure: true,
    })

    const taskId = res.data.taskId

    // Poll task status with proper cleanup
    updatePollTimer = setInterval(async () => {
      try {
        const statusRes = await getTaskStatus(taskId)
        updateProgress.value = statusRes.data.message
        if (statusRes.data.status === 'SUCCESS' || statusRes.data.status === 'FAILURE') {
          clearInterval(updatePollTimer!)
          updatePollTimer = null
          updateLoading.value = false
          if (statusRes.data.status === 'SUCCESS') {
            ElMessage.success('服务更新成功')
            showUpdateDialog.value = false
            fetchContainer()
          } else {
            ElMessage.error('服务更新失败: ' + statusRes.data.message)
          }
        }
      } catch {
        if (updatePollTimer) {
          clearInterval(updatePollTimer)
          updatePollTimer = null
        }
        updateLoading.value = false
      }
    }, 2000)
  } catch (e: any) {
    ElMessage.error('更新失败: ' + (e.message || 'Unknown'))
    updateLoading.value = false
  }
}

// === Container Actions ===
const handleRestart = async () => {
  try {
    await ElMessageBox.confirm('确认重启此容器?', '确认')
    await restartContainer(containerId)
    ElMessage.success('重启指令已发送')
    setTimeout(fetchContainer, 3000)
  } catch {}
}

const handleStop = async () => {
  try {
    await ElMessageBox.confirm('确认停止此容器?', '确认', { type: 'warning' })
    await stopContainer(containerId)
    ElMessage.success('停止指令已发送')
    setTimeout(fetchContainer, 2000)
  } catch {}
}

const handleStart = async () => {
  await startContainer(containerId)
  ElMessage.success('启动指令已发送')
  setTimeout(fetchContainer, 3000)
}

const formatBytes = (bytes: number) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i]
}

const filteredLogs = shallowRef<{streamType: string; line: string}[]>([])

// Debounced log search
let searchDebounceTimer: ReturnType<typeof setTimeout> | null = null
watch(logSearch, (val) => {
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  searchDebounceTimer = setTimeout(() => {
    if (!val) {
      filteredLogs.value = logLines.value
    } else {
      const lower = val.toLowerCase()
      filteredLogs.value = logLines.value.filter(l => l.line.toLowerCase().includes(lower))
    }
  }, 200)
})
watch(logLines, () => {
  if (!logSearch.value) filteredLogs.value = logLines.value
}, { deep: false })

watch(activeTab, (tab) => {
  if (tab === 'logs' && !logWs.value) connectLogWs()
  if (tab === 'stats' && !statsWs.value) connectStatsWs()
  if (tab === 'fs' && fileEntries.value.length === 0) loadDirectory('/')
})

onMounted(() => {
  fetchContainer()
})

onUnmounted(() => {
  logWs.value?.close()
  statsWs.value?.close()
  logWs.value = null
  statsWs.value = null
  if (logReconnectTimer) { clearTimeout(logReconnectTimer); logReconnectTimer = null }
  if (statsReconnectTimer) { clearTimeout(statsReconnectTimer); statsReconnectTimer = null }
  if (updatePollTimer) { clearInterval(updatePollTimer); updatePollTimer = null }
  if (searchDebounceTimer) { clearTimeout(searchDebounceTimer); searchDebounceTimer = null }
})
</script>

<template>
  <div v-loading="loading">
    <div class="page-header">
      <div class="page-header-left">
        <el-button text @click="router.back()" class="back-btn">
          <el-icon><ArrowLeft /></el-icon> 返回
        </el-button>
        <div class="page-title-area">
          <h2 class="page-title">{{ container?.containerName || containerId }}</h2>
          <el-tag v-if="container" :type="container.state === 'RUNNING' ? 'success' : 'danger'" size="small" effect="dark" round>
            {{ container.state }}
          </el-tag>
        </div>
      </div>
    </div>

    <template v-if="container">
      <!-- Container Actions -->
      <el-card class="action-card">
        <div class="action-bar">
          <div class="action-info">
            <span class="action-status">{{ container.status }}</span>
          </div>
          <div class="action-buttons">
            <el-button type="primary" @click="handleRestart" :disabled="container.state !== 'RUNNING'" round>
              <el-icon><RefreshRight /></el-icon> 重启
            </el-button>
            <el-button type="danger" @click="handleStop" :disabled="container.state !== 'RUNNING'" round>
              <el-icon><VideoPause /></el-icon> 停止
            </el-button>
            <el-button type="success" @click="handleStart" :disabled="container.state === 'RUNNING'" round>
              <el-icon><VideoPlay /></el-icon> 启动
            </el-button>
            <el-button @click="openUpdateDialog" type="warning" round>
              <el-icon><Upload /></el-icon> 更新版本
            </el-button>
          </div>
        </div>
      </el-card>

      <!-- Tabs -->
      <el-card class="tabs-card">
        <el-tabs v-model="activeTab">
          <!-- Info Tab -->
          <el-tab-pane label="基本信息" name="info">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="容器ID">
                <code class="code-text">{{ container.containerId }}</code>
              </el-descriptions-item>
              <el-descriptions-item label="容器名">{{ container.containerName }}</el-descriptions-item>
              <el-descriptions-item label="服务名">{{ container.serviceName }}</el-descriptions-item>
              <el-descriptions-item label="所属项目">{{ container.projectName }}</el-descriptions-item>
              <el-descriptions-item label="镜像">
                <span class="image-text">{{ container.image }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="镜像ID">
                <code class="code-text">{{ container.imageId?.substring(0, 19) }}</code>
              </el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="container.state === 'RUNNING' ? 'success' : 'danger'" size="small" effect="dark" round>
                  {{ container.state }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="运行时间">{{ container.status }}</el-descriptions-item>
              <el-descriptions-item label="端口" :span="2">
                <el-tag v-for="port in container.ports" :key="port" size="small" class="port-tag">
                  {{ port }}
                </el-tag>
                <span v-if="!container.ports?.length" class="na-text">无</span>
              </el-descriptions-item>
              <el-descriptions-item label="Digests" :span="2">
                <div v-if="container.digests?.length">
                  <div v-for="d in container.digests" :key="d" class="digest-line">
                    <code class="digest-code">{{ d }}</code>
                  </div>
                </div>
                <span v-else class="na-text">N/A</span>
              </el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ container.createdAt }}</el-descriptions-item>
              <el-descriptions-item label="工作目录">
                <span class="dir-text">{{ container.composeWorkingDir }}</span>
              </el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>

          <!-- Logs Tab -->
          <el-tab-pane label="日志" name="logs">
            <div class="log-toolbar">
              <el-input v-model="logSearch" placeholder="搜索日志..." clearable style="width: 250px;" size="small">
                <template #prefix><el-icon><Search /></el-icon></template>
              </el-input>
              <el-tag :type="logConnected ? 'success' : 'danger'" size="small" round>
                {{ logConnected ? '已连接' : '未连接' }}
              </el-tag>
              <el-checkbox v-model="logAutoScroll" label="自动滚动" size="small" />
              <el-button size="small" @click="connectLogWs" round>
                <el-icon><Refresh /></el-icon> 重连
              </el-button>
              <el-tag size="small" type="info">{{ logLines.length }} 行</el-tag>
            </div>
            <div ref="logContainerRef" class="log-viewer">
              <div v-for="(log, idx) in (logSearch ? filteredLogs : logLines)" :key="idx"
                   :class="['log-line', log.streamType]">
                <span class="log-text">{{ log.line }}</span>
              </div>
              <div v-if="logLines.length === 0" class="log-empty">等待日志数据...</div>
            </div>
          </el-tab-pane>

          <!-- Stats Tab -->
          <el-tab-pane label="监控" name="stats">
            <div v-if="currentStats" class="stats-overview">
              <el-row :gutter="16">
                <el-col :span="6">
                  <div class="stat-box cpu-box">
                    <div class="stat-title">CPU 使用率</div>
                    <div class="stat-number" :class="{ danger: currentStats.cpu?.percent > 80 }">
                      {{ (currentStats.cpu?.percent || 0).toFixed(1) }}%
                    </div>
                  </div>
                </el-col>
                <el-col :span="6">
                  <div class="stat-box mem-box">
                    <div class="stat-title">内存使用</div>
                    <div class="stat-number">{{ formatBytes(currentStats.memory?.usage) }}</div>
                    <div class="stat-sub">/ {{ formatBytes(currentStats.memory?.limit) }} ({{ (currentStats.memory?.percent || 0).toFixed(1) }}%)</div>
                  </div>
                </el-col>
                <el-col :span="6">
                  <div class="stat-box net-box">
                    <div class="stat-title">网络 ↓</div>
                    <div class="stat-number">{{ formatBytes(currentStats.network?.rxBytesPerSec) }}/s</div>
                    <div class="stat-sub">总: {{ formatBytes(currentStats.network?.rxBytes) }}</div>
                  </div>
                </el-col>
                <el-col :span="6">
                  <div class="stat-box net-up-box">
                    <div class="stat-title">网络 ↑</div>
                    <div class="stat-number">{{ formatBytes(currentStats.network?.txBytesPerSec) }}/s</div>
                    <div class="stat-sub">总: {{ formatBytes(currentStats.network?.txBytes) }}</div>
                  </div>
                </el-col>
              </el-row>

              <!-- Simple chart representation -->
              <div class="chart-section">
                <div class="chart-title">CPU 使用率 (最近60秒)</div>
                <div class="mini-chart">
                  <div v-for="(val, i) in cpuHistory" :key="i" class="chart-bar"
                       :style="{ height: Math.min(val, 100) + '%', background: val > 80 ? '#F56C6C' : '#409EFF' }">
                  </div>
                </div>
              </div>
              <div class="chart-section">
                <div class="chart-title">内存使用率 (最近60秒)</div>
                <div class="mini-chart">
                  <div v-for="(val, i) in memHistory" :key="i" class="chart-bar"
                       :style="{ height: Math.min(val, 100) + '%', background: val > 80 ? '#F56C6C' : '#67C23A' }">
                  </div>
                </div>
              </div>
            </div>
            <el-empty v-else description="等待监控数据..." :image-size="80" />
          </el-tab-pane>

          <!-- File System Tab -->
          <el-tab-pane label="文件系统" name="fs">
            <div class="fs-toolbar">
              <el-button size="small" @click="navigateUp" :disabled="currentPath === '/'" round>
                <el-icon><Top /></el-icon> 上级目录
              </el-button>
              <el-breadcrumb separator="/" class="fs-path">
                <el-breadcrumb-item @click="loadDirectory('/')">/</el-breadcrumb-item>
                <el-breadcrumb-item v-for="(part, i) in currentPath.split('/').filter(Boolean)" :key="i"
                  @click="loadDirectory('/' + currentPath.split('/').filter(Boolean).slice(0, i + 1).join('/'))">
                  {{ part }}
                </el-breadcrumb-item>
              </el-breadcrumb>
              <el-button size="small" @click="loadDirectory(currentPath)" round>
                <el-icon><Refresh /></el-icon>
              </el-button>
            </div>

            <el-row :gutter="16">
              <el-col :span="fileContent !== null ? 10 : 24">
                <el-table :data="fileEntries" v-loading="fsLoading" size="small"
                          @row-click="(row: any) => openFile(row)" class="fs-table" max-height="500">
                  <el-table-column label="名称" min-width="200">
                    <template #default="{ row }">
                      <div class="fs-name-cell">
                        <el-icon v-if="row.type === 'directory'" color="#E6A23C"><Folder /></el-icon>
                        <el-icon v-else-if="row.type === 'link'" color="#409EFF"><Link /></el-icon>
                        <el-icon v-else color="#909399"><Document /></el-icon>
                        <span :class="{ 'dir-name': row.type === 'directory' }">{{ row.name }}</span>
                      </div>
                    </template>
                  </el-table-column>
                  <el-table-column prop="permissions" label="权限" width="120" />
                  <el-table-column prop="owner" label="所有者" width="80" />
                  <el-table-column label="大小" width="100">
                    <template #default="{ row }">
                      {{ row.type === 'file' ? formatBytes(row.size) : '-' }}
                    </template>
                  </el-table-column>
                  <el-table-column prop="modifiedTime" label="修改时间" width="150" />
                </el-table>
              </el-col>

              <el-col :span="14" v-if="fileContent !== null">
                <div class="file-viewer">
                  <div class="file-viewer-header">
                    <span>{{ selectedFile }}</span>
                    <el-button size="small" @click="fileContent = null" round>关闭</el-button>
                  </div>
                  <pre class="file-content">{{ fileContent }}</pre>
                </div>
              </el-col>
            </el-row>
          </el-tab-pane>
        </el-tabs>
      </el-card>

      <!-- Update Dialog -->
      <el-dialog v-model="showUpdateDialog" title="更新镜像版本" width="500" class="update-dialog">
        <el-form label-width="80px">
          <el-form-item label="当前镜像">
            <el-tag>{{ container.image }}</el-tag>
          </el-form-item>
          <el-form-item label="新 Tag">
            <el-select v-model="newTag" filterable allow-create placeholder="输入新的 tag" style="width: 100%;">
              <el-option v-for="tag in availableTags" :key="tag" :label="tag" :value="tag" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="updateProgress" label="进度">
            <div class="update-progress">{{ updateProgress }}</div>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showUpdateDialog = false">取消</el-button>
          <el-button type="primary" @click="doUpdate" :loading="updateLoading" :disabled="!newTag">
            确认更新
          </el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.page-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.back-btn {
  color: #86909c;
  font-size: 13px;
}

.back-btn:hover {
  color: #409EFF;
}

.page-title-area {
  display: flex;
  align-items: center;
  gap: 10px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #1d2129;
}

/* Action Bar */
.action-card {
  margin-bottom: 16px;
  border-radius: 12px;
  border: none;
}

.action-card :deep(.el-card__body) {
  padding: 16px 20px;
}

.action-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.action-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.action-status {
  font-size: 13px;
  color: #86909c;
}

.action-buttons {
  display: flex;
  gap: 8px;
}

/* Tabs Card */
.tabs-card {
  border-radius: 12px;
  border: none;
}

.tabs-card :deep(.el-card__body) {
  padding: 12px 20px 20px;
}

/* Info tab */
.code-text {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 4px;
  color: #4e5969;
}

.image-text {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  color: #4e5969;
}

.port-tag {
  margin-right: 4px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 11px;
}

.digest-line {
  margin-bottom: 4px;
}

.digest-code {
  font-size: 12px;
  word-break: break-all;
  color: #4e5969;
}

.na-text {
  color: #c0c4cc;
  font-size: 12px;
}

.dir-text {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  color: #4e5969;
}

/* Logs */
.log-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.log-viewer {
  background: #1a1a2e;
  border-radius: 10px;
  padding: 16px;
  height: 500px;
  overflow-y: auto;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
}

.log-line {
  white-space: pre-wrap;
  word-break: break-all;
}

.log-line.stderr .log-text {
  color: #f56c6c;
}

.log-line.stdout .log-text {
  color: #e0e0e0;
}

.log-empty {
  color: #909399;
  text-align: center;
  padding: 40px;
}

/* Stats */
.stats-overview {
  padding: 8px 0;
}

.stat-box {
  text-align: center;
  padding: 20px 16px;
  background: #f7f8fa;
  border-radius: 12px;
  transition: transform 0.2s;
}

.stat-box:hover {
  transform: translateY(-2px);
}

.cpu-box { border-left: 3px solid #409EFF; }
.mem-box { border-left: 3px solid #67C23A; }
.net-box { border-left: 3px solid #E6A23C; }
.net-up-box { border-left: 3px solid #F56C6C; }

.stat-title {
  font-size: 13px;
  color: #86909c;
  margin-bottom: 8px;
  font-weight: 500;
}

.stat-number {
  font-size: 24px;
  font-weight: 700;
  color: #1d2129;
}

.stat-number.danger {
  color: #F56C6C;
}

.stat-sub {
  font-size: 11px;
  color: #86909c;
  margin-top: 4px;
}

.chart-section {
  margin-top: 20px;
}

.chart-title {
  font-size: 13px;
  font-weight: 600;
  color: #4e5969;
  margin-bottom: 8px;
}

.mini-chart {
  height: 80px;
  display: flex;
  align-items: flex-end;
  gap: 1px;
  background: #f7f8fa;
  border-radius: 8px;
  padding: 6px;
}

.chart-bar {
  flex: 1;
  min-width: 3px;
  border-radius: 2px 2px 0 0;
  transition: height 0.3s;
}

/* File System */
.fs-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.fs-path {
  flex: 1;
}

.fs-table {
  cursor: pointer;
}

.fs-name-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.dir-name {
  font-weight: 600;
  color: #1d2129;
}

.file-viewer {
  border: 1px solid #ebeef5;
  border-radius: 10px;
  overflow: hidden;
}

.file-viewer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: #f7f8fa;
  font-size: 13px;
  font-family: monospace;
  border-bottom: 1px solid #ebeef5;
}

.file-content {
  padding: 14px;
  margin: 0;
  max-height: 500px;
  overflow: auto;
  background: #fafafa;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.update-progress {
  font-size: 13px;
  color: #606266;
}
</style>
