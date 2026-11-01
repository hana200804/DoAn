import { useCallback, useEffect, useState } from 'react'

/** Tải dữ liệu một lần và cho phép tải lại sau khi thao tác. */
export function useLoad<T>(load: () => Promise<T>) {
  const [data, setData] = useState<T>()
  const [error, setError] = useState<string>()
  const reload = useCallback(() => {
    setError(undefined)
    load().then(setData, (e: Error) => setError(e.message))
  }, [load])
  useEffect(reload, [reload])
  return { data, error, reload }
}
