<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'

const router = useRouter()
const auth = useAuthStore()
const activeTab = ref('login')
const loading = ref(false)

const loginForm = reactive({ username: '', password: '' })
const registerForm = reactive({ username: '', password: '', confirmPassword: '', nickname: '' })

// Password strength
const passwordStrength = computed(() => {
  const pwd = registerForm.password
  if (!pwd) return { level: 0, text: '', color: '' }
  let score = 0
  if (pwd.length >= 6) score++
  if (pwd.length >= 10) score++
  if (/[A-Z]/.test(pwd)) score++
  if (/[0-9]/.test(pwd)) score++
  if (/[^A-Za-z0-9]/.test(pwd)) score++
  if (score <= 1) return { level: 1, text: '弱', color: '#F56C6C' }
  if (score <= 3) return { level: 2, text: '中', color: '#E6A23C' }
  return { level: 3, text: '强', color: '#67C23A' }
})

const handleLogin = async () => {
  if (!loginForm.username || !loginForm.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await auth.login(loginForm.username, loginForm.password)
    ElMessage.success('登录成功')
    const redirect = (router.currentRoute.value.query.redirect as string) || '/'
    router.push(redirect)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error || '登录失败')
  } finally {
    loading.value = false
  }
}

const handleRegister = async () => {
  if (!registerForm.username || !registerForm.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  if (registerForm.password !== registerForm.confirmPassword) {
    ElMessage.warning('两次密码不一致')
    return
  }
  if (registerForm.password.length < 6) {
    ElMessage.warning('密码至少6位')
    return
  }
  loading.value = true
  try {
    await auth.register(registerForm.username, registerForm.password, registerForm.nickname || undefined)
    ElMessage.success('注册成功')
    router.push('/')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error || '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <!-- Animated background -->
    <div class="login-bg">
      <div class="bg-shape shape-1"></div>
      <div class="bg-shape shape-2"></div>
      <div class="bg-shape shape-3"></div>
    </div>

    <div class="login-container">
      <div class="login-header">
        <div class="login-logo">
          <el-icon :size="40" color="#409EFF"><Monitor /></el-icon>
        </div>
        <h1 class="login-title">Docker Manager</h1>
        <p class="login-subtitle">Docker Compose 运行监控系统</p>
      </div>

      <el-card class="login-card" shadow="never">
        <el-tabs v-model="activeTab" stretch>
          <el-tab-pane label="登录" name="login">
            <el-form :model="loginForm" @submit.prevent="handleLogin" class="login-form">
              <el-form-item>
                <el-input v-model="loginForm.username" placeholder="用户名" size="large" :prefix-icon="User" />
              </el-form-item>
              <el-form-item>
                <el-input v-model="loginForm.password" type="password" placeholder="密码" size="large"
                          :prefix-icon="Lock" show-password @keyup.enter="handleLogin" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" size="large" class="login-btn" :loading="loading"
                           @click="handleLogin">
                  登 录
                </el-button>
              </el-form-item>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="注册" name="register">
            <el-form :model="registerForm" @submit.prevent="handleRegister" class="login-form">
              <el-form-item>
                <el-input v-model="registerForm.username" placeholder="用户名" size="large" :prefix-icon="User" />
              </el-form-item>
              <el-form-item>
                <el-input v-model="registerForm.nickname" placeholder="昵称（可选）" size="large"
                          :prefix-icon="UserFilled" />
              </el-form-item>
              <el-form-item>
                <el-input v-model="registerForm.password" type="password" placeholder="密码（至少6位）" size="large"
                          :prefix-icon="Lock" show-password />
              </el-form-item>
              <!-- Password strength indicator -->
              <div v-if="registerForm.password" class="password-strength">
                <div class="strength-bars">
                  <div v-for="i in 3" :key="i" class="strength-bar"
                       :class="{ active: passwordStrength.level >= i }"
                       :style="{ background: passwordStrength.level >= i ? passwordStrength.color : '#e4e7ed' }">
                  </div>
                </div>
                <span class="strength-text" :style="{ color: passwordStrength.color }">
                  {{ passwordStrength.text }}
                </span>
              </div>
              <el-form-item>
                <el-input v-model="registerForm.confirmPassword" type="password" placeholder="确认密码" size="large"
                          :prefix-icon="Lock" show-password @keyup.enter="handleRegister" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" size="large" class="login-btn" :loading="loading"
                           @click="handleRegister">
                  注 册
                </el-button>
              </el-form-item>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </el-card>

      <div class="login-footer">
        <span>默认账号: admin / admin123</span>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { User, Lock, UserFilled } from '@element-plus/icons-vue'
export default {
  setup() {
    return { User, Lock, UserFilled }
  }
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  background: linear-gradient(135deg, #0c1b2e 0%, #1a3a5c 50%, #0f2840 100%);
  overflow: hidden;
}

/* Animated background shapes */
.login-bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse at 20% 50%, rgba(64, 158, 255, 0.12) 0%, transparent 50%),
    radial-gradient(ellipse at 80% 20%, rgba(103, 194, 58, 0.08) 0%, transparent 50%),
    radial-gradient(ellipse at 50% 80%, rgba(230, 162, 60, 0.06) 0%, transparent 50%);
}

.bg-shape {
  position: absolute;
  border-radius: 50%;
  opacity: 0.06;
  animation: float 20s infinite ease-in-out;
}

.shape-1 {
  width: 400px;
  height: 400px;
  background: #409EFF;
  top: -100px;
  left: -100px;
  animation-delay: 0s;
}

.shape-2 {
  width: 300px;
  height: 300px;
  background: #67C23A;
  bottom: -80px;
  right: -80px;
  animation-delay: -7s;
}

.shape-3 {
  width: 200px;
  height: 200px;
  background: #E6A23C;
  top: 50%;
  right: 20%;
  animation-delay: -14s;
}

@keyframes float {
  0%, 100% { transform: translate(0, 0) scale(1); }
  25% { transform: translate(30px, -30px) scale(1.05); }
  50% { transform: translate(-20px, 20px) scale(0.95); }
  75% { transform: translate(15px, 10px) scale(1.02); }
}

.login-container {
  position: relative;
  z-index: 1;
  width: 420px;
  animation: slideUp 0.6s ease-out;
}

@keyframes slideUp {
  from { opacity: 0; transform: translateY(30px); }
  to { opacity: 1; transform: translateY(0); }
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.login-logo {
  width: 72px;
  height: 72px;
  margin: 0 auto 16px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  transition: transform 0.3s;
}

.login-logo:hover {
  transform: scale(1.05) rotate(3deg);
}

.login-title {
  color: #fff;
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 8px;
  letter-spacing: 1px;
}

.login-subtitle {
  color: rgba(255, 255, 255, 0.5);
  font-size: 14px;
  margin: 0;
}

.login-card {
  border-radius: 16px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.98);
  border: none;
}

.login-card :deep(.el-card__body) {
  padding: 32px 32px 16px;
}

.login-card :deep(.el-tabs__header) {
  margin-bottom: 24px;
}

.login-card :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.login-card :deep(.el-tabs__active-bar) {
  background: linear-gradient(90deg, #409EFF, #66b1ff);
  height: 3px;
  border-radius: 3px;
}

.login-form :deep(.el-input__wrapper) {
  border-radius: 10px;
  padding: 4px 14px;
  transition: box-shadow 0.3s;
}

.login-form :deep(.el-input__wrapper:focus-within) {
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.12);
}

.login-btn {
  width: 100%;
  border-radius: 10px;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  background: linear-gradient(135deg, #409EFF, #337ecc);
  border: none;
  transition: all 0.3s;
}

.login-btn:hover {
  background: linear-gradient(135deg, #66b1ff, #409EFF);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.35);
}

/* Password Strength */
.password-strength {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 4px;
  margin: -8px 0 12px;
}

.strength-bars {
  display: flex;
  gap: 4px;
  flex: 1;
}

.strength-bar {
  height: 4px;
  flex: 1;
  border-radius: 2px;
  background: #e4e7ed;
  transition: background 0.3s;
}

.strength-text {
  font-size: 12px;
  font-weight: 600;
  min-width: 20px;
}

.login-footer {
  text-align: center;
  margin-top: 24px;
  color: rgba(255, 255, 255, 0.4);
  font-size: 12px;
}
</style>
