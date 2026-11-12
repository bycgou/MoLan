import { defineConfig, type Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

/**
 * 演示网关（zhl-app-proxy）把服务挂在 /<用户名>/ 这类子路径下时，
 * 请求路径会原样转发过来（不剥前缀），需要在这一层剥掉，
 * 否则静态资源与 /api 代理都匹配不上。
 */
const KNOWN_ROOTS = [
  'api',
  'assets',
  'src',
  'public',
  'static',
  'node_modules',
  '@vite',
  '@fs',
  '@id',
  'vite.svg',
  'favicon.ico',
]

function stripGatewayPrefix(): Plugin {
  const middleware = (req: any, _res: any, next: any) => {
    const raw = req.url || '/'
    const qIndex = raw.indexOf('?')
    const pathname = qIndex === -1 ? raw : raw.slice(0, qIndex)
    const query = qIndex === -1 ? '' : raw.slice(qIndex)
    const segments = pathname.split('/')
    // /<前缀>/<已知根>/... → /<已知根>/...
    if (segments.length > 2 && !KNOWN_ROOTS.includes(segments[1]) && KNOWN_ROOTS.includes(segments[2])) {
      req.url = '/' + segments.slice(2).join('/') + query
    }
    next()
  }

  return {
    name: 'gateway-prefix-strip',
    configureServer(server) {
      server.middlewares.use(middleware)
    },
    configurePreviewServer(server) {
      server.middlewares.use(middleware)
    },
  }
}

// 后端的 context-path，部署时与 SERVER_CONTEXT_PATH 保持一致
const BACKEND_CONTEXT_PATH = process.env.BACKEND_CONTEXT_PATH || '/api'

export default defineConfig({
  plugins: [vue(), stripGatewayPrefix()],
  // 相对基址：打包产物用相对路径引用，挂在任意子路径下都能加载
  base: './',
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
        changeOrigin: true,
        // 网关按前缀路由接口时，context-path 带前缀，这里补齐
        rewrite: (path) =>
          BACKEND_CONTEXT_PATH === '/api'
            ? path
            : BACKEND_CONTEXT_PATH + path.slice('/api'.length),
      }
    }
  }
})
