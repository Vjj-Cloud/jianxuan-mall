<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getProduct } from '@/api/product'
import { createOrder } from '@/api/order'
import { useCartStore } from '@/stores/cart'
import { formatPrice } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const cartStore = useCartStore()

const items = ref([])
const loading = ref(true)
const submitting = ref(false)
const formRef = ref()
const form = reactive({ receiverName: '', receiverPhone: '', receiverAddress: '' })

const rules = {
  receiverName: [{ required: true, message: '请输入收货人姓名', trigger: 'blur' }],
  receiverPhone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  receiverAddress: [{ required: true, message: '请输入详细收货地址', trigger: 'blur' }],
}

const totalAmount = computed(() =>
  Number(items.value.reduce((sum, i) => sum + i.price * i.quantity, 0).toFixed(2)),
)

const totalCount = computed(() => items.value.reduce((sum, i) => sum + i.quantity, 0))

onMounted(async () => {
  try {
    if (route.query.itemIds) {
      await cartStore.load()
      const ids = String(route.query.itemIds).split(',').map(Number)
      items.value = cartStore.items
        .filter((i) => ids.includes(i.itemId))
        .map((i) => ({
          productId: i.productId,
          name: i.product.name,
          cover: i.product.cover,
          price: i.product.price,
          quantity: i.quantity,
        }))
    } else if (route.query.productId) {
      const product = await getProduct(route.query.productId)
      items.value = [
        {
          productId: product.id,
          name: product.name,
          cover: product.cover,
          price: product.price,
          quantity: Math.max(Number(route.query.quantity) || 1, 1),
        },
      ]
    }
    if (!items.value.length) {
      ElMessage.warning('没有可结算的商品')
      router.replace('/cart')
    }
  } finally {
    loading.value = false
  }
})

async function submit() {
  await formRef.value.validate()
  submitting.value = true
  try {
    await createOrder({
      items: items.value.map((i) => ({ productId: i.productId, quantity: i.quantity })),
      ...form,
    })
    await cartStore.load()
    ElMessage.success('下单成功')
    router.replace({ path: '/orders', query: { created: 1 } })
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page-wrap">
    <h2 class="section-title">确认订单</h2>

    <el-skeleton v-if="loading" :rows="6" animated />

    <template v-else-if="items.length">
      <div class="checkout-grid">
        <div class="left">
          <el-card shadow="never" class="block">
            <template #header><span class="block-title">收货信息</span></template>
            <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
              <el-form-item label="收货人" prop="receiverName">
                <el-input v-model="form.receiverName" placeholder="请输入姓名" style="max-width: 320px" />
              </el-form-item>
              <el-form-item label="手机号" prop="receiverPhone">
                <el-input v-model="form.receiverPhone" placeholder="请输入手机号" style="max-width: 320px" />
              </el-form-item>
              <el-form-item label="收货地址" prop="receiverAddress">
                <el-input
                  v-model="form.receiverAddress"
                  type="textarea"
                  :rows="2"
                  placeholder="省 / 市 / 区 / 详细地址"
                />
              </el-form-item>
            </el-form>
          </el-card>

          <el-card shadow="never" class="block">
            <template #header><span class="block-title">商品清单</span></template>
            <div v-for="item in items" :key="item.productId" class="item-row">
              <img :src="item.cover" class="item-cover" />
              <div class="item-info">
                <div class="item-name ellipsis-2">{{ item.name }}</div>
                <div class="item-price">¥{{ formatPrice(item.price) }} × {{ item.quantity }}</div>
              </div>
              <div class="item-subtotal">¥{{ formatPrice(item.price * item.quantity) }}</div>
            </div>
          </el-card>
        </div>

        <el-card shadow="never" class="summary">
          <template #header><span class="block-title">订单汇总</span></template>
          <div class="summary-row">
            <span>商品件数</span>
            <span>{{ totalCount }} 件</span>
          </div>
          <div class="summary-row">
            <span>商品金额</span>
            <span>¥{{ formatPrice(totalAmount) }}</span>
          </div>
          <div class="summary-row">
            <span>运费</span>
            <span>¥0.00</span>
          </div>
          <el-divider />
          <div class="summary-row total">
            <span>应付总额</span>
            <span class="price">¥{{ formatPrice(totalAmount) }}</span>
          </div>
          <el-button type="danger" size="large" class="submit-btn" :loading="submitting" @click="submit">
            提交订单
          </el-button>
          <p class="tip">提交后进入「待发货」，可在「我的订单」中查看</p>
        </el-card>
      </div>
    </template>
  </div>
</template>

<style scoped>
.checkout-grid {
  display: grid;
  grid-template-columns: 1fr 340px;
  gap: 16px;
  align-items: start;
}

.block {
  border-radius: 10px;
  margin-bottom: 16px;
}

.block-title {
  font-weight: 600;
}

.item-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 0;
  border-bottom: 1px dashed #ebeef5;
}

.item-row:last-child {
  border-bottom: none;
}

.item-cover {
  width: 64px;
  height: 64px;
  border-radius: 8px;
  object-fit: cover;
  flex-shrink: 0;
}

.item-info {
  flex: 1;
  min-width: 0;
}

.item-name {
  font-size: 14px;
  font-weight: 500;
}

.item-price {
  font-size: 12px;
  color: var(--text-sub);
  margin-top: 6px;
}

.item-subtotal {
  font-weight: 600;
}

.summary {
  border-radius: 10px;
  position: sticky;
  top: 84px;
}

.summary-row {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  font-size: 14px;
  color: #606266;
}

.summary-row.total {
  font-size: 16px;
  color: var(--text-main);
  font-weight: 600;
}

.summary-row.total .price {
  font-size: 24px;
}

.submit-btn {
  width: 100%;
  margin-top: 14px;
}

.tip {
  margin-top: 12px;
  font-size: 12px;
  color: var(--text-sub);
  text-align: center;
}

@media (max-width: 900px) {
  .checkout-grid {
    grid-template-columns: 1fr;
  }
  .summary {
    position: static;
  }
}
</style>
