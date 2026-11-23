<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listProducts, listCategories } from '@/api/product'
import ProductCard from '@/components/ProductCard.vue'

const route = useRoute()
const router = useRouter()

const products = ref([])
const categories = ref([])
const total = ref(0)
const loading = ref(false)
const keyword = ref('')
const categoryId = ref('')
const sort = ref('')

const page = ref(1)
const size = 12

async function fetchProducts() {
  loading.value = true
  try {
    const data = await listProducts({
      keyword: keyword.value || undefined,
      categoryId: categoryId.value || undefined,
      sort: sort.value || undefined,
      page: page.value,
      size,
    })
    products.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function syncFromQuery() {
  keyword.value = route.query.keyword || ''
  categoryId.value = route.query.categoryId ? Number(route.query.categoryId) : ''
  sort.value = route.query.sort || ''
  page.value = Number(route.query.page) || 1
}

function pushQuery(patch = {}) {
  router.push({
    path: '/products',
    query: {
      ...(keyword.value ? { keyword: keyword.value } : {}),
      ...(categoryId.value ? { categoryId: categoryId.value } : {}),
      ...(sort.value ? { sort: sort.value } : {}),
      ...(page.value > 1 ? { page: page.value } : {}),
      ...patch,
    },
  })
}

function handleCategoryChange(value) {
  page.value = 1
  pushQuery({ categoryId: value || undefined, page: undefined })
}

function handleSortChange() {
  page.value = 1
  pushQuery({ sort: sort.value || undefined, page: undefined })
}

function handlePageChange(p) {
  page.value = p
  pushQuery({ page: p })
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

watch(() => route.query, () => {
  syncFromQuery()
  fetchProducts()
})

onMounted(async () => {
  syncFromQuery()
  categories.value = await listCategories()
  fetchProducts()
})
</script>

<template>
  <div class="page-wrap">
    <el-card shadow="never" class="filter-card">
      <div class="filter-row">
        <span class="filter-label">分类</span>
        <el-radio-group v-model="categoryId" @change="handleCategoryChange">
          <el-radio-button :value="''">全部</el-radio-button>
          <el-radio-button v-for="cate in categories" :key="cate.id" :value="cate.id">
            {{ cate.name }}
          </el-radio-button>
        </el-radio-group>
      </div>
      <div class="filter-row">
        <span class="filter-label">排序</span>
        <el-radio-group v-model="sort" @change="handleSortChange">
          <el-radio-button value="">综合</el-radio-button>
          <el-radio-button value="salesDesc">销量优先</el-radio-button>
          <el-radio-button value="priceAsc">价格从低到高</el-radio-button>
          <el-radio-button value="priceDesc">价格从高到低</el-radio-button>
        </el-radio-group>
        <span v-if="keyword" class="keyword-tip">
          搜索「{{ keyword }}」，共 {{ total }} 件商品
        </span>
      </div>
    </el-card>

    <el-skeleton v-if="loading" :rows="6" animated class="skeleton" />

    <template v-else>
      <div v-if="products.length" class="grid">
        <ProductCard v-for="product in products" :key="product.id" :product="product" />
      </div>
      <el-empty v-else description="没有找到相关商品" />

      <div v-if="total > size" class="pagination">
        <el-pagination
          :current-page="page"
          :page-size="size"
          :total="total"
          layout="prev, pager, next, total"
          background
          @current-change="handlePageChange"
        />
      </div>
    </template>
  </div>
</template>

<style scoped>
.filter-card {
  margin-bottom: 20px;
  border-radius: 10px;
}

.filter-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px 0;
}

.filter-label {
  color: var(--text-sub);
  font-size: 14px;
  flex-shrink: 0;
}

.keyword-tip {
  margin-left: auto;
  font-size: 13px;
  color: var(--text-sub);
}

.grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 28px;
}

.skeleton {
  background: #fff;
  padding: 24px;
  border-radius: 10px;
}

@media (max-width: 900px) {
  .grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
