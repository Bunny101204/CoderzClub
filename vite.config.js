import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        // Always proxy to localhost:8080 within Codespaces
        // Since Vite and backend are in the same container, this avoids GitHub tunnel auth issues
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
        ws: true, // Support WebSockets if needed
      },
    },
  },
})
