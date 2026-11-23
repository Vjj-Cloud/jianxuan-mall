<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useCartStore } from '@/stores/cart'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const cartStore = useCartStore()
const keyword = ref('')

onMounted(() => {
  cartStore.load()
})

watch(
  () => userStore.token,
  () => cartStore.load(),
)

function handleSearch() {
  router.push({ path: '/products', query: keyword.value ? { keyword: keyword.value } : {} })
}

function handleCommand(command) {
  if (command === 'logout') {
    userStore.logout()
    cartStore.reset()
    ElMessage.success('已退出登录')
    router.push('/')
  } else {
    router.push(command)
  }
}
</script>

<template>
  <div class="user-layout">
    <header class="top-bar">
      <div class="top-bar-inner">
        <router-link to="/" class="logo">
          <span class="logo-mark">简</span>
          <span class="logo-text">简选商城</span>
        </router-link>

        <nav class="nav">
          <router-link to="/" class="nav-item" :class="{ active: route.name === 'home' }">首页</router-link>
          <router-link to="/products" class="nav-item" :class="{ active: route.name === 'products' }">全部商品</router-link>
          <router-link to="/orders" class="nav-item" :class="{ active: route.name === 'orders' }">我的订单</router-link>
        </nav>

        <div class="actions">
          <el-input
            v-model="keyword"
            class="search-input"
            placeholder="搜索商品"
            clearable
            @keyup.enter="handleSearch"
          >
            <template #append>
              <el-button @click="handleSearch"><el-icon><Search /></el-icon></el-button>
            </template>
          </el-input>

          <router-link to="/cart" class="cart-link">
            <el-badge :value="cartStore.count" :hidden="!cartStore.count">
              <el-button circle><el-icon><ShoppingCart /></el-icon></el-button>
            </el-badge>
          </router-link>

          <template v-if="userStore.isLogin">
            <el-dropdown @command="handleCommand">
              <span class="user-entry">
                <el-avatar :size="30">{{ userStore.displayName.slice(0, 1) }}</el-avatar>
                <span class="user-name">{{ userStore.displayName }}</span>
                <el-icon><ArrowDown /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="/orders">我的订单</el-dropdown-item>
                  <el-dropdown-item v-if="userStore.isAdmin" command="/admin">管理后台</el-dropdown-item>
                  <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <template v-else>
            <router-link to="/login"><el-button type="primary" plain>登录</el-button></router-link>
            <router-link to="/register"><el-button>注册</el-button></router-link>
          </template>
        </div>
      </div>
    </header>

    <main class="main-content">
      <router-view />
    </main>

    <footer class="footer">
      <p>简选商城 · 前后端分离练手项目 · 前端 Vue 3 + Element Plus</p>
      <p class="footer-sub">仅用于学习交流，非真实交易平台</p>
    </footer>
  </div>
</template>

<style scoped>
.user-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.top-bar {
  position: sticky;
  top: 0;
  z-index: 100;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.top-bar-inner {
  max-width: 1200px;
  margin: 0 auto;
  height: 64px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  gap: 28px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.logo-mark {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: linear-gradient(135deg, #409eff, #6c5ce7);
  color: #fff;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.logo-text {
  font-size: 18px;
  font-weight: 700;
}

.nav {
  display: flex;
  gap: 24px;
}

.nav-item {
  font-size: 15px;
  color: #606266;
  padding: 6px 0;
  border-bottom: 2px solid transparent;
}

.nav-item.active,
.nav-item:hover {
  color: var(--brand-color);
  border-bottom-color: var(--brand-color);
}

.actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 14px;
}

.search-input {
  width: 260px;
}

.cart-link {
  display: flex;
  align-items: center;
}

.user-entry {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}

.user-name {
  font-size: 14px;
  color: #303133;
}

.main-content {
  flex: 1;
}

.footer {
  background: #fff;
  border-top: 1px solid #ebeef5;
  padding: 24px 16px;
  text-align: center;
  color: var(--text-sub);
  font-size: 13px;
  line-height: 1.9;
}

.footer-sub {
  font-size: 12px;
  color: #c0c4cc;
}
</style>
