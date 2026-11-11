import { useMemo, useState } from 'react'
import { formatDate, locations, quantity } from '../lib/format.js'
import IngredientIcon from './IngredientIcon.jsx'
import { ExpiryBadge } from './UI.jsx'

const weekdays = ['일', '월', '화', '수', '목', '금', '토']
const pad = (value) => String(value).padStart(2, '0')
const dayKey = (year, month, day) => `${year}-${pad(month + 1)}-${pad(day)}`
const tone = (status) => (status === 'EXPIRED' ? 'expired' : status === 'SOON' ? 'soon' : 'safe')

// 유통기한이 있는 재료를 달력에 점으로 보여 주고, 날짜를 누르면 그날 만료되는 재료를 보여 줍니다.
// 오늘 날짜는 서버가 준 값(서울 기준)을 쓰고, 상태·D-Day도 서버 계산 값을 그대로 씁니다.
export default function ExpiryCalendar({ today, items }) {
  const [year, setYear] = useState(Number(today.slice(0, 4)))
  const [month, setMonth] = useState(Number(today.slice(5, 7)) - 1)
  const [selected, setSelected] = useState(today)
  const byDate = useMemo(() => {
    const map = new Map()
    for (const item of items) {
      if (!item.expirationDate) continue
      if (!map.has(item.expirationDate)) map.set(item.expirationDate, [])
      map.get(item.expirationDate).push(item)
    }
    return map
  }, [items])
  // UTC로 계산하면 사용자 시간대와 상관없이 요일이 어긋나지 않습니다.
  const firstWeekday = new Date(Date.UTC(year, month, 1)).getUTCDay()
  const daysInMonth = new Date(Date.UTC(year, month + 1, 0)).getUTCDate()
  const cells = [...Array(firstWeekday).fill(null), ...Array.from({ length: daysInMonth }, (_, index) => index + 1)]
  const monthTotal = [...byDate].filter(([date]) => date.startsWith(`${year}-${pad(month + 1)}-`)).reduce((sum, [, list]) => sum + list.length, 0)
  const selectedItems = byDate.get(selected) || []
  function shift(delta) {
    const next = new Date(Date.UTC(year, month + delta, 1))
    setYear(next.getUTCFullYear())
    setMonth(next.getUTCMonth())
  }
  function goToday() {
    setYear(Number(today.slice(0, 4)))
    setMonth(Number(today.slice(5, 7)) - 1)
    setSelected(today)
  }
  return <div className="expiry-calendar">
    <div className="calendar-main">
      <div className="calendar-head">
        <div><strong className="calendar-title">{year}년 {month + 1}월</strong><p className="helper">이 달에 유통기한이 있는 재료 {monthTotal}개</p></div>
        <div className="calendar-nav">
          <button type="button" className="icon-button" aria-label="이전 달" onClick={() => shift(-1)}>‹</button>
          <button type="button" className="button ghost small" onClick={goToday}>오늘</button>
          <button type="button" className="icon-button" aria-label="다음 달" onClick={() => shift(1)}>›</button>
        </div>
      </div>
      <div className="calendar-grid" role="group" aria-label={`${year}년 ${month + 1}월 유통기한 달력`}>
        {weekdays.map((name) => <span className="calendar-weekday" key={name} aria-hidden="true">{name}</span>)}
        {cells.map((day, index) => {
          if (day === null) return <span key={`blank-${index}`} aria-hidden="true" />
          const key = dayKey(year, month, day)
          const list = byDate.get(key) || []
          return <button type="button" key={key} className={`calendar-day ${list.length ? 'has-items' : 'empty'} ${key === today ? 'today' : ''} ${key === selected ? 'selected' : ''}`} aria-pressed={key === selected} aria-current={key === today ? 'date' : undefined}
            aria-label={`${month + 1}월 ${day}일${list.length ? `, 유통기한 재료 ${list.length}개` : ''}`} onClick={() => setSelected(key)}>
            <span className="calendar-date">{day}</span>
            {list.length > 0 && <span className="calendar-dots" aria-hidden="true">{list.slice(0, 3).map((item) => <i key={item.id} className={tone(item.status)} />)}{list.length > 3 && <b>+{list.length - 3}</b>}</span>}
          </button>
        })}
      </div>
    </div>
    <div className="calendar-side" aria-live="polite">
      <p className="eyebrow">{selected === today ? 'TODAY' : 'SELECTED'}</p>
      <h3>{formatDate(selected, true)}</h3>
      {selectedItems.length
        ? <ul className="calendar-items">{selectedItems.map((item) => <li className="cal-item" key={item.id}>
          {item.imageUrl ? <img src={item.imageUrl} alt="" width="44" height="44" loading="lazy" /> : <IngredientIcon className="ingredient-emoji" item={item} />}
          <div><strong>{item.name}</strong><p className="helper">{quantity(item.quantity, item.unit)} · {locations[item.storageType]}</p></div>
          <ExpiryBadge status={item.status} days={item.daysUntilExpiration} />
        </li>)}</ul>
        : <p className="muted calendar-empty">이 날 유통기한이 끝나는 재료가 없어요.</p>}
    </div>
  </div>
}
