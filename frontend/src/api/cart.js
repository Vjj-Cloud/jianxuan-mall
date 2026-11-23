import http from './http'

export const getCart = () => http.get('/cart')
export const addToCart = (data) => http.post('/cart', data)
export const updateCartItem = (itemId, data) => http.put(`/cart/${itemId}`, data)
export const removeCartItem = (itemId) => http.delete(`/cart/${itemId}`)
export const clearCart = () => http.delete('/cart')
