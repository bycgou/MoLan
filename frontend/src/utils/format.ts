/**
 * 格式化字数显示
 * 例: 12345 → "1.2万字", 500 → "500字"
 */
export function formatWordCount(count: number): string {
  if (count >= 10000) return (count / 10000).toFixed(1) + '万字'
  return count + '字'
}

/**
 * 格式化日期
 */
export function formatDate(dateStr: string | null | undefined): string {
  if (!dateStr) return '未知'
  const d = new Date(dateStr)
  return d.toLocaleDateString('zh-CN')
}

/**
 * 格式化日期时间
 */
export function formatDateTime(dateStr: string | null | undefined): string {
  if (!dateStr) return '未知'
  const d = new Date(dateStr)
  return d.toLocaleString('zh-CN')
}

/**
 * 获取小说状态标签类型
 */
export function getNovelStatusType(status: number): string {
  const map: Record<number, string> = { 0: 'info', 1: 'success', 2: 'warning' }
  return map[status] || 'info'
}

/**
 * 获取小说状态文本
 */
export function getNovelStatusText(status: number): string {
  const map: Record<number, string> = { 0: '草稿', 1: '连载中', 2: '已完结' }
  return map[status] || '未知'
}

/**
 * 获取 Strand 类型标签
 */
export function getStrandType(tag: string): string {
  const map: Record<string, string> = {
    quest: 'danger',
    fire: 'success',
    constellation: 'warning',
  }
  return map[tag?.toLowerCase()] || 'info'
}

/**
 * 格式化 Strand 名称
 */
export function formatStrand(strand: string): string {
  const map: Record<string, string> = {
    quest: 'Quest主线',
    fire: 'Fire感情线',
    constellation: 'Constellation世界观',
  }
  return map[strand?.toLowerCase()] || strand || '未设置'
}

/**
 * 获取角色类型标签
 */
export function getRoleType(role: string): string {
  const map: Record<string, string> = {
    主角: 'danger',
    配角: 'warning',
    反派: 'danger',
    龙套: 'info',
  }
  return map[role] || 'info'
}

/**
 * 防抖函数
 */
export function debounce<T extends (...args: unknown[]) => unknown>(fn: T, delay: number): T {
  let timer: ReturnType<typeof setTimeout>
  return ((...args: Parameters<T>) => {
    clearTimeout(timer)
    timer = setTimeout(() => fn(...args), delay)
  }) as T
}

/**
 * 节流函数
 */
export function throttle<T extends (...args: unknown[]) => unknown>(fn: T, delay: number): T {
  let last = 0
  return ((...args: Parameters<T>) => {
    const now = Date.now()
    if (now - last >= delay) {
      last = now
      fn(...args)
    }
  }) as T
}
