import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  // Khi phát triển, chuyển /api sang nginx của hạ tầng local (infra/)
  server: { proxy: { '/api': process.env.VITE_API_PROXY ?? 'http://localhost:8080' } },
  test: { environment: 'jsdom' },
})
