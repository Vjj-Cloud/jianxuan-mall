import { defineStore } from 'pinia'
import {
  getCart,
  addToCart,
  updateCartItem,
  removeCartItem,
  clearCart,
} from '@/api/cart'
import { useUserStore } from './user'

export const useCartStore = defineStore('cart', {
  state: () => ({
    items: [],
    loaded: false,
  }),
  getters: {
    count: (state) => state.items.reduce((sum, i) => sum + i.quantity, 0),
    totalAmount: (state) =>
      Number(
        state.items
          .reduce((sum, i) => sum + i.product.price * i.quantity, 0)
          .toFixed(2),
      ),
  },
  actions: {
    async load() {
      const userStore = useUserStore()
      if (!userStore.isLogin) {
        this.items = []
        this.loaded = false
        return
      }
      this.items = await getCart()
      this.loaded = true
    },
    async add(productId, quantity = 1) {
      this.items = await addToCart({ productId, quantity })
    },
    async update(itemId, quantity) {
      this.items = await updateCartItem(itemId, { quantity })
    },
    async remove(itemId) {
      this.items = await removeCartItem(itemId)
    },
    async clear() {
      await clearCart()
      this.items = []
    },
    reset() {
      this.items = []
      this.loaded = false
    },
  },
})
