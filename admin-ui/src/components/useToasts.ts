import { useCallback, useState } from 'react'
import type { ToastItem, ToastProps } from './Toast'

/** Oddiy toast holati. push({tone,title,description,audit}) → `ttl` ms dan keyin o'zi yopiladi. */
export function useToasts(ttl = 5000) {
  const [toasts, setToasts] = useState<ToastItem[]>([])
  const dismiss = useCallback((id: string) => setToasts((ts) => ts.filter((t) => t.id !== id)), [])
  const push = useCallback((t: ToastProps) => {
    const id = Math.random().toString(36).slice(2)
    setToasts((ts) => [...ts, { ...t, id }])
    if (ttl) setTimeout(() => dismiss(id), ttl)
  }, [ttl, dismiss])
  return { toasts, push, dismiss }
}
