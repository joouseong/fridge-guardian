// 실행 중인 실제 서버/MySQL을 대상으로 JWT 로그인·재료·사진·추천·개인화·장바구니·알림 흐름을 HTTP로 검증합니다.
// 생성한 식재료·장바구니는 finally에서 제거합니다. 테스트 회원 2개는 smoke- 접두사로 남습니다.
import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'

const base = process.env.API_BASE_URL || 'http://127.0.0.1:8080'
const today = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit',
}).format(new Date())
function dateAfter(days) {
  const value = new Date(`${today}T00:00:00Z`)
  value.setUTCDate(value.getUTCDate() + days)
  return value.toISOString().slice(0, 10)
}
// 1x1 PNG. 서버는 확장자가 아니라 파일 앞부분(매직 바이트)으로 이미지를 판별합니다.
const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64')

class Client {
  cookies = new Map()
  token = null
  async request(method, path, body, { auth = true, form } = {}) {
    const headers = {}
    if (!form && body !== undefined) headers['Content-Type'] = 'application/json'
    if (auth && this.token) headers.Authorization = `Bearer ${this.token}`
    if (this.cookies.size) headers.Cookie = [...this.cookies].map(([k, v]) => `${k}=${v}`).join('; ')
    const response = await fetch(base + path, { method, headers, redirect: 'manual',
      body: form ?? (body === undefined ? undefined : JSON.stringify(body)) })
    for (const cookie of response.headers.getSetCookie()) {
      const pair = cookie.split(';', 1)[0]
      const equals = pair.indexOf('=')
      const name = pair.slice(0, equals), value = pair.slice(equals + 1)
      if (value) this.cookies.set(name, value)
      else this.cookies.delete(name)
    }
    const text = await response.text()
    let data = null
    try { data = text ? JSON.parse(text) : null } catch { data = text }
    return { status: response.status, data, headers: response.headers }
  }
  async signup(email, password) {
    const register = await this.request('POST', '/api/auth/register',
      { email, password, passwordConfirm: password, nickname: '검증용 요리사' }, { auth: false })
    assert.equal(register.status, 201)
    assert.equal(register.data.password, undefined)
    const login = await this.request('POST', '/api/auth/login', { email, password }, { auth: false })
    assert.equal(login.status, 200)
    assert.equal(login.data.tokenType, 'Bearer')
    assert.ok(this.cookies.has('refresh_token'), 'Refresh Token은 쿠키로만 내려와야 해요')
    assert.ok(!JSON.stringify(login.data).includes(this.cookies.get('refresh_token')))
    this.token = login.data.accessToken
  }
}
const owner = new Client(), other = new Client()
const fixtureIds = []
const suffix = randomUUID()
const password = randomUUID()
const inputs = [
  ['계란', 'DAIRY', 6, 'PIECE', 0], ['대파', 'VEGETABLE', 1, 'BUNCH', 3],
  ['김치', 'VEGETABLE', 500, 'GRAM', 10], ['돼지고기', 'MEAT', 1, 'KILOGRAM', 7],
  ['소금', 'SEASONING', 1, 'GRAM', -1], ['우유', 'DAIRY', 500, 'MILLILITER', 6],
]
try {
  assert.equal((await owner.request('GET', '/api/health')).status, 200)
  assert.equal((await owner.request('GET', '/api/ingredients')).status, 401)
  const providers = (await owner.request('GET', '/api/auth/providers')).data
  assert.equal(typeof providers.google, 'boolean')
  // 소셜 로그인 시작 주소는 제공자 인증 화면으로 이동시킵니다.
  const kakao = await owner.request('GET', '/oauth2/authorization/kakao')
  assert.equal(kakao.status, 302)
  assert.match(kakao.headers.get('location'), /^https:\/\/kauth\.kakao\.com\/oauth\/authorize\?/)
  await owner.signup(`smoke-owner-${suffix}@example.test`, password)
  await other.signup(`smoke-other-${suffix}@example.test`, password)
  assert.equal((await owner.request('GET', '/api/auth/me')).data.nickname, '검증용 요리사')

  // Refresh Token은 한 번 쓰면 교체됩니다.
  const oldRefresh = owner.cookies.get('refresh_token')
  const refreshed = await owner.request('POST', '/api/auth/refresh', undefined, { auth: false })
  assert.equal(refreshed.status, 200)
  assert.notEqual(owner.cookies.get('refresh_token'), oldRefresh)
  owner.token = refreshed.data.accessToken

  for (const [name, category, quantity, unit, days] of inputs) {
    const response = await owner.request('POST', '/api/ingredients', {
      name, category, quantity, unit, purchaseDate: dateAfter(-7), expirationDate: dateAfter(days), storageType: 'FRIDGE',
      memo: `${name} 메모`,
    })
    assert.equal(response.status, 201, `식재료 생성: ${name}`)
    fixtureIds.push(response.data.id)
    assert.equal(response.data.memo, `${name} 메모`)
  }
  const dashboard = (await owner.request('GET', '/api/dashboard')).data
  assert.deepEqual([dashboard.total, dashboard.todayCount, dashboard.soonCount, dashboard.expiredCount], [6, 1, 2, 1])
  assert.equal(dashboard.today, today)
  assert.equal(dashboard.nearestIngredients.length, 5) // 만료된 소금은 카드에서 제외
  assert.equal(dashboard.nearestIngredients[0].name, '계란')

  // 검색·필터
  const filtered = async (query) => (await owner.request('GET', `/api/ingredients?${query}`)).data
  assert.deepEqual((await filtered('q=' + encodeURIComponent('우유'))).map(item => item.name), ['우유'])
  assert.equal((await filtered('status=SOON')).length, 2) // 계란(오늘까지), 대파(3일 뒤)
  assert.equal((await filtered('status=EXPIRED')).length, 1)
  assert.equal((await filtered('category=MEAT')).length, 1)
  assert.equal((await owner.request('GET', '/api/ingredients?status=BROKEN')).status, 400)

  // 소유자 격리
  assert.equal((await other.request('GET', '/api/ingredients')).data.length, 0)
  for (const method of ['GET', 'DELETE']) {
    assert.equal((await other.request(method, `/api/ingredients/${fixtureIds[0]}`)).status, 404)
  }
  const original = (await owner.request('GET', `/api/ingredients/${fixtureIds[0]}`)).data
  const updated = { ...original, quantity: 8 }
  assert.equal((await other.request('PUT', `/api/ingredients/${original.id}`, updated)).status, 404)
  assert.equal((await owner.request('PUT', `/api/ingredients/${original.id}`, updated)).data.version, 1)
  assert.equal((await owner.request('PUT', `/api/ingredients/${original.id}`, updated)).status, 409)

  // 사진 업로드 → 공개 URL → 교체 → 삭제
  const form = new FormData()
  form.append('file', new Blob([PNG], { type: 'image/png' }), 'egg.png')
  const uploaded = await owner.request('POST', `/api/ingredients/${original.id}/image`, undefined, { form })
  assert.equal(uploaded.status, 200)
  assert.match(uploaded.data.imageUrl, /^\/uploads\/[0-9a-f-]{36}\.png$/)
  const image = await fetch(base + uploaded.data.imageUrl)
  assert.equal(image.status, 200)
  assert.equal(image.headers.get('content-type'), 'image/png')
  const notImage = new FormData()
  notImage.append('file', new Blob(['not an image'], { type: 'image/png' }), 'fake.png')
  assert.equal((await owner.request('POST', `/api/ingredients/${original.id}/image`, undefined, { form: notImage })).status, 400)
  assert.equal((await other.request('POST', `/api/ingredients/${original.id}/image`, undefined, { form })).status, 404)
  assert.equal((await owner.request('DELETE', `/api/ingredients/${original.id}/image`)).status, 200)
  assert.equal((await fetch(base + uploaded.data.imageUrl)).status, 404)

  // 추천: 일치율(%), BEST MATCH, 만료 재료 제외
  const recipes = (await owner.request('GET', '/api/recipes/recommendations?servings=2')).data
  assert.equal(recipes.length, 21)
  assert.equal(recipes.filter(recipe => recipe.best).length, 1)
  assert.equal(recipes[0].best, true)
  assert.ok(recipes.every((recipe, index) => index === 0 || recipes[index - 1].matchRate >= recipe.matchRate))
  const stew = recipes.find(recipe => recipe.name === '김치찌개')
  assert.ok(stew)
  assert.ok(stew.urgentIngredients.includes('대파'))
  assert.equal(stew.requiredIngredients.find(item => item.name === '돼지고기').availableQuantity, 1000)
  assert.ok(recipes.flatMap(recipe => recipe.availableIngredients).every(item => item.name !== '소금'))
  for (const name of ['계란볶음밥', '김치볶음밥', '두부계란전', '닭가슴살 샐러드', '감자계란국', '참치마요 덮밥', '토마토 계란볶음', '소고기 양파볶음']) {
    assert.ok(recipes.some(recipe => recipe.name === name), `PRD 샘플 레시피: ${name}`)
  }
  const half = (await owner.request('GET', `/api/recipes/${stew.id}?servings=1`)).data
  assert.equal(half.requiredIngredients.find(item => item.name === '돼지고기').requiredQuantity, 100)
  assert.equal((await owner.request('GET', '/api/recipes/recommendations?servings=0')).status, 400)

  // 개인화: 우유 알레르기·김치 비선호·순한 맛
  const saved = await owner.request('PUT', '/api/users/me/preferences', { spicyLevel: 0, allergies: ['우유'], dislikes: ['김치'] })
  assert.equal(saved.status, 200)
  const personalized = (await owner.request('GET', '/api/recipes/recommendations?servings=1')).data
  assert.ok(personalized.length < 21)
  assert.ok(personalized.every(recipe => recipe.spicyLevel === 0))
  assert.ok(personalized.every(recipe => recipe.requiredIngredients.every(item => !/김치|우유/.test(item.name))))
  assert.equal((await other.request('GET', '/api/recipes/recommendations?servings=1')).data.length, 21)

  // 부족한 재료 → 장바구니
  const cart = await owner.request('POST', '/api/shopping-items/from-recipe', { recipeId: personalized[0].id, servings: 1 })
  assert.equal(cart.status, 201)
  assert.ok(cart.data.every(item => item.recipeId === personalized[0].id))
  assert.equal((await other.request('GET', '/api/shopping-items')).data.length, 0)
  assert.equal((await owner.request('GET', '/api/notifications')).data.unreadCount, 0)

  // AI
  const aiStatus = await owner.request('GET', '/api/ai/status')
  assert.equal(aiStatus.status, 200)
  const live = aiStatus.data.available && process.env.LIVE_OPENAI === '1'
  if (!aiStatus.data.available) {
    const generated = await owner.request('POST', '/api/ai/recipes', { ingredientIds: [fixtureIds[0]], servings: 1 })
    assert.equal(generated.status, 503)
    assert.equal(generated.data.code, 'AI_NOT_CONFIGURED')
  } else if (live) {
    // 유료 호출은 LIVE_OPENAI=1을 명시했을 때만 실행합니다.
    const generated = await owner.request('POST', '/api/ai/recipes', { ingredientIds: [fixtureIds[0], fixtureIds[1]], servings: 1 })
    assert.equal(generated.status, 201, `AI 레시피 오류: ${generated.data?.code}`)
    assert.ok(generated.data.steps.length > 0)
  }
  const chat = await owner.request('POST', '/api/ai/chat', { message: '오늘 냉장고 털이 요리 추천해줘', servings: 1, history: [], mode: live ? 'AUTO' : 'LOCAL' })
  assert.equal(chat.status, 200, `채팅 오류: ${chat.data?.code || chat.status}`)
  assert.equal(chat.data.source, live ? 'OPENAI' : 'LOCAL')

  // 로그아웃하면 Refresh Token이 폐기됩니다.
  const cookie = owner.cookies.get('refresh_token')
  assert.equal((await owner.request('POST', '/api/auth/logout', undefined, { auth: false })).status, 204)
  owner.cookies.set('refresh_token', cookie)
  assert.equal((await owner.request('POST', '/api/auth/refresh', undefined, { auth: false })).status, 401)
  console.log(`PASS: 실제 MySQL JWT 로그인·Refresh 회전/재료 CRUD·검색·사진/일치율·BEST MATCH/개인화/장바구니/알림/AI(${aiStatus.data.available ? (live ? 'LIVE' : 'LOCAL 채팅') : '키 없음 503'}) 검증`)
} finally {
  // 로그아웃으로 Refresh Token이 폐기됐어도 Access Token(15분)은 남아 있으므로 정리 요청은 계속 보낼 수 있습니다.
  for (const id of fixtureIds) await owner.request('DELETE', `/api/ingredients/${id}`).catch(() => {})
  const items = await owner.request('GET', '/api/shopping-items').catch(() => null)
  for (const item of items?.data || []) await owner.request('DELETE', `/api/shopping-items/${item.id}`).catch(() => {})
}
