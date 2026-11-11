import { DEFAULT_IMAGE, formatDate, locations, quantity } from '../lib/format.js'
import { ExpiryBadge } from './UI.jsx'

// 대시보드용 카드: 사진(없으면 기본 이미지)·수량·보관 위치·유통기한·D-Day
export default function IngredientCard({ item }) {
  return <li className="ingredient-card">
    <div className="ingredient-photo-wrap">
      <img className="ingredient-photo" src={item.imageUrl || DEFAULT_IMAGE} alt="" loading="lazy" width="96" height="96" />
      {item.imageAttributionUrl && <a className="ingredient-image-attribution" href={item.imageAttributionUrl} target="_blank" rel="noreferrer">출처</a>}
    </div>
    <div className="ingredient-card-body">
      <strong>{item.name}</strong>
      <p className="muted">{quantity(item.quantity, item.unit)} · {locations[item.storageType]}</p>
      <p className="helper">유통기한 {formatDate(item.expirationDate)}</p>
    </div>
    <ExpiryBadge status={item.status} days={item.daysUntilExpiration} />
  </li>
}
