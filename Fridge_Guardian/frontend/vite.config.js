import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, fileURLToPath(new URL('../', import.meta.url)), '')
  const apiTarget = env.API_PROXY_TARGET || `http://127.0.0.1:${env.SERVER_PORT || '8080'}`
  // 브라우저에서는 항상 같은 출처로 요청하고 Vite가 Spring Boot로 전달합니다. (쿠키·소셜 로그인 redirect_uri가 한 주소로 맞아요)
  //  /api            REST API
  //  /oauth2, /auth/{google|kakao}/callback   Google·Kakao 소셜 로그인 시작·콜백. Host를 바꾸지 않아야 redirect_uri가 localhost:3000 주소로 만들어집니다.
  //  /uploads        업로드한 식재료 사진
  const proxy = {
    '/api': { target: apiTarget, changeOrigin: false, xfwd: true },
    '/oauth2': { target: apiTarget, changeOrigin: false, xfwd: true },
    '^/auth/(google|kakao)/callback': { target: apiTarget, changeOrigin: false, xfwd: true },
    '/uploads': { target: apiTarget, changeOrigin: false, xfwd: true },
  }
  return {
    plugins: [react()],
    // 포트가 이미 사용 중이면 오류를 보여 주어 실행 주소가 바뀌지 않게 합니다.
    server: { host: '127.0.0.1', port: 3000, strictPort: true, proxy },
    preview: { host: '127.0.0.1', port: 4173, strictPort: true, proxy },
  }
})
