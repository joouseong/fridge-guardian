import { difficulties, quantity } from '../lib/format.js'

export default function RecipeCard({ recipe, onOpen }) {
  return <article className="recipe-card">
    <div className="recipe-visual" aria-hidden="true"><span>🍲</span><span className="recipe-visual-caption">오늘의 한 끼</span></div>
    <div className="recipe-body"><div className="recipe-meta"><span>{recipe.cookingTime}분</span><span>{difficulties[recipe.difficulty]}</span><span>{recipe.servings}인분</span></div><h3>{recipe.name}</h3><p className="muted">{recipe.description}</p>
      <div className="recipe-tags"><span className={`badge ${recipe.canCook ? 'safe' : ''}`}>{recipe.canCook ? '재료 준비 완료' : `${recipe.missingCount}종 보충 필요`}</span><span className="badge">보유 재료 {recipe.matchedCount}종</span></div>
      {recipe.availableIngredients.length > 0 && <p className="helper">활용: {recipe.availableIngredients.map((item) => `${item.name} ${quantity(Math.min(item.requiredQuantity, item.availableQuantity), item.unit)}`).join(', ')}</p>}
      {recipe.missingIngredients.length > 0 && <p className="helper">부족: {recipe.missingIngredients.map((item) => `${item.name} ${quantity(item.missingQuantity, item.unit)}`).join(', ')}</p>}
      <p className="recipe-reason">{recipe.reason}</p><button className="button secondary full-width" onClick={() => onOpen(recipe.id)} aria-label={`${recipe.name} 레시피 보기`}>레시피 보기 <span aria-hidden="true">→</span></button>
    </div>
  </article>
}
