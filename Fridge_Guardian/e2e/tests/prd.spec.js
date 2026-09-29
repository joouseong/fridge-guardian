import { test, expect } from '@playwright/test'
import { PNG, apiHeaders, cleanup, createIngredient, dateAfter, menu, signup } from './support.js'

const noOverflow = (page) => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)

test('사진·메모로 등록하고 D-Day 카드, 사진 교체, 삭제 시 파일 정리까지', async ({ page }, testInfo) => {
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await signup(page)
  try {
    await page.getByRole('button', { name: '📷 사진·바코드로 등록', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: '식재료 추가', exact: true })
    await dialog.getByRole('button', { name: '두부 선택', exact: true }).click()
    await dialog.getByLabel('유통기한', { exact: true }).fill(dateAfter(2))
    await dialog.getByRole('textbox', { name: '메모 (선택)', exact: true }).fill('유기농 두부')
    // 6MB 파일은 서버에 보내기 전에 화면에서 안내합니다. 이미지가 아닌 파일도 거절합니다.
    await dialog.locator('input[type=file]').setInputFiles({ name: 'big.png', mimeType: 'image/png', buffer: Buffer.alloc(6 * 1024 * 1024, 1) })
    await expect(dialog.getByRole('alert')).toContainText('5MB 이하')
    await dialog.locator('input[type=file]').setInputFiles({ name: 'doc.pdf', mimeType: 'application/pdf', buffer: Buffer.from('x') })
    await expect(dialog.getByRole('alert')).toContainText('JPG, PNG, WEBP')
    await dialog.locator('input[type=file]').setInputFiles({ name: 'tofu.png', mimeType: 'image/png', buffer: PNG })
    await expect(dialog.getByRole('alert')).toHaveCount(0)
    await page.screenshot({ path: testInfo.outputPath('editor.png'), fullPage: true })
    await dialog.getByRole('button', { name: '식재료 저장', exact: true }).click()
    await expect(dialog).not.toBeVisible()

    // 대시보드 카드: 사진 · 수량 · 보관 위치 · 유통기한 · D-Day
    const card = page.locator('.ingredient-card').filter({ hasText: '두부' })
    await expect(card).toContainText('임박')
    await expect(card).toContainText('D-2')
    await expect(card).toContainText('냉장')
    const source = await card.locator('img').getAttribute('src')
    expect(source).toMatch(/^\/uploads\/[0-9a-f-]{36}\.png$/)
    expect((await page.request.get(source)).status()).toBe(200)
    await expect(card.locator('img')).toHaveJSProperty('naturalWidth', 1)

    await menu(page, '내 냉장고').click()
    await expect(page.locator('.ingredient-row').filter({ hasText: '두부' })).toContainText('유기농 두부')
    // 수정할 때 사진을 고르지 않으면 기존 사진이 유지됩니다.
    await page.getByRole('button', { name: '두부 수정', exact: true }).click()
    const edit = page.getByRole('dialog', { name: '식재료 수정', exact: true })
    await edit.getByRole('textbox', { name: '메모 (선택)', exact: true }).fill('유기농 두부 · 개봉')
    await edit.getByRole('button', { name: '수정 저장', exact: true }).click()
    await expect(edit).not.toBeVisible()
    const row = page.locator('.ingredient-row').filter({ hasText: '두부' })
    await expect(row).toContainText('개봉')
    await expect(row.locator('img')).toHaveAttribute('src', source)
    // 새 사진으로 바꾸면 이전 파일은 서버에서 지워집니다.
    await page.getByRole('button', { name: '두부 수정', exact: true }).click()
    await edit.locator('input[type=file]').setInputFiles({ name: 'tofu2.png', mimeType: 'image/png', buffer: Buffer.concat([PNG, Buffer.from([0])]) })
    await edit.getByRole('button', { name: '수정 저장', exact: true }).click()
    await expect(edit).not.toBeVisible()
    await expect(row.locator('img')).not.toHaveAttribute('src', source)
    const replaced = await row.locator('img').getAttribute('src')
    expect((await page.request.get(source)).status()).toBe(404)
    expect((await page.request.get(replaced)).status()).toBe(200)
    // 삭제하면 사진 파일도 함께 지워집니다.
    await page.getByRole('button', { name: '두부 삭제', exact: true }).click()
    await page.getByRole('dialog', { name: '식재료 삭제', exact: true }).getByRole('button', { name: '삭제하기', exact: true }).click()
    await expect(page.getByRole('heading', { name: '냉장고의 첫 재료를 기록해 볼까요?' })).toBeVisible()
    expect((await page.request.get(replaced)).status()).toBe(404)
    expect(await noOverflow(page)).toBe(true)
    expect(errors).toEqual([])
  } finally { await cleanup(page).catch(() => {}) }
})

test('대시보드는 유통기한이 가까운 재료 6개만 D-Day 배지와 함께 보여 준다', async ({ page }, testInfo) => {
  await signup(page)
  try {
    const headers = await apiHeaders(page)
    for (const [name, days] of [['재료 A', 1], ['재료 B', 2], ['재료 C', 4], ['재료 D', 6], ['재료 E', 9], ['재료 F', 12], ['재료 G', 20]]) {
      await createIngredient(page, headers, { name, expirationDate: dateAfter(days) })
    }
    await createIngredient(page, headers, { name: '만료 재료', expirationDate: dateAfter(-1) })
    await createIngredient(page, headers, { name: '기한 없는 재료' })
    await page.reload()
    const cards = page.locator('.ingredient-card')
    await expect(cards).toHaveCount(6)
    await expect(cards.first()).toContainText('재료 A')
    await expect(cards.first()).toContainText('D-1')
    await expect(cards.nth(2)).toContainText('D-4')
    await expect(cards.nth(2)).toContainText('여유') // 3일 초과 → 여유
    await expect(cards.first()).toContainText('임박') // 3일 이내 → 임박
    await expect(page.locator('.ingredient-card', { hasText: '재료 G' })).toHaveCount(0)
    await expect(page.locator('.ingredient-card', { hasText: '만료 재료' })).toHaveCount(0)
    await expect(page.getByRole('link').filter({ hasText: '유통기한 만료' })).toContainText('1개')
    await page.screenshot({ path: testInfo.outputPath('dashboard.png'), fullPage: true })
    expect(await noOverflow(page)).toBe(true)
  } finally { await cleanup(page).catch(() => {}) }
})

test('검색·카테고리·보관 위치·상태 필터와 초기화 버튼', async ({ page }) => {
  await signup(page)
  try {
    const headers = await apiHeaders(page)
    await createIngredient(page, headers, { name: '우유', category: 'DAIRY', expirationDate: dateAfter(1) })
    await createIngredient(page, headers, { name: '사과', category: 'FRUIT', expirationDate: dateAfter(10) })
    await createIngredient(page, headers, { name: '냉동만두', category: 'PROCESSED', storageType: 'FREEZER', expirationDate: dateAfter(60) })
    await createIngredient(page, headers, { name: '오래된 요거트', category: 'DAIRY', expirationDate: dateAfter(-2) })
    await menu(page, '내 냉장고').click()
    await expect(page.getByText('전체 4개', { exact: true })).toBeVisible()
    await page.getByLabel('식재료 검색', { exact: true }).fill('우유')
    await expect(page.getByText('조건에 맞는 재료 1개', { exact: true })).toBeVisible()
    await page.getByLabel('식재료 검색', { exact: true }).fill('')
    await page.getByRole('combobox', { name: '카테고리 필터', exact: true }).selectOption('DAIRY')
    await expect(page.getByText('조건에 맞는 재료 2개', { exact: true })).toBeVisible()
    await page.getByRole('button', { name: '만료', exact: true }).click()
    await expect(page.getByText('조건에 맞는 재료 1개', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: '오래된 요거트 수정', exact: true })).toBeVisible()
    await page.getByRole('button', { name: '검색 조건 초기화', exact: true }).click()
    await expect(page.getByText('전체 4개', { exact: true })).toBeVisible()
    await expect(page.getByRole('combobox', { name: '카테고리 필터', exact: true })).toHaveValue('')
    await page.getByRole('combobox', { name: '보관 위치 필터', exact: true }).selectOption('FREEZER')
    await expect(page.getByRole('button', { name: '냉동만두 수정', exact: true })).toBeVisible()
    await expect(page.getByText('조건에 맞는 재료 1개', { exact: true })).toBeVisible()
    await page.getByRole('button', { name: '검색 조건 초기화', exact: true }).click()
    await page.getByLabel('식재료 검색', { exact: true }).fill('없는재료')
    await expect(page.getByRole('heading', { name: '조건에 맞는 재료가 없어요' })).toBeVisible()
    await page.getByRole('button', { name: '검색과 필터 초기화', exact: true }).click()
    await expect(page.getByText('전체 4개', { exact: true })).toBeVisible()
  } finally { await cleanup(page).catch(() => {}) }
})

test('일치율·BEST MATCH·부족 재료 장바구니와 쇼핑 링크', async ({ page }, testInfo) => {
  await signup(page)
  try {
    const headers = await apiHeaders(page)
    await createIngredient(page, headers, { name: '계란', quantity: 6, expirationDate: dateAfter(2) })
    await createIngredient(page, headers, { name: '밥', category: 'GRAIN', quantity: 400, unit: 'GRAM', expirationDate: dateAfter(3) })
    await menu(page, '오늘의 레시피').click()
    await expect(page.getByText('BEST MATCH', { exact: true })).toHaveCount(1)
    const best = page.locator('.recipe-card.best')
    await expect(best.locator('.match-rate strong')).toHaveText(/\d+%/)
    await expect(best.getByRole('list', { name: '필요한 재료' })).toContainText('계란')
    await page.screenshot({ path: testInfo.outputPath('recipes.png'), fullPage: true })
    // 간장 계란밥(밥·계란·간장·식용유)은 일치율 50%, 부족한 재료는 간장·식용유 2개
    await page.getByRole('button', { name: '간장 계란밥 레시피 보기', exact: true }).click()
    const recipe = page.getByRole('dialog', { name: '간장 계란밥', exact: true })
    await expect(recipe).toContainText('재료 일치율 50%')
    const links = recipe.getByRole('link', { name: /에서 찾기/ })
    await expect(links).toHaveCount(4) // 부족한 재료 2개 × (쿠팡, 마켓컬리)
    await expect(links.first()).toHaveAttribute('href', /^https:\/\/www\.coupang\.com\/np\/search\?q=/)
    await expect(links.first()).toHaveAttribute('target', '_blank')
    await expect(links.first()).toHaveAttribute('rel', /noopener/)
    await recipe.getByRole('button', { name: '🛒 부족한 재료 장바구니에 담기', exact: true }).click()
    await expect(recipe.getByRole('status')).toContainText('2개 재료를 장바구니에 담았어요')
    await recipe.getByRole('button', { name: '🛒 부족한 재료 장바구니에 담기', exact: true }).click()
    await expect(recipe.getByRole('status')).toContainText('이미 장바구니에')
    await recipe.getByRole('button', { name: '닫기', exact: true }).last().click()

    await menu(page, '장바구니').click()
    await expect(page.getByLabel('살 재료')).toContainText('간장')
    await expect(page.getByLabel('살 재료')).toContainText('식용유')
    await page.getByRole('checkbox').first().click()
    await expect(page.getByLabel('산 재료')).toBeVisible()
    await page.getByRole('button', { name: '산 항목 비우기', exact: true }).click()
    await expect(page.getByLabel('산 재료')).toHaveCount(0)
    await page.getByLabel('직접 추가', { exact: true }).fill('우유')
    await page.getByRole('button', { name: '담기', exact: true }).click()
    await expect(page.getByLabel('살 재료')).toContainText('우유')
    expect(await noOverflow(page)).toBe(true)
  } finally { await cleanup(page).catch(() => {}) }
})

test('알레르기·비선호·매운맛 설정이 추천 목록에 반영된다', async ({ page }) => {
  await signup(page)
  try {
    await menu(page, '오늘의 레시피').click()
    await expect(page.getByText('총 21개 메뉴 · 1인분 기준', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: '김치찌개 레시피 보기', exact: true })).toBeVisible()
    await menu(page, '내 설정').click()
    await page.getByRole('combobox', { name: '먹을 수 있는 매운맛', exact: true }).selectOption('0')
    await page.getByLabel('알레르기 재료', { exact: true }).fill('우유')
    await page.getByLabel('알레르기 재료', { exact: true }).press('Enter')
    await page.getByLabel('비선호 재료', { exact: true }).fill('김치')
    await page.getByRole('button', { name: '추가', exact: true }).last().click()
    await expect(page.getByRole('list', { name: '알레르기 재료 목록' })).toContainText('우유')
    await page.getByRole('button', { name: '설정 저장', exact: true }).click()
    await expect(page.getByRole('status').filter({ hasText: '설정을 저장했어요' })).toBeVisible()
    await page.reload()
    await expect(page.getByRole('combobox', { name: '먹을 수 있는 매운맛', exact: true })).toHaveValue('0')
    await expect(page.getByRole('list', { name: '비선호 재료 목록' })).toContainText('김치')
    await menu(page, '오늘의 레시피').click()
    await expect(page.getByRole('button', { name: '김치찌개 레시피 보기', exact: true })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '버섯 우유 파스타 레시피 보기', exact: true })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '간장 계란밥 레시피 보기', exact: true })).toBeVisible()
    // 설정을 지우면 다시 모두 추천됩니다.
    await menu(page, '내 설정').click()
    await page.getByRole('combobox', { name: '먹을 수 있는 매운맛', exact: true }).selectOption('3')
    await page.getByRole('button', { name: '우유 삭제', exact: true }).click()
    await page.getByRole('button', { name: '김치 삭제', exact: true }).click()
    await page.getByRole('button', { name: '설정 저장', exact: true }).click()
    await expect(page.getByRole('status').filter({ hasText: '설정을 저장했어요' })).toBeVisible()
    await menu(page, '오늘의 레시피').click()
    await expect(page.getByText('총 21개 메뉴 · 1인분 기준', { exact: true })).toBeVisible()
  } finally { await cleanup(page).catch(() => {}) }
})

test('AI 레시피: 키가 없으면 안내하고, 만들어진 결과는 보유·부족을 구분해 보여 준다', async ({ page }, testInfo) => {
  await signup(page)
  try {
    const headers = await apiHeaders(page)
    await createIngredient(page, headers, { name: '계란', quantity: 6, expirationDate: dateAfter(2) })
    await createIngredient(page, headers, { name: '두부', unit: 'BLOCK', expirationDate: dateAfter(4) })
    await createIngredient(page, headers, { name: '상한 우유', unit: 'LITER', expirationDate: dateAfter(-1) })
    await menu(page, 'AI 레시피').click()
    const status = await (await page.request.get('/api/ai/status', { headers })).json()
    if (!status.available) {
      await expect(page.getByRole('status').filter({ hasText: 'AI 연결이 아직 설정되지 않았어요' })).toBeVisible()
      await expect(page.getByRole('button', { name: '재료를 먼저 골라 주세요', exact: true })).toBeDisabled()
    }
    // 만료된 재료는 고를 수 없습니다.
    await expect(page.getByRole('button', { name: /상한 우유/ })).toHaveCount(0)
    await page.getByRole('button', { name: /^계란/ }).click()
    await page.getByRole('button', { name: /^두부/ }).click()
    await expect(page.getByText('2/12개 선택', { exact: true })).toBeVisible()

    // 실제 LLM 호출 없이 서버가 만들어 줄 응답 형식으로 화면을 검증합니다.
    await page.route('**/api/ai/status', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ available: true, model: 'gpt-5-mini' }) }))
    await page.reload()
    await page.getByRole('button', { name: /^계란/ }).click()
    await page.getByRole('button', { name: /^두부/ }).click()
    let requestBody
    await page.route('**/api/ai/recipes', async route => {
      if (route.request().method() !== 'POST') return route.continue()
      requestBody = route.request().postDataJSON()
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({
        id: 0, title: '두부계란부침', description: '두부와 계란을 노릇하게 부칩니다.', servings: 1, cookingTimeMinutes: 15, difficulty: 'EASY',
        ingredients: [{ name: '계란', quantity: 2, unit: '개', status: 'OWNED' }, { name: '두부', quantity: 0.5, unit: '모', status: 'OWNED' },
          { name: '소금', quantity: 1, unit: 'g', status: 'BASIC' }, { name: '대파', quantity: 0.5, unit: '단', status: 'MISSING' }],
        missing: ['대파'], steps: ['두부의 물기를 뺍니다.', '계란물을 입혀 속까지 익힙니다.'], tips: '대파가 없으면 생략해도 좋아요.', model: 'fake',
        sourceIngredients: ['계란', '두부'], shoppingLinks: [{ ingredient: '대파', mall: '쿠팡', url: 'https://www.coupang.com/np/search?q=%EB%8C%80%ED%8C%8C' },
          { ingredient: '대파', mall: '마켓컬리', url: 'https://www.kurly.com/search?sword=%EB%8C%80%ED%8C%8C' }], favorite: false, createdAt: '2026-09-28T12:00:00' }) })
    })
    await page.getByLabel('요청 사항 (선택)', { exact: true }).fill('간단하게')
    await page.getByRole('button', { name: '선택한 2개로 AI 레시피 만들기', exact: true }).click()
    const panel = page.locator('.ai-recipe').first()
    await expect(panel.getByRole('heading', { name: '두부계란부침' })).toBeVisible()
    expect(requestBody).toEqual({ ingredientIds: expect.any(Array), servings: 1, request: '간단하게' })
    expect(requestBody.ingredientIds).toHaveLength(2)
    await expect(panel.locator('.ai-ingredients li').filter({ hasText: '대파' })).toContainText('부족')
    await expect(panel.locator('.ai-ingredients li').filter({ hasText: '소금' })).toContainText('기본 양념')
    await expect(panel.locator('.ai-ingredients li').filter({ hasText: '계란' })).toContainText('보유')
    await expect(panel.getByRole('link', { name: /쿠팡에서 찾기/ })).toHaveAttribute('href', /coupang\.com/)
    await panel.getByRole('button', { name: '🛒 부족한 재료 장바구니에 담기', exact: true }).click()
    await expect(panel.getByRole('status')).toContainText('1개 재료를 장바구니에 담았어요')
    await page.screenshot({ path: testInfo.outputPath('ai-recipe.png'), fullPage: true })
    expect(await noOverflow(page)).toBe(true)
  } finally { await cleanup(page).catch(() => {}) }
})

test('소셜 로그인 버튼과 제공자 인증 시작 주소, 로그인 유지와 로그아웃', async ({ page }, testInfo) => {
  await page.route('**/api/auth/providers', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ google: true, kakao: true }) }))
  await page.goto('/login')
  const google = page.getByRole('link', { name: 'Google로 계속하기', exact: true })
  const kakao = page.getByRole('link', { name: '카카오로 계속하기', exact: true })
  await expect(google).toHaveAttribute('href', '/oauth2/authorization/google')
  await expect(kakao).toHaveAttribute('href', '/oauth2/authorization/kakao')
  await page.screenshot({ path: testInfo.outputPath('login.png'), fullPage: true })
  // Vite 프록시를 거쳐 서버가 제공자 인증 화면으로 보내고, 콜백 주소가 프런트엔드 주소로 만들어지는지 확인합니다.
  for (const [provider, host] of [['google', 'https://accounts.google.com/'], ['kakao', 'https://kauth.kakao.com/oauth/authorize']]) {
    const response = await page.request.get(`/oauth2/authorization/${provider}`, { maxRedirects: 0 })
    expect(response.status()).toBe(302)
    const location = response.headers().location
    expect(location.startsWith(host)).toBe(true)
    expect(decodeURIComponent(location)).toContain(`redirect_uri=http://127.0.0.1:5173/login/oauth2/code/${provider}`)
  }
  // 소셜 로그인 실패는 로그인 화면에 안내합니다. 그 밖의 잘못된 콜백 주소는 기본 화면으로 돌아옵니다.
  await page.goto('/login?error=oauth')
  await expect(page.getByRole('alert')).toContainText('소셜 로그인에 실패했어요')
  await page.goto('/oauth/callback')
  await expect(page).toHaveURL(/\/login\?error=oauth$/)
  await page.unroute('**/api/auth/providers')

  await signup(page)
  try {
    await page.reload()
    await expect(page.getByRole('heading', { level: 1 })).toContainText('검증 요리사님') // Refresh 쿠키로 로그인 유지
    await page.getByRole('button', { name: /알림함 열기|알림 \d+개 확인하기/ }).click()
    await expect(page.getByRole('dialog', { name: '유통기한 알림', exact: true })).toContainText('새 알림이 없어요')
    await page.getByRole('dialog', { name: '유통기한 알림', exact: true }).getByRole('button', { name: '닫기', exact: true }).click()
    await page.getByRole('button', { name: '로그아웃', exact: true }).click()
    await expect(page.getByRole('button', { name: '로그인', exact: true })).toBeVisible()
    await page.reload()
    await expect(page.getByRole('button', { name: '로그인', exact: true })).toBeVisible() // 로그아웃 뒤에는 복구되지 않음
    await expect(page.getByRole('heading', { level: 1 })).toHaveCount(1)
  } finally { await cleanup(page).catch(() => {}) }
})

test('소셜 로그인 키가 없으면 버튼을 숨기지 않고 비활성으로 안내한다', async ({ page }) => {
  await page.route('**/api/auth/providers', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ google: false, kakao: false }) }))
  await page.goto('/login')
  await expect(page.getByRole('button', { name: /Google로 계속하기/ })).toBeDisabled()
  await expect(page.getByRole('button', { name: /카카오로 계속하기/ })).toBeDisabled()
  await expect(page.getByRole('link', { name: /계속하기/ })).toHaveCount(0)
})

test('회원가입 검증: 비밀번호 6자 이상과 확인 일치', async ({ page }) => {
  await page.goto('/signup')
  await page.getByLabel('닉네임', { exact: true }).fill('검증')
  await page.getByLabel('이메일', { exact: true }).fill(`rule-${Date.now()}@example.test`)
  await page.getByLabel('비밀번호', { exact: true }).fill('abc12')
  await page.getByLabel('비밀번호 확인', { exact: true }).fill('abc12')
  await page.getByRole('button', { name: '회원가입', exact: true }).click()
  await expect(page).toHaveURL(/\/signup$/) // 브라우저 검증(minLength)이 막습니다.
  await page.getByLabel('비밀번호', { exact: true }).fill('abc123')
  await page.getByLabel('비밀번호 확인', { exact: true }).fill('abc124')
  await page.getByRole('button', { name: '회원가입', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('일치하지 않아요')
  await page.getByLabel('비밀번호 확인', { exact: true }).fill('abc123')
  await page.getByRole('button', { name: '회원가입', exact: true }).click()
  await expect(page).toHaveURL(/\/login$/)
})

test('계정 정보: 닉네임 변경과 비밀번호 변경 후 새 비밀번호로 로그인', async ({ page }) => {
  const { email, password } = await signup(page)
  try {
    await menu(page, '내 설정').click()
    await page.getByLabel('닉네임', { exact: true }).fill('바뀐 이름')
    // 현재 비밀번호가 틀리면 저장되지 않습니다.
    await page.getByLabel('현재 비밀번호', { exact: true }).fill('wrong-password')
    await page.getByLabel('새 비밀번호 (6자 이상)', { exact: true }).fill('newpass123')
    await page.getByLabel('새 비밀번호 확인', { exact: true }).fill('newpass123')
    await page.getByRole('button', { name: '계정 정보 저장', exact: true }).click()
    await expect(page.getByRole('alert')).toContainText('현재 비밀번호가 일치하지 않')
    await page.getByLabel('현재 비밀번호', { exact: true }).fill(password)
    await page.getByRole('button', { name: '계정 정보 저장', exact: true }).click()
    await expect(page.getByRole('status').filter({ hasText: '계정 정보를 저장했어요' })).toBeVisible()
    await expect(page.locator('.user-pill')).toContainText('바뀐 이름님')
    // 새 비밀번호로 다시 로그인됩니다.
    await page.getByRole('button', { name: '로그아웃', exact: true }).click()
    await page.getByLabel('이메일', { exact: true }).fill(email)
    await page.getByLabel('비밀번호', { exact: true }).fill('newpass123')
    await page.getByRole('button', { name: '로그인', exact: true }).click()
    await expect(page.locator('.user-pill')).toContainText('바뀐 이름님')
  } finally { await cleanup(page).catch(() => {}) }
})
