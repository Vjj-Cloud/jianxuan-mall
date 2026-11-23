<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getProduct } from '@/api/product'
import { useUserStore } from '@/stores/user'
import { useCartStore } from '@/stores/cart'
import { formatPrice } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const cartStore = useCartStore()

const product = ref(null)
const loading = ref(false)
const quantity = ref(1)
const adding = ref(false)

async function fetchProduct() {
  loading.value = true
  try {
    product.value = await getProduct(route.params.id)
    quantity.value = 1
  } finally {
    loading.value = false
  }
}

function ensureLogin() {
  if (!userStore.isLogin) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return false
  }
  return true
}

async function handleAddToCart() {
  if (!ensureLogin()) return
  adding.value = true
  try {
    await cartStore.add(product.value.id, quantity.value)
    ElMessage.success('已加入购物车')
  } finally {
    adding.value = false
  }
}

function handleBuyNow() {
  if (!ensureLogin()) return
  router.push({ path: '/checkout', query: { productId: product.value.id, quantity: quantity.value } })
}

watch(() => route.params.id, fetchProduct)
onMounted(fetchProduct)
</script>

<template>
  <div class="page-wrap">
    <el-skeleton v-if="loading" :rows="8" animated />

    <template v-else-if="product">
      <el-breadcrumb separator="/" class="breadcrumb">
        <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item :to="{ path: '/products' }">全部商品</el-breadcrumb-item>
        <el-breadcrumb-item>{{ product.categoryName }}</el-breadcrumb-item>
        <el-breadcrumb-item>{{ product.name }}</el-breadcrumb-item>
      </el-breadcrumb>

      <div class="detail-card">
        <div class="gallery">
          <img :src="product.cover" :alt="product.name" class="main-image" />
        </div>

        <div class="info">
          <h1 class="name">{{ product.name }}</h1>
          <p class="subtitle">{{ product.subtitle }}</p>

          <div class="price-box">
            <div class="price-line">
              <span class="price-label">价格</span>
              <span class="price big">¥{{ formatPrice(product.price) }}</span>
              <span v-if="product.originalPrice > product.price" class="origin">
                ¥{{ formatPrice(product.originalPrice) }}
              </span>
            </div>
            <div class="meta-line">
              <span>销量 {{ product.sales }}</span>
              <el-divider direction="vertical" />
              <span>库存 {{ product.stock }}</span>
            </div>
          </div>

          <div class="quantity-row">
            <span class="label">数量</span>
            <el-input-number v-model="quantity" :min="1" :max="Math.max(product.stock, 1)" />
            <span v-if="product.stock === 0" class="out-stock">已售罄</span>
          </div>

          <div class="action-row">
            <el-button
              size="large"
              type="primary"
              plain
              :disabled="product.stock === 0"
              :loading="adding"
              @click="handleAddToCart"
            >
              加入购物车
            </el-button>
            <el-button size="large" type="danger" :disabled="product.stock === 0" @click="handleBuyNow">
              立即购买
            </el-button>
          </div>

          <div class="service-row">
            <el-tag type="success" effect="plain">正品保障</el-tag>
            <el-tag type="success" effect="plain">极速发货</el-tag>
            <el-tag type="success" effect="plain">7 天无理由</el-tag>
          </div>
        </div>
      </div>

      <el-card shadow="never" class="desc-card">
        <template #header><span class="desc-title">商品详情</span></template>
        <p class="desc-text">{{ product.desc }}</p>
        <img :src="product.cover" :alt="product.name" class="desc-image" />
      </el-card>
    </template>

    <el-empty v-else description="商品不存在或已下架" />
  </div>
</template>

<style scoped>
.breadcrumb {
  margin-bottom: 16px;
}

.detail-card {
  background: #fff;
  border-radius: 12px;
  padding: 28px;
  display: flex;
  gap: 40px;
}

.gallery {
  width: 420px;
  flex-shrink: 0;
}

.main-image {
  width: 100%;
  border-radius: 10px;
  aspect-ratio: 1 / 1;
  object-fit: cover;
}

.info {
  flex: 1;
  min-width: 0;
}

.name {
  font-size: 24px;
  line-height: 1.4;
}

.subtitle {
  color: var(--text-sub);
  margin: 10px 0 20px;
}

.price-box {
  background: linear-gradient(90deg, #fff5f5, #fff);
  border-radius: 8px;
  padding: 16px 20px;
}

.price-line {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.price-label {
  color: var(--text-sub);
  font-size: 13px;
}

.price.big {
  font-size: 32px;
}

.origin {
  color: #c0c4cc;
  text-decoration: line-through;
  font-size: 14px;
}

.meta-line {
  margin-top: 10px;
  font-size: 13px;
  color: var(--text-sub);
}

.quantity-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 24px 0;
}

.quantity-row .label {
  color: var(--text-sub);
  font-size: 14px;
}

.out-stock {
  color: var(--price-color);
  font-size: 13px;
}

.action-row {
  display: flex;
  gap: 14px;
}

.service-row {
  display: flex;
  gap: 10px;
  margin-top: 26px;
}

.desc-card {
  margin-top: 20px;
  border-radius: 12px;
}

.desc-title {
  font-weight: 600;
}

.desc-text {
  color: #606266;
  line-height: 1.9;
  margin-bottom: 18px;
}

.desc-image {
  width: 100%;
  max-width: 640px;
  border-radius: 10px;
  display: block;
}

@media (max-width: 800px) {
  .detail-card {
    flex-direction: column;
  }
  .gallery {
    width: 100%;
  }
}
</style>
