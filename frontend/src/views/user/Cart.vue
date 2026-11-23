<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useCartStore } from '@/stores/cart'
import { formatPrice } from '@/utils/format'

const router = useRouter()
const cartStore = useCartStore()
const loading = ref(false)
const selected = ref([])

const selectedAmount = computed(() =>
  Number(
    selected.value
      .reduce((sum, item) => sum + item.product.price * item.quantity, 0)
      .toFixed(2),
  ),
)

const allSelected = computed(
  () => cartStore.items.length > 0 && selected.value.length === cartStore.items.length,
)

onMounted(async () => {
  loading.value = true
  try {
    await cartStore.load()
  } finally {
    loading.value = false
  }
})

function handleSelectionChange(rows) {
  selected.value = rows
}

function handleSelectAll(checked) {
  selected.value = checked ? [...cartStore.items] : []
}

async function handleQuantityChange(item) {
  try {
    await cartStore.update(item.itemId, item.quantity)
  } catch {
    await cartStore.load()
  }
}

async function handleRemove(item) {
  await ElMessageBox.confirm(`确定将「${item.product.name}」移出购物车？`, '提示', {
    type: 'warning',
  })
  await cartStore.remove(item.itemId)
  selected.value = selected.value.filter((i) => i.itemId !== item.itemId)
  ElMessage.success('已移除')
}

async function handleClear() {
  await ElMessageBox.confirm('确定清空购物车？', '提示', { type: 'warning' })
  await cartStore.clear()
  selected.value = []
}

function goCheckout() {
  if (!selected.value.length) {
    ElMessage.warning('请先选择要结算的商品')
    return
  }
  const itemIds = selected.value.map((i) => i.itemId).join(',')
  router.push({ path: '/checkout', query: { itemIds } })
}
</script>

<template>
  <div class="page-wrap">
    <div class="cart-head">
      <h2 class="section-title">我的购物车</h2>
      <el-button v-if="cartStore.items.length" link type="danger" @click="handleClear">
        清空购物车
      </el-button>
    </div>

    <el-skeleton v-if="loading" :rows="5" animated />

    <template v-else-if="cartStore.items.length">
      <div class="cart-card">
        <el-table
          :data="cartStore.items"
          @selection-change="handleSelectionChange"
          row-key="itemId"
        >
          <el-table-column type="selection" width="48" />
          <el-table-column label="商品" min-width="280">
            <template #default="{ row }">
              <router-link :to="`/products/${row.productId}`" class="product-cell">
                <img :src="row.product.cover" class="thumb" />
                <div>
                  <div class="product-name ellipsis-2">{{ row.product.name }}</div>
                  <div class="product-sub">{{ row.product.subtitle }}</div>
                </div>
              </router-link>
            </template>
          </el-table-column>
          <el-table-column label="单价" width="120">
            <template #default="{ row }">¥{{ formatPrice(row.product.price) }}</template>
          </el-table-column>
          <el-table-column label="数量" width="150">
            <template #default="{ row }">
              <el-input-number
                v-model="row.quantity"
                :min="1"
                :max="Math.max(row.product.stock, 1)"
                size="small"
                @change="handleQuantityChange(row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="小计" width="120">
            <template #default="{ row }">
              <span class="price">¥{{ formatPrice(row.product.price * row.quantity) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button link type="danger" @click="handleRemove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="settle-bar">
          <el-checkbox :model-value="allSelected" @change="handleSelectAll">
            全选（{{ selected.length }}/{{ cartStore.items.length }}）
          </el-checkbox>
          <div class="settle-right">
            <span class="settle-label">已选商品合计：</span>
            <span class="price total">¥{{ formatPrice(selectedAmount) }}</span>
            <el-button type="danger" size="large" @click="goCheckout">去结算</el-button>
          </div>
        </div>
      </div>
    </template>

    <div v-else class="empty-block">
      <el-empty description="购物车还是空的">
        <el-button type="primary" @click="router.push('/products')">去逛逛</el-button>
      </el-empty>
    </div>
  </div>
</template>

<style scoped>
.cart-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.cart-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
}

.product-cell {
  display: flex;
  gap: 12px;
  align-items: center;
}

.thumb {
  width: 72px;
  height: 72px;
  border-radius: 8px;
  object-fit: cover;
  flex-shrink: 0;
}

.product-name {
  font-size: 14px;
  font-weight: 500;
}

.product-sub {
  font-size: 12px;
  color: var(--text-sub);
  margin-top: 4px;
}

.settle-bar {
  border-top: 1px solid #ebeef5;
  padding: 16px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.settle-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.settle-label {
  font-size: 14px;
  color: var(--text-sub);
}

.price.total {
  font-size: 24px;
}
</style>
