import { useRef, useState } from 'react'
import { api } from '../services/api.js'
import { categories, locations, quantity, todayInSeoul, units } from '../lib/format.js'
import { findCatalogIngredient, ingredientSymbol, searchIngredients } from '../lib/ingredientCatalog.js'
import { batchPayload, ingredientDraft, MAX_BATCH_SIZE, parseIngredientList, validateDrafts } from '../lib/ingredientBatch.js'
import { Dialog, ErrorBox, SelectOptions } from './UI.jsx'

export default function IngredientAdder({ inventory = [], onClose, onSaved }) {
  const [drafts, setDrafts] = useState([])
  const [query, setQuery] = useState('')
  const [group, setGroup] = useState('')
  const [mode, setMode] = useState('pick')
  const [view, setView] = useState('pick')
  const [text, setText] = useState('')
  const [issues, setIssues] = useState([])
  const [notice, setNotice] = useState('')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  const saving = useRef(false)
  const attempt = useRef(null)
  const today = todayInSeoul()
  const results = searchIngredients(query)
  const owned = new Set(inventory.map(item => findCatalogIngredient(item.name)?.name || item.name))
  const choices = results.items.filter(item => !group || (group === 'owned' ? owned.has(item.name) : item.category === group))
  const selected = new Set(drafts.map(item => item.name))

  function addItems(items) {
    const fresh = items.filter(item => !selected.has(item.name))
    if (drafts.length + fresh.length > MAX_BATCH_SIZE) { setError(new Error('한 번에 50개까지 선택할 수 있어요. 먼저 선택한 재료를 저장해 주세요.')); return false }
    setDrafts(current => [...current, ...fresh.map(item => ({ ...item, key: crypto.randomUUID() }))])
    setNotice(fresh.length ? `${fresh.length}개를 선택 목록에 담았어요.${items.length > fresh.length ? ' 이미 선택한 재료는 그대로 유지했어요.' : ''}` : '이미 선택한 재료예요. 수량·기한에서 수량을 바꿀 수 있어요.')
    setError(null)
    return true
  }
  function toggle(item) {
    if (selected.has(item.name)) setDrafts(current => current.filter(draft => draft.name !== item.name))
    else addItems([ingredientDraft(item, today)])
  }
  function change(key, field, value) {
    setDrafts(current => current.map(item => item.key === key ? { ...item, [field]: value } : item))
    setError(null)
  }
  function paste() {
    const parsed = parseIngredientList(text, today)
    setIssues(parsed.issues)
    if (!parsed.issues.length && addItems(parsed.items)) { setText(''); setView('review') }
  }
  function custom() {
    if (addItems([ingredientDraft({ name: query.trim() }, today)])) { setView('review'); setQuery('') }
  }
  async function submit() {
    if (saving.current) return
    // Unprocessed text is kept visible; do not silently omit part of a shopping list.
    if (text.trim() || issues.length) { setView('pick'); setMode('paste'); setError(new Error('입력한 목록을 먼저 선택 목록에 담거나 지워 주세요.')); return }
    const invalid = validateDrafts(drafts, today)
    if (invalid) { setView('review'); setError(new Error(invalid)); return }
    const items = batchPayload(drafts)
    const signature = JSON.stringify(items)
    if (attempt.current?.signature !== signature) attempt.current = { signature, requestId: crypto.randomUUID() }
    saving.current = true
    setBusy(true)
    setError(null)
    try {
      const saved = await api('/ingredients/batch', { method: 'POST', body: { requestId: attempt.current.requestId, items } })
      onSaved(`${saved.length}개 식재료를 한 번에 추가했어요.`)
    } catch (failure) { setError(failure) } finally { saving.current = false; setBusy(false) }
  }

  return <Dialog title="식재료 한 번에 추가" onClose={onClose} busy={busy} className={`batch-dialog batch-view-${view}`}>
    <div className="batch-intro"><p>있는 재료를 톡톡 고르고, 한 번에 넣으세요.</p><span>수량은 바로 수정 · 유통기한은 나중에</span></div>
    {error && <div className="batch-error"><ErrorBox error={error} /></div>}
    <div className="batch-layout">
      <section className="batch-picker" aria-label="재료 고르기">
        <div className="batch-mode" role="group" aria-label="추가 방식"><button type="button" aria-pressed={mode === 'pick'} onClick={() => setMode('pick')} disabled={busy}>목록에서 고르기</button><button type="button" aria-pressed={mode === 'paste'} onClick={() => setMode('paste')} disabled={busy}>장본 목록 붙여넣기</button></div>
        {mode === 'pick' ? <>
          <label className="field batch-search">재료 검색<input type="search" placeholder="계란, 달걀, 두부…" value={query} maxLength={80} onChange={event => { setQuery(event.target.value); setGroup('') }} disabled={busy} /></label>
          <div className="batch-filters" role="group" aria-label="재료 분류">{[['', '전체'], ...(owned.size ? [['owned', '내가 등록한 재료']] : []), ...Object.entries(categories)].map(([value, label]) => <button type="button" className={`chip ${group === value ? 'active' : ''}`} key={value} aria-pressed={group === value} disabled={busy} onClick={() => setGroup(value)}>{label}</button>)}</div>
          {results.approximate && choices.length > 0 && <p className="helper" role="status">비슷한 이름이에요. 원하는 재료를 골라 주세요.</p>}
          <div className="batch-catalog">{choices.map(item => <button type="button" className={`batch-tile ${selected.has(item.name) ? 'selected' : ''}`} aria-label={`${item.name} 선택`} aria-pressed={selected.has(item.name)} key={item.name} disabled={busy} onClick={() => toggle(item)}><span className="tile-check" aria-hidden="true">{selected.has(item.name) ? '✓' : '+'}</span><span className="tile-symbol" aria-hidden="true">{ingredientSymbol(item)}</span><strong>{item.name}</strong><small>{quantity(item.quantity, item.unit)}</small>{owned.has(item.name) && <span className="tile-owned">등록 이력 있음</span>}</button>)}</div>
          {!choices.length && <p className="helper">목록에 없으면 이름을 직접 입력해 추가할 수 있어요.</p>}
          <button type="button" className="button ghost small batch-custom" onClick={custom} disabled={busy || (query.trim() && selected.has(query.trim()))}>{query.trim() ? `‘${query.trim()}’ 직접 추가` : '＋ 목록에 없는 재료 직접 입력'}</button>
        </> : <div className="stack batch-paste">
          <label className="field">장본 재료 목록<textarea aria-label="장본 재료 목록" rows={7} value={text} maxLength={4000} disabled={busy} placeholder={'계란 6개, 두부 1모\n김치 500g\n우유 1L'} onChange={event => { setText(event.target.value); setIssues([]) }} /></label>
          <p className="helper">쉼표나 줄바꿈으로 구분해 주세요. 수량을 생략하면 목록의 기본 수량을 사용해요.</p>
          {issues.length > 0 && <div className="error-box" role="alert"><ul>{issues.map((issue, index) => <li key={index}>{issue}</li>)}</ul></div>}
          <div className="row-actions"><button type="button" className="button secondary" disabled={busy || !text.trim()} onClick={paste}>선택 목록에 담기</button><button type="button" className="button ghost small" disabled={busy} onClick={() => { setText(''); setIssues([]); setError(null) }}>입력 지우기</button></div>
        </div>}
      </section>
      <section className="batch-review" aria-label="선택한 재료">
        <div className="batch-review-title"><h3>담을 재료 <span>{drafts.length}</span></h3><span className="helper">최대 50개</span></div>
        <p className="helper">표시된 수량을 확인해 주세요. 날짜를 몰라도 바로 저장할 수 있어요.</p>
        {!drafts.length && <div className="batch-empty"><span aria-hidden="true">🧺</span><p>냉장고에 있는 재료를 골라 보세요.</p><small>하나씩 저장할 필요 없이 모아서 추가해요.</small></div>}
        <div className="batch-rows">{drafts.map(item => <article className="batch-row" key={item.key} aria-label={`${item.name || '직접 입력'} 등록 정보`}>
          <div className="batch-row-heading"><label className="sr-only" htmlFor={`name-${item.key}`}>식재료명</label><input id={`name-${item.key}`} value={item.name} onChange={event => change(item.key, 'name', event.target.value)} maxLength={80} placeholder="식재료 이름" disabled={busy} /><button type="button" className="icon-button" aria-label={`${item.name || '직접 입력'} 선택 해제`} disabled={busy} onClick={() => setDrafts(current => current.filter(row => row.key !== item.key))}>×</button></div>
          <div className="batch-quantity"><button type="button" className="icon-button" aria-label={`${item.name} 수량 줄이기`} disabled={busy || Number(item.quantity) <= 1} onClick={() => change(item.key, 'quantity', Math.max(1, Number((Number(item.quantity || 0) - (['GRAM', 'MILLILITER'].includes(item.unit) ? 50 : 1)).toFixed(3))))}>−</button><label className="sr-only" htmlFor={`quantity-${item.key}`}>수량</label><input id={`quantity-${item.key}`} type="number" inputMode="decimal" min="0.001" max="999999999" step="0.001" value={item.quantity} onChange={event => change(item.key, 'quantity', event.target.value)} disabled={busy} /><label className="sr-only" htmlFor={`unit-${item.key}`}>단위</label><select id={`unit-${item.key}`} value={item.unit} onChange={event => change(item.key, 'unit', event.target.value)} disabled={busy}><SelectOptions values={units} /></select><button type="button" className="icon-button" aria-label={`${item.name} 수량 늘리기`} disabled={busy || Number(item.quantity) >= 999999949} onClick={() => change(item.key, 'quantity', Math.min(999999999, Number((Number(item.quantity || 0) + (['GRAM', 'MILLILITER'].includes(item.unit) ? 50 : 1)).toFixed(3))))}>＋</button></div>
          <details className="batch-details"><summary>{item.expirationDate ? `기한 ${item.expirationDate}` : '유통기한·보관 정보 (선택)'}</summary><div className="form-grid">
            <label className="field full-width">유통기한<input type="date" value={item.expirationDate} min={item.purchaseDate} onChange={event => change(item.key, 'expirationDate', event.target.value)} disabled={busy} /></label>
            <label className="field">카테고리<select value={item.category} onChange={event => change(item.key, 'category', event.target.value)} disabled={busy}><SelectOptions values={categories} /></select></label>
            <label className="field">보관 위치<select value={item.storageType} onChange={event => change(item.key, 'storageType', event.target.value)} disabled={busy}><SelectOptions values={locations} /></select></label>
            <label className="field full-width">구매 날짜<input type="date" value={item.purchaseDate} max={today} onChange={event => change(item.key, 'purchaseDate', event.target.value)} disabled={busy} /></label>
          </div></details>
          {!findCatalogIngredient(item.name) && <p className="helper">직접 입력한 재료 · 이름과 단위를 확인해 주세요.</p>}
        </article>)}</div>
      </section>
    </div>
    <div className="batch-footer">
      <p className="sr-only" role="status">{notice}</p>
      {drafts.length > 0 && <div className="batch-chosen" aria-label="선택 요약">{drafts.map(item => <span key={item.key}>{item.name || '이름 입력'} <strong>{quantity(item.quantity, item.unit)}</strong></span>)}</div>}
      <div className="batch-footer-actions"><button type="button" className="button secondary batch-review-toggle" disabled={busy} onClick={() => setView(view === 'pick' ? 'review' : 'pick')}>{view === 'pick' ? '수량·기한' : '재료 더 고르기'}</button><button type="button" className="button batch-save" disabled={busy || !drafts.length} onClick={submit}>{busy ? '한 번에 저장 중…' : drafts.length ? `${drafts.length}개 한 번에 추가` : '재료를 선택해 주세요'}</button></div>
    </div>
  </Dialog>
}
