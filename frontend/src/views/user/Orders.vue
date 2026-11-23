<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listOrders, cancelOrder, confirmOrder } from '@/api/order'
import { formatPrice, formatDate, orderStatusText, orderStatusType } from '@/utils/format'

const route = useRoute()
const activeStatus = ref('')
const orders = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const loading = ref(false)

const tabs = [
  { label: '全部', name: '' },
  { label: '待发货', name: 'PENDING' },
  { label: '已发货', name: 'SHIPPED' },
  { label: '已完成', name: 'COMPLETED' },
  { label: '已取消', name: 'CANCELLED' },
]

async function fetchOrders() {
  loading.value = true
  try {
    const data = await listOrders({
      status: activeStatus.value || undefined,
      page: page.value,
      size,
    })
    orders.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function handleTabChange() {
  page.value = 1
  fetchOrders()
}

function handlePageChange(p) {
  page.value = p
  fetchOrders()
}

async function handleCancel(order) {
  await ElMessageBox.confirm(`确定取消订单 ${order.orderNo} 吗？`, '取消订单', { type: 'warning' })
  await cancelOrder(order.id)
  ElMessage.success('订单已取消')
  fetchOrders()
}

async function handleConfirm(order) {
  await ElMessageBox.confirm('请确认您已收到全部商品？', '确认收货', {
    type: 'info',
    confirmButtonText: '确认收货',
  })
  await confirmOrder(order.id)
  ElMessage.success('已确认收货')
  fetchOrders()
}

onMounted(() => {
  if (route.query.created) ElMessage.success('下单成功，等待商家发货')
  fetchOrders()
})
</script>

<template>
  <div class="page-wrap">
    <h2 class="section-title">我的订单</h2>

    <el-tabs v-model="activeStatus" @tab-change="handleTabChange">
      <el-tab-pane v-for="tab in tabs" :key="tab.name" :label="tab.label" :name="tab.name" />
    </el-tabs>

    <el-skeleton v-if="loading" :rows="5" animated />

    <template v-else-if="orders.length">
      <div v-for="order in orders" :key="order.id" class="order-card">
        <div class="order-head">
          <div class="order-meta">
            <span class="order-no">订单号：{{ order.orderNo }}</span>
            <span class="order-time">{{ formatDate(order.createdAt) }}</span>
          </div>
          <el-tag :type="orderStatusType(order.status)" effect="light">
            {{ orderStatusText(order.status) }}
          </el-tag>
        </div>

        <div class="order-items">
          <div v-for="item in order.items" :key="item.productId" class="order-item">
            <img :src="item.cover" class="item-cover" />
            <div class="item-info">
              <div class="item-name">{{ item.name }}</div>
              <div class="item-price">¥{{ formatPrice(item.price) }} × {{ item.quantity }}</div>
            </div>
            <div class="item-subtotal">¥{{ formatPrice(item.price * item.quantity) }}</div>
          </div>
        </div>

        <div class="order-foot">
          <div class="receiver">
            收货人：{{ order.receiverName }} · {{ order.receiverPhone }} · {{ order.receiverAddress }}
          </div>
          <div class="foot-right">
            <span class="total-label">实付：</span>
            <span class="price total">¥{{ formatPrice(order.totalAmount) }}</span>
            <el-button
              v-if="order.status === 'PENDING'"
              size="small"
              @click="handleCancel(order)"
            >
              取消订单
            </el-button>
            <el-button
              v-if="order.status === 'SHIPPED'"
              size="small"
              type="primary"
              @click="handleConfirm(order)"
            >
              确认收货
            </el-button>
          </div>
        </div>
      </div>

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

    <div v-else class="empty-block">
      <el-empty description="暂无相关订单" />
    </div>
  </div>
</template>

<style scoped>
.order-card {
  background: #fff;
  border-radius: 12px;
  margin-bottom: 16px;
  overflow: hidden;
}

.order-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  background: #fafbfc;
  border-bottom: 1px solid #f0f2f5;
}

.order-meta {
  display: flex;
  gap: 20px;
  font-size: 13px;
  color: var(--text-sub);
}

.order-no {
  color: var(--text-main);
  font-weight: 500;
}

.order-items {
  padding: 6px 20px;
}

.order-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 0;
  border-bottom: 1px dashed #f0f2f5;
}

.order-item:last-child {
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

.order-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 20px;
  border-top: 1px solid #f0f2f5;
}

.receiver {
  font-size: 12px;
  color: var(--text-sub);
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.foot-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.total-label {
  font-size: 13px;
  color: var(--text-sub);
}

.price.total {
  font-size: 20px;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}
</style>
