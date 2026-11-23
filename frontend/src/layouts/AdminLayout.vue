<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useCartStore } from '@/stores/cart'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const cartStore = useCartStore()

const activeMenu = computed(() => route.path)

function handleSelect(index) {
  router.push(index)
}

function handleLogout() {
  userStore.logout()
  cartStore.reset()
  ElMessage.success('已退出登录')
  router.push('/admin/login')
}
</script>

<template>
  <el-container class="admin-layout">
    <el-aside width="220px" class="aside">
      <div class="admin-logo">
        <span class="logo-mark">简</span>
        <span>简选商城 · 后台</span>
      </div>
      <el-menu :default-active="activeMenu" class="admin-menu" @select="handleSelect">
        <el-menu-item index="/admin/products">
          <el-icon><Goods /></el-icon>
          <span>商品管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/orders">
          <el-icon><Tickets /></el-icon>
          <span>订单管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="admin-header">
        <div class="header-left">
          <el-button link @click="router.push('/')"><el-icon class="mr-4"><Back /></el-icon>返回商城</el-button>
        </div>
        <div class="header-right">
          <span class="welcome">{{ userStore.displayName }}</span>
          <el-button link type="danger" @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main class="admin-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.admin-layout {
  min-height: 100vh;
}

.aside {
  background: #1f2d3d;
  color: #fff;
}

.admin-logo {
  height: 64px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  font-weight: 600;
  color: #fff;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.logo-mark {
  width: 30px;
  height: 30px;
  border-radius: 7px;
  background: linear-gradient(135deg, #409eff, #6c5ce7);
  display: flex;
  align-items: center;
  justify-content: center;
}

.admin-menu {
  border-right: none;
  background: transparent;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: #c0c4cc;
  --el-menu-hover-bg-color: rgba(255, 255, 255, 0.06);
  --el-menu-active-color: #fff;
}

.admin-menu :deep(.el-menu-item.is-active) {
  background: var(--brand-color);
}

.admin-header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.welcome {
  font-size: 14px;
  color: #606266;
}

.admin-main {
  background: var(--page-bg);
  padding: 20px;
}
</style>
