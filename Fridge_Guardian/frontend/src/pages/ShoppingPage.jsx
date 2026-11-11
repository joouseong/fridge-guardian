import { useState } from 'react'
import { EmptyState, ErrorBox, Link, Loading, PageHeading } from '../components/UI.jsx'
import useResource from '../hooks/useResource.js'
import { quantity } from '../lib/format.js'
import { api } from '../services/api.js'

/** 부족한 재료를 담아 두는 장바구니. 산 재료는 체크하고, 필요하면 내 냉장고에 등록해요. */
export default function ShoppingPage() {
  const { data, loading, error, reload } = useResource('/shopping-items')
  const [name, setName] = useState('')
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState(null)
  async function run(action) {
    setBusy(true)
    setFailure(null)
    try { await action(); reload() } catch (problem) { setFailure(problem) } finally { setBusy(false) }
  }
  function add(event) {
    event.preventDefault()
    const value = name.trim()
    if (!value) return
    run(async () => { await api('/shopping-items', { method: 'POST', body: { name: value } }); setName('') })
  }
  const todo = data?.filter((item) => !item.checked) || []
  const done = data?.filter((item) => item.checked) || []
  return <>
    <PageHeading eyebrow="SHOPPING LIST" title="장바구니" action={done.length > 0 && <button className="button secondary" onClick={() => run(() => api('/shopping-items/checked', { method: 'DELETE' }))} disabled={busy}>산 항목 비우기</button>}>레시피에서 부족한 재료를 담아 두고 장 볼 때 확인하세요.</PageHeading>
    <section className="panel stack">
      <form className="inline-form" onSubmit={add}><label className="field">직접 추가<input value={name} maxLength={80} onChange={(event) => setName(event.target.value)} placeholder="예: 우유" disabled={busy} /></label><button className="button" disabled={busy || !name.trim()}>담기</button></form>
      {loading && <Loading />}{error && <ErrorBox error={error} onRetry={reload} />}{failure && <ErrorBox error={failure} />}
      {data && (data.length ? <>
        <ul className="shopping-list" aria-label="살 재료">{todo.map((item) => <ShoppingRow key={item.id} item={item} busy={busy} run={run} />)}</ul>
        {done.length > 0 && <><h3>산 재료</h3><ul className="shopping-list done" aria-label="산 재료">{done.map((item) => <ShoppingRow key={item.id} item={item} busy={busy} run={run} />)}</ul></>}
      </> : <EmptyState title="장바구니가 비어 있어요" action={<Link className="button secondary" to="/recipes">레시피 보러 가기</Link>}>레시피 화면에서 부족한 재료를 한 번에 담을 수 있어요.</EmptyState>)}
    </section>
  </>
}

function ShoppingRow({ item, busy, run }) {
  return <li className={item.checked ? 'checked' : ''}>
    <label className="checkbox-field"><input type="checkbox" checked={item.checked} disabled={busy} onChange={() => run(() => api(`/shopping-items/${item.id}/checked`, { method: 'PUT', body: { checked: !item.checked } }))} />
      <span><strong>{item.name}</strong>{item.quantity != null && <span className="muted"> {quantity(item.quantity, item.unit)}</span>}</span></label>
    <a className="shop-link" href={`https://www.coupang.com/np/search?q=${encodeURIComponent(item.name)}`} target="_blank" rel="noopener noreferrer">쿠팡 ↗</a>
    <a className="shop-link" href={`https://www.kurly.com/search?sword=${encodeURIComponent(item.name)}`} target="_blank" rel="noopener noreferrer">마켓컬리 ↗</a>
    <button className="button ghost small" aria-label={`${item.name} 삭제`} disabled={busy} onClick={() => run(() => api(`/shopping-items/${item.id}`, { method: 'DELETE' }))}>삭제</button>
  </li>
}
