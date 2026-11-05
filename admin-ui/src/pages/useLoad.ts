import { useCallback, useEffect, useState } from 'react'
import { errorText } from '../api'

/** Oddiy yuklash hook'i: data, xato, qayta yuklash. */
export function useLoad<T>(fn: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const load = useCallback(fn, deps)
  const reload = useCallback(() => {
    setLoading(true)
    load()
      .then((d) => { setData(d); setError(null) })
      .catch((e) => setError(errorText(e)))
      .finally(() => setLoading(false))
  }, [load])
  useEffect(() => { reload() }, [reload])
  return { data, error, loading, reload }
}
