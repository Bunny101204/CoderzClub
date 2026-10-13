import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        // Dev server only. `vite build` does not include this proxy.
        // Production API calls use VITE_API_BASE_URL instead.
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
        ws: true, // Support WebSockets if needed
      },
    },
  },
})
