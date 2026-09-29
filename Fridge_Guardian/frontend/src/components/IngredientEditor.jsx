import { useCallback, useState } from 'react'
import { api } from '../services/api.js'
import { categories, formatDate, locations, quantity, todayInSeoul, units } from '../lib/format.js'
import { searchIngredients } from '../lib/ingredientCatalog.js'
import BarcodeScanner from './BarcodeScanner.jsx'
import ImageUploader from './ImageUploader.jsx'
import { Dialog, ErrorBox, SelectOptions } from './UI.jsx'

// 한 개씩 자세히 등록·수정합니다. (여러 개를 빠르게 넣을 때는 IngredientAdder)
export default function IngredientEditor({ ingredient, onClose, onSaved, onReload }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const [query, setQuery] = useState('')
  const [group, setGroup] = useState('')
  const [pickerOpen, setPickerOpen] = useState(!ingredient)
  const [expanded, setExpanded] = useState(Boolean(ingredient))
  const [scanning, setScanning] = useState(false)
  const [scanMessage, setScanMessage] = useState('')
  const [file, setFile] = useState(null)
  const [removeImage, setRemoveImage] = useState(false)
  const [values, setValues] = useState({ name: ingredient?.name || '', category: ingredient?.category || 'OTHER',
    storageType: ingredient?.storageType || 'FRIDGE', quantity: ingredient?.quantity || 1, unit: ingredient?.unit || 'PIECE',
    purchaseDate: ingredient?.purchaseDate || todayInSeoul(), expirationDate: ingredient?.expirationDate || '',
    memo: ingredient?.memo || '', barcode: ingredient?.barcode || '' })
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
  // 스캔한 바코드로 품목명·기본 유통기한을 조회해 입력칸을 채웁니다. 사용자가 그대로 고칠 수 있어요.
  const detected = useCallback(async (code) => {
    setScanning(false)
    setValues(current => ({ ...current, barcode: code }))
    setPickerOpen(false)
    setScanMessage('바코드 정보를 찾고 있어요…')
    try {
      const found = await api(`/barcodes/${code}`)
      setValues(current => ({ ...current, name: found.name, category: found.category || current.category,
        expirationDate: current.expirationDate || found.suggestedExpirationDate || '' }))
      setScanMessage(found.suggestedExpirationDate
        ? `‘${found.name}’을(를) 찾았어요. 유통기한 ${formatDate(found.suggestedExpirationDate)}은 기본 유통기간으로 계산한 값이니 제품에 적힌 기한과 맞는지 확인해 주세요.`
        : `‘${found.name}’을(를) 찾았어요. 유통기한은 제품에 적힌 날짜를 직접 입력해 주세요.`)
    } catch (failure) {
      setScanMessage(failure.status === 404 ? '바코드 정보를 찾지 못했어요. 식재료 이름을 직접 입력해 주세요.' : failure.message)
    }
    setExpanded(true)
  }, [])
  async function submit(event) {
    event.preventDefault()
    if (busy) return
    setError(null)
    if (!values.name.trim()) { setError(new Error('식재료를 선택하거나 이름을 입력해 주세요.')); return }
    if (values.expirationDate && values.expirationDate < values.purchaseDate) { setError(new Error('유통기한은 구매 날짜와 같거나 이후여야 해요.')); return }
    const body = { ...values, name: values.name.trim(), quantity: Number(values.quantity), expirationDate: values.expirationDate || null,
      memo: values.memo.trim() || null, barcode: values.barcode.trim() || null,
      ...(ingredient ? { version: ingredient.version } : {}) }
    setBusy(true)
    let saved
    try {
      saved = await api(ingredient ? `/ingredients/${ingredient.id}` : '/ingredients', { method: ingredient ? 'PUT' : 'POST', body })
    } catch (failure) { setError(failure); setExpanded(true); setBusy(false); return }
    // 재료 정보는 이미 저장됐습니다. 사진은 따로 올리므로 실패해도 재료를 다시 만들지 않도록 구분해서 안내합니다.
    try {
      if (file) {
        const form = new FormData()
        form.append('file', file)
        await api(`/ingredients/${saved.id}/image`, { method: 'POST', formData: form })
      } else if (removeImage && ingredient?.imageUrl) {
        await api(`/ingredients/${saved.id}/image`, { method: 'DELETE' })
      }
      onSaved(ingredient ? '식재료를 수정했어요.' : '냉장고에 식재료를 추가했어요.')
    } catch (failure) {
      onSaved(`식재료는 저장했지만 사진은 올리지 못했어요. ${failure.message} 내 냉장고에서 수정해 다시 올려 주세요.`)
    } finally { setBusy(false) }
  }
  return <>
    <Dialog title={ingredient ? '식재료 수정' : '식재료 추가'} onClose={onClose} busy={busy} className="ingredient-dialog">
      <form onSubmit={submit}><div className="dialog-body stack">
        <p className="muted">재료를 고르면 기본 정보가 채워져요. 유통기한은 아는 경우에만 입력하세요.</p>
        {error && <ErrorBox error={error} />}
        {error?.status === 409 && <button type="button" className="button secondary" onClick={onReload}>최신 목록 다시 불러오기</button>}
        <div className="scan-row"><button type="button" className="button secondary small" disabled={busy} onClick={() => setScanning(true)}>📷 바코드로 입력</button>{scanMessage && <p className="helper" role="status">{scanMessage}</p>}</div>
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
        <section className="stack" aria-label="사진과 메모"><h3>사진과 메모</h3>
          <ImageUploader currentUrl={ingredient?.imageUrl} file={file} removed={removeImage} onFile={setFile} onRemoved={setRemoveImage} disabled={busy} />
          <label className="field">메모 (선택)<textarea name="memo" value={values.memo} onChange={change} maxLength={500} rows={2} placeholder="예: 유기농, 개봉 후 3일 안에 먹기" disabled={busy} /></label>
        </section>
        <details open={expanded} onToggle={event => setExpanded(event.currentTarget.open)} className="ingredient-details"><summary>이름·수량·보관 위치 등 수정</summary><div className="form-grid">
          <label className="field full-width">식재료명<input name="name" value={values.name} onChange={change} placeholder="예: 계란, 대파, 두부" maxLength={80} required disabled={busy} /></label>
          <label className="field">카테고리<select name="category" value={values.category} onChange={change} disabled={busy} required><SelectOptions values={categories} /></select></label>
          <label className="field">보관 위치<select name="storageType" value={values.storageType} onChange={change} disabled={busy} required><SelectOptions values={locations} /></select></label>
          <label className="field">수량<input name="quantity" type="number" min="0.001" max="999999999" step="0.001" value={values.quantity} onChange={change} required disabled={busy} /></label>
          <label className="field">단위<select name="unit" value={values.unit} onChange={change} disabled={busy} required><SelectOptions values={units} /></select></label>
          <label className="field">구매 날짜<input name="purchaseDate" type="date" value={values.purchaseDate} max={todayInSeoul()} onChange={change} required disabled={busy} /></label>
          <label className="field">바코드 (선택)<input name="barcode" inputMode="numeric" pattern="\d{6,14}" title="숫자 6~14자리" value={values.barcode} onChange={change} maxLength={14} disabled={busy} /></label>
        </div></details>
      </div><div className="dialog-actions"><button type="button" className="button secondary" onClick={onClose} disabled={busy}>취소</button><button className="button" disabled={busy || !values.name.trim()}>{busy ? '저장 중…' : ingredient ? '수정 저장' : '식재료 저장'}</button></div></form>
    </Dialog>
    {scanning && <BarcodeScanner onDetected={detected} onClose={() => setScanning(false)} />}
  </>
}
