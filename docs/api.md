# 简选商城 · 接口契约 v1

前端已按本文档开发（`frontend/src/api/*.js`）。后端实现必须与此一致；
调整字段前先改本文件并同步前端。

---

## 0. 通用约定

| 项 | 约定 |
|---|---|
| Base URL | `/api`（前端开发期由 Vite proxy 转发到 `http://localhost:8080`） |
| 鉴权 | 请求头 `Authorization: Bearer <token>`（登录/注册/商品浏览等公开接口除外） |
| 响应 | HTTP 200 + `{ code, message, data }`；401/403/500 可使用对应 HTTP 状态码 |
| 成功 | `code = 200`，`data` 为业务数据 |
| 失败 | `code ∈ {400,401,403,404,500}`，`message` 为可直接展示给用户的中文提示 |
| 分页请求 | `page` 从 1 开始，`size` 默认 12（订单默认 10） |
| 分页响应 | `{ list, total, page, size }` |
| 金额 | 单位：元，JSON number，两位小数（后端 `BigDecimal`，库中 `DECIMAL(10,2)`） |
| 时间 | ISO 8601 字符串，如 `2026-11-21T10:30:00` |
| 图片 | 商品封面为 URL 字符串（前端支持任意可访问 URL / data URI） |

统一失败示例：

```json
{ "code": 400, "message": "库存不足", "data": null }
```

---

## 1. 认证

### 1.1 注册

`POST /api/auth/register` · 公开 · 注册成功即登录

请求：

```json
{ "username": "jack", "password": "123456", "nickname": "小杰" }
```

响应 `data`：

```json
{
  "token": "eyJhbGciOi...",
  "user": { "id": 3, "username": "jack", "nickname": "小杰", "role": "USER" }
}
```

规则：`username` 唯一；密码入库前 BCrypt；`nickname` 可不传（默认用户名）。

### 1.2 登录

`POST /api/auth/login` · 公开

请求：

```json
{ "username": "user", "password": "user123" }
```

响应：同 1.1（`token` + `user`）。

### 1.3 当前用户

`GET /api/auth/me` · 需登录

响应 `data`：

```json
{ "id": 2, "username": "user", "nickname": "测试用户", "role": "USER" }
```

### User 对象

| 字段 | 类型 | 说明 |
|---|---|---|
| id | number | |
| username | string | |
| nickname | string | 展示名 |
| role | string | `USER` / `ADMIN` |

---

## 2. 分类与商品（公开）

### 2.1 分类列表

`GET /api/categories`

响应 `data`：

```json
[ { "id": 1, "name": "手机数码" } ]
```

### 2.2 商品列表

`GET /api/products` · 公开 · 仅返回上架商品

查询参数：

| 参数 | 说明 |
|---|---|
| keyword | 可选，匹配名称/副标题（模糊） |
| categoryId | 可选，分类 ID |
| sort | 可选：`priceAsc` / `priceDesc` / `salesDesc`；缺省按最新（id 倒序） |
| page / size | 分页，size 默认 12 |

响应 `data`（分页结构，元素为 Product）：

```json
{
  "list": [
    {
      "id": 1,
      "name": "星环 X1 智能手机",
      "subtitle": "6.7英寸 2K 屏 / 骁龙旗舰芯",
      "price": 2999.00,
      "originalPrice": 3499.00,
      "stock": 120,
      "sales": 860,
      "categoryId": 1,
      "categoryName": "手机数码",
      "cover": "https://.../cover.png",
      "desc": "旗舰级性能……"
    }
  ],
  "total": 16,
  "page": 1,
  "size": 12
}
```

### 2.3 商品详情

`GET /api/products/{id}` · 公开

响应 `data`：Product。商品不存在或已下架 → `code 404`，`message "商品不存在或已下架"`。

### Product 对象

| 字段 | 类型 | 说明 |
|---|---|---|
| id | number | |
| name | string | |
| subtitle | string | 卖点副标题 |
| price | number | 售价 |
| originalPrice | number | 划线价 |
| stock | number | 库存 |
| sales | number | 销量 |
| categoryId | number | |
| categoryName | string | 冗余联查 |
| cover | string | 封面 URL |
| desc | string | 详情文案 |

---

## 3. 购物车 · 均需登录

### 3.1 购物车列表

`GET /api/cart`

响应 `data`：购物车条目数组（已下架/删除的商品不返回）：

```json
[
  {
    "itemId": 1732200000000,
    "productId": 1,
    "quantity": 2,
    "product": { "...": "Product 对象" }
  }
]
```

> `itemId` 为购物车行主键，后续数量修改/删除都用它。

### 3.2 加入购物车

`POST /api/cart`

```json
{ "productId": 1, "quantity": 1 }
```

规则：商品须上架且库存 > 0；已存在则数量累加，上限为库存。
响应 `data`：更新后的购物车条目数组（同 3.1）。

### 3.3 修改数量

`PUT /api/cart/{itemId}`

```json
{ "quantity": 3 }
```

规则：`quantity >= 1` 且不超过库存。响应 `data`：购物车条目数组。

### 3.4 删除条目

`DELETE /api/cart/{itemId}` → 响应 `data`：购物车条目数组。

### 3.5 清空购物车

`DELETE /api/cart` → 响应 `data`：`[]`。

---

## 4. 订单 · 均需登录

### 4.1 创建订单

`POST /api/orders`

```json
{
  "requestId": "3f2a9c18-7b4e-4d55-9c0a-1e2f3a4b5c6d",
  "items": [ { "productId": 1, "quantity": 2 } ],
  "receiverName": "小杰",
  "receiverPhone": "13800138000",
  "receiverAddress": "北京市朝阳区 XX 路 1 号"
}
```

`requestId` 是**幂等键**，必填，`^[A-Za-z0-9_-]{8,64}$`。由客户端为「一次购买意图」生成
（前端用 `crypto.randomUUID()`），**重试时必须保持不变**。

规则：

- 收货信息三项必填（后端校验，缺失返回 400）
- `requestId` 缺失或格式不合法返回 400
- 全部商品先校验（存在、上架、库存足够），再统一扣减；任一失败整体回滚
- 扣库存使用 `UPDATE ... WHERE stock >= quantity`，影响行数为 0 即失败（防超卖）
- 下单成功后删除购物车中对应商品
- 金额服务端重新计算，不信任前端传值

**幂等语义**：同一个 `requestId` 重复提交（手抖连点、超时重试）只会产生一笔订单，
第 2 次起直接返回第 1 次的订单，状态码仍是 200。区分三种情况：

| 情况 | 响应 |
|---|---|
| 首次提交且成功 | 200 + 新建的 Order |
| 重复提交，首次已完成 | 200 + **同一笔** Order（订单号相同） |
| 重复提交，首次仍在处理中（30 秒内） | 409 + `订单正在处理中，请勿重复提交` |
| 首次提交失败（如库存不足） | 400，且幂等键立即释放，可用同一 `requestId` 重试 |

响应 `data`：单个 Order（见 4.4）。

### 4.2 我的订单列表

`GET /api/orders?status=&page=1&size=10`

`status` 可选：`PENDING` / `SHIPPED` / `COMPLETED` / `CANCELLED`；不传查全部。
响应 `data`：分页结构的 Order 数组（仅本人订单，按 id 倒序）。

### 4.3 订单详情

`GET /api/orders/{id}` · 仅本人可见

### 4.4 取消订单

`POST /api/orders/{id}/cancel` · 仅本人

规则：仅 `PENDING` 可取消；取消后回补库存、回退销量。响应 `data`：订单对象。

### 4.5 确认收货

`POST /api/orders/{id}/confirm` · 仅本人

规则：仅 `SHIPPED` 可确认。响应 `data`：订单对象。

### Order 对象

| 字段 | 类型 | 说明 |
|---|---|---|
| id | number | 自增主键 |
| orderNo | string | 业务订单号 |
| items | array | 订单明细（见下） |
| totalAmount | number | 实付总额（服务端计算） |
| status | string | `PENDING` / `SHIPPED` / `COMPLETED` / `CANCELLED` |
| receiverName | string | |
| receiverPhone | string | |
| receiverAddress | string | |
| createdAt | string | 下单时间 |
| shippedAt | string \| null | 发货时间 |

OrderItem：

| 字段 | 类型 | 说明 |
|---|---|---|
| productId | number | |
| name | string | 下单时商品名快照 |
| cover | string | 快照 |
| price | number | 下单时单价快照 |
| quantity | number | |

状态流转：`PENDING → SHIPPED → COMPLETED`；`PENDING → CANCELLED`。其余流转一律返回 400。

---

## 5. 管理后台 · 需登录且 role = ADMIN

非管理员访问 → HTTP 403 + `{ "code": 403, "message": "没有权限" }`。

### 5.1 商品列表（含下架）

`GET /api/admin/products?keyword=&status=&page=1&size=10`

`status`：`1` 上架 / `0` 下架 / 不传全部。
响应 `data`：分页，元素为完整 Product（额外包含 `status` 字段）。

### 5.2 新增商品

`POST /api/admin/products`

```json
{
  "name": "新品耳机",
  "subtitle": "卖点一句话",
  "price": 199,
  "originalPrice": 259,
  "stock": 100,
  "categoryId": 1,
  "cover": "https://.../cover.png",
  "desc": "详情文案",
  "status": 1
}
```

`name`、`price` 必填；`cover` 为空时后端可给默认图。响应 `data`：创建后的 Product。

### 5.3 编辑商品

`PUT /api/admin/products/{id}` · 请求体同 5.2（字段均可选，只更新传入项）· 响应：更新后的 Product。

### 5.4 删除商品

`DELETE /api/admin/products/{id}` → `data: null`。（建议逻辑删除，见开发指南）

### 5.5 上下架

`PUT /api/admin/products/{id}/status`

```json
{ "status": 0 }
```

### 5.6 订单列表

`GET /api/admin/orders?status=&keyword=&page=1&size=10`

`keyword` 匹配订单号；`status` 同上。响应：分页 Order 数组（全部用户）。

### 5.7 发货

`PUT /api/admin/orders/{id}/ship`

规则：仅 `PENDING` 可发货，置为 `SHIPPED` 并写 `shippedAt`；否则 400。响应 `data`：订单对象。

### 5.8 分类列表（后台用）

`GET /api/admin/categories` → 同 2.1。

---

## 6. 接入前自测清单

- [ ] 统一返回体：成功 `code=200`，失败有可读 `message`
- [ ] 未带 token 访问登录接口 → HTTP 401
- [ ] 普通用户访问 `/api/admin/**` → HTTP 403
- [ ] 商品列表只出现上架商品；搜索/分类/排序/分页都生效
- [ ] 加购重复商品数量累加，不产生重复行
- [ ] 下单：库存不足时整单失败且库存不变
- [ ] 并发下单不超卖（JMeter/脚本同商品多请求）
- [ ] 取消订单库存回补正确，重复取消被拒绝
- [ ] 订单/购物车接口只能操作本人数据
- [ ] 订单明细中的名称/价格是下单时快照

---

## 7. 联调切换（前端侧）

1. 注释 `frontend/src/main.js` 中的 `import './mock'`
2. 打开 `frontend/vite.config.js` 中的 `server.proxy` 指向 `http://localhost:8080`
3. 重启 `npm run dev`
