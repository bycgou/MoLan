import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: Number(process.env.FRONTEND_PORT) || 21060,
    host: '0.0.0.0',
    strictPort: true,
    // 演示窗口通过域名网关转发到本机，Host 头不是本机地址时 Vite 默认会 403 拒绝
    allowedHosts: true,
    proxy: {
      '/api': {
        target: process.env.BACKEND_URL || 'http://127.0.0.1:21160',
        changeOrigin: true
      }
    }
  }
})
