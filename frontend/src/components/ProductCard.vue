<script setup>
import { formatPrice } from '@/utils/format'

defineProps({
  product: { type: Object, required: true },
})
</script>

<template>
  <router-link :to="`/products/${product.id}`" class="product-card">
    <div class="cover-wrap">
      <img :src="product.cover" :alt="product.name" class="cover" />
      <span v-if="product.originalPrice > product.price" class="discount-tag">
        {{ Math.round((product.price / product.originalPrice) * 100) }}折
      </span>
    </div>
    <div class="info">
      <h3 class="name ellipsis-2">{{ product.name }}</h3>
      <p class="subtitle">{{ product.subtitle }}</p>
      <div class="price-row">
        <span class="price">¥{{ formatPrice(product.price) }}</span>
        <span v-if="product.originalPrice > product.price" class="origin-price">
          ¥{{ formatPrice(product.originalPrice) }}
        </span>
        <span class="sales">已售 {{ product.sales }}</span>
      </div>
    </div>
  </router-link>
</template>

<style scoped>
.product-card {
  display: block;
  background: #fff;
  border-radius: 10px;
  overflow: hidden;
  transition: transform 0.2s, box-shadow 0.2s;
}

.product-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}

.cover-wrap {
  position: relative;
  aspect-ratio: 1 / 1;
  background: #fafafa;
}

.cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.discount-tag {
  position: absolute;
  left: 10px;
  top: 10px;
  background: var(--price-color);
  color: #fff;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
}

.info {
  padding: 12px 14px 16px;
}

.name {
  font-size: 15px;
  font-weight: 600;
  line-height: 1.4;
  min-height: 42px;
}

.subtitle {
  font-size: 12px;
  color: var(--text-sub);
  margin: 4px 0 10px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.price {
  color: var(--price-color);
  font-size: 18px;
  font-weight: 700;
}

.origin-price {
  color: #c0c4cc;
  font-size: 12px;
  text-decoration: line-through;
}

.sales {
  margin-left: auto;
  font-size: 12px;
  color: var(--text-sub);
}
</style>
