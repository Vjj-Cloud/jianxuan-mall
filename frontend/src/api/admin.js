import http from './http'

export const adminListProducts = (params) => http.get('/admin/products', { params })
export const adminCreateProduct = (data) => http.post('/admin/products', data)
export const adminUpdateProduct = (id, data) => http.put(`/admin/products/${id}`, data)
export const adminDeleteProduct = (id) => http.delete(`/admin/products/${id}`)
export const adminUpdateProductStatus = (id, status) =>
  http.put(`/admin/products/${id}/status`, { status })
export const adminListOrders = (params) => http.get('/admin/orders', { params })
export const adminShipOrder = (id) => http.put(`/admin/orders/${id}/ship`)
export const adminListCategories = () => http.get('/admin/categories')
