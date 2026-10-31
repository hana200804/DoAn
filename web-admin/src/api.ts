/** Gọi backend qua cùng origin (/api, do nginx định tuyến). Lỗi theo docs/07-api.md §2. */

const SESSION_KEY = 'signa.session'

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message)
  }
}

export const session = {
  get: () => sessionStorage.getItem(SESSION_KEY),
  set: (id: string) => sessionStorage.setItem(SESSION_KEY, id),
  clear: () => sessionStorage.removeItem(SESSION_KEY),
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = session.get()
  const res = await fetch(`/api/v1${path}`, {
    ...init,
    headers: {
      ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init.headers,
    },
  })
  if (res.status === 401) {
    session.clear()
  }
  if (!res.ok) {
    const body = await res.json().catch(() => null)
    throw new ApiError(res.status, body?.code ?? 'HTTP_' + res.status, body?.message ?? res.statusText)
  }
  return res.status === 204 ? (undefined as T) : res.json()
}

export const post = <T,>(path: string, body: unknown) =>
  api<T>(path, { method: 'POST', body: JSON.stringify(body) })

export const put = <T,>(path: string, body: unknown) =>
  api<T>(path, { method: 'PUT', body: JSON.stringify(body) })

// Kiểu dữ liệu khớp với sign-service
export type SignType = {
  code: string
  group: string
  name: string
  description?: string
  speedKmh?: number
  active: boolean
}

export type Sign = {
  id: string
  signCode: string
  name: string
  location: { lat: number; lng: number }
  confidence: number
  status: string
  observationCount: number
  imageUrl?: string
}

export type Conflict = { sign: Sign; codeWeights: Record<string, number> }

export type Report = {
  id: string
  clusterId: string
  reason: string
  note?: string
  createdAt: string
}
