<script setup>
import { onMounted, ref } from 'vue'
import { listProducts, listCategories } from '@/api/product'
import ProductCard from '@/components/ProductCard.vue'

const hotProducts = ref([])
const newProducts = ref([])
const categories = ref([])
const loading = ref(false)

const banners = [
  { title: '秋季焕新季', sub: '精选好物 低至 5 折', bg: 'linear-gradient(120deg, #667eea 0%, #764ba2 100%)' },
  { title: '数码狂欢周', sub: '旗舰新品 限时直降', bg: 'linear-gradient(120deg, #f093fb 0%, #f5576c 100%)' },
  { title: '居家生活节', sub: '舒适生活 从此开始', bg: 'linear-gradient(120deg, #43e97b 0%, #38f9d7 100%)' },
]

const categoryIcons = ['Cellphone', 'Monitor', 'HomeFilled', 'ShoppingBag', 'Apple']

onMounted(async () => {
  loading.value = true
  try {
    const [hot, latest, cate] = await Promise.all([
      listProducts({ sort: 'salesDesc', size: 8 }),
      listProducts({ size: 8 }),
      listCategories(),
    ])
    hotProducts.value = hot.list
    newProducts.value = latest.list
    categories.value = cate
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div>
    <div class="hero">
      <el-carousel height="320px" :interval="4000" class="banner">
        <el-carousel-item v-for="banner in banners" :key="banner.title">
          <div class="banner-slide" :style="{ background: banner.bg }">
            <div>
              <h2>{{ banner.title }}</h2>
              <p>{{ banner.sub }}</p>
              <el-button round size="large" @click="$router.push('/products')">立即选购</el-button>
            </div>
          </div>
        </el-carousel-item>
      </el-carousel>

      <div class="category-panel">
        <h3>商品分类</h3>
        <router-link
          v-for="(cate, index) in categories"
          :key="cate.id"
          :to="`/products?categoryId=${cate.id}`"
          class="category-item"
        >
          <el-icon :size="20"><component :is="categoryIcons[index % categoryIcons.length]" /></el-icon>
          <span>{{ cate.name }}</span>
          <el-icon class="arrow"><ArrowRight /></el-icon>
        </router-link>
      </div>
    </div>

    <div class="page-wrap">
      <div class="section-head">
        <h2 class="section-title">热销榜单</h2>
        <router-link to="/products" class="more-link">查看全部 ›</router-link>
      </div>
      <el-skeleton v-if="loading" :rows="4" animated />
      <div v-else class="grid">
        <ProductCard v-for="product in hotProducts" :key="product.id" :product="product" />
      </div>

      <div class="section-head">
        <h2 class="section-title">新品上架</h2>
        <router-link to="/products" class="more-link">查看全部 ›</router-link>
      </div>
      <div v-if="!loading" class="grid">
        <ProductCard v-for="product in newProducts" :key="product.id" :product="product" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.hero {
  max-width: 1200px;
  margin: 20px auto 0;
  padding: 0 16px;
  display: flex;
  gap: 16px;
}

.banner {
  flex: 1;
  border-radius: 12px;
  overflow: hidden;
}

.banner-slide {
  height: 100%;
  display: flex;
  align-items: center;
  padding: 0 60px;
  color: #fff;
}

.banner-slide h2 {
  font-size: 38px;
  margin-bottom: 10px;
}

.banner-slide p {
  font-size: 16px;
  opacity: 0.9;
  margin-bottom: 22px;
}

.banner-slide .el-button {
  color: #303133;
  font-weight: 600;
}

.category-panel {
  width: 220px;
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  flex-shrink: 0;
}

.category-panel h3 {
  font-size: 15px;
  margin-bottom: 8px;
  padding-left: 8px;
}

.category-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 8px;
  border-radius: 8px;
  color: #606266;
  font-size: 14px;
}

.category-item:hover {
  background: #f0f7ff;
  color: var(--brand-color);
}

.category-item .arrow {
  margin-left: auto;
  font-size: 12px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.more-link {
  font-size: 13px;
  color: var(--text-sub);
}

.more-link:hover {
  color: var(--brand-color);
}

.grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 36px;
}

@media (max-width: 900px) {
  .hero {
    flex-direction: column;
  }
  .category-panel {
    width: auto;
  }
  .grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
