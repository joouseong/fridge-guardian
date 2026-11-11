// Access Token은 이 모듈의 변수(메모리)에만 보관합니다. localStorage·sessionStorage에는 두지 않아
// 화면의 스크립트가 훔쳐 가기 어렵고, 새로고침하면 아래 refreshSession()으로 다시 받아 옵니다.
// Refresh Token은 서버가 HttpOnly 쿠키로 관리하므로 JavaScript에서 읽을 수 없습니다.
let accessToken = null
let refreshRequest = null

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

export function hasAccessToken() { return accessToken !== null }

/**
 * 쿠키의 Refresh Token으로 새 Access Token과 사용자 정보를 받습니다.
 * Refresh Token은 한 번 쓰면 교체되므로, 여러 요청이 동시에 불러도 서버에는 한 번만 보냅니다.
 */
export function refreshSession() {
  if (!refreshRequest) {
    refreshRequest = fetchResponse('/api/auth/refresh', { method: 'POST', headers: { Accept: 'application/json' }, credentials: 'include' })
      .then(async (response) => {
        const body = await response.json().catch(() => null)
        if (!response.ok) throw new ApiError(response.status, body || {})
        if (!body?.accessToken || !body?.user) throw new Error(unreadableResponse)
        accessToken = body.accessToken
        return body.user
      })
      .catch((error) => { accessToken = null; throw error })
      .finally(() => { refreshRequest = null })
  }
  return refreshRequest
}

/** 이메일 로그인. 성공하면 Access Token을 메모리에 두고 사용자 정보를 돌려줍니다. */
export async function login(email, password) {
  const body = await api('/auth/login', { method: 'POST', body: { email, password }, silentAuth: true })
  if (!body?.accessToken || !body?.user) throw new Error(unreadableResponse)
  accessToken = body.accessToken
  return body.user
}

/** 서버의 Refresh Token을 폐기하고 메모리의 Access Token을 지웁니다. 서버 요청이 실패해도 이 브라우저에서는 로그아웃됩니다. */
export async function logout() {
  try { await api('/auth/logout', { method: 'POST', silentAuth: true }) }
  finally { accessToken = null }
}

/**
 * options: method, body(JSON), formData(파일 업로드), signal,
 * silentAuth(true면 401에서 갱신·로그인 화면 이동을 하지 않음. 로그인·복구 요청용)
 */
export async function api(path, options = {}) {
  const { method = 'GET', body, formData, signal, silentAuth = false, retry = true } = options
  const headers = { Accept: 'application/json' }
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`
  // JSON일 때만 Content-Type을 지정합니다. FormData는 브라우저가 boundary를 포함해 직접 지정합니다.
  if (body !== undefined && !formData) headers['Content-Type'] = 'application/json'
  const response = await fetchResponse(`/api${path}`, {
    method, headers, credentials: 'include', signal,
    ...(formData ? { body: formData } : body !== undefined ? { body: JSON.stringify(body) } : {}),
  })
  if (response.status === 204) return null
  if (response.status === 401 && !silentAuth) {
    if (retry) {
      // Access Token(15분)이 만료됐다면 Refresh Token으로 갱신하고 원래 요청을 한 번만 다시 보냅니다.
      try { await refreshSession() }
      catch { accessToken = null; window.dispatchEvent(new Event('session-expired')); throw new ApiError(401, await readBody(response)) }
      return api(path, { ...options, retry: false })
    }
    accessToken = null
    window.dispatchEvent(new Event('session-expired'))
  }
  let result
  try { result = await response.json() }
  catch { if (response.ok) throw new Error(unreadableResponse); result = { message: unreadableResponse } }
  if (!response.ok) throw new ApiError(response.status, result)
  return result
}

async function readBody(response) {
  try { return await response.json() } catch { return { message: unreadableResponse } }
}
