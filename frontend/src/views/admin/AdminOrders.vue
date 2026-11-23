<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminListOrders, adminShipOrder } from '@/api/admin'
import { formatPrice, formatDate, orderStatusText, orderStatusType } from '@/utils/format'

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const loading = ref(false)
const query = reactive({ keyword: '', status: '' })

const statusOptions = [
  { label: '待发货', value: 'PENDING' },
  { label: '已发货', value: 'SHIPPED' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已取消', value: 'CANCELLED' },
]

async function fetchData() {
  loading.value = true
  try {
    const data = await adminListOrders({
      keyword: query.keyword || undefined,
      status: query.status || undefined,
      page: page.value,
      size,
    })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchData()
}

function handlePageChange(p) {
  page.value = p
  fetchData()
}

async function handleShip(row) {
  await ElMessageBox.confirm(`确认对订单 ${row.orderNo} 发货？`, '订单发货', {
    type: 'info',
    confirmButtonText: '确认发货',
  })
  await adminShipOrder(row.id)
  ElMessage.success('已发货')
  fetchData()
}

onMounted(fetchData)
</script>

<template>
  <div>
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="搜索订单号"
          clearable
          class="search-input"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select v-model="query.status" placeholder="订单状态" clearable class="status-select" @change="handleSearch">
          <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table :data="list" v-loading="loading" row-key="id">
        <el-table-column prop="orderNo" label="订单号" width="210" />
        <el-table-column prop="userId" label="用户ID" width="80" />
        <el-table-column label="商品" min-width="260">
          <template #default="{ row }">
            <div class="order-goods">
              <img :src="row.items[0]?.cover" class="goods-thumb" />
              <div>
                <div class="goods-name">{{ row.items[0]?.name }}</div>
                <div class="goods-count">共 {{ row.items.reduce((n, i) => n + i.quantity, 0) }} 件</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="120">
          <template #default="{ row }">
            <span class="price">¥{{ formatPrice(row.totalAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="收货信息" min-width="200">
          <template #default="{ row }">
            <div class="receiver">{{ row.receiverName }} {{ row.receiverPhone }}</div>
            <div class="receiver addr">{{ row.receiverAddress }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="orderStatusType(row.status)" effect="light">
              {{ orderStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下单时间" width="160">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'PENDING'"
              link
              type="primary"
              @click="handleShip(row)"
            >
              发货
            </el-button>
            <span v-else class="no-action">—</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          :current-page="page"
          :page-size="size"
          :total="total"
          layout="prev, pager, next, total"
          background
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.toolbar-card {
  border-radius: 10px;
  margin-bottom: 16px;
}

.toolbar {
  display: flex;
  gap: 12px;
}

.search-input {
  width: 260px;
}

.status-select {
  width: 140px;
}

.table-card {
  border-radius: 10px;
}

.order-goods {
  display: flex;
  align-items: center;
  gap: 10px;
}

.goods-thumb {
  width: 44px;
  height: 44px;
  border-radius: 6px;
  object-fit: cover;
  flex-shrink: 0;
}

.goods-name {
  font-size: 13px;
  font-weight: 500;
}

.goods-count {
  font-size: 12px;
  color: var(--text-sub);
  margin-top: 2px;
}

.receiver {
  font-size: 13px;
}

.receiver.addr {
  font-size: 12px;
  color: var(--text-sub);
  margin-top: 2px;
}

.no-action {
  color: #c0c4cc;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
