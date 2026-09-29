// 시연용: 화면이 보이는 Chrome으로 앱을 천천히 둘러봅니다. (테스트가 아님)
import { chromium } from '@playwright/test'
import fs from 'node:fs'

const base = 'http://127.0.0.1:5173'
const email = 'demo@example.test'
const password = 'demo1234'
const out = new URL('./shots/', import.meta.url)
fs.mkdirSync(out, { recursive: true })
const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul' }).format(new Date())
const after = (days) => { const d = new Date(`${today}T00:00:00Z`); d.setUTCDate(d.getUTCDate() + days); return d.toISOString().slice(0, 10) }

// 1) 시연 계정과 샘플 재료 준비 (이미 있으면 재사용)
async function api(path, options = {}, token) {
  const response = await fetch(base + '/api' + path, { ...options, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...options.headers } })
  const text = await response.text()
  return { status: response.status, data: text ? JSON.parse(text) : null }
}
await api('/auth/register', { method: 'POST', body: JSON.stringify({ email, password, passwordConfirm: password, nickname: '민우' }) })
const login = await api('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) })
const token = login.data.accessToken
const existing = (await api('/ingredients', {}, token)).data
if (!existing.length) {
  const items = [['계란', 'DAIRY', 6, 'PIECE', 1], ['두부', 'OTHER', 1, 'BLOCK', 2], ['김치', 'VEGETABLE', 300, 'GRAM', 9], ['대파', 'VEGETABLE', 1, 'BUNCH', 3],
    ['우유', 'DAIRY', 500, 'MILLILITER', 5], ['밥', 'GRAIN', 400, 'GRAM', 4], ['돼지고기', 'MEAT', 300, 'GRAM', 6], ['사과', 'FRUIT', 2, 'PIECE', 12], ['소금', 'SEASONING', 100, 'GRAM', -1]]
  for (const [name, category, quantity, unit, days] of items) {
    await api('/ingredients', { method: 'POST', body: JSON.stringify({ name, category, quantity, unit, purchaseDate: after(-5), expirationDate: after(days), storageType: 'FRIDGE', memo: name === '계란' ? '유기농 달걀' : undefined }) }, token)
  }
}

// 2) 눈에 보이는 Chrome으로 로그인부터 둘러보기
const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: false, slowMo: 350, args: ['--window-size=1400,950'] })
const page = await (await browser.newContext({ viewport: { width: 1360, height: 860 } })).newPage()
const shot = async (name, full = false) => page.screenshot({ path: new URL(`${name}.png`, out).pathname.replace(/^\//, ''), fullPage: full })
await page.goto(base + '/login')
await page.waitForTimeout(800)
await shot('1-login')
await page.getByLabel('이메일', { exact: true }).fill(email)
await page.getByLabel('비밀번호', { exact: true }).fill(password)
await page.getByRole('button', { name: '로그인', exact: true }).click()
await page.getByRole('heading', { level: 1 }).waitFor()
await page.waitForTimeout(1200)
await shot('2-dashboard', true)
await page.getByRole('navigation', { name: '주 메뉴' }).getByRole('link', { name: '내 냉장고', exact: true }).click()
await page.waitForTimeout(800)
await page.getByRole('button', { name: '임박', exact: true }).click()
await page.waitForTimeout(800)
await shot('3-ingredients-soon')
await page.getByRole('button', { name: '검색 조건 초기화', exact: true }).click()
await page.getByRole('navigation', { name: '주 메뉴' }).getByRole('link', { name: '오늘의 레시피', exact: true }).click()
await page.getByText('BEST MATCH').waitFor()
await page.waitForTimeout(800)
await shot('4-recipes')
await page.locator('.recipe-card.best').getByRole('button', { name: /레시피 보기/ }).click()
await page.waitForTimeout(1200)
await shot('5-recipe-detail')
await page.getByRole('button', { name: '닫기', exact: true }).last().click()
await page.getByRole('navigation', { name: '주 메뉴' }).getByRole('link', { name: '내 설정', exact: true }).click()
await page.waitForTimeout(600)
await shot('6-profile')
await page.getByRole('navigation', { name: '주 메뉴' }).getByRole('link', { name: 'AI 레시피', exact: true }).click()
await page.waitForTimeout(1000)
await page.getByRole('button', { name: /^계란/ }).click()
await page.getByRole('button', { name: /^두부/ }).click()
await shot('7-ai-recipe')
console.log('DONE — 브라우저 창은 열어 둡니다. 닫으려면 창을 직접 닫으세요.')
await page.waitForTimeout(600000)
await browser.close()
