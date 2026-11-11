import { useState } from 'react'
import AiRecipePanel from '../components/AiRecipePanel.jsx'
import { Dialog, EmptyState, ErrorBox, Link, Loading, PageHeading, ServingControl } from '../components/UI.jsx'
import useResource from '../hooks/useResource.js'
import { ddayText, quantity } from '../lib/format.js'
import { api } from '../services/api.js'

const MAX_SELECTED = 12

export default function AiRecipePage() {
  const status = useResource('/ai/status')
  // 만료된 재료는 서버에서 제외합니다. 유통기한이 가까운 순서로 보여 줍니다.
  const stock = useResource('/ingredients?status=USABLE&sort=expiration')
  const history = useResource('/ai/recipes')
  const [picked, setPicked] = useState([])
  const [servings, setServings] = useState(1)
  const [note, setNote] = useState('')
  const [job, setJob] = useState({ busy: false, result: null, error: null })
  const [viewing, setViewing] = useState(null)
  const available = status.data ? status.data.available : true
  const ready = picked.length > 0 && !job.busy && available
  function toggle(id) { setPicked((current) => current.includes(id) ? current.filter((item) => item !== id) : current.length < MAX_SELECTED ? [...current, id] : current) }
  async function generate() {
    setJob({ busy: true, result: null, error: null })
    try {
      const result = await api('/ai/recipes', { method: 'POST', body: { ingredientIds: picked, servings, ...(note.trim() ? { request: note.trim() } : {}) } })
      setJob({ busy: false, result, error: null })
      history.reload()
    } catch (error) { setJob({ busy: false, result: null, error }) }
  }
  async function favorite(recipe) {
    try {
      const updated = await api(`/ai/recipes/${recipe.id}/favorite`, { method: 'PUT', body: { favorite: !recipe.favorite } })
      setJob((current) => current.result?.id === updated.id ? { ...current, result: updated } : current)
      setViewing((current) => current?.id === updated.id ? updated : current)
      history.reload()
    } catch (error) { setJob((current) => ({ ...current, error })) }
  }
  async function remove(recipe) {
    try { await api(`/ai/recipes/${recipe.id}`, { method: 'DELETE' }); setViewing(null); history.reload() }
    catch (error) { setJob((current) => ({ ...current, error })) }
  }
  return <>
    <PageHeading eyebrow="AI RECIPE" title="AI 레시피 만들기">가지고 있는 재료를 골라 보세요. 알레르기·비선호 재료와 매운맛 설정을 지키면서 맞춤 레시피를 만들어 드려요.</PageHeading>
    {status.data && !status.data.available && <p className="notice" role="status">AI 연결이 아직 설정되지 않았어요. 지금은 <Link className="text-link" to="/recipes">보유 재료 기반 레시피 추천</Link>을 이용해 주세요.</p>}
    <section className="panel stack">
      <div className="section-heading"><h2>1. 사용할 재료 고르기</h2><span className="helper" aria-live="polite">{picked.length}/{MAX_SELECTED}개 선택</span></div>
      {stock.loading && <Loading />}{stock.error && <ErrorBox error={stock.error} onRetry={stock.reload} />}
      {stock.data && (stock.data.length ? <div className="pick-grid" role="group" aria-label="사용할 재료">{stock.data.map((item) => <button type="button" key={item.id} className={`pick-item ${picked.includes(item.id) ? 'selected' : ''}`} aria-pressed={picked.includes(item.id)} disabled={!picked.includes(item.id) && picked.length >= MAX_SELECTED} onClick={() => toggle(item.id)}><strong>{item.name}</strong><small>{quantity(item.quantity, item.unit)}{item.status === 'SOON' ? ` · ${ddayText(item.daysUntilExpiration)}` : ''}</small></button>)}</div> : <EmptyState title="사용할 수 있는 재료가 없어요" action={<Link className="button secondary" to="/ingredients">식재료 추가하러 가기</Link>}>유통기한이 지나지 않은 재료를 등록하면 AI 레시피를 만들 수 있어요.</EmptyState>)}
      <div className="section-heading"><h2>2. 인분과 요청 사항</h2></div>
      <ServingControl value={servings} onChange={setServings} disabled={job.busy} />
      <label className="field">요청 사항 (선택)<input value={note} maxLength={200} onChange={(event) => setNote(event.target.value)} placeholder="예: 15분 안에 끝나는 간단한 요리" disabled={job.busy} /></label>
      <button className="button" onClick={generate} disabled={!ready}>{job.busy ? '레시피를 만드는 중이에요…' : picked.length ? `선택한 ${picked.length}개로 AI 레시피 만들기` : '재료를 먼저 골라 주세요'}</button>
      <p className="helper">재료 이름과 수량·유통기한만 AI에 전달해요. 같은 재료 조합은 저장된 결과를 다시 보여 줄 수 있어요.</p>
    </section>
    {job.error && <ErrorBox error={job.error} onRetry={ready ? generate : undefined} />}
    {job.busy && <Loading label="AI가 레시피를 만들고 있어요. 최대 1분 정도 걸릴 수 있어요…" />}
    {job.result && <section className="panel"><AiRecipePanel recipe={job.result} onFavorite={favorite} /></section>}
    <section className="panel stack"><div className="section-heading"><h2>이전에 만든 레시피</h2></div>
      {history.loading && <Loading />}{history.error && <ErrorBox error={history.error} onRetry={history.reload} />}
      {history.data && (history.data.length ? <ul className="history-list">{history.data.map((recipe) => <li key={recipe.id}><button className="history-item" onClick={() => setViewing(recipe)}><strong>{recipe.favorite && '★ '}{recipe.title}</strong><small>{recipe.sourceIngredients.join(', ')} · {recipe.servings}인분</small></button></li>)}</ul> : <p className="helper">아직 만든 AI 레시피가 없어요.</p>)}
    </section>
    {viewing && <Dialog title="AI 레시피" wide onClose={() => setViewing(null)}><div className="dialog-body"><AiRecipePanel recipe={viewing} onFavorite={favorite} /></div><div className="dialog-actions"><button className="button danger" onClick={() => remove(viewing)}>삭제</button><button className="button secondary" onClick={() => setViewing(null)}>닫기</button></div></Dialog>}
  </>
}
