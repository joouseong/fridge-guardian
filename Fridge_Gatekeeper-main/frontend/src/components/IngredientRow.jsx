import { categories, daysLabel, formatDate, locations, quantity, symbols } from '../lib/format.js'
import { StatusBadge } from './UI.jsx'

export default function IngredientRow({ item, onEdit, onDelete }) {
  return <li className="ingredient-row">
    <span className="ingredient-symbol" aria-hidden="true">{symbols[item.category] || '🥬'}</span>
    <div className="ingredient-summary"><strong>{item.name}</strong><p className="muted">{categories[item.category]} · {quantity(item.quantity, item.unit)} · {locations[item.storageType]}</p><p className="helper">구매 {formatDate(item.purchaseDate)} · 유통기한 {formatDate(item.expirationDate)}</p></div>
    <div className="ingredient-expiry"><StatusBadge status={item.status} /><p className="helper">{daysLabel(item.daysUntilExpiration)}</p></div>
    {onEdit && <div className="row-actions"><button className="button secondary small" aria-label={`${item.name} 수정`} onClick={() => onEdit(item)}>수정</button><button className="button ghost small" aria-label={`${item.name} 삭제`} onClick={() => onDelete(item)}>삭제</button></div>}
  </li>
}
