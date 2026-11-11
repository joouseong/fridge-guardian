import { chromium } from '@playwright/test'
const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' })
for (const [w, h] of [[1366, 768], [1920, 1080], [2560, 1440]]) {
  const page = await (await browser.newContext({ viewport: { width: w, height: h } })).newPage()
  await page.goto('http://localhost:3000/login')
  await page.getByLabel('이메일', { exact: true }).fill('demo@example.test')
  await page.getByLabel('비밀번호', { exact: true }).fill('demo1234')
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await page.getByRole('heading', { level: 1 }).waitFor()
  await page.waitForSelector('.recipe-card')
  const m = await page.evaluate(() => {
    const box = (s) => Math.round(document.querySelector(s).getBoundingClientRect().width)
    const cols = getComputedStyle(document.querySelector('.recipe-grid')).gridTemplateColumns.split(' ').length
    return { viewport: window.innerWidth, main: box('.main-content'), topbar: box('.topbar'), recipeColumns: cols, overflow: document.documentElement.scrollWidth > window.innerWidth }
  })
  console.log(w + 'x' + h, JSON.stringify(m))
  await page.screenshot({ path: `demo/shots/wide-${w}.png` })
  await page.close()
}
await browser.close()
