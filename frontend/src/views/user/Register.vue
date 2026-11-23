<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '', confirmPassword: '', nickname: '' })

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '长度 3-20 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.password) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    await userStore.register({
      username: form.username,
      password: form.password,
      nickname: form.nickname || form.username,
    })
    ElMessage.success('注册成功，已自动登录')
    router.replace('/')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <h2 class="title">创建账号</h2>
      <p class="subtitle">注册后可同步购物车与订单</p>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="submit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名（3-20 位）" />
        </el-form-item>
        <el-form-item prop="nickname">
          <el-input v-model="form.nickname" placeholder="昵称（可选）" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码（至少 6 位）" show-password />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" placeholder="确认密码" show-password />
        </el-form-item>
        <el-button type="primary" class="submit-btn" :loading="loading" @click="submit">注 册</el-button>
      </el-form>

      <div class="footer-links">
        <router-link to="/login">已有账号？去登录</router-link>
        <router-link to="/">返回首页</router-link>
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
