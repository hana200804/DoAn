import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, session } from './api'

const respond = (status: number, body?: unknown) =>
  vi.spyOn(globalThis, 'fetch').mockResolvedValue(
    new Response(body === undefined ? null : JSON.stringify(body), { status }),
  )

describe('api', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    session.clear()
  })

  it('gửi Bearer token và trả JSON', async () => {
    session.set('abc')
    const fetch = respond(200, [{ code: 'P.102' }])

    const data = await api<{ code: string }[]>('/sign-types')

    expect(data[0].code).toBe('P.102')
    expect(fetch.mock.calls[0][1]?.headers).toMatchObject({ Authorization: 'Bearer abc' })
  })

  it('chuyển lỗi chuẩn của backend thành ApiError', async () => {
    respond(409, { code: 'CONFLICT', message: 'Mã biển đã tồn tại' })

    await expect(api('/admin/sign-types')).rejects.toEqual(new ApiError(409, 'CONFLICT', 'Mã biển đã tồn tại'))
  })

  it('xoá phiên khi bị 401', async () => {
    session.set('expired')
    respond(401, { code: 'UNAUTHENTICATED', message: 'Cần đăng nhập' })

    await expect(api('/admin/conflicts')).rejects.toBeInstanceOf(ApiError)
    expect(session.get()).toBeNull()
  })

  it('trả undefined với 204', async () => {
    respond(204)
    await expect(api('/auth/logout')).resolves.toBeUndefined()
  })
})
