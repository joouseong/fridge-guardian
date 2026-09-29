import { useState } from 'react'
import IngredientRow from '../components/IngredientRow.jsx'
import IngredientEditor from '../components/IngredientEditor.jsx'
import IngredientAdder from '../components/IngredientAdder.jsx'
import { Dialog, EmptyState, ErrorBox, Loading, PageHeading, SelectOptions } from '../components/UI.jsx'
import useResource from '../hooks/useResource.js'
import { categories, locations, statuses } from '../lib/format.js'
import { api } from '../services/api.js'
import { navigate, useSearch } from '../lib/router.js'

export default function IngredientsPage() {
  const [sort, setSort] = useState('expiration')
  const [search, setSearch] = useState('')
  const query = useSearch()
  const requestedStatus = new URLSearchParams(query).get('status')
  const status = requestedStatus === 'TODAY' || Object.hasOwn(statuses, requestedStatus) ? requestedStatus : ''
  const [category, setCategory] = useState('')
  const [storage, setStorage] = useState('')
  const [editor, setEditor] = useState(null)
  const [deleting, setDeleting] = useState(null)
  const [deleteBusy, setDeleteBusy] = useState(false)
  const [deleteError, setDeleteError] = useState(null)
  const [notice, setNotice] = useState('')
  const { data, loading, error, reload } = useResource(`/ingredients?sort=${sort}`)
  const filtered = data?.filter((item) => item.name.toLocaleLowerCase('ko').includes(search.trim().toLocaleLowerCase('ko')) && (!status || (status === 'TODAY' ? item.daysUntilExpiration === 0 : item.status === status)) && (!category || item.category === category) && (!storage || item.storageType === storage)) || []
  function setStatus(value) {
    const params = new URLSearchParams(query)
    if (value) params.set('status', value)
    else params.delete('status')
    navigate(`/ingredients${params.size ? `?${params}` : ''}`, { scroll: false })
  }
  function saved(message) { setEditor(null); setNotice(message); reload() }
  async function remove() {
    setDeleteBusy(true)
    setDeleteError(null)
    try { await api(`/ingredients/${deleting.id}`, { method: 'DELETE' }); setDeleting(null); setNotice('식재료를 삭제했어요.'); reload() }
    catch (failure) { setDeleteError(failure) } finally { setDeleteBusy(false) }
  }
  function resetFilters() { setSearch(''); setStatus(''); setCategory(''); setStorage('') }
  return <>
    <PageHeading eyebrow="MY FRIDGE" title="내 냉장고" action={<button className="button" onClick={() => setEditor({ ingredient: null })}>＋ 식재료 추가</button>}>냉장고 속 재료를 한눈에 살피고, 신선할 때 맛있게 즐겨요.</PageHeading>
    {notice && <p className="success-box" role="status">{notice}</p>}
    <section className="panel"><div className="toolbar"><label className="field">식재료 검색<input type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="재료 이름으로 검색" /></label><label className="field">카테고리 필터<select value={category} onChange={(event) => setCategory(event.target.value)}><option value="">모든 카테고리</option><SelectOptions values={categories} /></select></label><label className="field">보관 위치 필터<select value={storage} onChange={(event) => setStorage(event.target.value)}><option value="">모든 보관 위치</option><SelectOptions values={locations} /></select></label><label className="field">정렬<select value={sort} onChange={(event) => setSort(event.target.value)}><option value="expiration">유통기한 가까운 순</option><option value="category">카테고리순</option><option value="storage">보관 위치순</option></select></label></div>
      <div className="section-heading"><div className="filter-chips" role="group" aria-label="유통기한 상태 필터">{[['', '전체'], ['TODAY', '오늘까지'], ...Object.entries(statuses)].map(([value, label]) => <button className={`chip ${status === value ? 'active' : ''}`} key={value} aria-pressed={status === value} onClick={() => setStatus(value)}>{label}</button>)}</div><button className="button ghost small" onClick={reload} disabled={loading}>새로고침</button></div>
      {loading && <Loading />}{error && <ErrorBox error={error} onRetry={reload} />}
      {data && <><p className="helper" aria-live="polite">전체 {data.length}개 중 {filtered.length}개</p>{filtered.length ? <ul className="ingredient-list">{filtered.map((item) => <IngredientRow item={item} key={item.id} onEdit={(ingredient) => setEditor({ ingredient })} onDelete={(ingredient) => { setDeleting(ingredient); setDeleteError(null) }} />)}</ul> : <EmptyState title={data.length ? '조건에 맞는 재료가 없어요' : '냉장고의 첫 재료를 기록해 볼까요?'} action={data.length ? <button className="button secondary" onClick={resetFilters}>검색과 필터 초기화</button> : <button className="button" onClick={() => setEditor({ ingredient: null })}>첫 식재료 추가</button>}>{data.length ? '검색어나 필터를 바꾸면 다른 재료를 찾을 수 있어요.' : '식재료를 추가하면 유통기한과 맞춤 레시피를 함께 확인할 수 있어요.'}</EmptyState>}</>}
    </section><p className="helper">안전: 4일 이상 남음 · 임박: 오늘부터 3일 이내 · 만료: 오늘 이전. ‘안전’은 날짜 기준이며, 실제 상태도 함께 확인해 주세요.</p>
    {editor && (editor.ingredient ? <IngredientEditor ingredient={editor.ingredient} onClose={() => setEditor(null)} onSaved={saved} onReload={() => { setEditor(null); reload() }} /> : <IngredientAdder inventory={data || []} onClose={() => setEditor(null)} onSaved={saved} />)}
    {deleting && <Dialog title="식재료 삭제" busy={deleteBusy} onClose={() => setDeleting(null)}><div className="dialog-body stack"><p className="confirmation-copy"><strong>{deleting.name}</strong>을(를) 냉장고에서 삭제할까요?</p><p className="muted">삭제한 재료는 추천에 반영되지 않아요. 필요하면 다시 추가할 수 있어요.</p>{deleteError && <ErrorBox error={deleteError} />}</div><div className="dialog-actions"><button className="button secondary" onClick={() => setDeleting(null)} disabled={deleteBusy}>취소</button><button className="button danger" onClick={remove} disabled={deleteBusy}>{deleteBusy ? '삭제 중…' : '삭제하기'}</button></div></Dialog>}
  </>
}
