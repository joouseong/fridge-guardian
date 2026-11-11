import { difficulties, quantity, spicyLabels } from '../lib/format.js'

// 필요한 재료마다 ✓(충분) / △(일부만 보유) / ＋(없음)로 보유 여부를 보여 줍니다.
function ownership(item) {
  if (item.missingQuantity <= 0) return { mark: '✓', className: 'have', label: '보유' }
  if (item.availableQuantity > 0) return { mark: '△', className: 'partial', label: '일부 보유' }
  return { mark: '＋', className: 'lack', label: '부족' }
}

export default function RecipeCard({ recipe, onOpen }) {
  return <article className={`recipe-card ${recipe.best ? 'best' : ''}`}>
    <div className="recipe-visual" aria-hidden="true"><span>🍲</span><span className="recipe-visual-caption">오늘의 한 끼</span></div>
    <div className="recipe-body">
      {recipe.best && <span className="best-match">BEST MATCH</span>}
      <div className="recipe-meta"><span>{recipe.cookingTime}분</span><span>{difficulties[recipe.difficulty]}</span><span>{recipe.servings}인분</span>{recipe.spicyLevel > 0 && <span>🌶 {spicyLabels[recipe.spicyLevel]}</span>}</div>
      <h3>{recipe.name}</h3><p className="muted">{recipe.description}</p>
      <div className="match-rate"><strong>{recipe.matchRate}%</strong><span>재료 일치율</span>
        <div className="match-meter" role="meter" aria-label={`${recipe.name} 재료 일치율`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={recipe.matchRate}><span style={{ width: `${recipe.matchRate}%` }} /></div></div>
      <ul className="ingredient-checklist" aria-label="필요한 재료">{recipe.requiredIngredients.map((item) => { const state = ownership(item); return <li key={item.name} className={state.className}><span aria-hidden="true">{state.mark}</span><span className="sr-only">{state.label} </span>{item.name}</li> })}</ul>
      <div className="recipe-tags"><span className={`badge ${recipe.canCook ? 'safe' : ''}`}>{recipe.canCook ? '재료 준비 완료' : `${recipe.missingCount}종 보충 필요`}</span><span className="badge">보유 재료 {recipe.matchedCount}종</span></div>
      {recipe.missingIngredients.length > 0 && <p className="helper">부족: {recipe.missingIngredients.map((item) => `${item.name} ${quantity(item.missingQuantity, item.unit)}`).join(', ')}</p>}
      <p className="recipe-reason">{recipe.reason}</p><button className="button secondary full-width" onClick={() => onOpen(recipe.id)} aria-label={`${recipe.name} 레시피 보기`}>레시피 보기 <span aria-hidden="true">→</span></button>
    </div>
  </article>
}
