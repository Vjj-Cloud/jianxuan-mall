import MockAdapter from 'axios-mock-adapter'
import http from '../api/http'

// onNoMatch: 'passthrough' —— 没有注册 mock 的请求直接发到真实后端（经 Vite proxy），
// 这是「渐进式替换 mock」的前提：一次接一个模块，其余保持 mock，未接的接口不会被迫返回假数据
const mock = new MockAdapter(http, { delayResponse: 150, onNoMatch: 'passthrough' })

/* ---------------- 本地数据（模拟后端数据库，刷新不丢） ---------------- */
const DB_PREFIX = 'mall_mock_v1_'
const load = (key, fallback) => {
  try {
    const raw = localStorage.getItem(DB_PREFIX + key)
    return raw ? JSON.parse(raw) : fallback
  } catch {
    return fallback
  }
}
const save = (key, value) => localStorage.setItem(DB_PREFIX + key, JSON.stringify(value))

function cover(text, hue) {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="600" height="600">
    <defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="hsl(${hue},75%,90%)"/>
      <stop offset="100%" stop-color="hsl(${hue + 45},70%,68%)"/>
    </linearGradient></defs>
    <rect width="600" height="600" fill="url(#g)"/>
    <circle cx="470" cy="120" r="150" fill="rgba(255,255,255,0.35)"/>
    <circle cx="90" cy="520" r="90" fill="rgba(255,255,255,0.25)"/>
    <text x="50%" y="53%" font-size="56" font-family="sans-serif" fill="rgba(35,45,65,0.6)" text-anchor="middle">${text}</text>
  </svg>`
  return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg)
}

const categories = [
  { id: 1, name: '手机数码' },
  { id: 2, name: '电脑办公' },
  { id: 3, name: '家用电器' },
  { id: 4, name: '服饰鞋包' },
  { id: 5, name: '食品生鲜' },
]

const seedProducts = [
  { id: 1, name: '星环 X1 智能手机', subtitle: '6.7英寸 2K 屏 / 骁龙旗舰芯 / 5000mAh', price: 2999, originalPrice: 3499, stock: 120, sales: 860, categoryId: 1, status: 1, cover: cover('星环 X1', 210), desc: '旗舰级性能，一亿像素影像系统，支持 120W 快充。' },
  { id: 2, name: '声浪 Pro 无线降噪耳机', subtitle: '主动降噪 / 40小时续航 / 空间音频', price: 499, originalPrice: 699, stock: 300, sales: 1520, categoryId: 1, status: 1, cover: cover('声浪 Pro', 250), desc: '深度降噪，佩戴轻盈，通勤办公两相宜。' },
  { id: 3, name: '极光 S2 智能手表', subtitle: '血氧监测 / 双频 GPS / 14天续航', price: 899, originalPrice: 1099, stock: 88, sales: 640, categoryId: 1, status: 1, cover: cover('极光 S2', 160), desc: '全方位健康监测，运动场景全覆盖。' },
  { id: 4, name: '简行 14 轻薄笔记本', subtitle: '16G 内存 / 1TB 固态 / 1.2kg', price: 4599, originalPrice: 5199, stock: 45, sales: 210, categoryId: 2, status: 1, cover: cover('简行 14', 200), desc: '轻薄机身，2.8K 高刷屏，办公学习高性能。' },
  { id: 5, name: '静音机械键盘', subtitle: '热插拔 / 三模连接 / PBT 键帽', price: 329, originalPrice: 399, stock: 200, sales: 980, categoryId: 2, status: 1, cover: cover('机械键盘', 30), desc: '办公安静的机械手感，支持有线、蓝牙、2.4G。' },
  { id: 6, name: '27 英寸 2K 显示器', subtitle: 'IPS 面板 / 165Hz / 升降旋转支架', price: 1299, originalPrice: 1599, stock: 60, sales: 430, categoryId: 2, status: 1, cover: cover('2K 显示器', 190), desc: '广色域高刷屏，设计办公两不误。' },
  { id: 7, name: '轻氧无线鼠标', subtitle: '静音微动 / 人体工学 / 长续航', price: 89, originalPrice: 119, stock: 500, sales: 2100, categoryId: 2, status: 1, cover: cover('无线鼠标', 220), desc: '轻至 60g，长时间使用不累手。' },
  { id: 8, name: '云净 空气净化器', subtitle: 'HEPA 滤网 / 除甲醛 / 静音模式', price: 899, originalPrice: 1099, stock: 35, sales: 320, categoryId: 3, status: 1, cover: cover('净化器', 120), desc: '高效过滤，适合卧室与客厅。' },
  { id: 9, name: '暖食 智能电饭煲 4L', subtitle: 'IH 加热 / 24小时预约 / 不粘内胆', price: 399, originalPrice: 499, stock: 150, sales: 780, categoryId: 3, status: 1, cover: cover('电饭煲', 20), desc: '一键煮饭，口感更佳。' },
  { id: 10, name: '鲜榨 便携榨汁杯', subtitle: '无线便携 / 一键清洗 / 350ml', price: 129, originalPrice: 169, stock: 260, sales: 1150, categoryId: 3, status: 1, cover: cover('榨汁杯', 90), desc: '随身携带，鲜榨果汁随时喝。' },
  { id: 11, name: '润风 静音加湿器', subtitle: '4L 大容量 / 恒湿 / 大雾量', price: 199, originalPrice: 259, stock: 180, sales: 690, categoryId: 3, status: 1, cover: cover('加湿器', 170), desc: '整夜安睡，不打扰。' },
  { id: 12, name: '轻氧 缓震跑鞋', subtitle: '透气网面 / 回弹中底 / 防滑耐磨', price: 369, originalPrice: 469, stock: 220, sales: 1340, categoryId: 4, status: 1, cover: cover('缓震跑鞋', 260), desc: '轻盈透气，通勤与跑步都合适。' },
  { id: 13, name: '纯棉基础圆领 T 恤', subtitle: '新疆长绒棉 / 质感厚实 / 多色可选', price: 79, originalPrice: 99, stock: 600, sales: 3200, categoryId: 4, status: 1, cover: cover('纯棉T恤', 40), desc: '亲肤透气，基础百搭。' },
  { id: 14, name: '通勤双肩包 20L', subtitle: '防泼水 / 15.6寸电脑仓 / 减压背带', price: 259, originalPrice: 329, stock: 140, sales: 560, categoryId: 4, status: 1, cover: cover('双肩包', 205), desc: '通勤出差，收纳井井有条。' },
  { id: 15, name: '针织开衫外套', subtitle: '羊毛混纺 / 落肩版型 / 春秋百搭', price: 189, originalPrice: 259, stock: 95, sales: 410, categoryId: 4, status: 1, cover: cover('针织开衫', 10), desc: '柔软亲肤，通勤休闲皆宜。' },
  { id: 16, name: '高山原香大米 5kg', subtitle: '当季新米 / 颗颗饱满 / 产地直发', price: 49.9, originalPrice: 69, stock: 800, sales: 2400, categoryId: 5, status: 1, cover: cover('原香大米', 140), desc: '新米上市，煮饭香气浓郁。' },
]

const defaultUsers = [
  { id: 1, username: 'admin', password: 'admin123', role: 'ADMIN', nickname: '管理员' },
  { id: 2, username: 'user', password: 'user123', role: 'USER', nickname: '测试用户' },
]

let products = load('products', seedProducts)
let users = load('users', defaultUsers)
let carts = load('carts', {})
let orders = load('orders', [])
let nextProductId = Math.max(...products.map((p) => p.id), 0) + 1

/* ---------------- 通用工具 ---------------- */
const pathOf = (config) => (config.url || '').split('?')[0]
const escapeReg = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
// 同时兼容 baseURL 合并前后两种 URL：/products 与 /api/products
const apiPath = (p) => new RegExp(`^(?:/api)?${escapeReg(p)}$`)
const detailPath = (p) => new RegExp(`^(?:/api)?${escapeReg(p)}/\\d+$`)
const subPath = (p, action) => new RegExp(`^(?:/api)?${escapeReg(p)}/\\d+/${action}$`)
const parseId = (config) => Number(pathOf(config).match(/\/(\d+)(?:\/[a-z]+)?$/)[1])

const ok = (data = null) => [200, { code: 200, message: 'ok', data }]
const fail = (message, code = 400) => [200, { code, message, data: null }]
const unauthorized = () => [401, { code: 401, message: '未登录或登录已过期', data: null }]

/** 解析真实后端签发的 JWT payload（base64url），失败返回 null */
function decodeJwtPayload(token) {
  const parts = token.split('.')
  if (parts.length !== 3) return null
  try {
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const json = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join(''),
    )
    const payload = JSON.parse(json)
    return payload && payload.sub ? payload : null
  } catch {
    return null
  }
}

/**
 * 取当前用户。兼容两种 token：
 *   1) 真实后端 JWT —— 从 payload 的 sub / role 构造（MOCK_AUTH=false 时走这条）
 *   2) 本地 mock-token-{id}-{role}（MOCK_AUTH=true 时走这条）
 * 只用于 mock 数据隔离，不做真正的安全校验。
 */
function currentUser(config) {
  const raw = config.headers?.Authorization || config.headers?.get?.('Authorization') || ''
  if (!raw.startsWith('Bearer ')) return null
  const token = raw.slice(7)

  const payload = decodeJwtPayload(token)
  if (payload) {
    return {
      id: Number(payload.sub),
      username: 'user' + payload.sub,
      nickname: '',
      role: payload.role || 'USER',
    }
  }

  const m = /^mock-token-(\d+)-(USER|ADMIN)$/.exec(token)
  if (!m) return null
  return users.find((u) => u.id === Number(m[1])) || null
}

const publicProduct = (p) => {
  const { status, ...rest } = p
  return { ...rest, categoryName: categories.find((c) => c.id === p.categoryId)?.name || '' }
}

function paginate(list, params = {}) {
  const page = Math.max(Number(params.page) || 1, 1)
  const size = Math.max(Number(params.size) || 12, 1)
  return {
    list: list.slice((page - 1) * size, page * size),
    total: list.length,
    page,
    size,
  }
}

function userCart(userId) {
  if (!carts[userId]) carts[userId] = []
  return carts[userId]
}

function saveCart() {
  save('carts', carts)
}

function cartView(userId) {
  return userCart(userId)
    .map((item) => {
      const product = products.find((p) => p.id === item.productId && p.status === 1)
      if (!product) return null
      return { itemId: item.itemId, productId: product.id, quantity: Math.min(item.quantity, product.stock), product: publicProduct(product) }
    })
    .filter(Boolean)
}

const persistProducts = () => save('products', products)

/* ---------------- 鉴权（已接入真实后端，见下方 MOCK_AUTH 开关） ---------------- */
/**
 * 后端联调开关：true = 该模块仍走本地 mock；false = 请求真实后端（经 Vite proxy 转发到 8080）。
 *
 * 目前 auth 已完成（M1），所以关掉；商品/购物车/订单还是 M2~M4 的活，
 * 等对应后端模块做完再把开关逐个关掉，实现渐进式替换，避免页面一次性全坏。
 */
const MOCK_AUTH = false

if (MOCK_AUTH) {
  mock.onPost(apiPath('/auth/login')).reply((config) => {
    const { username, password } = JSON.parse(config.data || '{}')
    const user = users.find((u) => u.username === username && u.password === password)
    if (!user) return fail('用户名或密码错误')
    const { password: _pwd, ...safeUser } = user
    return ok({ token: `mock-token-${user.id}-${user.role}`, user: safeUser })
  })

  mock.onPost(apiPath('/auth/register')).reply((config) => {
    const { username, password, nickname } = JSON.parse(config.data || '{}')
    if (!username || !password) return fail('用户名和密码不能为空')
    if (users.some((u) => u.username === username)) return fail('用户名已被占用')
    const user = {
      id: Math.max(...users.map((u) => u.id), 0) + 1,
      username,
      password,
      role: 'USER',
      nickname: nickname || username,
    }
    users.push(user)
    save('users', users)
    const { password: _pwd, ...safeUser } = user
    return ok({ token: `mock-token-${user.id}-${user.role}`, user: safeUser })
  })

  mock.onGet(apiPath('/auth/me')).reply((config) => {
    const user = currentUser(config)
    if (!user) return unauthorized()
    const { password: _pwd, ...safeUser } = user
    return ok(safeUser)
  })
} else {
  // 已接入真实后端：必须显式 passThrough，
  // 否则请求会被文件末尾的 mock.onAny() 兜底拦成「Mock 接口未实现」
  mock.onPost(apiPath('/auth/login')).passThrough()
  mock.onPost(apiPath('/auth/register')).passThrough()
  mock.onGet(apiPath('/auth/me')).passThrough()
}

/* ---------------- 管理后台接口（先注册，避免与用户端路径冲突） ---------------- */
const requireAdmin = (config) => {
  const user = currentUser(config)
  if (!user) return { error: unauthorized() }
  if (user.role !== 'ADMIN') return { error: [403, { code: 403, message: '没有权限', data: null }] }
  return { user }
}

/* 商品管理接口已接入真实后端，mock 注册统一挪到文件末尾「商品模块」那一段 */
mock.onGet(apiPath('/admin/orders')).reply((config) => {
  const auth = requireAdmin(config)
  if (auth.error) return auth.error
  const params = config.params || {}
  let list = orders.slice().sort((a, b) => b.id - a.id)
  if (params.status) list = list.filter((o) => o.status === params.status)
  if (params.keyword) list = list.filter((o) => o.orderNo.includes(params.keyword))
  return ok(paginate(list, params))
})

mock.onPut(subPath('/admin/orders', 'ship')).reply((config) => {
  const auth = requireAdmin(config)
  if (auth.error) return auth.error
  const order = orders.find((o) => o.id === parseId(config))
  if (!order) return fail('订单不存在', 404)
  if (order.status !== 'PENDING') return fail('当前订单状态不可发货')
  order.status = 'SHIPPED'
  order.shippedAt = new Date().toISOString()
  save('orders', orders)
  return ok(order)
})

/* ---------------- 商品模块（已接入真实后端 M2，全部走 Spring Boot） ---------------- */
// 注意：只要注册过 handler，mock 就会拦下来，不会自动落到真实后端，
// 所以必须显式 passThrough，不能指望 onNoMatch 兜底。
// 管理端
mock.onGet(apiPath('/admin/products')).passThrough()
mock.onPost(apiPath('/admin/products')).passThrough()
mock.onPut(detailPath('/admin/products')).passThrough()
mock.onDelete(detailPath('/admin/products')).passThrough()
mock.onPut(subPath('/admin/products', 'status')).passThrough()
mock.onGet(apiPath('/admin/categories')).passThrough()
// 用户端
mock.onGet(apiPath('/categories')).passThrough()
mock.onGet(apiPath('/products')).passThrough()
mock.onGet(detailPath('/products')).passThrough()

/* ---------------- 购物车 ---------------- */
// 购物车已接入真实后端（M3）。把 MOCK_CART 改成 true 可以退回假数据调 UI。
// 注意下面 userCart / cartView / saveCart 这几个辅助函数不能删：
// 订单 mock 下单成功后要清理购物车，还在用它们。
const MOCK_CART = false

if (MOCK_CART) {
mock.onGet(apiPath('/cart')).reply((config) => {
  const user = currentUser(config)
  if (!user) return unauthorized()
  return ok(cartView(user.id))
})

mock.onPost(apiPath('/cart')).reply((config) => {
  const user = currentUser(config)
  if (!user) return unauthorized()
  const { productId, quantity = 1 } = JSON.parse(config.data || '{}')
  const product = products.find((p) => p.id === Number(productId) && p.status === 1)
  if (!product) return fail('商品不存在或已下架')
  if (product.stock <= 0) return fail('商品库存不足')
  const cart = userCart(user.id)
  const exist = cart.find((i) => i.productId === product.id)
  if (exist) {
    exist.quantity = Math.min(exist.quantity + Number(quantity), product.stock)
  } else {
    cart.push({ itemId: Date.now(), productId: product.id, quantity: Math.min(Number(quantity), product.stock) })
  }
  saveCart()
  return ok(cartView(user.id))
})

mock.onPut(detailPath('/cart')).reply((config) => {
  const user = currentUser(config)
  if (!user) return unauthorized()
  const item = userCart(user.id).find((i) => i.itemId === parseId(config))
  if (!item) return fail('购物车条目不存在', 404)
  const product = products.find((p) => p.id === item.productId)
  const { quantity } = JSON.parse(config.data || '{}')
  const qty = Number(quantity)
  if (!qty || qty < 1) return fail('购买数量不能小于 1')
  if (product && qty > product.stock) return fail('超出库存数量')
  item.quantity = qty
  saveCart()
  return ok(cartView(user.id))
})

mock.onDelete(detailPath('/cart')).reply((config) => {
  const user = currentUser(config)
  if (!user) return unauthorized()
  const itemId = parseId(config)
  carts[user.id] = userCart(user.id).filter((i) => i.itemId !== itemId)
  saveCart()
  return ok(cartView(user.id))
})

mock.onDelete(apiPath('/cart')).reply((config) => {
  const user = currentUser(config)
  if (!user) return unauthorized()
  carts[user.id] = []
  saveCart()
  return ok([])
})
} else {
  // 只要注册过 handler，mock 就会拦下来，不会自动落到真实后端，必须显式 passThrough
  mock.onGet(apiPath('/cart')).passThrough()
  mock.onPost(apiPath('/cart')).passThrough()
  mock.onPut(detailPath('/cart')).passThrough()
  // 先注册 detail，再注册清空：两条正则是精确锚定的，本身不冲突，顺序只是读起来更清楚
  mock.onDelete(detailPath('/cart')).passThrough()
  mock.onDelete(apiPath('/cart')).passThrough()
}

/* ---------------- 订单 ---------------- */
// 订单已接入真实后端（M4）。改回 true 可以退回假数据调 UI。
const MOCK_ORDER = false

if (MOCK_ORDER) {
mock.onPost(apiPath('/orders')).reply((config) => {
  const user = currentUser(config)
  if (!user) return unauthorized()
  const { items, receiverName, receiverPhone, receiverAddress } = JSON.parse(config.data || '{}')
  if (!Array.isArray(items) || items.length === 0) return fail('请选择要购买的商品')
  if (!receiverName || !receiverPhone || !receiverAddress) return fail('请填写完整的收货信息')

  // 先整体校验，再统一扣减，模拟后端事务
  const lines = []
  for (const item of items) {
    const product = products.find((p) => p.id === Number(item.productId))
    const qty = Number(item.quantity)
    if (!product || product.status !== 1) return fail('部分商品已下架，请刷新后重试')
    if (!qty || qty < 1) return fail('购买数量不合法')
    if (product.stock < qty) return fail(`「${product.name}」库存不足`)
    lines.push({ product, qty })
  }

  const orderItems = lines.map(({ product, qty }) => {
    product.stock -= qty
    product.sales += qty
    return {
      productId: product.id,
      name: product.name,
      cover: product.cover,
      price: product.price,
      quantity: qty,
    }
  })
  const totalAmount = Number(orderItems.reduce((sum, i) => sum + i.price * i.quantity, 0).toFixed(2))
  const order = {
    id: Math.max(...orders.map((o) => o.id), 0) + 1,
    orderNo: 'SO' + Date.now() + String(Math.floor(Math.random() * 900) + 100),
    userId: user.id,
    items: orderItems,
    totalAmount,
    status: 'PENDING',
    receiverName,
    receiverPhone,
    receiverAddress,
    createdAt: new Date().toISOString(),
    shippedAt: null,
  }
  orders.push(order)
  carts[user.id] = userCart(user.id).filter((i) => !items.some((it) => Number(it.productId) === i.productId))
  save('orders', orders)
  persistProducts()
  saveCart()
  return ok(order)
})

mock.onGet(apiPath('/orders')).reply((config) => {
  const user = currentUser(config)
  if (!user) return unauthorized()
  const params = config.params || {}
  let list = orders.filter((o) => o.userId === user.id).sort((a, b) => b.id - a.id)
  if (params.status) list = list.filter((o) => o.status === params.status)
  return ok(paginate(list, { page: params.page, size: params.size || 10 }))
})

mock.onGet(detailPath('/orders')).reply((config) => {
  const user = currentUser(config)
  if (!user) return unauthorized()
  const order = orders.find((o) => o.id === parseId(config) && o.userId === user.id)
  if (!order) return fail('订单不存在', 404)
  return ok(order)
})

mock.onPost(subPath('/orders', 'cancel')).reply((config) => {
  const user = currentUser(config)
  if (!user) return unauthorized()
  const order = orders.find((o) => o.id === parseId(config) && o.userId === user.id)
  if (!order) return fail('订单不存在', 404)
  if (order.status !== 'PENDING') return fail('当前订单状态不可取消')
  order.status = 'CANCELLED'
  order.items.forEach((item) => {
    const product = products.find((p) => p.id === item.productId)
    if (product) {
      product.stock += item.quantity
      product.sales = Math.max(0, product.sales - item.quantity)
    }
  })
  save('orders', orders)
  persistProducts()
  return ok(order)
})

mock.onPost(subPath('/orders', 'confirm')).reply((config) => {
  const user = currentUser(config)
  if (!user) return unauthorized()
  const order = orders.find((o) => o.id === parseId(config) && o.userId === user.id)
  if (!order) return fail('订单不存在', 404)
  if (order.status !== 'SHIPPED') return fail('当前订单状态不可确认收货')
  order.status = 'COMPLETED'
  save('orders', orders)
  return ok(order)
})

} else {
  // 注册过 handler 就会被拦截，必须显式放行到真实后端
  mock.onPost(apiPath('/orders')).passThrough()
  mock.onGet(apiPath('/orders')).passThrough()
  mock.onGet(detailPath('/orders')).passThrough()
  mock.onPost(subPath('/orders', 'cancel')).passThrough()
  mock.onPost(subPath('/orders', 'confirm')).passThrough()
}

// 未匹配的请求不再用 mock.onAny() 兜底：那种写法会把「已接入真实后端」的接口
// 一起拦成 404。改为在构造时设置 onNoMatch: 'passthrough'，未 mock 的请求直接放行到后端。

export default mock
