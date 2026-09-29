import { useState } from 'react'
import { api } from '../services/api.js'
import { categories, locations, quantity, todayInSeoul, units } from '../lib/format.js'
import { searchIngredients } from '../lib/ingredientCatalog.js'
import { Dialog, ErrorBox, SelectOptions } from './UI.jsx'

export default function IngredientEditor({ ingredient, onClose, onSaved, onReload }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const [query, setQuery] = useState('')
  const [group, setGroup] = useState('')
  const [pickerOpen, setPickerOpen] = useState(!ingredient)
  const [expanded, setExpanded] = useState(Boolean(ingredient))
  const [values, setValues] = useState({ name: ingredient?.name || '', category: ingredient?.category || 'OTHER',
    storageType: ingredient?.storageType || 'FRIDGE', quantity: ingredient?.quantity || 1, unit: ingredient?.unit || 'PIECE',
    purchaseDate: ingredient?.purchaseDate || todayInSeoul(), expirationDate: ingredient?.expirationDate || '' })
  const results = searchIngredients(query)
  const choices = results.items.filter(item => !group || item.category === group)
  function change(event) { setValues(current => ({ ...current, [event.target.name]: event.target.value })) }
  function choose(item) {
    setValues(current => ({ ...current, name: item.name, category: item.category, quantity: item.quantity, unit: item.unit, storageType: item.storageType }))
    setError(null)
    setPickerOpen(false)
    setExpanded(false)
  }
  function adjustQuantity(direction) {
    const step = ['GRAM', 'MILLILITER'].includes(values.unit) ? 50 : 1
    setValues(current => ({ ...current, quantity: Math.min(999999999, Math.max(1, Math.round((Number(current.quantity) + direction * step) * 1000) / 1000)) }))
  }
  async function submit(event) {
    event.preventDefault()
    if (busy) return
    setError(null)
    if (!values.name.trim()) { setError(new Error('식재료를 선택하거나 이름을 입력해 주세요.')); return }
    if (values.expirationDate && values.expirationDate < values.purchaseDate) { setError(new Error('유통기한은 구매 날짜와 같거나 이후여야 해요.')); return }
    const body = { ...values, name: values.name.trim(), quantity: Number(values.quantity), expirationDate: values.expirationDate || null,
      ...(ingredient ? { version: ingredient.version } : {}) }
    setBusy(true)
    try {
      await api(ingredient ? `/ingredients/${ingredient.id}` : '/ingredients', { method: ingredient ? 'PUT' : 'POST', body })
      onSaved(ingredient ? '식재료를 수정했어요.' : '냉장고에 식재료를 추가했어요.')
    } catch (failure) { setError(failure); setExpanded(true) } finally { setBusy(false) }
  }
  return <Dialog title={ingredient ? '식재료 수정' : '식재료 추가'} onClose={onClose} busy={busy} className="ingredient-dialog">
    <form onSubmit={submit}><div className="dialog-body stack">
      <p className="muted">재료를 고르면 기본 정보가 채워져요. 유통기한은 아는 경우에만 입력하세요.</p>
      {error && <ErrorBox error={error} />}
      {error?.status === 409 && <button type="button" className="button secondary" onClick={onReload}>최신 목록 다시 불러오기</button>}
      {!ingredient && pickerOpen && <section className="stack" aria-label="자주 쓰는 식재료">
        <label className="field">자주 쓰는 식재료 검색<input type="search" value={query} onChange={event => setQuery(event.target.value)} maxLength={80} placeholder="예: 계란, 달걀, 두부" disabled={busy} /></label>
        <label className="field">재료 분류<select value={group} onChange={event => setGroup(event.target.value)} disabled={busy}><option value="">전체 재료</option><SelectOptions values={categories} /></select></label>
        {results.approximate && results.items.length > 0 && <p className="helper" role="status">비슷한 이름을 찾았어요. 원하는 재료를 선택해 주세요.</p>}
        <p className="helper">{choices.length}가지 재료 · 선택 후 수량을 바꿀 수 있어요</p>
        <div className="ingredient-catalog">{choices.map(item => <button type="button" key={item.name} className={`catalog-item ${values.name === item.name ? 'selected' : ''}`} aria-label={`${item.name} 선택`} aria-pressed={values.name === item.name} disabled={busy} onClick={() => choose(item)}><strong>{item.name}</strong><small>{quantity(item.quantity, item.unit)}</small></button>)}</div>
        {!choices.length && <p className="helper">분류를 바꾸거나 목록에 없는 재료를 직접 입력해 주세요.</p>}
        <button type="button" className="button ghost small" disabled={busy} onClick={() => { setValues(current => ({ ...current, name: query.trim() })); setExpanded(true); setPickerOpen(false) }}>목록에 없는 재료 직접 입력</button>
      </section>}
      {values.name && <div className="selected-ingredient"><div className="section-heading"><div><p className="helper">선택한 재료</p><strong>{values.name}</strong><p className="helper">{locations[values.storageType]} 보관</p></div>{!ingredient && !pickerOpen && <button type="button" className="button secondary small" disabled={busy} onClick={() => setPickerOpen(true)}>다른 재료 선택</button>}</div><div className="quantity-stepper"><button type="button" className="icon-button" aria-label="수량 줄이기" disabled={busy || Number(values.quantity) <= 1} onClick={() => adjustQuantity(-1)}>−</button><output aria-live="polite">{quantity(values.quantity, values.unit)}</output><button type="button" className="icon-button" aria-label="수량 늘리기" disabled={busy || Number(values.quantity) >= 999999949} onClick={() => adjustQuantity(1)}>＋</button></div><p className="helper">실제 보유량과 다르면 수량을 바꿔 주세요.</p></div>}
      <label className="field">유통기한 (선택)<input aria-label="유통기한" name="expirationDate" type="date" value={values.expirationDate} onChange={change} min={values.purchaseDate} disabled={busy} /></label>
      <p className="helper">비워 두면 ‘기한 미등록’으로 저장돼요. 기한을 추정하지 않으며 사용 전에 실제 상태를 확인해 주세요.</p>
      <details open={expanded} onToggle={event => setExpanded(event.currentTarget.open)} className="ingredient-details"><summary>이름·수량·보관 위치 등 수정</summary><div className="form-grid">
        <label className="field full-width">식재료명<input name="name" value={values.name} onChange={change} placeholder="예: 계란, 대파, 두부" maxLength={80} required disabled={busy} /></label>
        <label className="field">카테고리<select name="category" value={values.category} onChange={change} disabled={busy} required><SelectOptions values={categories} /></select></label>
        <label className="field">보관 위치<select name="storageType" value={values.storageType} onChange={change} disabled={busy} required><SelectOptions values={locations} /></select></label>
        <label className="field">수량<input name="quantity" type="number" min="0.001" max="999999999" step="0.001" value={values.quantity} onChange={change} required disabled={busy} /></label>
        <label className="field">단위<select name="unit" value={values.unit} onChange={change} disabled={busy} required><SelectOptions values={units} /></select></label>
        <label className="field">구매 날짜<input name="purchaseDate" type="date" value={values.purchaseDate} max={todayInSeoul()} onChange={change} required disabled={busy} /></label>
      </div></details>
    </div><div className="dialog-actions"><button type="button" className="button secondary" onClick={onClose} disabled={busy}>취소</button><button className="button" disabled={busy || !values.name.trim()}>{busy ? '저장 중…' : ingredient ? '수정 저장' : '식재료 저장'}</button></div></form>
  </Dialog>
}
