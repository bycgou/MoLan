"use client"

import { useState, useCallback, useRef } from "react"

interface UseSSEReturn {
  content: string
  isStreaming: boolean
  error: string | null
  start: (body: Record<string, unknown>) => Promise<void>
  cancel: () => void
  reset: () => void
}

export function useSSE(url: string): UseSSEReturn {
  const [content, setContent] = useState("")
  const [isStreaming, setIsStreaming] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const abortRef = useRef<AbortController | null>(null)

  const cancel = useCallback(() => {
    abortRef.current?.abort()
    setIsStreaming(false)
  }, [])

  const reset = useCallback(() => {
    setContent("")
    setError(null)
    setIsStreaming(false)
  }, [])

  const start = useCallback(
    async (body: Record<string, unknown>) => {
      cancel()
      reset()
      setIsStreaming(true)
      abortRef.current = new AbortController()

      try {
        const token = localStorage.getItem("token")
        const res = await fetch(url, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${token}`,
          },
          body: JSON.stringify(body),
          signal: abortRef.current.signal,
        })

        if (!res.ok) {
          throw new Error(`HTTP ${res.status}`)
        }

        const reader = res.body?.getReader()
        if (!reader) throw new Error("No reader")

        const decoder = new TextDecoder()
        let buffer = ""

        while (true) {
          const { done, value } = await reader.read()
          if (done) break

          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split("\n")
          buffer = lines.pop() || ""

          for (const line of lines) {
            if (line.startsWith("data:")) {
              const data = line.slice(5).trim()
              if (data === "[DONE]") continue
              try {
                const parsed = JSON.parse(data)
                if (parsed.content) {
                  setContent((prev) => prev + parsed.content)
                } else if (parsed.token) {
                  setContent((prev) => prev + parsed.token)
                } else if (typeof parsed === "string") {
                  setContent((prev) => prev + parsed)
                }
              } catch {
                // Plain text token
                setContent((prev) => prev + data)
              }
            }
          }
        }
      } catch (e: unknown) {
        if (e instanceof Error && e.name !== "AbortError") {
          setError(e.message)
        }
      } finally {
        setIsStreaming(false)
      }
    },
    [url, cancel, reset],
  )

  return { content, isStreaming, error, start, cancel, reset }
}
