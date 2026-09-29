import { test, expect } from '@playwright/test'
import { randomUUID } from 'node:crypto'

const today = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit',
}).format(new Date())
function dateAfter(days) {
  const date = new Date(`${today}T00:00:00Z`)
  date.setUTCDate(date.getUTCDate() + days)
  return date.toISOString().slice(0, 10)
}
async function signup(page) {
  const credentials = { email: `e2e-${randomUUID()}@example.test`, password: randomUUID() }
  await page.goto('/signup')
  await page.getByLabel('닉네임', { exact: true }).fill('검증 요리사')
  await page.getByLabel('이메일', { exact: true }).fill(credentials.email)
  await page.getByLabel('비밀번호', { exact: true }).fill(credentials.password)
  await page.getByRole('button', { name: '회원가입', exact: true }).click()
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('status')).toContainText('가입이 완료')
  await expect(page.getByLabel('이메일', { exact: true })).toHaveValue(credentials.email)
  await page.getByLabel('비밀번호', { exact: true }).fill(credentials.password)
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page.getByRole('heading', { level: 1 })).toContainText('검증 요리사님')
  return credentials
}
async function addIngredient(page, name, category, amount, unit, days) {
  await page.getByRole('button', { name: '＋ 식재료 추가', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '식재료 한 번에 추가', exact: true })
  await dialog.getByRole('button', { name: '＋ 목록에 없는 재료 직접 입력', exact: true }).click()
  const row = dialog.locator('.batch-row').last()
  await row.getByLabel('식재료명', { exact: true }).fill(name)
  await row.getByLabel('수량', { exact: true }).fill(String(amount))
  await row.getByRole('combobox', { name: '단위', exact: true }).selectOption(unit)
  await row.getByText('유통기한·보관 정보 (선택)', { exact: true }).click()
  await row.getByRole('combobox', { name: '카테고리', exact: true }).selectOption(category)
  await row.getByLabel('구매 날짜', { exact: true }).fill(dateAfter(-7))
  await row.getByLabel('유통기한', { exact: true }).fill(dateAfter(days))
  await dialog.getByRole('button', { name: '1개 한 번에 추가', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  await expect(page.getByRole('button', { name: `${name} 수정`, exact: true })).toBeVisible()
}
async function cleanup(page) {
  const csrfResponse = await page.request.get('/api/auth/csrf', { timeout: 5000 })
  if (!csrfResponse.ok()) return
  const csrf = await csrfResponse.json()
  const response = await page.request.get('/api/ingredients')
  if (!response.ok()) return
  for (const ingredient of await response.json()) {
    await page.request.delete(`/api/ingredients/${ingredient.id}`, { headers: { [csrf.headerName]: csrf.token } })
  }
  await page.request.post('/api/auth/logout', { headers: { [csrf.headerName]: csrf.token } })
}

test('회원가입부터 재료 관리, 인분별 레시피, 채팅과 재로그인까지', async ({ page }, testInfo) => {
  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  const credentials = await signup(page)
  try {
    await page.getByRole('navigation', { name: '주 메뉴' }).getByRole('link', { name: '내 냉장고', exact: true }).click()
    await expect(page.getByRole('heading', { name: '냉장고의 첫 재료를 기록해 볼까요?' })).toBeVisible()
    await addIngredient(page, '계란', 'DAIRY', 6, 'PIECE', 0)
    await addIngredient(page, '돼지고기', 'MEAT', 1, 'KILOGRAM', 7)
    await addIngredient(page, '김치', 'VEGETABLE', 500, 'GRAM', 7)
    await addIngredient(page, '소금', 'SEASONING', 10, 'GRAM', -1)

    await page.getByRole('button', { name: '계란 수정', exact: true }).click()
    const editor = page.getByRole('dialog', { name: '식재료 수정', exact: true })
    await editor.getByLabel('수량', { exact: true }).fill('8')
    await editor.getByRole('button', { name: '수정 저장', exact: true }).click()
    await expect(editor).not.toBeVisible()
    await expect(page.locator('.ingredient-row').filter({ has: page.getByRole('button', { name: '계란 수정', exact: true }) })).toContainText('8개')

    await page.getByRole('button', { name: '오늘까지', exact: true }).click()
    await expect(page.getByText('전체 4개 중 1개', { exact: true })).toBeVisible()
    await page.getByRole('button', { name: '만료', exact: true }).click()
    await expect(page.getByRole('button', { name: '소금 수정', exact: true })).toBeVisible()
    await page.goBack()
    await expect(page.getByRole('button', { name: '오늘까지', exact: true })).toHaveAttribute('aria-pressed', 'true')
    await expect(page.getByRole('button', { name: '계란 수정', exact: true })).toBeVisible()
    await page.getByRole('button', { name: '전체', exact: true }).click()
    await page.getByLabel('식재료 검색', { exact: true }).fill('김치')
    await expect(page.getByText('전체 4개 중 1개', { exact: true })).toBeVisible()
    await page.getByLabel('식재료 검색', { exact: true }).fill('')

    await page.getByRole('navigation', { name: '주 메뉴' }).getByRole('link', { name: '오늘의 레시피', exact: true }).click()
    await expect(page.getByText('총 16개 메뉴 · 1인분 기준', { exact: true })).toBeVisible()
    await page.getByRole('button', { name: '김치찌개 레시피 보기', exact: true }).click()
    const recipe = page.getByRole('dialog', { name: '김치찌개', exact: true })
    const pork = recipe.getByRole('row').filter({ has: page.getByRole('rowheader', { name: '돼지고기', exact: true }) })
    await expect(pork.getByRole('cell').nth(0)).toHaveText('100g')
    await expect(pork.getByRole('cell').nth(1)).toHaveText('1,000g')
    await recipe.getByRole('button', { name: '2인분', exact: true }).click()
    await expect(pork.getByRole('cell').nth(0)).toHaveText('200g')
    await expect(recipe.getByRole('heading', { name: '차근차근 만드는 순서', exact: true })).toBeVisible()
    await recipe.getByRole('button', { name: '닫기', exact: true }).last().click()

    await page.getByRole('navigation', { name: '주 메뉴' }).getByRole('link', { name: 'AI 요리 도우미', exact: true }).click()
    const aiStatus = await (await page.request.get('/api/ai/status')).json()
    {
      const live = aiStatus.available && process.env.LIVE_OPENAI === '1'
      if (!live) await page.getByRole('combobox', { name: '대화 방식', exact: true }).selectOption('LOCAL')
      await page.getByLabel('요리 도우미에게 질문하기', { exact: true }).fill('오늘 냉장고 재료로 만들 요리를 추천해 줘')
      await page.getByRole('button', { name: '보내기', exact: true }).click()
      await expect(page.getByRole('log').getByText(live ? 'AI 답변' : '기본 추천', { exact: true })).toBeVisible({ timeout: 55_000 })
      await expect(page.getByRole('heading', { name: '함께 볼 수 있는 레시피', exact: true })).toBeVisible()
      await page.screenshot({ path: testInfo.outputPath('chat.png'), fullPage: true })
    }
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)

    await page.getByRole('navigation', { name: '주 메뉴' }).getByRole('link', { name: '내 냉장고', exact: true }).click()
    await page.getByRole('button', { name: '소금 삭제', exact: true }).click()
    await page.getByRole('dialog', { name: '식재료 삭제', exact: true }).getByRole('button', { name: '삭제하기', exact: true }).click()
    await expect(page.getByText('전체 3개 중 3개', { exact: true })).toBeVisible()
    await page.reload()
    await expect(page.getByRole('button', { name: '계란 수정', exact: true })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await page.screenshot({ path: testInfo.outputPath('ingredients.png'), fullPage: true })
    await page.getByRole('button', { name: '로그아웃', exact: true }).click()
    await expect(page.getByRole('button', { name: '로그인', exact: true })).toBeVisible()
    await page.getByLabel('이메일', { exact: true }).fill(credentials.email)
    await page.getByLabel('비밀번호', { exact: true }).fill(credentials.password)
    await page.getByRole('button', { name: '로그인', exact: true }).click()
    await expect(page.getByRole('heading', { level: 1 })).toContainText('검증 요리사님')
    await expect(page.getByRole('link').filter({ hasText: '전체 식재료' })).toContainText('3개')
    expect(pageErrors).toEqual([])
  } finally { await cleanup(page).catch(() => {}) }
})

test('재료 선택과 오타 후보, 유통기한 없이 저장하고 나중에 변경', async ({ page }, testInfo) => {
  await signup(page)
  try {
    await page.getByRole('navigation', { name: '주 메뉴' }).getByRole('link', { name: '내 냉장고', exact: true }).click()
    await page.getByRole('button', { name: '＋ 식재료 추가', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: '식재료 한 번에 추가', exact: true })
    await expect(dialog.getByRole('button', { name: '재료를 선택해 주세요', exact: true })).toBeDisabled()
    await dialog.getByRole('group', { name: '재료 분류', exact: true }).getByRole('button', { name: '채소', exact: true }).click()
    await expect(dialog.getByRole('button', { name: '계란 선택', exact: true })).toHaveCount(0)
    await dialog.getByRole('group', { name: '재료 분류', exact: true }).getByRole('button', { name: '전체', exact: true }).click()
    await page.screenshot({ path: testInfo.outputPath('ingredient-picker.png'), fullPage: true })
    await dialog.getByLabel('재료 검색', { exact: true }).fill('게란')
    await expect(dialog.getByText('비슷한 이름이에요. 원하는 재료를 골라 주세요.', { exact: true })).toBeVisible()
    await dialog.getByRole('button', { name: '계란 선택', exact: true }).click()
    await expect(dialog.getByRole('button', { name: '계란 선택', exact: true })).toHaveAttribute('aria-pressed', 'true')
    const review = dialog.getByRole('button', { name: '수량·기한', exact: true })
    if (await review.isVisible()) await review.click()
    await dialog.getByRole('button', { name: '계란 수량 늘리기', exact: true }).click()
    await expect(dialog.getByLabel('수량', { exact: true })).toHaveValue('2')
    await dialog.getByRole('button', { name: '계란 수량 줄이기', exact: true }).click()
    await page.screenshot({ path: testInfo.outputPath('quick-ingredient.png'), fullPage: true })
    const saveBounds = await dialog.getByRole('button', { name: '1개 한 번에 추가', exact: true }).boundingBox()
    expect(saveBounds.y + saveBounds.height).toBeLessThanOrEqual(page.viewportSize().height)
    await dialog.getByRole('button', { name: '1개 한 번에 추가', exact: true }).click()
    await expect(dialog).not.toBeVisible()
    await page.getByRole('button', { name: '기한 미등록', exact: true }).click()
    await expect(page.getByText('전체 1개 중 1개', { exact: true })).toBeVisible()
    await expect(page.locator('.ingredient-row')).toContainText('1개')
    await expect(page.locator('.ingredient-row')).toContainText('사용 전 상태 확인')
    await page.reload()
    await page.getByRole('button', { name: '계란 수정', exact: true }).click()
    const edit = page.getByRole('dialog', { name: '식재료 수정', exact: true })
    await edit.getByLabel('유통기한', { exact: true }).fill(dateAfter(5))
    await edit.getByRole('button', { name: '수정 저장', exact: true }).click()
    await expect(edit).not.toBeVisible()
    await expect(page.getByText('전체 1개 중 0개', { exact: true })).toBeVisible()
    await page.getByRole('button', { name: '전체', exact: true }).click()
    await page.getByRole('button', { name: '계란 수정', exact: true }).click()
    await edit.getByLabel('유통기한', { exact: true }).fill('')
    await edit.getByRole('button', { name: '수정 저장', exact: true }).click()
    await expect(edit).not.toBeVisible()
    await expect(page.locator('.ingredient-row')).toContainText('기한 미등록')
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  } finally { await cleanup(page).catch(() => {}) }
})

test('대시보드에서 여러 재료를 고르고 한 번에 등록', async ({ page }, testInfo) => {
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await signup(page)
  try {
    await page.getByRole('button', { name: '＋ 식재료 추가', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: '식재료 한 번에 추가', exact: true })
    for (const name of ['계란', '두부', '김치']) await dialog.getByRole('button', { name: `${name} 선택`, exact: true }).click()
    // Toggle removes the item without changing the other selections.
    await dialog.getByRole('button', { name: '김치 선택', exact: true }).click()
    await expect(dialog.getByRole('button', { name: '2개 한 번에 추가', exact: true })).toBeEnabled()
    await dialog.getByRole('button', { name: '김치 선택', exact: true }).click()
    const save = dialog.getByRole('button', { name: '3개 한 번에 추가', exact: true })
    const bounds = await save.boundingBox()
    expect(bounds.y + bounds.height).toBeLessThanOrEqual(page.viewportSize().height)
    expect(await dialog.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true)
    await page.screenshot({ path: testInfo.outputPath('batch-select.png'), fullPage: true })
    const review = dialog.getByRole('button', { name: '수량·기한', exact: true })
    if (await review.isVisible()) await review.click()
    const eggs = dialog.getByRole('article', { name: '계란 등록 정보', exact: true })
    await eggs.getByLabel('수량', { exact: true }).fill('6')
    const kimchi = dialog.getByRole('article', { name: '김치 등록 정보', exact: true })
    await kimchi.getByText('유통기한·보관 정보 (선택)', { exact: true }).click()
    await kimchi.getByLabel('유통기한', { exact: true }).fill(dateAfter(2))
    await page.screenshot({ path: testInfo.outputPath('batch-review.png'), fullPage: true })
    await save.click()
    await expect(dialog).not.toBeVisible()
    await expect(page.getByRole('status').filter({ hasText: '3개 식재료를 한 번에 추가' })).toBeVisible()
    await expect(page.getByRole('link').filter({ hasText: '전체 식재료' })).toContainText('3개')
    const ingredients = await (await page.request.get('/api/ingredients')).json()
    expect(ingredients).toHaveLength(3)
    expect(ingredients.find(item => item.name === '계란').quantity).toBe(6)
    expect(ingredients.filter(item => item.expirationDate === null)).toHaveLength(2)
    expect(ingredients.find(item => item.name === '김치').status).toBe('SOON')
    expect(errors).toEqual([])
  } finally { await cleanup(page).catch(() => {}) }
})

test('목록 붙여넣기 검증과 응답 유실 후 재시도는 중복 없이 저장', async ({ page }, testInfo) => {
  await signup(page)
  try {
    await page.getByRole('button', { name: '＋ 식재료 추가', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: '식재료 한 번에 추가', exact: true })
    await dialog.getByRole('button', { name: '장본 목록 붙여넣기', exact: true }).click()
    await dialog.getByLabel('장본 재료 목록', { exact: true }).fill('게란 6개, 두부 1모')
    await dialog.getByRole('button', { name: '선택 목록에 담기', exact: true }).click()
    await expect(dialog.getByRole('alert')).toContainText('계란')
    await expect(dialog.getByRole('button', { name: '재료를 선택해 주세요', exact: true })).toBeDisabled()
    await dialog.getByLabel('장본 재료 목록', { exact: true }).fill('달걀 6개, 두부 1모\n우유 1L')
    await dialog.getByRole('button', { name: '선택 목록에 담기', exact: true }).click()
    await expect(dialog.locator('.batch-row')).toHaveCount(3)
    await expect(dialog.getByRole('article', { name: '우유 등록 정보', exact: true }).getByRole('combobox', { name: '단위', exact: true })).toHaveValue('LITER')
    await page.screenshot({ path: testInfo.outputPath('batch-pasted.png'), fullPage: true })
    const attempts = []
    await page.route('**/api/ingredients/batch', async route => {
      attempts.push(route.request().postDataJSON())
      if (attempts.length === 1) {
        // Commit on the real server, then simulate a lost response to the browser.
        const committed = await route.fetch()
        expect(committed.status()).toBe(201)
        await route.abort('failed')
      } else await route.continue()
    })
    await dialog.getByRole('button', { name: '3개 한 번에 추가', exact: true }).click()
    await expect(dialog.getByRole('alert')).toBeVisible()
    await expect(dialog.locator('.batch-row')).toHaveCount(3)
    await dialog.getByRole('button', { name: '3개 한 번에 추가', exact: true }).click()
    await expect(dialog).not.toBeVisible()
    expect(attempts).toHaveLength(2)
    expect(attempts[0].requestId).toBe(attempts[1].requestId)
    const saved = await (await page.request.get('/api/ingredients')).json()
    expect(saved).toHaveLength(3)
    expect(saved.map(item => item.name).sort()).toEqual(['계란', '두부', '우유'])
    await page.reload()
    await expect(page.getByRole('link').filter({ hasText: '전체 식재료' })).toContainText('3개')
  } finally { await cleanup(page).catch(() => {}) }
})

test('AI 오류 후 같은 질문을 기본 추천으로 전환', async ({ page }) => {
  await signup(page)
  const sent = []
  try {
    await page.route('**/api/ai/status', route => route.fulfill({
      contentType: 'application/json', body: JSON.stringify({ available: true, model: 'gpt-5-mini' }),
    }))
    await page.route('**/api/ai/chat', async route => {
      const payload = route.request().postDataJSON()
      sent.push(payload)
      if (payload.mode === 'LOCAL') return route.continue()
      // 실제 OpenAI 요청 없이 결제 오류를 재현합니다.
      await route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({
        code: 'AI_QUOTA_EXCEEDED', message: 'AI 서비스의 결제 잔액 또는 사용 한도를 확인해야 해요.', fieldErrors: {},
      }) })
    })
    await page.getByRole('navigation', { name: '주 메뉴' }).getByRole('link', { name: 'AI 요리 도우미', exact: true }).click()
    await expect(page.getByText('AI 대화 · gpt-5-mini · 답변마다 제공 방식을 표시해요', { exact: true })).toBeVisible()
    await page.getByLabel('요리 도우미에게 질문하기', { exact: true }).fill('오늘 뭘 먹을까?')
    await page.getByRole('button', { name: '보내기', exact: true }).click()
    await expect(page.getByRole('alert')).toContainText('결제 잔액')
    await expect(page.getByRole('log').getByText('AI 답변', { exact: true })).toHaveCount(0)
    await page.getByRole('button', { name: '기본 추천으로 답변 받기', exact: true }).click()
    await expect(page.getByRole('log').getByText('기본 추천', { exact: true })).toBeVisible()
    await expect(page.getByRole('log')).toContainText('아직 등록한 식재료가 없어요')
    await expect(page.getByRole('log').getByText('오늘 뭘 먹을까?', { exact: true })).toHaveCount(1)
    await expect(page.getByRole('combobox', { name: '대화 방식', exact: true })).toHaveValue('LOCAL')
    expect(sent.map(item => item.mode)).toEqual(['AUTO', 'LOCAL'])
    expect(sent[0].message).toBe(sent[1].message)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  } finally { await cleanup(page).catch(() => {}) }
})

test('재가입 화면 이동과 서버 오류 재시도, 만료된 세션 복구', async ({ page }) => {
  await signup(page)
  try {
    await page.route('**/api/ingredients?sort=expiration', route => route.fulfill({
      status: 503, contentType: 'application/json', body: JSON.stringify({ code: 'TEST_UNAVAILABLE', message: '검증용 일시 오류', fieldErrors: {} }),
    }))
    await page.getByRole('navigation', { name: '주 메뉴' }).getByRole('link', { name: '내 냉장고', exact: true }).click()
    await expect(page.getByRole('alert')).toContainText('검증용 일시 오류')
    await page.unroute('**/api/ingredients?sort=expiration')
    await page.getByRole('button', { name: '다시 시도', exact: true }).click()
    await expect(page.getByRole('heading', { name: '냉장고의 첫 재료를 기록해 볼까요?' })).toBeVisible()
    const csrf = await (await page.request.get('/api/auth/csrf')).json()
    await page.request.post('/api/auth/logout', { headers: { [csrf.headerName]: csrf.token } })
    await page.getByRole('button', { name: '새로고침', exact: true }).click()
    await expect(page.getByRole('button', { name: '로그인', exact: true })).toBeVisible()
    await expect(page.getByRole('status')).toContainText('로그인 시간이 만료')
    await page.getByRole('link', { name: '회원가입', exact: true }).click()
    await expect(page.getByLabel('닉네임', { exact: true })).toBeVisible()
  } finally { await cleanup(page).catch(() => {}) }
})
