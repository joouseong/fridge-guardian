import { useEffect, useRef, useState } from 'react'
import RecipeCard from '../components/RecipeCard.jsx'
import RecipeDetail from '../components/RecipeDetail.jsx'
import { ErrorBox, Link, Loading, PageHeading, ServingControl } from '../components/UI.jsx'
import useResource from '../hooks/useResource.js'
import { api } from '../services/api.js'

const suggestions = ['유통기한 임박 재료로 뭘 만들 수 있어?', '간단하게 만들 수 있는 한 끼를 추천해 줘', '내 재료로 단백질을 챙길 수 있는 요리 알려줘']

export default function ChatPage() {
  const status = useResource('/ai/status')
  const [servings, setServings] = useState(1)
  const [mode, setMode] = useState('AUTO')
  const [message, setMessage] = useState('')
  const [messages, setMessages] = useState([])
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState(null)
  const [selectedRecipe, setSelectedRecipe] = useState(null)
  const [recommended, setRecommended] = useState([])
  const endRef = useRef(null)
  const requestRef = useRef(null)
  const inputRef = useRef(null)
  useEffect(() => { endRef.current?.scrollIntoView({ block: 'nearest', behavior: 'smooth' }) }, [messages, busy])
  useEffect(() => { if (!busy && messages.length) inputRef.current?.focus() }, [busy, messages.length])
  useEffect(() => () => requestRef.current?.abort(), [])

  async function request(payload) {
    if (requestRef.current) return
    const controller = new AbortController()
    requestRef.current = controller
    setBusy(true)
    setFailure(null)
    try {
      const response = await api('/ai/chat', { method: 'POST', body: payload, signal: controller.signal })
      setMessages((current) => [...current, { id: crypto.randomUUID(), role: 'assistant', content: response.reply, source: response.source }])
      setRecommended(response.recommendedRecipes || [])
    } catch (error) {
      if (error.name !== 'AbortError') setFailure({ error, payload })
    } finally {
      requestRef.current = null
      setBusy(false)
    }
  }
  function send(text) {
    const content = text.trim()
    if (!content || content.length > 2000 || busy || requestRef.current) return
    // 답변 전문은 화면에 유지하고, 서버로 보내는 이전 대화만 최근 10개·각 2,000자로 제한합니다.
    const history = messages.slice(-10).map(({ role, content: previous }) => ({ role, content: previous.slice(0, 2000) }))
    const payload = { message: content, servings, history, mode }
    setMessages((current) => [...current, { id: crypto.randomUUID(), role: 'user', content }])
    setMessage('')
    request(payload)
  }
  function clear() { setMessages([]); setRecommended([]); setFailure(null); setMessage(''); inputRef.current?.focus() }
  return <>
    <PageHeading eyebrow="YOUR COOKING COMPANION" title="오늘의 메뉴, 함께 고민해요" action={<ServingControl value={servings} onChange={setServings} disabled={busy} />}>냉장고 재료와 원하는 메뉴를 이야기해 주세요.</PageHeading>
    <div className="chat-layout"><section className="panel chat-panel" aria-label="요리 도우미 대화"><div className="section-heading"><div><h2>요리 도우미 <span aria-hidden="true">✧</span></h2>{status.data && <p className="chat-source">{mode !== 'LOCAL' && status.data.available ? `AI 대화 · ${status.data.model || 'OpenAI'} · 답변마다 제공 방식을 표시해요` : '기본 추천 모드 · 보유 재료를 바탕으로 정해진 기준에 따라 추천해요'}</p>}</div><button className="button ghost small" onClick={clear} disabled={busy || messages.length === 0}>대화 지우기</button></div>
      {status.loading && <Loading label="요리 도우미 연결 상태를 확인하고 있어요…" />}{status.error && <ErrorBox error={status.error} onRetry={status.reload} />}
      <label className="field">대화 방식<select value={mode} onChange={(event) => setMode(event.target.value)} disabled={busy}>
        <option value="AUTO">{status.data?.available ? 'AI 대화' : '자동 선택'}</option><option value="LOCAL">기본 추천 (AI 호출 없음)</option>
      </select></label>
      <div className="chat-messages" role="log" aria-live="polite" aria-relevant="additions text" aria-label="대화 내용">
        {messages.length === 0 && <div className="chat-message assistant"><p>안녕하세요! 오늘은 어떤 한 끼가 먹고 싶나요?</p><p>내 냉장고의 재료를 살펴 메뉴를 추천해 드릴게요. 위에서 1인분 또는 2인분을 선택할 수 있어요.</p></div>}
        {messages.map((item) => <article key={item.id} className={`chat-message ${item.role}`}><small>{item.role === 'user' ? '나' : item.source === 'OPENAI' ? 'AI 답변' : '기본 추천'}</small><p>{item.content}</p></article>)}
        {busy && <div className="chat-message assistant"><Loading label="냉장고 재료를 살펴보고 있어요…" /></div>}
        <div ref={endRef} />
      </div>
      {failure && <div className="stack chat-failure"><ErrorBox error={failure.error} onRetry={() => request(failure.payload)} />{failure.payload.mode !== 'LOCAL' && <button className="button secondary" disabled={busy} onClick={() => { setMode('LOCAL'); request({ ...failure.payload, mode: 'LOCAL' }) }}>기본 추천으로 답변 받기</button>}</div>}
      {messages.length === 0 && <div className="chat-suggestions" aria-label="추천 질문">{suggestions.map((suggestion) => <button className="chip" key={suggestion} onClick={() => send(suggestion)} disabled={busy || status.loading}>{suggestion}</button>)}</div>}
      <form className="chat-composer" onSubmit={(event) => { event.preventDefault(); send(message) }}><label className="field">요리 도우미에게 질문하기<textarea ref={inputRef} rows={3} value={message} onChange={(event) => setMessage(event.target.value)} maxLength={2000} placeholder="예: 두부와 계란으로 만들 수 있는 간단한 요리 추천해 줘" disabled={busy} /></label><div className="section-heading"><p className="helper">{message.length}/2,000자 · {servings}인분</p><button className="button" disabled={busy || status.loading || !message.trim()}>{busy ? '답변 기다리는 중…' : '보내기'}</button></div></form>
    </section><aside className="chat-side"><div className="panel stack"><p className="eyebrow">A FEW HELPFUL TIPS</p><h2>이렇게 이야기해 보세요</h2><p>먹고 싶은 메뉴나 조리 시간을 알려주면 한 끼를 고르는 데 도움이 돼요.</p><p className="muted">추천의 바탕은 내 냉장고에 기록한 재료예요. 수량과 유통기한을 먼저 확인해 주세요.</p><Link className="button secondary full-width" to="/ingredients">내 냉장고 확인하기</Link><p className="helper">대화는 현재 화면에서만 유지해요. 화면을 나가거나 새로고침하면 지워져요.</p><p className="helper">AI 대화는 질문·최근 대화·재료 정보를 OpenAI로 보내요. 기본 추천은 외부 전송 없이 사용할 수 있어요.</p>{(mode === 'LOCAL' || status.data?.available === false) && <p className="notice">현재는 AI 대화 대신 기본 추천을 제공해요. 자유로운 질문의 모든 조건을 반영하지 못할 수 있어요.</p>}</div></aside></div>
    {recommended.length > 0 && <section className="page-section"><div className="section-heading"><h2>함께 볼 수 있는 레시피</h2><Link className="text-link" to="/recipes">전체 레시피 보기 →</Link></div><p className="muted">마지막 답변에서 선택한 메뉴예요. 카드에서 필요한 재료와 실제 보유량을 확인해 주세요.</p><div className="recipe-grid">{recommended.map((recipe) => <RecipeCard recipe={recipe} key={recipe.id} onOpen={setSelectedRecipe} />)}</div></section>}
    {selectedRecipe !== null && <RecipeDetail id={selectedRecipe} initialServings={recommended.find((recipe) => recipe.id === selectedRecipe)?.servings || servings} onClose={() => setSelectedRecipe(null)} />}
  </>
}
