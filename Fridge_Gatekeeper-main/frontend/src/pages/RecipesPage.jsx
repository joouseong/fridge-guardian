import { useState } from 'react'
import RecipeCard from '../components/RecipeCard.jsx'
import RecipeDetail from '../components/RecipeDetail.jsx'
import { EmptyState, ErrorBox, Link, Loading, PageHeading, ServingControl } from '../components/UI.jsx'
import useResource from '../hooks/useResource.js'

export default function RecipesPage() {
  const [servings, setServings] = useState(1)
  const [selectedRecipe, setSelectedRecipe] = useState(null)
  const [onlyReady, setOnlyReady] = useState(false)
  const [search, setSearch] = useState('')
  const { data, loading, error, reload } = useResource(`/recipes/recommendations?servings=${servings}`)
  const recipes = data?.filter((recipe) => (!onlyReady || recipe.canCook) && recipe.name.toLocaleLowerCase('ko').includes(search.trim().toLocaleLowerCase('ko'))) || []
  return <>
    <PageHeading eyebrow="COOK WITH WHAT YOU HAVE" title="오늘은 무엇을 먹을까요?" action={<ServingControl value={servings} onChange={setServings} />}>내 냉장고의 재료를 활용하는 메뉴부터 추천해 드려요.</PageHeading>
    <div className="panel toolbar"><label className="field">레시피 검색<input type="search" placeholder="먹고 싶은 요리 이름" value={search} onChange={(event) => setSearch(event.target.value)} /></label><label className="checkbox-field"><input type="checkbox" checked={onlyReady} onChange={(event) => setOnlyReady(event.target.checked)} />재료가 모두 준비된 메뉴만</label><button className="button secondary" onClick={reload} disabled={loading}>추천 새로고침</button></div>
    <p className="notice">보유 재료 활용 종류가 많은 순 → 임박 재료 활용 종류가 많은 순 → 부족한 재료 종류가 적은 순으로 추천해요. 만료된 재료는 제외해요.</p>
    {loading && <Loading label={`${servings}인분에 맞춰 추천을 준비하고 있어요…`} />}{error && <ErrorBox error={error} onRetry={reload} />}
    {data && <><p className="helper">총 {recipes.length}개 메뉴 · {servings}인분 기준</p>{recipes.length ? <div className="recipe-grid">{recipes.map((recipe) => <RecipeCard recipe={recipe} key={recipe.id} onOpen={setSelectedRecipe} />)}</div> : <EmptyState title="조건에 맞는 레시피가 없어요" action={<Link className="button secondary" to="/ingredients">내 식재료 확인하기</Link>}>검색어와 ‘재료가 모두 준비된 메뉴만’ 조건을 확인하거나, 냉장고에 식재료를 추가해 주세요.</EmptyState>}</>}
    {selectedRecipe !== null && <RecipeDetail id={selectedRecipe} initialServings={servings} onClose={() => setSelectedRecipe(null)} />}
  </>
}
