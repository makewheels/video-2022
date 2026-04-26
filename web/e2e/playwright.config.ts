import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: '.',
  timeout: 60000,
  use: {
    baseURL: 'http://localhost:5173',
    headless: true,
    viewport: { width: 1280, height: 800 },
  },
  webServer: [
    {
      command: 'cd .. && python3 -m video_agent serve --backend fixture --port 8765',
      port: 8765,
      cwd: '.',
      reuseExistingServer: true,
      timeout: 15000,
    },
    {
      command: 'npm run dev',
      port: 5173,
      cwd: '..',
      reuseExistingServer: true,
      timeout: 15000,
    },
  ],
});
