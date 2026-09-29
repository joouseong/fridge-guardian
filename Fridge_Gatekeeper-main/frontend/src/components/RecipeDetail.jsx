import { useState } from 'react'
import useResource from '../hooks/useResource.js'
import { difficulties, quantity } from '../lib/format.js'
import { Dialog, ErrorBox, Loading, ServingControl } from './UI.jsx'

export default function RecipeDetail({ id, initialServings = 1, onClose }) {
  const [servings, setServings] = useState(initialServings)
  // 인분을 바꾸면 서버에서 수량과 조리 가능 여부를 다시 계산합니다.
  const { data: recipe, loading, error, reload } = useResource(`/recipes/${id}?servings=${servings}`)
  return <Dialog title={recipe?.name || '레시피 자세히 보기'} onClose={onClose} wide><div className="dialog-body stack">
    <ServingControl value={servings} onChange={setServings} />
    {loading && <Loading label="내 재료에 맞춰 레시피를 불러오고 있어요…" />}{error && <ErrorBox error={error} onRetry={reload} />}
    {recipe && <><p className="muted">{recipe.description}</p><div className="recipe-meta"><span>조리 {recipe.cookingTime}분</span><span>난이도 {difficulties[recipe.difficulty]}</span><span>{recipe.servings}인분</span></div><p className="notice">{recipe.reason}</p>
      <section><h3>필요한 재료 · {recipe.servings}인분</h3><p className="helper">보유량은 만료된 재료를 제외한 수량이에요. g↔kg, ml↔L는 자동 환산해요.</p><div className="table-scroll"><table className="ingredient-table"><caption className="sr-only">레시피별 필요량, 보유량과 부족량</caption><thead><tr><th scope="col">재료</th><th scope="col">필요</th><th scope="col">보유</th><th scope="col">부족</th></tr></thead><tbody>{recipe.requiredIngredients.map((item) => <tr key={item.name}><th scope="row">{item.name}{item.urgent && <span className="status-badge soon">임박</span>}{item.unitMismatch && <small className="error-text">일부 보유 재료 단위 확인 필요</small>}</th><td>{quantity(item.requiredQuantity, item.unit)}</td><td>{quantity(item.availableQuantity, item.unit)}</td><td>{item.missingQuantity > 0 ? quantity(item.missingQuantity, item.unit) : '준비 완료'}</td></tr>)}</tbody></table></div>
        {recipe.requiredIngredients.some((item) => item.unitMismatch) && <p className="helper">팩·개와 g처럼 환산할 수 없는 단위는 보유량에 합산하지 않아요. 내 냉장고에서 단위를 맞춰 주세요.</p>}
      </section>
      <section><h3>차근차근 만드는 순서</h3><ol className="step-list">{recipe.steps.map((step, index) => <li key={index}><span>{step}</span></li>)}</ol></section>
      <section><h3>영양 정보 <small className="muted">1인분 기준 · 추정값</small></h3><div className="nutrition-grid">{[['열량', recipe.nutrition.calories, 'kcal'], ['단백질', recipe.nutrition.protein, 'g'], ['탄수화물', recipe.nutrition.carbs, 'g'], ['지방', recipe.nutrition.fat, 'g']].map(([label, value, unit]) => <div className="nutrition-item" key={label}><span>{label}</span><strong>{value}<small>{unit}</small></strong></div>)}</div><p className="helper">인분을 바꿔도 위 영양값은 1인분 기준이에요. 재료와 조리 방식에 따라 실제 값은 달라져요.</p></section>
    </>}
  </div><div className="dialog-actions"><button className="button secondary" onClick={onClose}>닫기</button></div></Dialog>
}
