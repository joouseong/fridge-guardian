import { expect } from '@playwright/test'
import { randomUUID } from 'node:crypto'

export const today = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit',
}).format(new Date())

export function dateAfter(days) {
  const date = new Date(`${today}T00:00:00Z`)
  date.setUTCDate(date.getUTCDate() + days)
  return date.toISOString().slice(0, 10)
}

// 1x1 PNG. 서버는 파일 앞부분(매직 바이트)으로 이미지를 판별합니다.
export const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64')

/** 화면에서 회원가입(비밀번호 확인 포함)하고 로그인해 대시보드까지 이동합니다. */
export async function signup(page, nickname = '검증 요리사') {
  const credentials = { email: `e2e-${randomUUID()}@example.test`, password: randomUUID() }
  await page.goto('/signup')
  await page.getByLabel('닉네임', { exact: true }).fill(nickname)
  await page.getByLabel('이메일', { exact: true }).fill(credentials.email)
  await page.getByLabel('비밀번호', { exact: true }).fill(credentials.password)
  await page.getByLabel('비밀번호 확인', { exact: true }).fill(credentials.password)
  await page.getByRole('button', { name: '회원가입', exact: true }).click()
  await expect(page).toHaveURL(/\/login$/)
  await page.getByLabel('비밀번호', { exact: true }).fill(credentials.password)
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page.getByRole('heading', { level: 1 })).toContainText(`${nickname}님`)
  return credentials
}

/** 브라우저의 Refresh 쿠키로 Access Token을 받아 API를 직접 호출하는 도우미. (화면 없이 테스트 데이터를 만들 때 사용) */
export async function apiHeaders(page) {
  const refreshed = await page.request.post('/api/auth/refresh')
  expect(refreshed.ok()).toBe(true)
  return { Authorization: `Bearer ${(await refreshed.json()).accessToken}` }
}

export async function createIngredient(page, headers, values) {
  const response = await page.request.post('/api/ingredients', { headers, data: {
    category: 'OTHER', unit: 'PIECE', quantity: 1, storageType: 'FRIDGE', purchaseDate: dateAfter(-3), ...values } })
  expect(response.status()).toBe(201)
  return response.json()
}

export async function cleanup(page) {
  const refreshed = await page.request.post('/api/auth/refresh', { timeout: 5000 })
  if (!refreshed.ok()) return
  const headers = { Authorization: `Bearer ${(await refreshed.json()).accessToken}` }
  const ingredients = await page.request.get('/api/ingredients', { headers })
  if (ingredients.ok()) for (const item of await ingredients.json()) await page.request.delete(`/api/ingredients/${item.id}`, { headers })
  const cart = await page.request.get('/api/shopping-items', { headers })
  if (cart.ok()) for (const item of await cart.json()) await page.request.delete(`/api/shopping-items/${item.id}`, { headers })
  const ai = await page.request.get('/api/ai/recipes', { headers })
  if (ai.ok()) for (const item of await ai.json()) await page.request.delete(`/api/ai/recipes/${item.id}`, { headers })
  await page.request.post('/api/auth/logout')
}

export const menu = (page, name) => page.getByRole('navigation', { name: '주 메뉴' }).getByRole('link', { name, exact: true })
