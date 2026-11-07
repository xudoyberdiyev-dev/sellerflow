import { useCallback, useEffect, useRef, useState } from 'react'
import { errorText } from '../api'

/**
 * Oddiy yuklash hook'i: data, xato, qayta yuklash.
 * LOW (UI): har yuklash AbortController bilan — sahifadan chiqilsa yoki qayta yuklansa eski so'rov bekor qilinadi
 * va uning javobi holatni yangilamaydi (eski sahifa/filtr natijasi yangisini bosib ketmaydi).
 */
export function useLoad<T>(fn: (signal: AbortSignal) => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const current = useRef<AbortController | null>(null)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const load = useCallback(fn, deps)
  const reload = useCallback(() => {
    current.current?.abort()
    const ctrl = new AbortController()
    current.current = ctrl
    setLoading(true)
    load(ctrl.signal)
      .then((d) => { if (!ctrl.signal.aborted) { setData(d); setError(null) } })
      .catch((e) => { if (!ctrl.signal.aborted) setError(errorText(e)) })
      .finally(() => { if (!ctrl.signal.aborted) setLoading(false) })
  }, [load])
  useEffect(() => {
    reload()
    return () => current.current?.abort()
  }, [reload])
  return { data, error, loading, reload }
}
