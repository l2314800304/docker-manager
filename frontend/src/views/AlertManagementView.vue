<script setup lang="ts">
/**
 * 告警管理页面。
 *
 * <p>包含两个标签页：</p>
 * <ul>
 *   <li><b>告警规则</b> — 管理告警规则（增删改查、启用/禁用、测试通知）</li>
 *   <li><b>告警记录</b> — 查看历史告警触发记录（自动刷新）</li>
 * </ul>
 */
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import {
  getAlertRules, addAlertRule, updateAlertRule, deleteAlertRule,
  getAlertRecords, testNotification, getHosts
} from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

// ==================== 响应式数据 ====================

/** 当前激活的标签页（rules / records） */
const activeTab = ref('rules')

/** 告警规则列表 */
const rules = ref<any[]>([])
/** 规则列表加载状态 */
const rulesLoading = ref(false)

/** 告警记录列表 */
const records = ref<any[]>([])
/** 记录列表加载状态 */
const recordsLoading = ref(false)

/** 宿主机列表（用于规则表单中选择） */
const hosts = ref<any[]>([])

/** 新增/编辑规则对话框可见性 */
const dialogVisible = ref(false)
/** 对话框标题（新增/编辑） */
const dialogTitle = ref('新增告警规则')
/** 当前编辑的规则 ID（新增时为 null） */
const editingId = ref<number | null>(null)
/** 表单提交加载状态 */
const submitting = ref(false)

/** 规则表单数据 */
const ruleForm = reactive({
  name: '',
  hostId: null as number | null,
  metricType: 'HOST_CPU',
  compareOperator: '>',
  threshold: 80,
  duration: 0,
  notifyType: 'DINGTALK',
  webhookUrl: '',
  dingtalkSecret: '',
  cooldown: 300,
  enabled: true,
})

/** 记录自动刷新定时器 */
let refreshTimer: ReturnType<typeof setInterval> | null = null

// ==================== 指标类型映射 ====================

/** 指标类型枚举 → 中文显示名 */
const metricTypeOptions = [
  { value: 'HOST_CPU', label: '宿主机CPU' },
  { value: 'HOST_MEMORY', label: '宿主机内存' },
  { value: 'HOST_DISK', label: '宿主机磁盘' },
  { value: 'CONTAINER_CPU', label: '容器CPU' },
  { value: 'CONTAINER_MEMORY', label: '容器内存' },
  { value: 'CONTAINER_STOPPED', label: '容器停止' },
]

/** 比较运算符选项 */
const operatorOptions = [
  { value: '>', label: '>' },
  { value: '<', label: '<' },
  { value: '>=', label: '>=' },
  { value: '<=', label: '<=' },
  { value: '==', label: '==' },
]

/** 通知类型选项 */
const notifyTypeOptions = [
  { value: 'DINGTALK', label: '钉钉' },
]

/** 指标类型 → 标签颜色 */
const metricTagType: Record<string, string> = {
  HOST_CPU: '',
  HOST_MEMORY: 'success',
  HOST_DISK: 'warning',
  CONTAINER_CPU: 'info',
  CONTAINER_MEMORY: 'danger',
  CONTAINER_STOPPED: 'danger',
}

/** 获取指标类型中文名 */
const getMetricLabel = (type: string) =>
  metricTypeOptions.find(o => o.value === type)?.label ?? type

// ==================== 数据加载 ====================

/** 加载告警规则列表 */
const fetchRules = async () => {
  rulesLoading.value = true
  try {
    const res = await getAlertRules()
    rules.value = res.data || []
  } catch {
    ElMessage.error('获取告警规则失败')
  } finally {
    rulesLoading.value = false
  }
}

/** 加载告警记录列表 */
const fetchRecords = async () => {
  recordsLoading.value = true
  try {
    const res = await getAlertRecords(50)
    records.value = res.data || []
  } catch {
    ElMessage.error('获取告警记录失败')
  } finally {
    recordsLoading.value = false
  }
}

/** 加载宿主机列表（供表单选择用） */
const fetchHosts = async () => {
  try {
    const res = await getHosts()
    hosts.value = res.data || []
  } catch {
    hosts.value = []
  }
}

// ==================== 规则 CRUD 操作 ====================

/**
 * 打开新增规则对话框。
 * 重置表单为默认值，清空编辑 ID。
 */
const openAddDialog = () => {
  editingId.value = null
  dialogTitle.value = '新增告警规则'
  Object.assign(ruleForm, {
    name: '',
    hostId: null,
    metricType: 'HOST_CPU',
    compareOperator: '>',
    threshold: 80,
    duration: 0,
    notifyType: 'DINGTALK',
    webhookUrl: '',
    dingtalkSecret: '',
    cooldown: 300,
    enabled: true,
  })
  fetchHosts()
  dialogVisible.value = true
}

/**
 * 打开编辑规则对话框。
 * 将已有规则数据填充到表单中。
 */
const openEditDialog = (row: any) => {
  editingId.value = row.id
  dialogTitle.value = '编辑告警规则'
  Object.assign(ruleForm, {
    name: row.name,
    hostId: row.hostId ?? null,
    metricType: row.metricType,
    compareOperator: row.compareOperator,
    threshold: row.threshold,
    duration: row.duration ?? 0,
    notifyType: row.notifyType,
    webhookUrl: row.webhookUrl,
    dingtalkSecret: row.dingtalkSecret ?? '',
    cooldown: row.cooldown ?? 300,
    enabled: row.enabled,
  })
  fetchHosts()
  dialogVisible.value = true
}

/** 提交新增/编辑表单 */
const handleSubmit = async () => {
  // 表单校验
  if (!ruleForm.name.trim()) {
    ElMessage.warning('请输入规则名称')
    return
  }
  if (!ruleForm.webhookUrl.trim()) {
    ElMessage.warning('请输入 WebHook URL')
    return
  }

  submitting.value = true
  try {
    const payload = { ...ruleForm }
    if (editingId.value) {
      await updateAlertRule(editingId.value, payload)
      ElMessage.success('规则更新成功')
    } else {
      await addAlertRule(payload)
      ElMessage.success('规则创建成功')
    }
    dialogVisible.value = false
    fetchRules()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error || '操作失败')
  } finally {
    submitting.value = false
  }
}

/** 切换规则启用/禁用状态 */
const handleToggle = async (row: any) => {
  try {
    await updateAlertRule(row.id, { ...row, enabled: !row.enabled })
    ElMessage.success(row.enabled ? '已禁用' : '已启用')
    fetchRules()
  } catch {
    ElMessage.error('切换状态失败')
  }
}

/** 删除规则（带二次确认） */
const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm(
      `确认删除规则「${row.name}」？此操作不可恢复`,
      '警告',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
    await deleteAlertRule(row.id)
    ElMessage.success('删除成功')
    fetchRules()
  } catch { /* 用户取消 */ }
}

/** 测试通知发送 */
const handleTestNotify = async (row: any) => {
  try {
    await testNotification({
      notifyType: row.notifyType,
      webhookUrl: row.webhookUrl,
      dingtalkSecret: row.dingtalkSecret,
    })
    ElMessage.success('测试通知已发送')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error || '测试通知发送失败')
  }
}

// ==================== 工具函数 ====================

/** 格式化时间字符串（去掉 T 和毫秒） */
const formatTime = (t: string) => {
  if (!t) return '—'
  return t.replace('T', ' ').substring(0, 19)
}

/** 获取宿主机名称（根据 hostId 查找） */
const getHostName = (hostId: number | null) => {
  if (!hostId) return '所有宿主机'
  const h = hosts.value.find((h: any) => h.id === hostId)
  return h?.name ?? `ID: ${hostId}`
}

// ==================== 生命周期 ====================

onMounted(() => {
  fetchRules()
  fetchRecords()
  fetchHosts()
  // 告警记录每 30 秒自动刷新
  refreshTimer = setInterval(fetchRecords, 30000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<template>
  <div class="alert-management">
    <!-- 页面标题区域 -->
    <div class="page-header">
      <div class="page-header-left">
        <h2 class="page-title">告警管理</h2>
        <el-tag type="info" size="small">{{ rules.length }} 条规则</el-tag>
      </div>
    </div>

    <!-- ==================== 标签页 ==================== -->
    <el-tabs v-model="activeTab" class="alert-tabs">

      <!-- ==================== Tab 1: 告警规则 ==================== -->
      <el-tab-pane label="告警规则" name="rules">
        <div class="tab-toolbar">
          <el-button type="primary" @click="openAddDialog">
            <el-icon><Plus /></el-icon> 新增规则
          </el-button>
          <el-button @click="fetchRules">
            <el-icon><Refresh /></el-icon> 刷新
          </el-button>
        </div>

        <!-- 规则列表表格 -->
        <el-table
          :data="rules"
          v-loading="rulesLoading"
          stripe
          class="data-table"
          style="width: 100%;"
        >
          <!-- 规则名称 -->
          <el-table-column prop="name" label="规则名称" min-width="140">
            <template #default="{ row }">
              <span class="rule-name">{{ row.name }}</span>
            </template>
          </el-table-column>

          <!-- 宿主机 -->
          <el-table-column label="宿主机" min-width="120">
            <template #default="{ row }">
              <el-tag :type="row.hostId ? '' : 'info'" size="small" effect="light" round>
                {{ row.hostId ? getHostName(row.hostId) : '所有宿主机' }}
              </el-tag>
            </template>
          </el-table-column>

          <!-- 指标类型 -->
          <el-table-column label="指标类型" width="130">
            <template #default="{ row }">
              <el-tag :type="(metricTagType[row.metricType] as any) || 'info'" size="small" effect="dark" round>
                {{ getMetricLabel(row.metricType) }}
              </el-tag>
            </template>
          </el-table-column>

          <!-- 阈值条件 -->
          <el-table-column label="阈值条件" width="120">
            <template #default="{ row }">
              <span class="threshold-text">{{ row.compareOperator }} {{ row.threshold }}</span>
            </template>
          </el-table-column>

          <!-- 通知方式 -->
          <el-table-column label="通知方式" width="100">
            <template #default="{ row }">
              <el-tag type="primary" size="small" effect="plain" round>
                {{ row.notifyType === 'DINGTALK' ? '钉钉' : row.notifyType }}
              </el-tag>
            </template>
          </el-table-column>

          <!-- 启用状态 -->
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-switch
                :model-value="row.enabled"
                @change="handleToggle(row)"
                inline-prompt
                active-text="开"
                inactive-text="关"
              />
            </template>
          </el-table-column>

          <!-- 上次触发时间 -->
          <el-table-column label="上次触发" min-width="160">
            <template #default="{ row }">
              <span class="time-text">{{ formatTime(row.lastTriggeredAt) }}</span>
            </template>
          </el-table-column>

          <!-- 操作列 -->
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEditDialog(row)">
                <el-icon><Edit /></el-icon> 编辑
              </el-button>
              <el-button link type="success" @click="handleTestNotify(row)">
                <el-icon><Promotion /></el-icon> 测试
              </el-button>
              <el-button link type="danger" @click="handleDelete(row)">
                <el-icon><Delete /></el-icon> 删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ==================== Tab 2: 告警记录 ==================== -->
      <el-tab-pane label="告警记录" name="records">
        <div class="tab-toolbar">
          <el-button @click="fetchRecords">
            <el-icon><Refresh /></el-icon> 刷新
          </el-button>
          <span class="refresh-hint">每 30 秒自动刷新</span>
        </div>

        <!-- 告警记录表格 -->
        <el-table
          :data="records"
          v-loading="recordsLoading"
          stripe
          class="data-table"
          style="width: 100%;"
        >
          <!-- 触发时间 -->
          <el-table-column label="时间" min-width="170">
            <template #default="{ row }">
              <span class="time-text">{{ formatTime(row.triggeredAt || row.createdAt) }}</span>
            </template>
          </el-table-column>

          <!-- 规则名称 -->
          <el-table-column prop="ruleName" label="规则名称" min-width="140" show-overflow-tooltip />

          <!-- 宿主机 -->
          <el-table-column prop="hostName" label="宿主机" min-width="120" show-overflow-tooltip />

          <!-- 指标类型 -->
          <el-table-column label="指标" width="120">
            <template #default="{ row }">
              <el-tag :type="(metricTagType[row.metricType] as any) || 'info'" size="small" round>
                {{ getMetricLabel(row.metricType) }}
              </el-tag>
            </template>
          </el-table-column>

          <!-- 当前值 -->
          <el-table-column label="当前值" width="100">
            <template #default="{ row }">
              <span class="metric-value">{{ row.metricValue != null ? Number(row.metricValue).toFixed(1) : '—' }}</span>
            </template>
          </el-table-column>

          <!-- 阈值 -->
          <el-table-column label="阈值" width="100">
            <template #default="{ row }">
              <span class="threshold-text">{{ row.compareOperator }} {{ row.threshold }}</span>
            </template>
          </el-table-column>

          <!-- 发送状态（SENT / FAILED） -->
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag
                :type="row.sendStatus === 'SENT' ? 'success' : 'danger'"
                size="small"
                effect="dark"
                round
              >
                {{ row.sendStatus === 'SENT' ? '已发送' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>

          <!-- 结果/错误信息 -->
          <el-table-column label="结果" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <span :class="row.sendStatus === 'SENT' ? 'result-ok' : 'result-fail'">
                {{ row.errorMessage || (row.sendStatus === 'SENT' ? '通知发送成功' : '通知发送失败') }}
              </span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- ==================== 新增/编辑规则对话框 ==================== -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600"
      destroy-on-close
      class="rule-dialog"
    >
      <el-form :model="ruleForm" label-width="110px" class="rule-form">

        <!-- 规则名称（必填） -->
        <el-form-item label="规则名称" required>
          <el-input v-model="ruleForm.name" placeholder="例如：CPU 使用率过高" maxlength="100" />
        </el-form-item>

        <!-- 宿主机选择（全部 / 指定） -->
        <el-form-item label="宿主机">
          <el-select v-model="ruleForm.hostId" placeholder="所有宿主机" clearable style="width: 100%;">
            <el-option label="所有宿主机" :value="null" />
            <el-option
              v-for="h in hosts"
              :key="h.id"
              :label="h.name"
              :value="h.id"
            />
          </el-select>
        </el-form-item>

        <!-- 指标类型 -->
        <el-form-item label="指标类型">
          <el-select v-model="ruleForm.metricType" style="width: 100%;">
            <el-option
              v-for="opt in metricTypeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <!-- 比较运算符 + 阈值（同行显示） -->
        <el-form-item label="触发条件">
          <div class="condition-row">
            <el-select v-model="ruleForm.compareOperator" style="width: 100px;">
              <el-option
                v-for="opt in operatorOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <el-input-number
              v-model="ruleForm.threshold"
              :min="0"
              :max="100"
              :precision="1"
              :step="5"
              style="width: 160px;"
            />
            <span class="condition-hint">（百分比，0 ~ 100）</span>
          </div>
        </el-form-item>

        <!-- 持续时间 -->
        <el-form-item label="持续时间">
          <el-input-number v-model="ruleForm.duration" :min="0" :max="3600" :step="10" style="width: 160px;" />
          <span class="field-hint">秒（连续超过该时间后触发，默认 0）</span>
        </el-form-item>

        <!-- 通知类型 -->
        <el-form-item label="通知类型">
          <el-select v-model="ruleForm.notifyType" style="width: 100%;">
            <el-option
              v-for="opt in notifyTypeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <!-- WebHook URL（必填） -->
        <el-form-item label="WebHook URL" required>
          <el-input v-model="ruleForm.webhookUrl" placeholder="https://oapi.dingtalk.com/robot/send?access_token=..." />
        </el-form-item>

        <!-- 钉钉签名密钥（可选） -->
        <el-form-item label="签名密钥">
          <el-input v-model="ruleForm.dingtalkSecret" placeholder="钉钉加签密钥（可选）" show-password />
        </el-form-item>

        <!-- 冷却时间 -->
        <el-form-item label="冷却时间">
          <el-input-number v-model="ruleForm.cooldown" :min="0" :max="86400" :step="60" style="width: 160px;" />
          <span class="field-hint">秒（同一规则在此时间内不重复告警，默认 300）</span>
        </el-form-item>

        <!-- 是否启用 -->
        <el-form-item label="启用">
          <el-switch v-model="ruleForm.enabled" />
        </el-form-item>
      </el-form>

      <!-- 对话框底部按钮 -->
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ editingId ? '保存修改' : '创建规则' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
/* ==================== 页面整体布局 ==================== */
.alert-management {
  max-width: 1400px;
}

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

/* ==================== 标签页样式 ==================== */
.alert-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.alert-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
}

/* ==================== 工具栏（表格上方按钮区域） ==================== */
.tab-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  padding-top: 16px;
}

.refresh-hint {
  font-size: 12px;
  color: #c0c4cc;
}

/* ==================== 数据表格样式 ==================== */
.data-table {
  border-radius: 10px;
  overflow: hidden;
}

.data-table :deep(.el-table__header th) {
  background: #f7f8fa;
  color: #606266;
  font-weight: 600;
  font-size: 13px;
}

.rule-name {
  font-weight: 600;
  color: #1d2129;
}

.threshold-text {
  font-family: 'Consolas', 'Monaco', monospace;
  font-weight: 600;
  font-size: 13px;
  color: #e6a23c;
}

.metric-value {
  font-family: 'Consolas', 'Monaco', monospace;
  font-weight: 600;
  font-size: 13px;
  color: #409eff;
}

.time-text {
  font-size: 13px;
  color: #86909c;
  font-variant-numeric: tabular-nums;
}

/* 告警记录结果列颜色 */
.result-ok {
  color: #67c23a;
  font-size: 13px;
}

.result-fail {
  color: #f56c6c;
  font-size: 13px;
}

/* ==================== 对话框表单样式 ==================== */
.rule-dialog :deep(.el-dialog) {
  border-radius: 14px;
}

.rule-form {
  padding: 10px 0 0;
}

/* 触发条件行（运算符 + 阈值 同行显示） */
.condition-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.condition-hint {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
}

/* 字段提示文本 */
.field-hint {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
}
</style>
