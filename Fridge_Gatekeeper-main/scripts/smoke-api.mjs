// 실행 중인 실제 서버/MySQL을 대상으로 HTTP 세션과 업무 흐름을 검증합니다.
// 생성한 식재료는 finally에서 제거합니다. 테스트 회원 2개는 smoke- 접두사로 남습니다.
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
class Browser {
  cookies = new Map()
  csrf = null
  async request(method, path, body, secure = true) {
    const headers = { 'Content-Type': 'application/json' }
    if (this.cookies.size) headers.Cookie = [...this.cookies].map(([k,v]) => `${k}=${v}`).join('; ')
    if (secure && this.csrf && !['GET', 'HEAD'].includes(method)) headers[this.csrf.headerName] = this.csrf.token
    const response = await fetch(base + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
    for (const cookie of response.headers.getSetCookie()) {
      const pair = cookie.split(';', 1)[0]
      const equals = pair.indexOf('=')
      const name = pair.slice(0, equals), value = pair.slice(equals + 1)
      if (value) this.cookies.set(name, value)
      else this.cookies.delete(name)
    }
    const text = await response.text()
    return { status: response.status, data: text ? JSON.parse(text) : null }
  }
  async token() { this.csrf = (await this.request('GET', '/api/auth/csrf')).data }
  async login(email, password) {
    await this.token()
    const signup = await this.request('POST', '/api/auth/register', { email, password, nickname: '검증용 요리사' })
    assert.equal(signup.status, 201)
    assert.equal(signup.data.password, undefined)
    const login = await this.request('POST', '/api/auth/login', { email, password })
    assert.equal(login.status, 200)
    await this.token()
  }
}
const owner = new Browser(), other = new Browser()
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
  await owner.login(`smoke-owner-${suffix}@example.test`, password)
  await other.login(`smoke-other-${suffix}@example.test`, password)
  assert.equal((await owner.request('GET', '/api/auth/me')).data.nickname, '검증용 요리사')
  for (const [name,category,quantity,unit,days] of inputs) {
    const response = await owner.request('POST', '/api/ingredients', {
      name,category,quantity,unit,purchaseDate:dateAfter(-7),expirationDate:dateAfter(days),storageType:'FRIDGE',
    })
    assert.equal(response.status, 201, `식재료 생성: ${name}`)
    fixtureIds.push(response.data.id)
    assert.equal(response.data.name, name)
  }
  const dashboard = (await owner.request('GET', '/api/dashboard')).data
  assert.deepEqual([dashboard.total,dashboard.todayCount,dashboard.soonCount,dashboard.expiredCount], [6,1,2,1])
  assert.equal(dashboard.today, today)
  assert.equal((await other.request('GET', '/api/ingredients')).data.length, 0)
  for (const method of ['GET','DELETE']) {
    assert.equal((await other.request(method, `/api/ingredients/${fixtureIds[0]}`)).status, 404)
  }
  const original = (await owner.request('GET', `/api/ingredients/${fixtureIds[0]}`)).data
  const updated = { ...original, quantity: 8 }
  assert.equal((await other.request('PUT', `/api/ingredients/${original.id}`, updated)).status, 404)
  assert.equal((await owner.request('PUT', `/api/ingredients/${original.id}`, updated, false)).status, 403)
  assert.equal((await owner.request('PUT', `/api/ingredients/${original.id}`, updated)).data.version, 1)
  assert.equal((await owner.request('PUT', `/api/ingredients/${original.id}`, updated)).status, 409)
  assert.equal((await owner.request('GET', '/api/ingredients?sort=category')).status, 200)
  assert.equal((await owner.request('GET', '/api/ingredients?sort=storage')).status, 200)
  const recipes = (await owner.request('GET', '/api/recipes/recommendations?servings=2')).data
  assert.equal(recipes.length, 16)
  const stew = recipes.find(recipe => recipe.name === '김치찌개')
  assert.ok(stew)
  assert.ok(stew.urgentIngredients.includes('대파'))
  assert.equal(stew.requiredIngredients.find(item => item.name === '돼지고기').availableQuantity, 1000)
  assert.ok(recipes.flatMap(recipe => recipe.availableIngredients).every(item => item.name !== '소금'))
  const half = (await owner.request('GET', `/api/recipes/${stew.id}?servings=1`)).data
  assert.equal(half.requiredIngredients.find(item => item.name === '돼지고기').requiredQuantity, 100)
  assert.equal((await owner.request('GET', '/api/recipes/recommendations?servings=0')).status, 400)
  const aiStatus = await owner.request('GET', '/api/ai/status')
  assert.equal(aiStatus.status, 200)
  // 유료 AI 호출은 별도 LIVE_OPENAI=1 지정 시에만 실행합니다.
  {
    const live = aiStatus.data.available && process.env.LIVE_OPENAI === '1'
    const chat = await owner.request('POST', '/api/ai/chat', { message:'오늘 냉장고 털이 요리 추천해줘', servings:1, history:[], mode: live ? 'AUTO' : 'LOCAL' })
    assert.equal(chat.status, 200, `채팅 오류: ${chat.data?.code || chat.status}`)
    assert.equal(chat.data.source, live ? 'OPENAI' : 'LOCAL')
    assert.ok(chat.data.reply.length > 0)
    assert.ok(chat.data.recommendedRecipes.length > 0)
    console.log(`PASS: 채팅 source=${chat.data.source}, model=${aiStatus.data.model || 'LOCAL'}, replyLength=${chat.data.reply.length}`)
  }
  console.log('PASS: 실제 MySQL 회원/세션/CSRF/CRUD/소유자 격리/날짜/추천/단위/인분/채팅 검증')
} finally {
  for (const id of fixtureIds) await owner.request('DELETE', `/api/ingredients/${id}`).catch(() => {})
  await owner.request('POST', '/api/auth/logout').catch(() => {})
  await other.request('POST', '/api/auth/logout').catch(() => {})
}
