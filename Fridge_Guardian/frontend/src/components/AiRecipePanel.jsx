import { useState } from 'react'
import { difficulties, quantity as formatQuantity } from '../lib/format.js'
import { api } from '../services/api.js'
import { ErrorBox, Link } from './UI.jsx'

// 상태는 AI의 표기가 아니라 서버가 내가 고른 재료와 비교한 결과입니다.
const statusLabels = { OWNED: ['보유', 'safe'], BASIC: ['기본 양념', ''], MISSING: ['부족', 'soon'] }

/** AI가 만든 레시피 한 개: 재료(보유·기본 양념·부족), 조리 순서, 부족 재료 연결(쇼핑 링크·장바구니), 즐겨찾기. */
export default function AiRecipePanel({ recipe, onFavorite }) {
  const [cart, setCart] = useState({ busy: false, message: '', error: null })
  async function addToCart() {
    setCart({ busy: true, message: '', error: null })
    try {
      const added = await api('/shopping-items/bulk', { method: 'POST', body: { items: recipe.missing.map((name) => ({ name })) } })
      setCart({ busy: false, error: null, message: added.length ? `${added.length}개 재료를 장바구니에 담았어요.` : '이미 장바구니에 담겨 있어요.' })
    } catch (error) { setCart({ busy: false, message: '', error }) }
  }
  return <article className="ai-recipe stack">
    <div className="section-heading"><div><p className="eyebrow">AI RECIPE</p><h2>{recipe.title}</h2></div>
      {onFavorite && <button className={`button ghost small favorite ${recipe.favorite ? 'active' : ''}`} aria-pressed={recipe.favorite} onClick={() => onFavorite(recipe)}>{recipe.favorite ? '★ 즐겨찾기' : '☆ 즐겨찾기'}</button>}</div>
    <p className="muted">{recipe.description}</p>
    <div className="recipe-meta"><span>조리 {recipe.cookingTimeMinutes}분</span><span>난이도 {difficulties[recipe.difficulty]}</span><span>{recipe.servings}인분</span></div>
    <section><h3>재료</h3><ul className="ai-ingredients">{recipe.ingredients.map((item) => { const [label, tone] = statusLabels[item.status] || [item.status, '']; return <li key={`${item.name}-${item.status}`}><span>{item.name} <span className="muted">{formatQuantity(item.quantity, item.unit)}</span></span><span className={`badge ${tone}`}>{label}</span></li> })}</ul>
      <p className="helper">‘기본 양념’은 집에 있는지 직접 확인해 주세요. AI가 알려 준 분량은 참고용이에요.</p></section>
    {recipe.missing.length > 0 && <section className="stack"><h3>부족한 재료</h3><p>{recipe.missing.join(', ')}</p>
      {recipe.shoppingLinks.length > 0 && <ul className="missing-list">{recipe.missing.map((name) => <li key={name}><strong>{name}</strong>{recipe.shoppingLinks.filter((link) => link.ingredient === name).map((link) => <a key={link.mall} className="shop-link" href={link.url} target="_blank" rel="noopener noreferrer">{link.mall}에서 찾기 <span aria-hidden="true">↗</span></a>)}</li>)}</ul>}
      <div className="row-actions"><button className="button secondary" onClick={addToCart} disabled={cart.busy}>{cart.busy ? '담는 중…' : '🛒 부족한 재료 장바구니에 담기'}</button><Link className="button ghost" to="/shopping">장바구니 보기</Link></div>
      {cart.message && <p className="success-box" role="status">{cart.message}</p>}{cart.error && <ErrorBox error={cart.error} />}</section>}
    <section><h3>만드는 순서</h3><ol className="step-list">{recipe.steps.map((step, index) => <li key={index}><span>{step}</span></li>)}</ol></section>
    {recipe.tips && <p className="notice">💡 {recipe.tips}</p>}
    <p className="helper">AI가 만든 레시피예요. 알레르기와 조리 안전(충분히 익히기 등)은 직접 한 번 더 확인해 주세요.</p>
  </article>
}
