import { ref, watch, onMounted, onUnmounted, type Ref } from 'vue'
import { ElMessageBox } from 'element-plus'

interface UseAutoSaveOptions<T> {
  /** localStorage key */
  key: string
  /** 要监听的数据源 */
  data: Ref<T>
  /** debounce 延迟(ms)，默认 2000 */
  delay?: number
  /** 是否启用，默认 true */
  enabled?: boolean
}

export function useAutoSave<T>(options: UseAutoSaveOptions<T>) {
  const { key, data, delay: debounceDelay = 2000, enabled = true } = options

  const hasDraft = ref(false)
  let debounceTimer: ReturnType<typeof setTimeout> | null = null
  let skipNextWrite = false

  function getStorageKey() {
    return `autosave_${key}`
  }

  function save() {
    if (!enabled || skipNextWrite) {
      skipNextWrite = false
      return
    }
    try {
      localStorage.setItem(getStorageKey(), JSON.stringify(data.value))
    } catch {
      // localStorage 满或不可用，静默忽略
    }
  }

  function clear() {
    localStorage.removeItem(getStorageKey())
    hasDraft.value = false
  }

  function load(): T | null {
    try {
      const raw = localStorage.getItem(getStorageKey())
      if (!raw) return null
      return JSON.parse(raw) as T
    } catch {
      return null
    }
  }

  let stopWatch: (() => void) | null = null

  onMounted(() => {
    if (!enabled) return

    // 检查是否有草稿
    const existing = localStorage.getItem(getStorageKey())
    hasDraft.value = !!existing

    // 启动 watch（深度监听）
    stopWatch = watch(
      data,
      () => {
        if (debounceTimer) clearTimeout(debounceTimer)
        debounceTimer = setTimeout(save, debounceDelay)
      },
      { deep: true }
    )
  })

  onUnmounted(() => {
    if (debounceTimer) clearTimeout(debounceTimer)
    stopWatch?.()
  })

  async function promptRestore(): Promise<boolean> {
    if (!hasDraft.value) return false
    try {
      await ElMessageBox.confirm(
        '检测到未保存的草稿，是否恢复？',
        '恢复草稿',
        { confirmButtonText: '恢复', cancelButtonText: '丢弃', type: 'info' }
      )
      return true
    } catch {
      clear()
      return false
    }
  }

  return {
    hasDraft,
    clear,
    load,
    promptRestore,
    save,
  }
}
