import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, fileURLToPath(new URL('../', import.meta.url)), '')
  const apiTarget = env.API_PROXY_TARGET || `http://127.0.0.1:${env.SERVER_PORT || '8080'}`
  return {
    plugins: [react()],
    // 포트가 이미 사용 중이면 오류를 보여 주어 실행 주소가 바뀌지 않게 합니다.
    server: {
      host: '127.0.0.1',
      port: 5173,
      strictPort: true,
      // 브라우저에서는 같은 출처의 /api로 요청하고 Vite가 Spring Boot로 전달합니다.
      proxy: { '/api': { target: apiTarget, changeOrigin: true } },
    },
    preview: {
      host: '127.0.0.1',
      port: 4173,
      strictPort: true,
      proxy: { '/api': { target: apiTarget, changeOrigin: true } },
    },
  }
})
