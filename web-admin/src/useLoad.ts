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

/**
 * Gửi một thao tác của quản trị viên: khoá nút khi đang gửi, giữ lỗi từ API để hiển thị, và luôn tải
 * lại danh sách (ca có thể đã được người khác xử lý).
 */
export function useAction(reload: () => void) {
  const [busy, setBusy] = useState(false)
  const [actionError, setActionError] = useState<string>()
  const run = async (action: () => Promise<unknown>) => {
    setBusy(true)
    setActionError(undefined)
    try {
      await action()
    } catch (e) {
      setActionError((e as Error).message)
    } finally {
      setBusy(false)
      reload()
    }
  }
  return { busy, actionError, run }
}
