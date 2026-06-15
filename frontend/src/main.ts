/**
 * 应用入口文件。
 *
 * <p>初始化 Vue3 应用并注册全局插件和组件：</p>
 * <ul>
 *   <li><b>Pinia</b> — 状态管理（auth store 管理登录态）</li>
 *   <li><b>Vue Router</b> — 路由管理（含导航守卫认证检查）</li>
 *   <li><b>Element Plus</b> — UI 组件库（全量注册）</li>
 *   <li><b>图标</b> — 仅注册实际使用的 28 个图标（从 ~280 个中精选，减少包体积）</li>
 * </ul>
 *
 * <p>图标注册为全局组件，模板中直接使用 {@code <Folder />} 语法。</p>
 */
import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

// 按需导入使用到的 Element Plus 图标（30 个，而非全量注册 ~280 个）
import {
  ArrowDown, ArrowLeft, Aim, Bell, Box, CircleCheck, CircleClose, Coin,
  DataAnalysis, DataLine, Delete, Document, Expand, Folder,
  FullScreen, Link, Location, Lock, Monitor, Refresh,
  RefreshRight, Search, SwitchButton, Top, Upload,
  User, UserFilled, VideoPause, VideoPlay, Fold
} from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'

// 创建 Vue 应用实例
const app = createApp(App)

// 注册核心插件
app.use(createPinia())   // 状态管理
app.use(router)          // 路由（含 beforeEach 导航守卫）
app.use(ElementPlus)     // UI 组件库

// 将图标注册为全局组件，使模板中可直接使用 <IconName /> 语法
const icons = {
  ArrowDown, ArrowLeft, Aim, Bell, Box, CircleCheck, CircleClose, Coin,
  DataAnalysis, DataLine, Delete, Document, Expand, Folder,
  FullScreen, Link, Location, Lock, Monitor, Refresh,
  RefreshRight, Search, SwitchButton, Top, Upload,
  User, UserFilled, VideoPause, VideoPlay, Fold
}
for (const [key, component] of Object.entries(icons)) {
  app.component(key, component)
}

// 挂载到 DOM（public/index.html 中的 <div id="app">）
app.mount('#app')
