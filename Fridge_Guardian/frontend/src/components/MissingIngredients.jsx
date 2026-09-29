import { useState } from 'react'
import { quantity } from '../lib/format.js'
import { api } from '../services/api.js'
import { ErrorBox, Link } from './UI.jsx'

/**
 * 부족한 재료 연결: 외부 쇼핑몰(쿠팡·마켓컬리) 검색 링크(부족한 재료가 1~2개일 때)와
 * 앱 내 장바구니 자동 담기를 제공합니다.
 */
export default function MissingIngredients({ recipe }) {
  const [state, setState] = useState({ busy: false, message: '', error: null })
  const missing = recipe.missingIngredients
  if (!missing.length) return null
  async function addToCart() {
    setState({ busy: true, message: '', error: null })
    try {
      const added = await api('/shopping-items/from-recipe', { method: 'POST', body: { recipeId: recipe.id, servings: recipe.servings } })
      setState({ busy: false, error: null, message: added.length ? `${added.length}개 재료를 장바구니에 담았어요.` : '이미 장바구니에 모두 담겨 있어요.' })
    } catch (error) { setState({ busy: false, message: '', error }) }
  }
  return <section className="stack" aria-label="부족한 재료">
    <h3>부족한 재료</h3>
    <ul className="missing-list">{missing.map((item) => <li key={item.name}><strong>{item.name}</strong> <span className="muted">{quantity(item.missingQuantity, item.unit)} 필요</span>
      {recipe.shoppingLinks.filter((link) => link.ingredient === item.name).map((link) => <a key={link.mall} className="shop-link" href={link.url} target="_blank" rel="noopener noreferrer">{link.mall}에서 찾기 <span aria-hidden="true">↗</span></a>)}
    </li>)}</ul>
    {recipe.shoppingLinks.length === 0 && <p className="helper">부족한 재료가 3종 이상이라 쇼핑몰 링크는 생략했어요. 장바구니에 담아 두고 한 번에 준비해 보세요.</p>}
    <div className="row-actions"><button className="button secondary" onClick={addToCart} disabled={state.busy}>{state.busy ? '담는 중…' : '🛒 부족한 재료 장바구니에 담기'}</button><Link className="button ghost" to="/shopping">장바구니 보기</Link></div>
    {state.message && <p className="success-box" role="status">{state.message}</p>}
    {state.error && <ErrorBox error={state.error} />}
  </section>
}
