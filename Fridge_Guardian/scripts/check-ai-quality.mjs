import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import fs from 'node:fs'
// Explicit opt-in: this check makes three paid OpenAI requests using fixture data only.
if (process.env.LIVE_OPENAI !== '1') throw new Error('Set LIVE_OPENAI=1 to run the three paid AI quality checks.')
fs.mkdirSync('.local', { recursive: true })
const base = (process.env.API_BASE_URL || 'http://127.0.0.1:8080') + '/api'
const cookies = new Map()
let csrf
async function request(method, endpoint, body) {
  const headers = { 'Content-Type': 'application/json', Cookie: [...cookies].map(([k,v]) => `${k}=${v}`).join('; ') }
  if (csrf && method !== 'GET') headers[csrf.headerName] = csrf.token
  const response = await fetch(base + endpoint, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(60000) })
  for (const cookie of response.headers.getSetCookie()) {
    const pair = cookie.split(';')[0], i = pair.indexOf('=')
    cookies.set(pair.slice(0,i), pair.slice(i+1))
  }
  const text = await response.text()
  return { status: response.status, data: text ? JSON.parse(text) : null }
}
const today = new Intl.DateTimeFormat('en-CA', { timeZone:'Asia/Seoul', year:'numeric', month:'2-digit',day:'2-digit' }).format(new Date())
const email = `ai-quality-${randomUUID()}@example.test`, password = randomUUID()
const ids = [], results = [], history = []
try {
  csrf = (await request('GET','/auth/csrf')).data
  assert.equal((await request('POST','/auth/register',{ email,password,nickname:'AI 검증 요리사' })).status,201)
  assert.equal((await request('POST','/auth/login',{ email,password })).status,200)
  csrf = (await request('GET','/auth/csrf')).data
  for (const [name,quantity,unit] of [['계란',4,'PIECE'],['두부',1,'BLOCK'],['김치',200,'GRAM'],['식용유',100,'MILLILITER'],['간장',100,'MILLILITER']]) {
    const result = await request('POST','/ingredients',{name,quantity,unit,category:'OTHER',storageType:'FRIDGE',purchaseDate:today,expirationDate:null})
    assert.equal(result.status,201)
    ids.push(result.data.id)
  }
  const prompts = ['게란으로 계란말이를 만들고 싶어. 1인분 조리법과 내 냉장고에 없는 재료를 정확히 알려줘.',
    '그 메뉴 말고 계란이 들어가지 않는 두부 요리로 하나만 추천해줘. 고기도 빼줘.',
    '요리 추천 말고, 지금 내 냉장고에 두부가 몇 모 있는지만 알려줘.']
  for (const message of prompts) {
    const started = Date.now()
    const result = await request('POST','/ai/chat',{message,servings:1,mode:'AUTO',history:history.slice(-10)})
    results.push({message,elapsedMs:Date.now()-started,status:result.status,...result.data})
    fs.writeFileSync('.local/ai-quality-result.json', JSON.stringify(results,null,2))
    assert.equal(result.status,200, result.data?.code)
    assert.equal(result.data.source,'OPENAI')
    assert.doesNotMatch(result.data.reply,/\b(?:UNKNOWN|PIECE|BLOCK|BUNCH|MILLILITER|inventory|status)\b|\d+\.000/)
    history.push({role:'user',content:message},{role:'assistant',content:result.data.reply})
    console.log(JSON.stringify({status:result.status,elapsedMs:Date.now()-started,reply:result.data.reply,recipes:result.data.recommendedRecipes.map(r=>r.name)}))
  }
  assert.ok(results[0].recommendedRecipes.some(r=>r.name==='계란말이'))
  assert.equal(results[1].recommendedRecipes.length,1)
  assert.ok(results[1].recommendedRecipes.every(r=>r.requiredIngredients.some(i=>i.name==='두부') && r.requiredIngredients.every(i=>!['계란','돼지고기','소고기','닭고기'].includes(i.name))))
  assert.equal(results[2].recommendedRecipes.length,0)
  assert.match(results[2].reply,/1\s*모|한\s*모/)
  console.log('PASS: typo understanding, follow-up constraints, matching cards and current inventory answer')
} finally {
  for (const id of ids) await request('DELETE',`/ingredients/${id}`).catch(()=>{})
  await request('POST','/auth/logout').catch(()=>{})
}
