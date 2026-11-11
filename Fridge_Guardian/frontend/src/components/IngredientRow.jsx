import { DEFAULT_IMAGE, categories, daysLabel, formatDate, locations, quantity } from '../lib/format.js'
import { ExpiryBadge } from './UI.jsx'

export default function IngredientRow({ item, onEdit, onDelete }) {
  return <li className="ingredient-row">
    <div className="ingredient-thumb-wrap">
      <img className="ingredient-thumb" src={item.imageUrl || DEFAULT_IMAGE} alt="" loading="lazy" width="52" height="52" />
      {item.imageAttributionUrl && <a className="ingredient-image-attribution" href={item.imageAttributionUrl} target="_blank" rel="noreferrer">출처</a>}
    </div>
    <div className="ingredient-summary"><strong>{item.name}</strong><p className="muted">{categories[item.category]} · {quantity(item.quantity, item.unit)} · {locations[item.storageType]}</p><p className="helper">구매 {formatDate(item.purchaseDate)} · 유통기한 {formatDate(item.expirationDate)}</p>{item.memo && <p className="helper ingredient-memo">📝 {item.memo}</p>}</div>
    <div className="ingredient-expiry"><ExpiryBadge status={item.status} days={item.daysUntilExpiration} /><p className="helper">{daysLabel(item.daysUntilExpiration)}</p></div>
    {onEdit && <div className="row-actions"><button className="button secondary small" aria-label={`${item.name} 수정`} onClick={() => onEdit(item)}>수정</button><button className="button ghost small" aria-label={`${item.name} 삭제`} onClick={() => onDelete(item)}>삭제</button></div>}
  </li>
}
