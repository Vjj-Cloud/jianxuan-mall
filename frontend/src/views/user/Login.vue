<script setup>
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isAdminLogin = computed(() => Boolean(route.meta.adminLogin))
const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    const user = await userStore.login(form)
    ElMessage.success(`欢迎回来，${user.nickname || user.username}`)
    const redirect = route.query.redirect
    if (redirect) {
      router.replace(String(redirect))
    } else {
      router.replace(user.role === 'ADMIN' ? '/admin/products' : '/')
    }
  } finally {
    loading.value = false
  }
}

function fill(username, password) {
  form.username = username
  form.password = password
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="brand">
        <span class="logo-mark">简</span>
        <span class="brand-name">简选商城</span>
      </div>
      <h2 class="title">{{ isAdminLogin ? '管理员登录' : '欢迎登录' }}</h2>
      <p class="subtitle">{{ isAdminLogin ? '仅限管理员账号访问后台' : '登录后可加购、下单、查看订单' }}</p>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="submit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名">
            <template #prefix><el-icon><User /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" show-password>
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-button type="primary" class="submit-btn" :loading="loading" @click="submit">
          登 录
        </el-button>
      </el-form>

      <div class="demo-box">
        <span class="demo-label">演示账号（点击填入）</span>
        <div class="demo-actions">
          <el-tag class="demo-tag" @click="fill('user', 'user123')">用户 user / user123</el-tag>
          <el-tag class="demo-tag" type="warning" @click="fill('admin', 'admin123')">管理员 admin / admin123</el-tag>
        </div>
      </div>

      <div class="footer-links">
        <router-link v-if="!isAdminLogin" to="/register">没有账号？去注册</router-link>
        <router-link v-else to="/login">切换到用户登录</router-link>
        <router-link to="/">返回商城首页</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #eef4ff 0%, #f7f9fc 50%, #eefcf6 100%);
  padding: 24px;
}

.auth-card {
  width: 400px;
  background: #fff;
  border-radius: 16px;
  padding: 36px 32px 28px;
  box-shadow: 0 12px 40px rgba(64, 158, 255, 0.12);
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 26px;
}

.logo-mark {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: linear-gradient(135deg, #409eff, #6c5ce7);
  color: #fff;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand-name {
  font-weight: 700;
  font-size: 16px;
}

.title {
  font-size: 22px;
  margin-bottom: 6px;
}

.subtitle {
  font-size: 13px;
  color: var(--text-sub);
  margin-bottom: 24px;
}

.submit-btn {
  width: 100%;
  margin-top: 4px;
}

.demo-box {
  margin-top: 22px;
  padding: 12px 14px;
  background: #f7f9fc;
  border-radius: 10px;
}

.demo-label {
  font-size: 12px;
  color: var(--text-sub);
}

.demo-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 8px;
}

.demo-tag {
  cursor: pointer;
  align-self: flex-start;
}

.footer-links {
  display: flex;
  justify-content: space-between;
  margin-top: 22px;
  font-size: 13px;
}

.footer-links a {
  color: var(--brand-color);
}
</style>
