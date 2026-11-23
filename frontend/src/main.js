import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
// 图标按需引入。原来的写法是 `import * as ElementPlusIconsVue` + 全量注册，
// 那会把 1000+ 个图标全部打进包里（几百 KB），而整个项目只用到下面这 14 个。
// 注意：动态用法（<component :is="'Cellphone'" />）靠的也是全局注册，所以这里仍然要注册，
// 只是从「注册全部」改成「注册用到的」。
import {
  Apple,
  ArrowDown,
  ArrowRight,
  Back,
  Cellphone,
  Goods,
  HomeFilled,
  Lock,
  Monitor,
  Search,
  ShoppingBag,
  ShoppingCart,
  Tickets,
  User,
} from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './styles/global.css'
// 商品/购物车/订单暂由本地 mock 提供；鉴权已切到真实后端
// 由 src/mock/index.js 顶部的 MOCK_AUTH 开关控制，M2~M4 完成后逐个关闭
import './mock'

const app = createApp(App)

const icons = {
  Apple,
  ArrowDown,
  ArrowRight,
  Back,
  Cellphone,
  Goods,
  HomeFilled,
  Lock,
  Monitor,
  Search,
  ShoppingBag,
  ShoppingCart,
  Tickets,
  User,
}
for (const [key, component] of Object.entries(icons)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
