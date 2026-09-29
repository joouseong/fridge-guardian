import assert from 'node:assert/strict'
import { test } from 'node:test'

const json = (body, status = 200) => Response.json(body, { status })
const freshApi = () => import(`../src/services/api.js?test=${crypto.randomUUID()}`)

test('concurrent changes share one CSRF request and include session cookies', async (t) => {
  const requests = []
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    requests.push({ url, options })
    return url.endsWith('/csrf') ? json({ headerName: 'X-CSRF-TOKEN', token: 'current-token' }) : json({ id: requests.length }, 201)
  })
  const { api } = await freshApi()
  await Promise.all([
    api('/ingredients', { method: 'POST', body: { name: '계란' } }),
    api('/ingredients', { method: 'POST', body: { name: '두부' } }),
  ])
  assert.equal(requests.filter(({ url }) => url.endsWith('/csrf')).length, 1)
  for (const { options } of requests) assert.equal(options.credentials, 'include')
  for (const { options } of requests.filter(({ url }) => url.endsWith('/ingredients'))) {
    assert.equal(options.headers['X-CSRF-TOKEN'], 'current-token')
    assert.equal(options.headers['Content-Type'], 'application/json')
  }
})

test('an expired CSRF token is replaced and the change is retried once', async (t) => {
  let csrfRequests = 0
  let changes = 0
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (url.endsWith('/csrf')) return json({ headerName: 'X-CSRF-TOKEN', token: `token-${++csrfRequests}` })
    changes++
    assert.equal(options.headers['X-CSRF-TOKEN'], `token-${changes}`)
    return changes === 1 ? json({ code: 'CSRF', message: '만료' }, 403) : new Response(null, { status: 204 })
  })
  const { api } = await freshApi()
  assert.equal(await api('/ingredients/1', { method: 'DELETE' }), null)
  assert.equal(changes, 2)
  assert.equal(csrfRequests, 2)
})

test('a persistent forbidden response does not cause an infinite retry', async (t) => {
  let changes = 0
  t.mock.method(globalThis, 'fetch', async (url) => {
    if (url.endsWith('/csrf')) return json({ headerName: 'X-CSRF-TOKEN', token: 'token' })
    changes++
    return json({ code: 'FORBIDDEN', message: '요청을 확인해 주세요.' }, 403)
  })
  const { api } = await freshApi()
  await assert.rejects(api('/ingredients/1', { method: 'DELETE' }), { status: 403 })
  assert.equal(changes, 2)
})

test('session expiry is emitted for protected requests but not a rejected login', async (t) => {
  let expiredEvents = 0
  const previousWindow = globalThis.window
  globalThis.window = new EventTarget()
  t.after(() => { if (previousWindow === undefined) delete globalThis.window; else globalThis.window = previousWindow })
  globalThis.window.addEventListener('session-expired', () => expiredEvents++)
  t.mock.method(globalThis, 'fetch', async () => json({ message: '로그인이 필요해요.' }, 401))
  const { api } = await freshApi()
  await assert.rejects(api('/auth/me', { silentAuth: true }), { status: 401 })
  assert.equal(expiredEvents, 0)
  await assert.rejects(api('/ingredients'), { status: 401 })
  assert.equal(expiredEvents, 1)
})

test('a successful HTML response from a wrong proxy is reported as an error', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => new Response('<html>Index page</html>', { status: 200 }))
  const { api } = await freshApi()
  await assert.rejects(api('/dashboard'), /서버 응답을 읽을 수 없어요/)
})

test('invalid CSRF responses prevent the write and can be retried', async (t) => {
  let csrfRequests = 0
  let writes = 0
  t.mock.method(globalThis, 'fetch', async (url) => {
    if (url.endsWith('/csrf')) return ++csrfRequests === 1 ? json({ unexpected: true }) : json({ headerName: 'X-CSRF-TOKEN', token: 'valid' })
    writes++
    return json({ id: 1 }, 201)
  })
  const { api } = await freshApi()
  await assert.rejects(api('/ingredients', { method: 'POST', body: {} }), /보안 연결을 준비하지 못했어요/)
  assert.equal(writes, 0)
  assert.deepEqual(await api('/ingredients', { method: 'POST', body: {} }), { id: 1 })
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
