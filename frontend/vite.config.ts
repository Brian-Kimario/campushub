import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The dev proxy forwards /api/v1 to the backend unchanged. CAMPUSHUB_API_TARGET points a local run at another backend port.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.CAMPUSHUB_API_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
