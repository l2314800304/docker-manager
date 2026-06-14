<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getProjects } from '@/api'

const router = useRouter()
const projects = ref<any[]>([])
const loading = ref(false)

const fetchProjects = async () => {
  loading.value = true
  try {
    const res = await getProjects()
    projects.value = res.data
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const goToProject = (name: string) => router.push(`/projects/${name}`)
const goToContainer = (id: string) => router.push(`/containers/${id}`)

const getStateType = (state: string) => {
  const map: Record<string, string> = { RUNNING: 'success', STOPPED: 'danger', PAUSED: 'warning' }
  return map[state] || 'info'
}

onMounted(fetchProjects)
</script>

<template>
  <div v-loading="loading">
    <div class="page-header">
      <div class="page-header-left">
        <h2 class="page-title">Compose 项目</h2>
        <el-tag type="info" size="small">{{ projects.length }} 个项目</el-tag>
      </div>
      <el-button type="primary" @click="fetchProjects">
        <el-icon><Refresh /></el-icon> 刷新
      </el-button>
    </div>

    <el-row :gutter="20">
      <el-col :span="8" v-for="project in projects" :key="project.projectName">
        <el-card shadow="never" class="project-card" @click="goToProject(project.projectName)">
          <template #header>
            <div class="card-header">
              <div class="project-title">
                <div class="project-icon">
                  <el-icon :size="18"><Folder /></el-icon>
                </div>
                <span>{{ project.projectName }}</span>
              </div>
              <el-tag :type="project.stoppedServices === 0 ? 'success' : 'warning'" size="small" round>
                {{ project.runningServices }}/{{ project.totalServices }}
              </el-tag>
            </div>
          </template>

          <div class="project-meta">
            <div class="meta-item">
              <el-icon size="14"><Location /></el-icon>
              <span class="meta-text">{{ project.workingDir || 'N/A' }}</span>
            </div>
            <div class="meta-item">
              <el-icon size="14"><Box /></el-icon>
              <span>{{ project.totalServices }} 个服务</span>
            </div>
          </div>

          <div class="service-divider"></div>

          <div class="service-list">
            <div v-for="svc in project.services.slice(0, 5)" :key="svc.containerId"
                 class="service-row" @click.stop="goToContainer(svc.containerId)">
              <el-tag :type="getStateType(svc.state)" size="small" effect="dark" round>
                {{ svc.state?.substring(0, 1) }}
              </el-tag>
              <span class="svc-name">{{ svc.serviceName }}</span>
              <span class="svc-image">{{ svc.image }}</span>
            </div>
            <div v-if="project.services.length > 5" class="more-link">
              +{{ project.services.length - 5 }} 更多服务...
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-if="!loading && projects.length === 0" description="暂无 Compose 项目运行">
      <template #image>
        <el-icon :size="64" color="#c0c4cc"><Folder /></el-icon>
      </template>
    </el-empty>
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
  gap: 12px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #1d2129;
}

.project-card {
  margin-bottom: 20px;
  cursor: pointer;
  border-radius: 12px;
  border: 1px solid #f0f0f0;
  transition: all 0.25s ease;
}

.project-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  border-color: rgba(64, 158, 255, 0.2);
}

.project-card :deep(.el-card__header) {
  padding: 16px 20px;
  border-bottom: none;
}

.project-card :deep(.el-card__body) {
  padding: 0 20px 20px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.project-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 600;
  font-size: 16px;
  color: #1d2129;
}

.project-icon {
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

.project-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #86909c;
}

.meta-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.service-divider {
  height: 1px;
  background: #f0f0f0;
  margin: 14px 0;
}

.service-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.service-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 8px;
  font-size: 13px;
  transition: background 0.15s;
}

.service-row:hover {
  background: #ecf5ff;
}

.svc-name {
  font-weight: 500;
  min-width: 80px;
  color: #303133;
}

.svc-image {
  color: #86909c;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.more-link {
  font-size: 12px;
  color: #409EFF;
  padding: 6px 10px;
  cursor: pointer;
  font-weight: 500;
}
</style>
