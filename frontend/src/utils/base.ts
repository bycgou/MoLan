/**
 * 应用挂载目录与接口前缀。
 *
 * 演示网关（zhl-app-proxy）可能把服务挂在 /<用户名>/ 这类子路径下，
 * 浏览器的 location.pathname 就是挂载目录；所有接口地址都要带上它，
 * 否则请求会打到网关根路径去。
 */

function appBasePath(): string {
  const path = window.location.pathname || '/'
  if (path === '/') return '/'
  return path.endsWith('/') ? path : path.slice(0, path.lastIndexOf('/') + 1)
}

export const APP_BASE = appBasePath()

/** 接口前缀，如 '/xingpengbo/api' */
export const API_BASE = APP_BASE + 'api'

/** 把 '/chapter/list' 这类根相对路径补上挂载目录，得到可用的接口地址 */
export function apiUrl(path: string): string {
  if (!path.startsWith('/')) return path
  return APP_BASE + path.replace(/^\//, '')
}
