<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getProject } from '@/api'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const project = ref<any>(null)
const loading = ref(false)

const projectName = route.params.name as string

const fetchProject = async () => {
  loading.value = true
  try {
    const res = await getProject(projectName)
    project.value = res.data
  } catch (e) {
    ElMessage.error('获取项目信息失败')
  } finally {
    loading.value = false
  }
}

const goToContainer = (id: string) => router.push(`/containers/${id}`)

const getStateType = (state: string) => {
  const map: Record<string, string> = { RUNNING: 'success', STOPPED: 'danger', PAUSED: 'warning' }
  return map[state] || 'info'
}

const getStateLabel = (state: string) => {
  const map: Record<string, string> = { RUNNING: '运行中', STOPPED: '已停止', PAUSED: '已暂停', RESTARTING: '重启中' }
  return map[state] || state
}

onMounted(fetchProject)
</script>

<template>
  <div v-loading="loading">
    <div class="page-header">
      <div class="page-header-left">
        <el-button text @click="router.push('/projects')" class="back-btn">
          <el-icon><ArrowLeft /></el-icon> 返回
        </el-button>
        <div class="page-title-area">
          <h2 class="page-title">{{ projectName }}</h2>
        </div>
      </div>
      <el-button type="primary" @click="fetchProject">
        <el-icon><Refresh /></el-icon> 刷新
      </el-button>
    </div>

    <template v-if="project">
      <!-- Project Info -->
      <el-card class="info-card">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="项目名称">{{ project.projectName }}</el-descriptions-item>
          <el-descriptions-item label="工作目录">
            <span class="dir-text">{{ project.workingDir }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="总服务数">{{ project.totalServices }}</el-descriptions-item>
          <el-descriptions-item label="运行中">
            <el-tag type="success" effect="dark" round>{{ project.runningServices }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="已停止">
            <el-tag type="danger" effect="dark" round>{{ project.stoppedServices }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- Services Table -->
      <el-card class="table-card">
        <template #header>
          <div class="card-header">
            <span class="card-title">服务列表</span>
            <el-tag size="small" type="info">{{ project.services?.length }} 个服务</el-tag>
          </div>
        </template>

        <el-table :data="project.services" stripe
                  @row-click="(row: any) => goToContainer(row.containerId)"
                  class="service-table">
          <el-table-column prop="serviceName" label="服务名" min-width="120">
            <template #default="{ row }">
              <span class="svc-name">{{ row.serviceName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="getStateType(row.state)" size="small" effect="dark" round>
                {{ getStateLabel(row.state) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="image" label="镜像" min-width="200">
            <template #default="{ row }">
              <span class="image-text">{{ row.image }}</span>
            </template>
          </el-table-column>
          <el-table-column label="端口" min-width="150">
            <template #default="{ row }">
              <el-tag v-for="port in row.ports" :key="port" size="small" class="port-tag">
                {{ port }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="Digests" min-width="200">
            <template #default="{ row }">
              <div v-if="row.digests?.length">
                <el-tooltip v-for="d in row.digests.slice(0, 2)" :key="d" :content="d" placement="top">
                  <el-tag size="small" type="info" class="digest-tag">
                    {{ d.length > 40 ? d.substring(0, 40) + '...' : d }}
                  </el-tag>
                </el-tooltip>
              </div>
              <span v-else class="na-text">N/A</span>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="运行时间" width="160" />
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="goToContainer(row.containerId)">
                详情
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
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
  gap: 12px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #1d2129;
}

.info-card {
  margin-bottom: 20px;
  border-radius: 12px;
  border: none;
}

.info-card :deep(.el-descriptions__label) {
  font-weight: 600;
  background: #f7f8fa;
}

.dir-text {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  color: #4e5969;
}

.table-card {
  border-radius: 12px;
  border: none;
}

.table-card :deep(.el-card__header) {
  padding: 16px 20px;
  border-bottom: 1px solid #f0f0f0;
}

.table-card :deep(.el-card__body) {
  padding: 8px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-weight: 600;
  font-size: 15px;
  color: #1d2129;
}

.service-table {
  cursor: pointer;
}

.service-table :deep(.el-table__row) {
  cursor: pointer;
}

.svc-name {
  font-weight: 600;
  color: #1d2129;
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

.digest-tag {
  margin-bottom: 2px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 11px;
}

.na-text {
  color: #c0c4cc;
  font-size: 12px;
}
</style>
