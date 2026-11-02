import { act } from 'react'
import { createRoot } from 'react-dom/client'
import { afterEach, expect, it, vi } from 'vitest'
import Reports from './Reports'

// Cho React biết test đang bọc cập nhật trong act()
;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true

afterEach(() => {
  vi.restoreAllMocks()
  document.body.innerHTML = ''
})

it('hiện lỗi khi xử lý báo cáo thất bại và tải lại danh sách', async () => {
  const fetch = vi.spyOn(globalThis, 'fetch').mockImplementation(async (_url, init) =>
    init?.method === 'POST'
      ? new Response(JSON.stringify({ code: 'VALIDATION_ERROR', message: 'mã biển không có trong danh mục' }), {
          status: 400,
        })
      : new Response(
          JSON.stringify([{ id: 'r1', clusterId: 'c1234567890', reason: 'WRONG_TYPE', createdAt: '2026-01-15T08:30:00Z' }]),
          { status: 200 },
        ),
  )
  vi.spyOn(window, 'prompt').mockReturnValue('X.999')
  const el = document.createElement('div')
  document.body.append(el)

  await act(async () => createRoot(el).render(<Reports />))
  await act(async () => el.querySelector('button')!.click())

  expect(el.textContent).toContain('mã biển không có trong danh mục')
  expect(fetch.mock.calls.filter(([, init]) => !init?.method)).toHaveLength(2)
})
