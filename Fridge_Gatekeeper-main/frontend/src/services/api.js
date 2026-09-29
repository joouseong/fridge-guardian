// 쿠키는 브라우저가 관리합니다. 비밀번호·세션을 localStorage에 저장하지 않습니다.
let csrf = null
let csrfRequest = null

export class ApiError extends Error {
  constructor(status, body = {}) {
    super(body?.message || '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.')
    this.name = 'ApiError'
    this.status = status
    this.code = body?.code
    this.fieldErrors = body?.fieldErrors || {}
  }
}

const unreadableResponse = '서버 응답을 읽을 수 없어요. 잠시 후 다시 시도해 주세요.'

async function fetchResponse(url, options = {}) {
  const timeout = AbortSignal.timeout(60000)
  try {
    return await fetch(url, { ...options, signal: options.signal ? AbortSignal.any([options.signal, timeout]) : timeout })
  } catch (error) {
    if (options.signal?.aborted) throw error
    if (timeout.aborted) throw new Error('서버 응답이 늦어지고 있어요. 잠시 후 다시 시도해 주세요.', { cause: error })
    if (error.name === 'AbortError') throw error
    throw new Error('서버에 연결할 수 없어요. 연결 상태를 확인한 뒤 다시 시도해 주세요.', { cause: error })
  }
}

export async function refreshCsrf() {
  csrf = null
  // 동시에 여러 요청이 시작되어도 토큰 발급 요청은 하나만 보냅니다.
  if (!csrfRequest) {
    csrfRequest = fetchResponse('/api/auth/csrf', { credentials: 'include' })
      .then(async (response) => {
        if (!response.ok) throw new Error('보안 연결을 준비하지 못했어요. 다시 시도해 주세요.')
        const token = await response.json().catch(() => null)
        if (!token?.headerName || !token?.token) throw new Error('보안 연결을 준비하지 못했어요. 다시 시도해 주세요.')
        csrf = token
        return csrf
      }).finally(() => { csrfRequest = null })
  }
  return csrfRequest
}

export async function api(path, options = {}) {
  const { method = 'GET', body, signal, silentAuth = false, retryCsrf = true } = options
  const headers = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (!['GET', 'HEAD'].includes(method)) {
    const token = csrf || await refreshCsrf()
    headers[token.headerName] = token.token
  }
  const response = await fetchResponse(`/api${path}`, {
    method, headers, credentials: 'include', signal,
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  })
  if (response.status === 204) return null
  let result
  try { result = await response.json() }
  catch { if (response.ok) throw new Error(unreadableResponse); result = { message: unreadableResponse } }
  // 로그인·로그아웃 뒤 교체된 토큰이나 오래된 탭의 토큰을 한 번 갱신합니다.
  if (response.status === 403 && retryCsrf && method !== 'GET') {
    await refreshCsrf()
    return api(path, { ...options, retryCsrf: false })
  }
  if (!response.ok) {
    if (response.status === 401 && !silentAuth) {
      csrf = null
      window.dispatchEvent(new Event('session-expired'))
    }
    throw new ApiError(response.status, result)
  }
  return result
}
