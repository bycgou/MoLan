/**
 * 公共 SSE (Server-Sent Events) 流式连接工具
 * 支持自动重连、错误友好化、cancel 取消
 */

export interface SSEError extends Error {
  status?: number
  retryable: boolean
}

export interface SSEOptions {
  /** SSE 端点 URL（相对于 /api） */
  url: string
  /** 自定义 token，默认从 localStorage 读取 */
  token?: string
  /** POST body，存在时使用 POST，否则 GET */
  body?: object
  /** 连接建立后回调 */
  onOpen?: () => void
  /** 收到纯 data 消息时回调 */
  onMessage: (data: string) => void
  /** 收到命名事件时回调 */
  onEvent?: (event: string, data: string) => void
  /** 错误回调 */
  onError: (error: SSEError) => void
  /** 流正常结束回调 */
  onDone?: () => void
  /** 外部 AbortSignal */
  signal?: AbortSignal
  /** 最大重试次数，默认 2 */
  maxRetries?: number
  /** 重试间隔基准(ms)，默认 2000，指数退避 */
  retryDelay?: number
}

const SSE_ERROR_MESSAGES: Record<number, string> = {
  401: '登录已过期，请重新登录后重试',
  403: '没有权限执行此操作',
  429: 'AI 服务繁忙，请稍后重试',
  500: 'AI 生成失败，请稍后重试',
  502: 'AI 服务暂时不可用',
  503: 'AI 服务暂时不可用',
}

function createSSEError(status: number | undefined, fallbackMsg: string): SSEError {
  const msg = status ? (SSE_ERROR_MESSAGES[status] || `生成失败 (${status})，请稍后重试`) : fallbackMsg
  const err = new Error(msg) as SSEError
  err.status = status
  err.retryable = status === undefined || status >= 500
  return err
}

export function createSSEStream(options: SSEOptions): { cancel: () => void } {
  const {
    url,
    token,
    body,
    onOpen,
    onMessage,
    onEvent,
    onError,
    onDone,
    signal,
    maxRetries = 2,
    retryDelay = 2000,
  } = options

  let cancelled = false
  let retryCount = 0
  let internalAbort: AbortController | null = null

  function cancel() {
    cancelled = true
    internalAbort?.abort()
  }

  async function connect() {
    const authToken = token || localStorage.getItem('token') || ''

    while (!cancelled) {
      internalAbort = new AbortController()

      // 如果外部 signal 已 abort，直接退出
      if (signal?.aborted) {
        cancelled = true
        return
      }

      // 转发外部 abort 到内部 controller
      const onExternalAbort = () => internalAbort?.abort()
      signal?.addEventListener('abort', onExternalAbort)

      try {
        const fetchOptions: RequestInit = {
          method: body ? 'POST' : 'GET',
          headers: {
            'Authorization': `Bearer ${authToken}`,
            ...(body ? { 'Content-Type': 'application/json' } : {}),
          },
          signal: internalAbort.signal,
        }
        if (body) {
          fetchOptions.body = JSON.stringify(body)
        }

        const response = await fetch(url, fetchOptions)

        if (!response.ok) {
          const err = createSSEError(response.status, `HTTP ${response.status}`)
          // 4xx 不重试
          if (!err.retryable || retryCount >= maxRetries) {
            onError(err)
            return
          }
          // 5xx 可重试
          retryCount++
          await delay(retryDelay * retryCount)
          continue
        }

        // 连接成功，重置重试计数
        retryCount = 0

        const reader = response.body?.getReader()
        if (!reader) {
          onError(createSSEError(undefined, '无法读取响应流'))
          return
        }

        const decoder = new TextDecoder()
        let buffer = ''
        let currentEventName = ''
        let hasReceivedData = false

        while (!cancelled) {
          const { done, value } = await reader.read()
          if (done) {
            // 处理缓冲区剩余内容
            if (buffer.trim()) {
              processSSELine(buffer.trim())
            }
            onDone?.()
            return
          }

          if (!hasReceivedData) {
            hasReceivedData = true
            onOpen?.()
          }

          const chunk = decoder.decode(value, { stream: true })
          buffer += chunk
          const lines = buffer.split('\n')
          buffer = lines.pop() || ''

          for (const line of lines) {
            processSSELine(line)
          }
        }

        // cancelled = true 时退出 while 循环
        return
      } catch (err: any) {
        signal?.removeEventListener('abort', onExternalAbort)

        if (cancelled || err.name === 'AbortError') {
          // 用户取消或外部 abort，不报错
          return
        }

        // 网络错误，判断是否可重试
        if (retryCount < maxRetries) {
          retryCount++
          await delay(retryDelay * retryCount)
          continue
        }

        onError(createSSEError(undefined, '网络连接已断开，请检查网络后重试'))
        return
      }
    }

    function processSSELine(line: string) {
      if (line.startsWith('event:')) {
        currentEventName = line.slice(6).trim()
        return
      }
      if (line.startsWith('data:')) {
        const data = line.slice(5).trim()
        if (!data) return

        if (currentEventName) {
          onEvent?.(currentEventName, data)
          currentEventName = ''
        } else {
          onMessage(data)
        }
      }
    }
  }

  connect()

  return { cancel }
}

function delay(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms))
}
