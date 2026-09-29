export const categories = { VEGETABLE: '채소', FRUIT: '과일', MEAT: '육류', SEAFOOD: '수산물', DAIRY: '유제품', FROZEN: '냉동식품', SEASONING: '양념', GRAIN: '곡류', OTHER: '기타' }
export const units = { PIECE: '개', GRAM: 'g', KILOGRAM: 'kg', MILLILITER: 'ml', LITER: 'L', PACK: '팩', BAG: '봉', BLOCK: '모', BUNCH: '단' }
export const locations = { FRIDGE: '냉장', FREEZER: '냉동', PANTRY: '실온' }
export const statuses = { SAFE: '안전', SOON: '임박', EXPIRED: '만료', UNKNOWN: '기한 미등록' }
export const difficulties = { EASY: '쉬움', MEDIUM: '보통', HARD: '어려움' }
export const symbols = { VEGETABLE: '🥬', FRUIT: '🍎', MEAT: '🥩', SEAFOOD: '🐟', DAIRY: '🥛', FROZEN: '🧊', SEASONING: '🧂', GRAIN: '🌾', OTHER: '🥚' }

// ISO 날짜를 UTC로 해석하면 다른 시간대에서 하루가 바뀔 수 있어 서울 정오를 사용합니다.
export function formatDate(value, detailed = false) {
  if (!value) return '미등록'
  return new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', year: detailed ? 'numeric' : undefined, month: 'long', day: 'numeric', ...(detailed ? { weekday: 'long' } : {}) }).format(new Date(`${value}T12:00:00+09:00`))
}
export function todayInSeoul() {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date())
  const get = (name) => parts.find((part) => part.type === name).value
  return `${get('year')}-${get('month')}-${get('day')}`
}
export function quantity(value, unit) { return `${new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 3 }).format(value)}${units[unit] || unit}` }
export function daysLabel(days) { return days == null ? '사용 전 상태 확인' : days < 0 ? `${Math.abs(days)}일 지남` : days === 0 ? '오늘까지' : `${days}일 남음` }
