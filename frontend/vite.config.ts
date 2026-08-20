import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Dev-time stand-in for the nginx reverse proxy: /api -> Spring Boot :8080.
// Same-origin in both environments, so session cookies just work.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
