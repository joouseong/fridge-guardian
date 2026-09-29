import assert from 'node:assert/strict'
import { test } from 'node:test'

const json = (body, status = 200) => Response.json(body, { status })
const freshApi = () => import(`../src/services/api.js?test=${crypto.randomUUID()}`)
const session = (token = 'access-1') => ({ accessToken: token, tokenType: 'Bearer', expiresIn: 900, user: { id: 1, email: 'cook@example.com', nickname: '요리사' } })

function browserWindow(t) {
  const previousWindow = globalThis.window
  globalThis.window = new EventTarget()
  t.after(() => { if (previousWindow === undefined) delete globalThis.window; else globalThis.window = previousWindow })
  const events = { expired: 0 }
  globalThis.window.addEventListener('session-expired', () => events.expired++)
  return events
}

test('login keeps the access token in memory and sends it as a Bearer header afterwards', async (t) => {
  const requests = []
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    requests.push({ url, options })
    return url.endsWith('/auth/login') ? json(session()) : json([{ id: 1 }])
  })
  const { api, login, hasAccessToken } = await freshApi()
  assert.equal(hasAccessToken(), false)
  const user = await login('cook@example.com', 'pw1234')
  assert.equal(user.nickname, '요리사')
  assert.equal(hasAccessToken(), true)
  await api('/ingredients')
  assert.equal(requests[0].options.headers.Authorization, undefined, '로그인 요청에는 토큰을 보내지 않아요')
  assert.equal(requests[1].options.headers.Authorization, 'Bearer access-1')
  for (const { options } of requests) assert.equal(options.credentials, 'include')
  assert.equal(requests[0].options.headers['Content-Type'], 'application/json')
  assert.equal(requests[1].options.headers['Content-Type'], undefined)
})

test('an expired access token is refreshed once and the request is retried', async (t) => {
  const events = browserWindow(t)
  const calls = []
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    calls.push({ url, auth: options.headers?.Authorization })
    if (url.endsWith('/auth/refresh')) return json(session('access-2'))
    if (url.endsWith('/auth/login')) return json(session('access-1'))
    return options.headers.Authorization === 'Bearer access-2' ? json({ ok: true }) : json({ code: 'UNAUTHORIZED', message: '만료' }, 401)
  })
  const { api, login } = await freshApi()
  await login('cook@example.com', 'pw1234')
  assert.deepEqual(await api('/dashboard'), { ok: true })
  assert.deepEqual(calls.map(({ url }) => url), ['/api/auth/login', '/api/dashboard', '/api/auth/refresh', '/api/dashboard'])
  assert.equal(calls[3].auth, 'Bearer access-2')
  assert.equal(events.expired, 0)
})

test('simultaneous 401 responses share a single refresh request', async (t) => {
  browserWindow(t)
  let refreshes = 0
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (url.endsWith('/auth/refresh')) { refreshes++; await new Promise((resolve) => setTimeout(resolve, 10)); return json(session('fresh')) }
    return options.headers.Authorization === 'Bearer fresh' ? json({ ok: url }) : json({ message: '만료' }, 401)
  })
  const { api } = await freshApi()
  const results = await Promise.all([api('/ingredients'), api('/dashboard'), api('/recipes/recommendations')])
  assert.equal(refreshes, 1)
  assert.deepEqual(results.map((item) => item.ok), ['/api/ingredients', '/api/dashboard', '/api/recipes/recommendations'])
})

test('when the refresh token is also invalid the user is signed out once without looping', async (t) => {
  const events = browserWindow(t)
  let refreshes = 0
  let protectedCalls = 0
  t.mock.method(globalThis, 'fetch', async (url) => {
    if (url.endsWith('/auth/refresh')) { refreshes++; return json({ code: 'UNAUTHORIZED', message: '다시 로그인해 주세요.' }, 401) }
    protectedCalls++
    return json({ code: 'UNAUTHORIZED', message: '로그인이 필요해요.' }, 401)
  })
  const { api, hasAccessToken } = await freshApi()
  await assert.rejects(api('/ingredients'), { status: 401 })
  assert.equal(refreshes, 1)
  assert.equal(protectedCalls, 1)
  assert.equal(events.expired, 1)
  assert.equal(hasAccessToken(), false)
})

test('a rejected login and session recovery never emit session expiry or trigger a refresh', async (t) => {
  const events = browserWindow(t)
  const urls = []
  t.mock.method(globalThis, 'fetch', async (url) => { urls.push(url); return json({ code: 'INVALID_CREDENTIALS', message: '이메일 또는 비밀번호를 확인해 주세요.' }, 401) })
  const { login, refreshSession } = await freshApi()
  await assert.rejects(login('cook@example.com', 'wrong'), { status: 401, code: 'INVALID_CREDENTIALS' })
  await assert.rejects(refreshSession(), { status: 401 })
  assert.deepEqual(urls, ['/api/auth/login', '/api/auth/refresh'])
  assert.equal(events.expired, 0)
})

test('an invalid refresh response is rejected and clears the token', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => json({ unexpected: true }))
  const { refreshSession, hasAccessToken } = await freshApi()
  await assert.rejects(refreshSession(), /서버 응답을 읽을 수 없어요/)
  assert.equal(hasAccessToken(), false)
})

test('logout clears the token even when the server request fails', async (t) => {
  let fail = false
  const auths = []
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    auths.push(options.headers?.Authorization)
    if (fail) throw new TypeError('fetch failed')
    return url.endsWith('/auth/login') ? json(session()) : new Response(null, { status: 204 })
  })
  const { api, login, logout, hasAccessToken } = await freshApi()
  await login('cook@example.com', 'pw1234')
  fail = true
  await assert.rejects(logout(), /서버에 연결할 수 없어요/)
  assert.equal(hasAccessToken(), false)
  fail = false
  await api('/health', { silentAuth: true })
  assert.equal(auths.at(-1), undefined)
})

test('file uploads send FormData without forcing a JSON content type', async (t) => {
  let seen
  t.mock.method(globalThis, 'fetch', async (url, options) => { seen = options; return json({ imageUrl: '/uploads/a.png' }) })
  const { api } = await freshApi()
  const form = new FormData()
  form.append('file', new Blob(['png'], { type: 'image/png' }), 'egg.png')
  const result = await api('/ingredients/1/image', { method: 'POST', formData: form })
  assert.equal(result.imageUrl, '/uploads/a.png')
  assert.equal(seen.body, form)
  assert.equal(seen.headers['Content-Type'], undefined)
})

test('a successful HTML response from a wrong proxy is reported as an error', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => new Response('<html>Index page</html>', { status: 200 }))
  const { api } = await freshApi()
  await assert.rejects(api('/dashboard'), /서버 응답을 읽을 수 없어요/)
})

test('errors keep their status code and field messages', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => json({ code: 'VALIDATION_ERROR', message: '입력 내용을 확인해 주세요.', fieldErrors: { name: '필수' } }, 400))
  const { api } = await freshApi()
  await assert.rejects(api('/ingredients', { method: 'POST', body: {} }), (error) =>
    error.status === 400 && error.code === 'VALIDATION_ERROR' && error.fieldErrors.name === '필수')
})

test('network failures are explained without exposing low-level connection details', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => { throw new TypeError('fetch failed: ECONNREFUSED') })
  const { api } = await freshApi()
  await assert.rejects(api('/dashboard'), /서버에 연결할 수 없어요/)
  await assert.rejects(api('/ingredients', { method: 'POST', body: {} }), /서버에 연결할 수 없어요/)
})

test('request timeouts show a retryable message', async (t) => {
  t.mock.method(AbortSignal, 'timeout', () => AbortSignal.abort(new DOMException('Timeout', 'TimeoutError')))
  t.mock.method(globalThis, 'fetch', async (_url, options) => { throw options.signal.reason })
  const { api } = await freshApi()
  await assert.rejects(api('/dashboard'), /서버 응답이 늦어지고 있어요/)
})
