import { useCallback, useRef, useState } from 'react'

/**
 * LOW (UI): forma/amal bajarilayotganda ikkinchi marta yuborishni bloklaydi.
 * `run(fn)` — amal tugaguncha qayta chaqirilsa e'tiborsiz qoldiriladi; `busy` tugmani o'chirish uchun.
 */
export function useBusy() {
  const [busy, setBusy] = useState(false)
  const lock = useRef(false)
  const run = useCallback(async (fn: () => Promise<void>) => {
    if (lock.current) return
    lock.current = true
    setBusy(true)
    try {
      await fn()
    } finally {
      lock.current = false
      setBusy(false)
    }
  }, [])
  return { busy, run }
}
