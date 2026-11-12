const backendBaseUrl = (process.env.BACKEND_URL || 'https://fridge-guardian.onrender.com').replace(/\/+$/, '')

const requestHeadersToSkip = new Set([
  'connection', 'content-length', 'host', 'keep-alive', 'transfer-encoding', 'upgrade',
])

const responseHeadersToSkip = new Set([
  'connection', 'content-encoding', 'content-length', 'keep-alive', 'transfer-encoding', 'upgrade',
])

/** Vercel의 Node 함수가 JSON·multipart 본문을 바꾸지 않고 Render로 전달하도록 합니다. */
export const config = { api: { bodyParser: false } }

function upstreamPath(requestUrl) {
  const url = new URL(requestUrl, 'https://frontend.invalid')
  const { pathname } = url

  // vercel.json의 내부 rewrite로 들어온 같은 출처 경로들입니다.
  if (pathname.startsWith('/api/oauth2/')) return pathname.slice('/api'.length) + url.search
  if (pathname.startsWith('/api/oauth-callback/')) {
    const provider = pathname.slice('/api/oauth-callback/'.length)
    return `/auth/${provider}/callback${url.search}`
  }
  if (pathname.startsWith('/api/uploads/')) return pathname.slice('/api'.length) + url.search
  return pathname + url.search
}

function forwardedHeaders(request) {
  const headers = new Headers()
  for (const [name, value] of Object.entries(request.headers)) {
    if (value !== undefined && !requestHeadersToSkip.has(name.toLowerCase())) headers.set(name, value)
  }
  // Render는 이 주소를 OAuth redirect_uri 생성에 사용합니다.
  headers.set('x-forwarded-host', request.headers.host || '')
  headers.set('x-forwarded-proto', 'https')
  return headers
}

async function rawBody(request) {
  if (request.method === 'GET' || request.method === 'HEAD') return undefined
  const chunks = []
  for await (const chunk of request) chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk))
  return chunks.length ? Buffer.concat(chunks) : undefined
}

export default async function proxy(request, response) {
  try {
    const upstream = await fetch(`${backendBaseUrl}${upstreamPath(request.url)}`, {
      method: request.method,
      headers: forwardedHeaders(request),
      body: await rawBody(request),
      redirect: 'manual',
    })

    response.statusCode = upstream.status
    for (const [name, value] of upstream.headers) {
      if (!responseHeadersToSkip.has(name.toLowerCase()) && name.toLowerCase() !== 'set-cookie') {
        response.setHeader(name, value)
      }
    }
    const cookies = typeof upstream.headers.getSetCookie === 'function'
      ? upstream.headers.getSetCookie()
      : upstream.headers.get('set-cookie')
    if (cookies?.length) response.setHeader('set-cookie', cookies)

    response.end(Buffer.from(await upstream.arrayBuffer()))
  } catch (error) {
    console.error('Render API proxy failed', error)
    response.status(502).json({ code: 'BACKEND_UNAVAILABLE', message: '백엔드 서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.' })
  }
}
