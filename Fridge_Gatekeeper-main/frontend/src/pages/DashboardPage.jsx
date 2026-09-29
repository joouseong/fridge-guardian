import { useState } from 'react'
import IngredientRow from '../components/IngredientRow.jsx'
import RecipeCard from '../components/RecipeCard.jsx'
import RecipeDetail from '../components/RecipeDetail.jsx'
import IngredientAdder from '../components/IngredientAdder.jsx'
import { EmptyState, ErrorBox, Link, Loading, PageHeading } from '../components/UI.jsx'
import useResource from '../hooks/useResource.js'
import { formatDate } from '../lib/format.js'

export default function DashboardPage({ user }) {
  const dashboard = useResource('/dashboard')
  const recipes = useResource('/recipes/recommendations?servings=1')
  const [selectedRecipe, setSelectedRecipe] = useState(null)
  const [adding, setAdding] = useState(false)
  const [notice, setNotice] = useState('')
  const data = dashboard.data
  return <>
    <PageHeading eyebrow={data ? formatDate(data.today, true) : 'YOUR KITCHEN, AT A GLANCE'} title={`${user.nickname}님, 오늘도 알뜰한 한 끼!`} action={<button className="button" onClick={() => setAdding(true)}>＋ 식재료 추가</button>}>냉장고를 가볍게 살피는 작은 습관, 여기서 시작해요.</PageHeading>
    {notice && <p className="success-box" role="status">{notice}</p>}
    {dashboard.loading && <Loading />}{dashboard.error && <ErrorBox error={dashboard.error} onRetry={dashboard.reload} />}
    {data && <><div className="stat-grid">{[{ label: '전체 식재료', value: data.total, hint: `안전 ${data.safeCount}개 · 기한 미등록 ${data.unknownCount || 0}개`, to: '/ingredients', icon: '▤' }, { label: '오늘까지', value: data.todayCount, hint: '오늘 먼저 살펴봐요', to: '/ingredients?status=TODAY', icon: '◷', tone: 'warning' }, { label: '유통기한 임박', value: data.soonCount, hint: '오늘부터 3일 이내', to: '/ingredients?status=SOON', icon: '◴', tone: 'warning' }, { label: '유통기한 만료', value: data.expiredCount, hint: '정리가 필요해요', to: '/ingredients?status=EXPIRED', icon: '!', tone: 'danger' }].map((stat) => <Link className={`stat-card ${stat.tone || ''}`} to={stat.to} key={stat.label}><div><span>{stat.label}</span><span aria-hidden="true">{stat.icon}</span></div><p className="stat-value">{stat.value}<small>개</small></p><p className="helper">{stat.hint}</p></Link>)}</div>
      <div className="dashboard-columns"><section className="panel"><div className="section-heading"><h2>먼저 먹으면 좋아요 <span aria-hidden="true">🌿</span></h2><Link className="text-link" to="/ingredients?status=SOON">전체 보기</Link></div>{data.expiringIngredients.length ? <ul className="ingredient-list">{data.expiringIngredients.slice(0, 5).map((item) => <IngredientRow item={item} key={item.id} />)}</ul> : <EmptyState title={data.total ? '급하게 챙길 재료가 없어요' : '아직 등록한 재료가 없어요'} action={<Link className="button secondary small" to="/ingredients">식재료 관리</Link>}>{data.total ? '필요한 만큼 꺼내 맛있는 한 끼를 준비해요.' : '첫 식재료를 추가하고 냉장고 관리를 시작해요.'}</EmptyState>}</section>
        <section className="panel"><div className="section-heading"><h2>냉장고 정리 알림</h2><span className="status-badge expired">만료 {data.expiredCount}개</span></div>{data.expiredIngredients.length ? <><p className="muted">기한이 지난 재료는 레시피 추천에서 제외했어요.</p><ul className="ingredient-list">{data.expiredIngredients.slice(0, 3).map((item) => <IngredientRow item={item} key={item.id} />)}</ul><Link className="button secondary full-width" to="/ingredients?status=EXPIRED">만료된 식재료 정리하기</Link></> : <EmptyState title="만료된 식재료가 없어요">먹기 전에는 재료의 실제 상태도 함께 확인해 주세요.</EmptyState>}</section></div>
    </>}
    <section className="page-section"><div className="section-heading"><div><p className="eyebrow">ON THE MENU</p><h2>내 재료로 만드는 오늘의 메뉴</h2><p className="helper">보유 재료 활용 → 임박 재료 활용 → 부족 재료가 적은 순 · 1인분</p></div><Link className="text-link" to="/recipes">레시피 전체 보기 →</Link></div>{recipes.loading && <Loading label="오늘의 메뉴를 고르고 있어요…" />}{recipes.error && <ErrorBox error={recipes.error} onRetry={recipes.reload} />}{recipes.data && (recipes.data.some((recipe) => recipe.matchedCount > 0) ? <div className="recipe-grid">{recipes.data.slice(0, 3).map((recipe) => <RecipeCard recipe={recipe} key={recipe.id} onOpen={setSelectedRecipe} />)}</div> : <EmptyState title="추천에 활용할 식재료가 없어요" action={<Link className="button secondary" to="/ingredients">식재료 추가하러 가기</Link>}>냉장고에 사용할 수 있는 재료를 추가하면 맞춤 메뉴를 추천해 드려요.</EmptyState>)}</section>
    {selectedRecipe !== null && <RecipeDetail id={selectedRecipe} onClose={() => setSelectedRecipe(null)} />}
    {adding && <IngredientAdder onClose={() => setAdding(false)} onSaved={message => { setAdding(false); setNotice(message); dashboard.reload(); recipes.reload() }} />}
  </>
}
