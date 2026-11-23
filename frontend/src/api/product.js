import http from './http'

export const listProducts = (params) => http.get('/products', { params })
export const getProduct = (id) => http.get(`/products/${id}`)
export const listCategories = () => http.get('/categories')
