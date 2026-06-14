<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { adminListUsers, adminToggleUser, adminDeleteUser, adminResetPassword } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const users = ref<any[]>([])
const loading = ref(false)
const showResetDialog = ref(false)
const resetUserId = ref<number | null>(null)
const resetPassword = ref('')

const fetchUsers = async () => {
  loading.value = true
  try {
    const res = await adminListUsers()
    users.value = res.data
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error || '获取用户列表失败')
  } finally {
    loading.value = false
  }
}

const handleToggle = async (user: any) => {
  const action = user.enabled ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(`确认${action}用户 "${user.username}"？`, '确认')
    await adminToggleUser(user.id)
    ElMessage.success(`${action}成功`)
    fetchUsers()
  } catch {}
}

const handleDelete = async (user: any) => {
  try {
    await ElMessageBox.confirm(`确认删除用户 "${user.username}"？此操作不可恢复`, '警告', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
    await adminDeleteUser(user.id)
    ElMessage.success('删除成功')
    fetchUsers()
  } catch {}
}

const openResetDialog = (user: any) => {
  resetUserId.value = user.id
  resetPassword.value = ''
  showResetDialog.value = true
}

const handleResetPassword = async () => {
  if (!resetUserId.value) return
  if (resetPassword.value.length < 6) {
    ElMessage.warning('密码至少6位')
    return
  }
  try {
    await adminResetPassword(resetUserId.value, resetPassword.value)
    ElMessage.success('密码重置成功')
    showResetDialog.value = false
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error || '重置失败')
  }
}

onMounted(fetchUsers)
</script>

<template>
  <div class="user-management" v-loading="loading">
    <div class="page-header">
      <div class="page-header-left">
        <h2 class="page-title">用户管理</h2>
        <el-tag type="info" size="small">{{ users.length }} 位用户</el-tag>
      </div>
      <el-button type="primary" @click="fetchUsers">
        <el-icon><Refresh /></el-icon> 刷新
      </el-button>
    </div>

    <el-card class="user-table-card">
      <el-table :data="users" stripe style="width: 100%;">
        <el-table-column prop="username" label="用户名" min-width="120">
          <template #default="{ row }">
            <div class="username-cell">
              <el-avatar :size="28" class="user-avatar-sm">
                {{ (row.nickname || row.username)?.charAt(0)?.toUpperCase() }}
              </el-avatar>
              <span class="username-text">{{ row.username }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="nickname" label="昵称" min-width="120" />
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'" size="small" effect="dark" round>
              {{ row.role }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'danger'" size="small" round>
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="160">
          <template #default="{ row }">
            <span class="time-text">{{ row.createdAt?.replace('T', ' ')?.substring(0, 19) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="最后登录" min-width="160">
          <template #default="{ row }">
            <span class="time-text">{{ row.lastLoginAt ? row.lastLoginAt.replace('T', ' ').substring(0, 19) : '从未登录' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link :type="row.enabled ? 'warning' : 'success'" @click="handleToggle(row)">
              <el-icon><component :is="row.enabled ? 'CircleClose' : 'CircleCheck'" /></el-icon>
              {{ row.enabled ? '禁用' : '启用' }}
            </el-button>
            <el-button link type="primary" @click="openResetDialog(row)">
              <el-icon><Lock /></el-icon> 重置密码
            </el-button>
            <el-button link type="danger" @click="handleDelete(row)" :disabled="row.role === 'ADMIN'">
              <el-icon><Delete /></el-icon> 删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Reset Password Dialog -->
    <el-dialog v-model="showResetDialog" title="重置密码" width="400">
      <el-form>
        <el-form-item label="新密码">
          <el-input v-model="resetPassword" type="password" show-password placeholder="至少6位" @keyup.enter="handleResetPassword" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showResetDialog = false">取消</el-button>
        <el-button type="primary" @click="handleResetPassword">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.user-management {
  max-width: 1200px;
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

.user-table-card {
  border-radius: 12px;
  border: none;
}

.user-table-card :deep(.el-card__body) {
  padding: 4px;
}

.username-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-avatar-sm {
  background: linear-gradient(135deg, #409EFF, #337ecc);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.username-text {
  font-weight: 500;
}

.time-text {
  font-size: 13px;
  color: #86909c;
}
</style>
