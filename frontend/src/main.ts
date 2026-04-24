import { createApp } from 'vue'
import 'vditor/dist/index.css'
import 'element-plus/theme-chalk/el-overlay.css'
import 'element-plus/theme-chalk/el-message-box.css'
import 'element-plus/theme-chalk/el-message.css'
import App from './App.vue'
import router from './router'
import { pinia } from './stores'
import './styles/global.css'

const app = createApp(App)

app.use(pinia)
app.use(router)
app.mount('#app')
