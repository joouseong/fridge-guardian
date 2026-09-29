import { defineConfig } from '@playwright/test'

// 백엔드/MySQL/Vite를 먼저 실행합니다. 서버를 자동으로 삭제·재설정하지 않습니다.
export default defineConfig({
  testDir: './tests',
  timeout: 90_000,
  expect: { timeout: 10_000 },
  workers: 1,
  reporter: 'list',
  projects: [
    { name: 'desktop', use: { viewport: { width: 1440, height: 1000 } } },
    { name: 'mobile', use: { viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true } },
  ],
  use: {
    baseURL: process.env.APP_BASE_URL || 'http://127.0.0.1:5173',
    headless: true,
    actionTimeout: 15_000,
    navigationTimeout: 20_000,
    viewport: { width: 1440, height: 1000 },
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    launchOptions: { executablePath: process.env.BROWSER_EXECUTABLE || undefined },
  },
})
