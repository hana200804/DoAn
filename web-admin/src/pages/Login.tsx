import { FormEvent, useState } from 'react'
import { post, session } from '../api'

type LoginResponse = { sessionId: string; user: { role: string } }

/** Chỉ tài khoản ADMIN được vào (FR-20). */
export default function Login({ onLogin }: { onLogin: () => void }) {
  const [error, setError] = useState<string>()

  const submit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    const form = new FormData(e.currentTarget)
    try {
      const res = await post<LoginResponse>('/auth/login', {
        email: form.get('email'),
        password: form.get('password'),
      })
      if (res.user.role !== 'ADMIN') {
        session.set(res.sessionId)
        await post('/auth/logout', {}).catch(() => undefined)
        session.clear()
        setError('Tài khoản không có quyền quản trị')
        return
      }
      session.set(res.sessionId)
      onLogin()
    } catch (err) {
      setError((err as Error).message)
    }
  }

  return (
    <form className="login" onSubmit={submit}>
      <h1>Signa · Quản trị</h1>
      <label>
        Email
        <input name="email" type="email" required autoComplete="username" />
      </label>
      <label>
        Mật khẩu
        <input name="password" type="password" required autoComplete="current-password" />
      </label>
      {error && <p className="error">{error}</p>}
      <button type="submit">Đăng nhập</button>
    </form>
  )
}
