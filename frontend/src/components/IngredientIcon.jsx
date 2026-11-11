import { useState } from 'react'
import { ingredientIconSrc, ingredientSymbol } from '../lib/ingredientCatalog.js'

// 재료 이름에 맞는 아이콘(Twemoji SVG). 그림을 불러오지 못하면 같은 이모지 글자로 대신 보여 줍니다.
export default function IngredientIcon({ item, className = '' }) {
  const src = ingredientIconSrc(item)
  const [failedSrc, setFailedSrc] = useState(null)
  return <span className={`ingredient-icon ${className}`} aria-hidden="true">
    {failedSrc === src ? ingredientSymbol(item) : <img src={src} alt="" loading="lazy" decoding="async" draggable="false" onError={() => setFailedSrc(src)} />}
  </span>
}
