import { useEffect, useState } from 'react'
import { formatDuration } from './format'

/** Jonli qolgan vaqt (soniya). expiresAt: Date | ms | ISO satr. */
export function useCountdown(expiresAt?: Date | number | string | null): number | null {
  const target = expiresAt == null ? null : +new Date(expiresAt)
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (target == null) return
    const t = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(t)
  }, [target])
  return target == null ? null : Math.max(0, (target - now) / 1000)
}


export const formatCountdown = formatDuration
