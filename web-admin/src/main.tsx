import { StrictMode, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Navigate, NavLink, Route, Routes } from 'react-router-dom'
import { post, session } from './api'
import Conflicts from './pages/Conflicts'
import Login from './pages/Login'
import Reports from './pages/Reports'
import SignTypes from './pages/SignTypes'
import './styles.css'

function App() {
  const [loggedIn, setLoggedIn] = useState(!!session.get())

  if (!loggedIn) {
    return <Login onLogin={() => setLoggedIn(true)} />
  }

  const logout = async () => {
    await post('/auth/logout', {}).catch(() => undefined)
    session.clear()
    setLoggedIn(false)
  }

  return (
    <BrowserRouter>
      <header>
        <strong>Signa · Quản trị</strong>
        <nav>
          <NavLink to="/conflicts">Ca mâu thuẫn</NavLink>
          <NavLink to="/reports">Báo cáo sai</NavLink>
          <NavLink to="/sign-types">Danh mục biển</NavLink>
        </nav>
        <button onClick={logout}>Đăng xuất</button>
      </header>
      <main>
        <Routes>
          <Route path="/conflicts" element={<Conflicts />} />
          <Route path="/reports" element={<Reports />} />
          <Route path="/sign-types" element={<SignTypes />} />
          <Route path="*" element={<Navigate to="/conflicts" replace />} />
        </Routes>
      </main>
    </BrowserRouter>
  )
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
