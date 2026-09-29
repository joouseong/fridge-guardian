// PRD 카테고리: 채소, 과일, 육류, 수산물, 유제품, 곡류, 가공식품, 조미료, 기타
export const categories = { VEGETABLE: '채소', FRUIT: '과일', MEAT: '육류', SEAFOOD: '수산물', DAIRY: '유제품', GRAIN: '곡류', PROCESSED: '가공식품', SEASONING: '조미료', OTHER: '기타' }
// PRD 단위: 개, 팩, 병, 봉, g, kg, ml, L (모·단은 두부·대파 같은 재료용으로 함께 지원)
export const units = { PIECE: '개', PACK: '팩', BOTTLE: '병', BAG: '봉', GRAM: 'g', KILOGRAM: 'kg', MILLILITER: 'ml', LITER: 'L', BLOCK: '모', BUNCH: '단' }
export const locations = { FRIDGE: '냉장', FREEZER: '냉동', PANTRY: '실온' }
export const statuses = { SAFE: '여유', SOON: '임박', EXPIRED: '만료', UNKNOWN: '기한 미등록' }
export const difficulties = { EASY: '쉬움', MEDIUM: '보통', HARD: '어려움' }
export const symbols = { VEGETABLE: '🥬', FRUIT: '🍎', MEAT: '🥩', SEAFOOD: '🐟', DAIRY: '🥛', GRAIN: '🌾', PROCESSED: '🥫', SEASONING: '🧂', OTHER: '🥚' }
// 먹을 수 있는 최대 매운맛(0~3). 서버의 recipes.spicy_level과 같은 기준입니다.
export const spicyLevels = { 0: '순한 맛만', 1: '약간 매운 맛까지', 2: '보통 매운 맛까지', 3: '아주 매운 맛도 좋아요' }
export const spicyLabels = { 0: '순한 맛', 1: '약간 매움', 2: '매움', 3: '아주 매움' }
// 사진이 없는 식재료에 보여 주는 기본 이미지
export const DEFAULT_IMAGE = '/default-ingredient.svg'

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
// D-5, D-1, D-0, 지난 뒤에는 D+2. 유통기한이 없으면 빈 문자열입니다.
export function ddayText(days) { return days == null ? '' : days > 0 ? `D-${days}` : days === 0 ? 'D-0' : `D+${Math.abs(days)}` }
