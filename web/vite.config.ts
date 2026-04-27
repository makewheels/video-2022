/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  build: {
    outDir: '../server/video/src/main/resources/static',
    emptyOutDir: true,
  },
  server: {
    proxy: {
      '/video': 'http://localhost:5022',
      '/comment': 'http://localhost:5022',
      '/videoLike': 'http://localhost:5022',
      '/user': 'http://localhost:5022',
      '/file': 'http://localhost:5022',
      '/watchController': 'http://localhost:5022',
      '/playlist': 'http://localhost:5022',
      '/heartbeat': 'http://localhost:5022',
      '/progress': 'http://localhost:5022',
      '/statistics': 'http://localhost:5022',
      '/session': 'http://localhost:5022',
      '/client': 'http://localhost:5022',
      '/cover': 'http://localhost:5022',
      '/oss-log': 'http://localhost:5022',
      '/playback': 'http://localhost:5022',
      '/agent-api': {
        target: 'http://localhost:8765',
        rewrite: (path: string) => path.replace(/^\/agent-api/, ''),
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './tests/setup.ts',
    exclude: ['e2e/**', 'node_modules/**'],
  },
})
