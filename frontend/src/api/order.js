import http from './http'

export const createOrder = (data) => http.post('/orders', data)
export const listOrders = (params) => http.get('/orders', { params })
export const getOrder = (id) => http.get(`/orders/${id}`)
export const cancelOrder = (id) => http.post(`/orders/${id}/cancel`)
export const confirmOrder = (id) => http.post(`/orders/${id}/confirm`)
