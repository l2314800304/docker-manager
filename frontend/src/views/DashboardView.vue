<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getProjects, getAllStats, getAuditLogs } from '@/api'

const router = useRouter()
const projects = ref<any[]>([])
const allStats = ref<any[]>([])
const loading = ref(false)
let refreshTimer: ReturnType<typeof setInterval> | null = null
let countdownTimer: ReturnType<typeof setInterval> | null = null
const countdown = ref(30)
const lastUpdate = ref('')

// Animated values
const animProjects = ref(0)
const animRunning = ref(0)
const animStopped = ref(0)
const animTotal = ref(0)

const totalContainers = ref(0)
const runningContainers = ref(0)
const stoppedContainers = ref(0)
const auditLogs = ref<any[]>([])

const animateNumber = (target: number, current: { value: number }, duration = 600) => {
  const start = current.value
  const diff = target - start
  if (diff === 0) return
  const startTime = performance.now()
  const step = (now: number) => {
    const elapsed = now - startTime
    const progress = Math.min(elapsed / duration, 1)
    // Ease out cubic
    const eased = 1 - Math.pow(1 - progress, 3)
    current.value = Math.round(start + diff * eased)
    if (progress < 1) requestAnimationFrame(step)
  }
  requestAnimationFrame(step)
}

const fetchProjects = async () => {
  loading.value = true
  try {
    const [projRes, statsRes] = await Promise.all([getProjects(), getAllStats()])
    projects.value = projRes.data
    allStats.value = statsRes.data

    const newTotal = projects.value.reduce((sum: number, p: any) => sum + p.totalServices, 0)
    const newRunning = projects.value.reduce((sum: number, p: any) => sum + p.runningServices, 0)
    const newStopped = projects.value.reduce((sum: number, p: any) => sum + p.stoppedServices, 0)

    animateNumber(projects.value.length, animProjects)
    animateNumber(newRunning, animRunning)
    animateNumber(newStopped, animStopped)
    animateNumber(newTotal, animTotal)

    totalContainers.value = newTotal
    runningContainers.value = newRunning
    stoppedContainers.value = newStopped

    lastUpdate.value = new Date().toLocaleTimeString()
    countdown.value = 30
  } catch (e) {
    console.error('Failed to fetch projects:', e)
  } finally {
    loading.value = false
  }
}

const goToProject = (name: string) => router.push(`/projects/${name}`)
const goToContainer = (id: string) => router.push(`/containers/${id}`)

const getStateTag = (state: string) => {
  const map: Record<string, string> = { RUNNING: 'success', STOPPED: 'danger', PAUSED: 'warning', RESTARTING: 'info' }
  return map[state] || 'info'
}

const getHealthPercent = (project: any) => {
  if (!project.totalServices) return 0
  return Math.round((project.runningServices / project.totalServices) * 100)
}

const formatBytes = (bytes: number) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i]
}

const fetchAuditLogs = async () => {
  try {
    const res = await getAuditLogs(20)
    auditLogs.value = res.data || []
  } catch {
    auditLogs.value = []
  }
}

const getActionTagType = (action: string) => {
  const map: Record<string, string> = {
    RESTART: 'warning', STOP: 'danger', START: 'success',
    UPDATE_TAG: 'info', DELETE: 'danger'
  }
  return map[action] || 'info'
}

const formatTime = (t: string) => {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 19)
}

onMounted(() => {
  fetchProjects()
  fetchAuditLogs()
  refreshTimer = setInterval(fetchProjects, 30000)
  countdownTimer = setInterval(() => {
    if (countdown.value > 0) countdown.value--
  }, 1000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  if (countdownTimer) clearInterval(countdownTimer)
})
</script>

<template>
  <div class="dashboard" v-loading="loading">
    <!-- Stats Cards Row -->
    <div class="stats-grid">
      <div class="stat-card projects-card">
        <div class="stat-card-inner">
          <div class="stat-card-content">
            <div class="stat-value">{{ animProjects }}</div>
            <div class="stat-label">Compose 项目</div>
          </div>
          <div class="stat-icon-wrap">
            <el-icon :size="24"><Folder /></el-icon>
          </div>
        </div>
        <div class="stat-bar"><div class="stat-bar-fill" style="width: 100%;"></div></div>
      </div>

      <div class="stat-card running-card">
        <div class="stat-card-inner">
          <div class="stat-card-content">
            <div class="stat-value">{{ animRunning }}</div>
            <div class="stat-label">运行中容器</div>
          </div>
          <div class="stat-icon-wrap success">
            <el-icon :size="24"><CircleCheck /></el-icon>
          </div>
        </div>
        <div class="stat-bar success"><div class="stat-bar-fill" :style="{ width: (totalContainers ? (runningContainers / totalContainers * 100) : 0) + '%' }"></div></div>
      </div>

      <div class="stat-card stopped-card">
        <div class="stat-card-inner">
          <div class="stat-card-content">
            <div class="stat-value">{{ animStopped }}</div>
            <div class="stat-label">已停止容器</div>
          </div>
          <div class="stat-icon-wrap danger">
            <el-icon :size="24"><CircleClose /></el-icon>
          </div>
        </div>
        <div class="stat-bar danger"><div class="stat-bar-fill" :style="{ width: (totalContainers ? (stoppedContainers / totalContainers * 100) : 0) + '%' }"></div></div>
      </div>

      <div class="stat-card total-card">
        <div class="stat-card-inner">
          <div class="stat-card-content">
            <div class="stat-value">{{ animTotal }}</div>
            <div class="stat-label">总容器数</div>
          </div>
          <div class="stat-icon-wrap warning">
            <el-icon :size="24"><Box /></el-icon>
          </div>
        </div>
        <div class="stat-bar warning"><div class="stat-bar-fill" style="width: 100%;"></div></div>
      </div>
    </div>

    <!-- Main Content -->
    <el-row :gutter="20">
      <!-- Left: Projects -->
      <el-col :span="14">
        <el-card class="section-card">
          <template #header>
            <div class="section-header">
              <div class="section-title-area">
                <el-icon :size="18" color="#409EFF"><Folder /></el-icon>
                <span class="section-title">Compose 项目</span>
                <el-tag size="small" type="info" round>{{ projects.length }}</el-tag>
              </div>
              <div class="section-actions">
                <span class="refresh-hint" v-if="!loading">{{ countdown }}s</span>
                <el-button text size="small" @click="fetchProjects" :loading="loading">
                  <el-icon v-if="!loading"><Refresh /></el-icon> 刷新
                </el-button>
              </div>
            </div>
          </template>

          <div v-if="projects.length === 0 && !loading" class="empty-state">
            <el-empty description="暂无 Compose 项目">
              <template #image>
                <el-icon :size="56" color="#dcdfe6"><Folder /></el-icon>
              </template>
            </el-empty>
          </div>

          <transition-group name="list" tag="div">
            <div v-for="project in projects" :key="project.projectName" class="project-item"
                 @click="goToProject(project.projectName)">
              <div class="project-top">
                <div class="project-name-row">
                  <span class="project-name">{{ project.projectName }}</span>
                  <div class="project-tags">
                    <el-tag size="small" type="success" effect="light" round>{{ project.runningServices }} 运行</el-tag>
                    <el-tag v-if="project.stoppedServices > 0" size="small" type="danger" effect="light" round>
                      {{ project.stoppedServices }} 停止
                    </el-tag>
                  </div>
                </div>
                <el-progress :percentage="getHealthPercent(project)" :show-text="false" :stroke-width="4"
                             :color="getHealthPercent(project) === 100 ? '#67C23A' : '#E6A23C'"
                             class="health-bar" />
              </div>
              <div class="project-services">
                <div v-for="svc in project.services.slice(0, 6)" :key="svc.containerId" class="service-item"
                     @click.stop="goToContainer(svc.containerId)">
                  <el-tag :type="getStateTag(svc.state)" size="small" effect="dark" round class="state-tag">
                    {{ svc.state?.substring(0, 1) }}
                  </el-tag>
                  <span class="service-name">{{ svc.serviceName }}</span>
                  <span class="service-image">{{ svc.image }}</span>
                </div>
                <div v-if="project.services.length > 6" class="more-services">
                  +{{ project.services.length - 6 }} 更多
                </div>
              </div>
            </div>
          </transition-group>
        </el-card>
      </el-col>

      <!-- Right: Resource Stats -->
      <el-col :span="10">
        <el-card class="section-card">
          <template #header>
            <div class="section-header">
              <div class="section-title-area">
                <el-icon :size="18" color="#67C23A"><DataLine /></el-icon>
                <span class="section-title">资源使用</span>
              </div>
              <el-tag v-if="allStats.length" size="small" type="success" round>{{ allStats.length }} 容器</el-tag>
            </div>
          </template>

          <div v-if="allStats.length === 0" class="empty-state">
            <el-empty description="暂无运行中的容器" :image-size="80">
              <template #image>
                <el-icon :size="56" color="#dcdfe6"><Monitor /></el-icon>
              </template>
            </el-empty>
          </div>

          <div v-for="stat in allStats" :key="stat.containerId" class="resource-item"
               @click="goToContainer(stat.containerId)">
            <div class="resource-header">
              <span class="resource-name">{{ stat.containerId?.substring(0, 12) }}</span>
              <span class="resource-cpu" :class="{ 'high': (stat.cpu?.percent || 0) > 80 }">
                {{ (stat.cpu?.percent || 0).toFixed(1) }}% CPU
              </span>
            </div>
            <div class="resource-bars">
              <div class="resource-bar-group">
                <span class="bar-label">CPU</span>
                <el-progress :percentage="Math.min(stat.cpu?.percent || 0, 100)"
                            :stroke-width="6" :show-text="false"
                            :color="(stat.cpu?.percent || 0) > 80 ? '#F56C6C' : '#409EFF'" />
              </div>
              <div class="resource-bar-group">
                <span class="bar-label">MEM</span>
                <el-progress :percentage="Math.min(stat.memory?.percent || 0, 100)"
                            :stroke-width="6" :show-text="false"
                            :color="(stat.memory?.percent || 0) > 80 ? '#F56C6C' : '#67C23A'" />
              </div>
            </div>
            <div class="resource-detail">
              <span>{{ formatBytes(stat.memory?.usage) }} / {{ formatBytes(stat.memory?.limit) }}</span>
              <span>↓{{ formatBytes(stat.network?.rxBytesPerSec) }}/s ↑{{ formatBytes(stat.network?.txBytesPerSec) }}/s</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Audit Logs -->
    <el-card class="section-card audit-card" v-if="auditLogs.length > 0">
      <template #header>
        <div class="section-header">
          <div class="section-title-area">
            <el-icon :size="18" color="#E6A23C"><Document /></el-icon>
            <span class="section-title">操作日志</span>
            <el-tag size="small" type="info" round>{{ auditLogs.length }}</el-tag>
          </div>
          <el-button text size="small" @click="fetchAuditLogs">
            <el-icon><Refresh /></el-icon> 刷新
          </el-button>
        </div>
      </template>
      <el-table :data="auditLogs" size="small" class="audit-table" :show-header="true">
        <el-table-column label="时间" width="170">
          <template #default="{ row }">
            <span class="audit-time">{{ formatTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-tag :type="getActionTagType(row.action)" size="small" effect="dark" round>
              {{ row.action }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="projectName" label="项目" min-width="100" />
        <el-table-column prop="serviceName" label="服务" min-width="100" />
        <el-table-column label="结果" width="80">
          <template #default="{ row }">
            <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" size="small" round>
              {{ row.result === 'SUCCESS' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="detail" label="详情" min-width="150" show-overflow-tooltip />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
/* Stats Grid */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  transition: all 0.3s ease;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.08);
}

.stat-card-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 20px 16px;
}

.stat-value {
  font-size: 34px;
  font-weight: 800;
  color: #1d2129;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}

.stat-label {
  font-size: 13px;
  color: #86909c;
  margin-top: 6px;
}

.stat-icon-wrap {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: linear-gradient(135deg, #409EFF, #66b1ff);
  flex-shrink: 0;
}
.stat-icon-wrap.success { background: linear-gradient(135deg, #67C23A, #85ce61); }
.stat-icon-wrap.danger { background: linear-gradient(135deg, #F56C6C, #f89898); }
.stat-icon-wrap.warning { background: linear-gradient(135deg, #E6A23C, #ebb563); }

.stat-bar {
  height: 3px;
  background: #f0f0f0;
}
.stat-bar .stat-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #409EFF, #66b1ff);
  transition: width 0.6s ease;
  border-radius: 0 3px 3px 0;
}
.stat-bar.success .stat-bar-fill { background: linear-gradient(90deg, #67C23A, #85ce61); }
.stat-bar.danger .stat-bar-fill { background: linear-gradient(90deg, #F56C6C, #f89898); }
.stat-bar.warning .stat-bar-fill { background: linear-gradient(90deg, #E6A23C, #ebb563); }

/* Section Cards */
.section-card {
  border-radius: 12px;
  border: none;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.section-card :deep(.el-card__header) {
  padding: 14px 20px;
  border-bottom: 1px solid #f5f5f5;
}

.section-card :deep(.el-card__body) {
  padding: 12px 16px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.section-title-area {
  display: flex;
  align-items: center;
  gap: 8px;
}

.section-title {
  font-weight: 600;
  font-size: 15px;
  color: #1d2129;
}

.section-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.refresh-hint {
  font-size: 11px;
  color: #c0c4cc;
  font-variant-numeric: tabular-nums;
}

/* Project Items */
.project-item {
  padding: 14px 16px;
  border-radius: 10px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid transparent;
}

.project-item:hover {
  background: #f7f9fc;
  border-color: #e8ecf1;
}

.project-item:last-child {
  margin-bottom: 0;
}

.project-top {
  margin-bottom: 8px;
}

.project-name-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.project-name {
  font-weight: 600;
  font-size: 14px;
  color: #1d2129;
}

.project-tags {
  display: flex;
  gap: 6px;
}

.health-bar {
  border-radius: 2px;
}

.health-bar :deep(.el-progress-bar__outer) {
  border-radius: 2px;
}

/* Service Items */
.project-services {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.service-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 10px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  transition: background 0.15s;
}

.service-item:hover {
  background: #ecf5ff;
}

.state-tag {
  min-width: 22px;
  text-align: center;
}

.service-name {
  font-weight: 500;
  min-width: 90px;
  color: #303133;
}

.service-image {
  color: #a0a4ab;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.more-services {
  font-size: 12px;
  color: #409EFF;
  padding: 4px 10px;
  font-weight: 500;
}

/* Resource Items */
.resource-item {
  padding: 12px 14px;
  border-radius: 10px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid transparent;
}

.resource-item:last-child {
  margin-bottom: 0;
}

.resource-item:hover {
  background: #f7f9fc;
  border-color: #e8ecf1;
}

.resource-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.resource-name {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  color: #303133;
  font-weight: 600;
}

.resource-cpu {
  font-size: 12px;
  font-weight: 600;
  color: #409EFF;
}

.resource-cpu.high {
  color: #F56C6C;
}

.resource-bars {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.resource-bar-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.bar-label {
  font-size: 10px;
  font-weight: 700;
  color: #a0a4ab;
  letter-spacing: 0.5px;
  width: 28px;
  flex-shrink: 0;
}

.resource-detail {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #a0a4ab;
  margin-top: 6px;
}

/* Empty */
.empty-state {
  padding: 30px 0;
}

/* Transitions */
.list-enter-active,
.list-leave-active {
  transition: all 0.3s ease;
}
.list-enter-from {
  opacity: 0;
  transform: translateX(-20px);
}
.list-leave-to {
  opacity: 0;
  transform: translateX(20px);
}

/* Audit Logs */
.audit-card {
  margin-top: 20px;
}

.audit-card :deep(.el-card__body) {
  padding: 4px 8px;
}

.audit-table :deep(.el-table__cell) {
  padding: 8px 0;
}

.audit-time {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  color: #86909c;
}
</style>
