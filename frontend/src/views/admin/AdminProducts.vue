<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  adminListProducts,
  adminCreateProduct,
  adminUpdateProduct,
  adminDeleteProduct,
  adminUpdateProductStatus,
  adminListCategories,
} from '@/api/admin'
import { formatPrice } from '@/utils/format'

const list = ref([])
const categories = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const loading = ref(false)

const query = reactive({ keyword: '', status: '' })

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref()
const editingId = ref(null)
const form = reactive({
  name: '',
  subtitle: '',
  categoryId: 1,
  price: undefined,
  originalPrice: undefined,
  stock: 0,
  cover: '',
  desc: '',
  status: 1,
})

const rules = {
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  price: [{ required: true, message: '请输入价格', trigger: 'blur' }],
}

async function fetchData() {
  loading.value = true
  try {
    const data = await adminListProducts({
      keyword: query.keyword || undefined,
      status: query.status === '' ? undefined : query.status,
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

function resetForm() {
  Object.assign(form, {
    name: '',
    subtitle: '',
    categoryId: categories.value[0]?.id || 1,
    price: undefined,
    originalPrice: undefined,
    stock: 0,
    cover: '',
    desc: '',
    status: 1,
  })
}

function openCreate() {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    name: row.name,
    subtitle: row.subtitle,
    categoryId: row.categoryId,
    price: row.price,
    originalPrice: row.originalPrice,
    stock: row.stock,
    cover: row.cover,
    desc: row.desc,
    status: row.status,
  })
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value.validate()
  submitting.value = true
  try {
    if (editingId.value) {
      await adminUpdateProduct(editingId.value, { ...form })
      ElMessage.success('商品已更新')
    } else {
      await adminCreateProduct({ ...form })
      ElMessage.success('商品已创建')
    }
    dialogVisible.value = false
    fetchData()
  } finally {
    submitting.value = false
  }
}

async function handleStatusChange(row) {
  const next = row.status
  try {
    await adminUpdateProductStatus(row.id, next)
    ElMessage.success(next === 1 ? '已上架' : '已下架')
  } catch {
    row.status = next === 1 ? 0 : 1
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(
    `确定删除商品「${row.name}」吗？此操作不可恢复。`,
    '删除商品',
    { type: 'warning', confirmButtonText: '删除', confirmButtonClass: 'el-button--danger' },
  )
  await adminDeleteProduct(row.id)
  ElMessage.success('已删除')
  if (list.value.length === 1 && page.value > 1) page.value -= 1
  fetchData()
}

onMounted(async () => {
  categories.value = await adminListCategories()
  fetchData()
})
</script>

<template>
  <div>
    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="搜索商品名称"
          clearable
          class="search-input"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select v-model="query.status" placeholder="上架状态" clearable class="status-select" @change="handleSearch">
          <el-option label="上架中" :value="1" />
          <el-option label="已下架" :value="0" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button type="success" @click="openCreate">新增商品</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table :data="list" v-loading="loading" row-key="id">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="封面" width="90">
          <template #default="{ row }">
            <el-image
              :src="row.cover"
              fit="cover"
              class="cover-thumb"
              :preview-src-list="[row.cover]"
              preview-teleported
            />
          </template>
        </el-table-column>
        <el-table-column label="商品信息" min-width="240">
          <template #default="{ row }">
            <div class="product-name">{{ row.name }}</div>
            <div class="product-sub">{{ row.subtitle }}</div>
          </template>
        </el-table-column>
        <el-table-column label="分类" width="110">
          <template #default="{ row }">{{ row.categoryName }}</template>
        </el-table-column>
        <el-table-column label="价格" width="110">
          <template #default="{ row }">
            <span class="price">¥{{ formatPrice(row.price) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="90" />
        <el-table-column prop="sales" label="销量" width="90" />
        <el-table-column label="上架" width="90">
          <template #default="{ row }">
            <el-switch v-model="row.status" :active-value="1" :inactive-value="0" @change="handleStatusChange(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
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

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑商品' : '新增商品'"
      width="620px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="商品名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入商品名称" />
        </el-form-item>
        <el-form-item label="副标题" prop="subtitle">
          <el-input v-model="form.subtitle" placeholder="一句话卖点（可选）" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="分类" prop="categoryId">
              <el-select v-model="form.categoryId" style="width: 100%">
                <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">上架</el-radio>
                <el-radio :value="0">下架</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="售价" prop="price">
              <el-input-number v-model="form.price" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="划线价" prop="originalPrice">
              <el-input-number v-model="form.originalPrice" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="库存" prop="stock">
              <el-input-number v-model="form.stock" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="封面图" prop="cover">
              <el-input v-model="form.cover" placeholder="图片 URL，留空自动生成" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="商品详情" prop="desc">
          <el-input v-model="form.desc" type="textarea" :rows="3" placeholder="商品详情文案" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
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

.cover-thumb {
  width: 56px;
  height: 56px;
  border-radius: 6px;
}

.product-name {
  font-weight: 500;
}

.product-sub {
  font-size: 12px;
  color: var(--text-sub);
  margin-top: 2px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
