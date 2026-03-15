import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  base: '/console/',
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/developer': 'http://localhost:5022',
      '/oauth': 'http://localhost:5022',
      '/api': 'http://localhost:5022',
    },
  },
})
