<script setup lang="ts">
/**
 * 宿主机管理页面。
 *
 * <p>提供 Docker 宿主机的增删改查、连接测试和资源监控功能。</p>
 *
 * <h3>功能概览：</h3>
 * <ul>
 *   <li>卡片网格展示所有宿主机，包含连接信息、状态和快捷操作</li>
 *   <li>新增/编辑宿主机表单对话框（支持 TLS 配置）</li>
 *   <li>查看宿主机资源指标（CPU / 内存 / 磁盘分区）</li>
 *   <li>测试宿主机连接可用性</li>
 *   <li>删除确认（ElMessageBox）</li>
 * </ul>
 */
import { ref, reactive, onMounted } from 'vue'
import {
  getHosts, addHost, updateHost, deleteHost,
  testHostConnection, getHostMetrics
} from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

// ==================== 响应式状态 ====================

/** 宿主机列表 */
const hosts = ref<any[]>([])
/** 页面整体加载状态 */
const loading = ref(false)

/** 新增/编辑对话框可见性 */
const formDialogVisible = ref(false)
/** 当前是否为编辑模式 */
const isEdit = ref(false)
/** 正在编辑的宿主机 ID（编辑模式下使用） */
const editingId = ref<number | null>(null)
/** 表单提交加载状态 */
const formLoading = ref(false)

/** 表单数据（新增和编辑共用） */
const formData = reactive({
  name: '',
  connectionType: 'SOCKET',
  connectionUrl: '',
  tlsEnabled: false,
  certificatePath: '',
})

/** 表单校验规则 */
const formRules = {
  name: [
    { required: true, message: '请输入宿主机名称', trigger: 'blur' },
    { min: 3, max: 100, message: '名称长度需在 3-100 个字符之间', trigger: 'blur' },
  ],
  connectionType: [
    { required: true, message: '请选择连接类型', trigger: 'change' },
  ],
  connectionUrl: [
    { required: true, message: '请输入连接地址', trigger: 'blur' },
  ],
}

/** 资源指标对话框可见性 */
const metricsDialogVisible = ref(false)
/** 当前查看指标的宿主机名称 */
const metricsHostName = ref('')
/** 指标数据 */
const metricsData = ref<any>(null)
/** 指标加载状态 */
const metricsLoading = ref(false)

/** 正在测试连接的宿主机 ID（用于按钮 loading 状态） */
const testingId = ref<number | null>(null)

// ==================== 工具函数 ====================

/**
 * 字节数格式化为可读字符串。
 * 例如：1024 → "1 KB"，1073741824 → "1 GB"
 */
const formatBytes = (bytes: number): string => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i]
}

/**
 * 格式化 ISO 时间字符串为本地可读格式。
 * 将 "T" 替换为空格并截取到秒。
 */
const formatTime = (t: string): string => {
  if (!t) return '从未连接'
  return t.replace('T', ' ').substring(0, 19)
}

/**
 * 获取状态标签的类型（颜色）。
 * ONLINE → 绿色, OFFLINE → 红色, 其他 → 灰色
 */
const getStatusType = (status: string): string => {
  const map: Record<string, string> = {
    ONLINE: 'success',
    OFFLINE: 'danger',
    UNKNOWN: 'info',
  }
  return map[status] || 'info'
}

/**
 * 获取状态中文显示文本。
 */
const getStatusText = (status: string): string => {
  const map: Record<string, string> = {
    ONLINE: '在线',
    OFFLINE: '离线',
    UNKNOWN: '未知',
  }
  return map[status] || '未知'
}

/**
 * 获取磁盘使用率的颜色：>90% 红色、>70% 橙色、其他绿色。
 */
const getDiskColor = (percent: number): string => {
  if (percent > 90) return '#F56C6C'
  if (percent > 70) return '#E6A23C'
  return '#67C23A'
}

// ==================== 数据加载 ====================

/** 获取宿主机列表 */
const fetchHosts = async () => {
  loading.value = true
  try {
    const res = await getHosts()
    hosts.value = res.data || []
  } catch (e) {
    ElMessage.error('获取宿主机列表失败')
  } finally {
    loading.value = false
  }
}

// ==================== 新增 / 编辑 ====================

/**
 * 打开新增宿主机对话框。
 * 重置表单为默认值并切换到新增模式。
 */
const openAddDialog = () => {
  isEdit.value = false
  editingId.value = null
  Object.assign(formData, {
    name: '',
    connectionType: 'SOCKET',
    connectionUrl: '',
    tlsEnabled: false,
    certificatePath: '',
  })
  formDialogVisible.value = true
}

/**
 * 打开编辑宿主机对话框。
 * 将当前宿主机数据填充到表单中。
 */
const openEditDialog = (host: any) => {
  isEdit.value = true
  editingId.value = host.id
  Object.assign(formData, {
    name: host.name,
    connectionType: host.connectionType,
    connectionUrl: host.connectionUrl,
    tlsEnabled: host.tlsEnabled || false,
    certificatePath: host.certificatePath || '',
  })
  formDialogVisible.value = true
}

/**
 * 提交表单（新增或编辑）。
 * 根据 isEdit 状态调用不同的 API。
 */
const submitForm = async () => {
  // 基本校验：名称和连接地址不能为空
  if (!formData.name || formData.name.length < 3) {
    ElMessage.warning('请输入有效的宿主机名称（至少 3 个字符）')
    return
  }
  if (!formData.connectionUrl) {
    ElMessage.warning('请输入连接地址')
    return
  }

  formLoading.value = true
  try {
    const payload = {
      name: formData.name,
      connectionType: formData.connectionType,
      connectionUrl: formData.connectionUrl,
      tlsEnabled: formData.tlsEnabled,
      certificatePath: formData.tlsEnabled ? formData.certificatePath : '',
    }

    if (isEdit.value && editingId.value !== null) {
      await updateHost(editingId.value, payload)
      ElMessage.success('宿主机信息已更新')
    } else {
      await addHost(payload)
      ElMessage.success('宿主机已添加')
    }

    formDialogVisible.value = false
    fetchHosts()
  } catch (e: any) {
    ElMessage.error((isEdit.value ? '更新' : '添加') + '失败: ' + (e.response?.data?.message || e.message || '未知错误'))
  } finally {
    formLoading.value = false
  }
}

// ==================== 删除 ====================

/**
 * 删除宿主机（带确认弹窗）。
 * 使用 ElMessageBox.confirm 防止误操作。
 */
const handleDelete = async (host: any) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除宿主机「${host.name}」吗？此操作不可恢复。`,
      '确认删除',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteHost(host.id)
    ElMessage.success('宿主机已删除')
    fetchHosts()
  } catch {
    // 用户取消操作，不做处理
  }
}

// ==================== 连接测试 ====================

/**
 * 测试宿主机连接。
 * 调用后端测试接口并显示结果。
 */
const handleTestConnection = async (host: any) => {
  testingId.value = host.id
  try {
    const res = await testHostConnection(host.id)
    const data = res.data
    if (data?.success) {
      ElMessage.success(`连接成功！Docker 版本: ${data.dockerVersion || 'N/A'}`)
    } else {
      ElMessage.warning(`连接失败: ${data?.message || '无法连接到宿主机'}`)
    }
    // 刷新列表以更新状态
    fetchHosts()
  } catch (e: any) {
    ElMessage.error('连接测试失败: ' + (e.response?.data?.message || e.message || '网络错误'))
  } finally {
    testingId.value = null
  }
}

// ==================== 资源指标 ====================

/**
 * 打开宿主机资源指标对话框。
 * 获取并展示 CPU、内存、磁盘分区等指标数据。
 */
const openMetricsDialog = async (host: any) => {
  metricsHostName.value = host.name
  metricsData.value = null
  metricsDialogVisible.value = true
  metricsLoading.value = true

  try {
    const res = await getHostMetrics(host.id)
    metricsData.value = res.data
  } catch (e) {
    ElMessage.error('获取资源指标失败')
  } finally {
    metricsLoading.value = false
  }
}

// ==================== 生命周期 ====================

onMounted(() => {
  fetchHosts()
})
</script>

<template>
  <div class="host-management" v-loading="loading">
    <!-- ==================== 页面头部 ==================== -->
    <div class="page-header">
      <div class="page-header-left">
        <el-icon :size="24" color="#409EFF"><Monitor /></el-icon>
        <h2 class="page-title">宿主机管理</h2>
        <el-tag size="small" type="info" round>{{ hosts.length }} 台</el-tag>
      </div>
      <div class="page-header-right">
        <!-- 刷新按钮 -->
        <el-button @click="fetchHosts" :loading="loading" round>
          <el-icon v-if="!loading"><Refresh /></el-icon>
          刷新
        </el-button>
        <!-- 新增宿主机按钮 -->
        <el-button type="primary" @click="openAddDialog" round>
          <el-icon><CircleCheck /></el-icon>
          添加宿主机
        </el-button>
      </div>
    </div>

    <!-- ==================== 宿主机卡片网格 ==================== -->
    <div v-if="hosts.length > 0" class="host-grid">
      <transition-group name="card-fade">
        <div v-for="host in hosts" :key="host.id" class="host-card-wrapper">
          <el-card class="host-card" shadow="hover">
            <!-- 卡片头部：名称 + 状态 -->
            <div class="host-card-header">
              <div class="host-name-row">
                <el-icon :size="18" color="#409EFF"><Monitor /></el-icon>
                <span class="host-name">{{ host.name }}</span>
              </div>
              <!-- 状态标签：ONLINE 绿色 / OFFLINE 红色 / UNKNOWN 灰色 -->
              <el-tag
                :type="getStatusType(host.status)"
                size="small"
                effect="dark"
                round
              >
                {{ getStatusText(host.status) }}
              </el-tag>
            </div>

            <!-- 卡片内容：连接信息 -->
            <div class="host-card-body">
              <div class="info-row">
                <span class="info-label">连接类型</span>
                <el-tag
                  :type="host.connectionType === 'TCP' ? 'warning' : 'primary'"
                  size="small"
                  effect="plain"
                >
                  {{ host.connectionType }}
                </el-tag>
              </div>
              <div class="info-row">
                <span class="info-label">连接地址</span>
                <span class="info-value url-text">{{ host.connectionUrl }}</span>
              </div>
              <!-- Docker 版本（仅在可用时显示） -->
              <div class="info-row" v-if="host.dockerVersion">
                <span class="info-label">Docker 版本</span>
                <span class="info-value">{{ host.dockerVersion }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">上次连接</span>
                <span class="info-value time-text">{{ formatTime(host.lastConnectedAt) }}</span>
              </div>
              <!-- TLS 启用标识 -->
              <div class="info-row" v-if="host.tlsEnabled">
                <span class="info-label">TLS</span>
                <el-tag type="success" size="small" effect="light" round>已启用</el-tag>
              </div>
            </div>

            <!-- 卡片底部：操作按钮 -->
            <div class="host-card-footer">
              <el-button
                size="small"
                type="success"
                plain
                round
                :loading="testingId === host.id"
                @click="handleTestConnection(host)"
              >
                <el-icon v-if="testingId !== host.id"><Connection /></el-icon>
                测试连接
              </el-button>
              <el-button size="small" @click="openMetricsDialog(host)" round>
                <el-icon><DataLine /></el-icon>
                指标
              </el-button>
              <el-button size="small" @click="openEditDialog(host)" round>
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
              <el-button size="small" type="danger" plain round @click="handleDelete(host)">
                <el-icon><Delete /></el-icon>
                删除
              </el-button>
            </div>
          </el-card>
        </div>
      </transition-group>
    </div>

    <!-- 空状态：无宿主机时显示 -->
    <div v-else-if="!loading" class="empty-state">
      <el-empty description="暂无宿主机，点击「添加宿主机」开始使用">
        <template #image>
          <el-icon :size="64" color="#dcdfe6"><Monitor /></el-icon>
        </template>
        <el-button type="primary" @click="openAddDialog" round>
          <el-icon><CircleCheck /></el-icon>
          添加宿主机
        </el-button>
      </el-empty>
    </div>

    <!-- ==================== 新增/编辑宿主机对话框 ==================== -->
    <el-dialog
      v-model="formDialogVisible"
      :title="isEdit ? '编辑宿主机' : '添加宿主机'"
      width="520"
      destroy-on-close
      class="host-form-dialog"
    >
      <el-form
        :model="formData"
        :rules="formRules"
        label-width="100px"
        label-position="right"
      >
        <!-- 宿主机名称 -->
        <el-form-item label="名称" prop="name">
          <el-input
            v-model="formData.name"
            placeholder="请输入宿主机名称（3-100 字符）"
            maxlength="100"
            show-word-limit
            clearable
          />
        </el-form-item>

        <!-- 连接类型选择 -->
        <el-form-item label="连接类型" prop="connectionType">
          <el-select v-model="formData.connectionType" style="width: 100%">
            <el-option label="Unix Socket" value="SOCKET" />
            <el-option label="TCP" value="TCP" />
          </el-select>
        </el-form-item>

        <!-- 连接地址 -->
        <el-form-item label="连接地址" prop="connectionUrl">
          <el-input
            v-model="formData.connectionUrl"
            :placeholder="formData.connectionType === 'SOCKET' ? 'unix:///var/run/docker.sock' : 'tcp://192.168.1.100:2375'"
            clearable
          />
        </el-form-item>

        <!-- TLS 开关 -->
        <el-form-item label="TLS 加密">
          <el-switch v-model="formData.tlsEnabled" />
          <span class="tls-hint">{{ formData.tlsEnabled ? '已启用' : '未启用' }}</span>
        </el-form-item>

        <!-- TLS 证书路径（仅在 TLS 启用时显示） -->
        <el-form-item v-if="formData.tlsEnabled" label="证书路径">
          <el-input
            v-model="formData.certificatePath"
            placeholder="例如：/etc/docker/certs"
            clearable
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="formLoading" round>
          {{ isEdit ? '保存修改' : '确认添加' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- ==================== 资源指标对话框 ==================== -->
    <el-dialog
      v-model="metricsDialogVisible"
      :title="`资源指标 — ${metricsHostName}`"
      width="600"
      destroy-on-close
      class="metrics-dialog"
    >
      <div v-loading="metricsLoading">
        <!-- 有指标数据时展示 -->
        <template v-if="metricsData">
          <!-- CPU 和内存概览 -->
          <el-row :gutter="16" class="metrics-overview">
            <el-col :span="12">
              <div class="metric-box cpu-box">
                <div class="metric-title">CPU 使用率</div>
                <div class="metric-value" :class="{ danger: (metricsData.cpu?.percent || 0) > 80 }">
                  {{ (metricsData.cpu?.percent || 0).toFixed(1) }}%
                </div>
                <el-progress
                  :percentage="Math.min(metricsData.cpu?.percent || 0, 100)"
                  :stroke-width="8"
                  :show-text="false"
                  :color="(metricsData.cpu?.percent || 0) > 80 ? '#F56C6C' : '#409EFF'"
                />
                <div class="metric-detail">
                  {{ metricsData.cpu?.cores || '-' }} 核心
                </div>
              </div>
            </el-col>
            <el-col :span="12">
              <div class="metric-box mem-box">
                <div class="metric-title">内存使用</div>
                <div class="metric-value" :class="{ danger: (metricsData.memory?.percent || 0) > 80 }">
                  {{ (metricsData.memory?.percent || 0).toFixed(1) }}%
                </div>
                <el-progress
                  :percentage="Math.min(metricsData.memory?.percent || 0, 100)"
                  :stroke-width="8"
                  :show-text="false"
                  :color="(metricsData.memory?.percent || 0) > 80 ? '#F56C6C' : '#67C23A'"
                />
                <div class="metric-detail">
                  {{ formatBytes(metricsData.memory?.usedBytes) }} / {{ formatBytes(metricsData.memory?.totalBytes) }}
                </div>
              </div>
            </el-col>
          </el-row>

          <!-- 磁盘分区详情 -->
          <div class="disk-section" v-if="metricsData.partitions && metricsData.partitions.length > 0">
            <div class="section-divider">
              <el-icon :size="16" color="#E6A23C"><Coin /></el-icon>
              <span class="section-divider-title">磁盘分区</span>
            </div>
            <div class="partition-grid">
              <div v-for="(part, idx) in metricsData.partitions" :key="idx" class="partition-item">
                <div class="partition-header">
                  <span class="partition-mount">{{ part.mountPoint }}</span>
                  <span class="partition-percent" :style="{ color: getDiskColor(part.usePercent) }">
                    {{ part.usePercent.toFixed(1) }}%
                  </span>
                </div>
                <el-progress
                  :percentage="Math.min(part.usePercent, 100)"
                  :stroke-width="10"
                  :show-text="false"
                  :color="getDiskColor(part.usePercent)"
                />
                <div class="partition-detail">
                  <span class="partition-fs">{{ part.filesystem }}</span>
                  <span class="partition-size">
                    {{ formatBytes(part.usedBytes) }} / {{ formatBytes(part.totalBytes) }}
                  </span>
                </div>
                <div class="partition-available">
                  可用 {{ formatBytes(part.availableBytes) }}
                </div>
              </div>
            </div>
          </div>
        </template>

        <!-- 无指标数据时显示空状态 -->
        <el-empty v-else-if="!metricsLoading" description="暂无指标数据，请确认宿主机在线" :image-size="80">
          <template #image>
            <el-icon :size="56" color="#dcdfe6"><DataLine /></el-icon>
          </template>
        </el-empty>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
/* ==================== 页面头部 ==================== */
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.page-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.page-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: #1d2129;
}

.page-header-right {
  display: flex;
  gap: 10px;
}

/* ==================== 宿主机卡片网格 ==================== */
.host-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 16px;
}

.host-card-wrapper {
  transition: all 0.3s ease;
}

.host-card {
  border-radius: 14px;
  border: none;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  transition: all 0.3s ease;
  height: 100%;
}

.host-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 28px rgba(0, 0, 0, 0.1);
}

.host-card :deep(.el-card__body) {
  padding: 20px;
  display: flex;
  flex-direction: column;
  height: 100%;
}

/* 卡片头部：名称和状态 */
.host-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.host-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.host-name {
  font-size: 16px;
  font-weight: 700;
  color: #1d2129;
}

/* 卡片内容：连接信息 */
.host-card-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 16px;
}

.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.info-label {
  font-size: 13px;
  color: #86909c;
  flex-shrink: 0;
}

.info-value {
  font-size: 13px;
  color: #303133;
  font-weight: 500;
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
}

.url-text {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  color: #4e5969;
}

.time-text {
  font-size: 12px;
  color: #86909c;
  font-variant-numeric: tabular-nums;
}

/* 卡片底部：操作按钮 */
.host-card-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}

/* ==================== 空状态 ==================== */
.empty-state {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
}

/* ==================== 新增/编辑对话框 ==================== */
.host-form-dialog :deep(.el-dialog) {
  border-radius: 16px;
}

.host-form-dialog :deep(.el-dialog__header) {
  padding: 20px 24px 16px;
  border-bottom: 1px solid #f5f5f5;
}

.host-form-dialog :deep(.el-dialog__body) {
  padding: 20px 24px;
}

.tls-hint {
  margin-left: 10px;
  font-size: 13px;
  color: #86909c;
}

/* ==================== 资源指标对话框 ==================== */
.metrics-dialog :deep(.el-dialog) {
  border-radius: 16px;
}

.metrics-dialog :deep(.el-dialog__header) {
  padding: 20px 24px 16px;
  border-bottom: 1px solid #f5f5f5;
}

.metrics-overview {
  margin-bottom: 20px;
}

.metric-box {
  text-align: center;
  padding: 20px 16px;
  background: #f7f8fa;
  border-radius: 12px;
  transition: all 0.2s ease;
}

.metric-box:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
}

.cpu-box {
  border-left: 4px solid #409EFF;
}

.mem-box {
  border-left: 4px solid #67C23A;
}

.metric-title {
  font-size: 13px;
  color: #86909c;
  margin-bottom: 8px;
  font-weight: 500;
}

.metric-value {
  font-size: 28px;
  font-weight: 800;
  color: #1d2129;
  margin-bottom: 10px;
  font-variant-numeric: tabular-nums;
}

.metric-value.danger {
  color: #F56C6C;
}

.metric-detail {
  font-size: 12px;
  color: #86909c;
  margin-top: 8px;
}

/* 磁盘分区区域 */
.disk-section {
  margin-top: 8px;
}

.section-divider {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 14px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f0;
}

.section-divider-title {
  font-weight: 600;
  font-size: 15px;
  color: #1d2129;
}

/* 分区网格布局 */
.partition-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}

.partition-item {
  background: #f7f8fa;
  border-radius: 10px;
  padding: 14px 16px;
  transition: all 0.2s ease;
}

.partition-item:hover {
  background: #eef0f3;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.partition-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 8px;
}

.partition-mount {
  font-weight: 700;
  font-size: 15px;
  color: #1d2129;
  font-family: 'Consolas', 'Monaco', monospace;
}

.partition-percent {
  font-weight: 800;
  font-size: 18px;
  font-variant-numeric: tabular-nums;
}

.partition-detail {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 12px;
}

.partition-fs {
  color: #86909c;
  font-family: 'Consolas', 'Monaco', monospace;
}

.partition-size {
  color: #606266;
  font-variant-numeric: tabular-nums;
}

.partition-available {
  margin-top: 4px;
  font-size: 11px;
  color: #a0a4ab;
}

/* ==================== 卡片列表过渡动画 ==================== */
.card-fade-enter-active,
.card-fade-leave-active {
  transition: all 0.4s ease;
}

.card-fade-enter-from {
  opacity: 0;
  transform: translateY(20px) scale(0.95);
}

.card-fade-leave-to {
  opacity: 0;
  transform: translateY(-20px) scale(0.95);
}

/* ==================== 响应式适配 ==================== */
@media (max-width: 768px) {
  .host-grid {
    grid-template-columns: 1fr;
  }

  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .host-card-footer {
    flex-wrap: wrap;
  }
}
</style>
