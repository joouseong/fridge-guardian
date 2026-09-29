import { findCatalogIngredient, searchIngredients } from './ingredientCatalog.js'

export const MAX_BATCH_SIZE = 50
const unitNames = { 개: 'PIECE', 모: 'BLOCK', 단: 'BUNCH', 팩: 'PACK', 봉: 'BAG', g: 'GRAM', kg: 'KILOGRAM', ml: 'MILLILITER', l: 'LITER' }
const amountPattern = /^(.*?)\s*([+-]?\d+(?:\.\d+)?)\s*(kg|ml|g|l|개|모|단|팩|봉)$/iu
const bareAmountPattern = /^(.*?)\s+([+-]?\d+(?:\.\d+)?)$/u

export function ingredientDraft(item, today) {
  return { name: item.name, category: item.category || 'OTHER', quantity: item.quantity ?? 1,
    unit: item.unit || 'PIECE', storageType: item.storageType || 'FRIDGE', purchaseDate: today, expirationDate: '' }
}

// Parse only explicit units and exact aliases. Typos are suggested for confirmation, never silently changed.
export function parseIngredientList(text, today) {
  const lines = text.normalize('NFKC').split(/[,，;\n]+/u).map(line => line.trim()).filter(Boolean)
  const issues = [], items = [], seen = new Set()
  if (!lines.length) return { items, issues: ['추가할 재료를 입력해 주세요.'] }
  if (text.length > 4000 || lines.length > MAX_BATCH_SIZE) return { items, issues: ['한 번에 50개, 4,000자 이내로 입력해 주세요.'] }
  for (const line of lines) {
    const match = line.match(amountPattern) || line.match(bareAmountPattern)
    const name = (match ? match[1] : line).trim()
    const known = findCatalogIngredient(name)
    if (!name || name.length > 80 || (!match && /\d/u.test(name))) {
      issues.push(`‘${line}’의 이름·수량을 확인해 주세요. 예: 계란 6개, 우유 1L`); continue
    }
    if (!known) {
      const suggestions = searchIngredients(name)
      if (suggestions.approximate && suggestions.items.length) {
        issues.push(`‘${name}’과 비슷한 재료: ${suggestions.items.map(item => item.name).join(', ')}. 이름을 확인해 주세요.`); continue
      }
    }
    const draft = ingredientDraft(known || { name }, today)
    if (match) {
      draft.quantity = Number(match[2])
      if (match[3]) draft.unit = unitNames[match[3].toLowerCase()]
    }
    if (!Number.isFinite(draft.quantity) || draft.quantity < 0.001 || draft.quantity > 999999999 ||
      (match && (match[2].split('.')[1]?.length || 0) > 3)) {
      issues.push(`‘${line}’의 수량은 0.001~999999999, 소수 3자리까지 입력해 주세요.`); continue
    }
    if (seen.has(draft.name)) { issues.push(`‘${draft.name}’이 두 번 있어요. 수량을 합쳐 한 번만 입력해 주세요.`); continue }
    seen.add(draft.name)
    items.push(draft)
  }
  return { items: issues.length ? [] : items, issues }
}

export function batchPayload(drafts) {
  return drafts.map(({ name, category, quantity, unit, storageType, purchaseDate, expirationDate }) => ({
    name: name.trim(), category, quantity: Number(quantity), unit, storageType, purchaseDate, expirationDate: expirationDate || null,
  }))
}

export function validateDrafts(drafts, today) {
  if (!drafts.length || drafts.length > MAX_BATCH_SIZE) return '재료를 1~50개 선택해 주세요.'
  for (const [index, item] of drafts.entries()) {
    const label = item.name.trim() || `${index + 1}번째 재료`
    if (!item.name.trim() || item.name.trim().length > 80) return `${index + 1}번째 재료의 이름을 1~80자로 입력해 주세요.`
    if (!/^\d+(?:\.\d{1,3})?$/u.test(String(item.quantity)) || Number(item.quantity) < 0.001 || Number(item.quantity) > 999999999) return `${label}의 수량을 확인해 주세요. (0.001 이상, 소수 3자리까지)`
    if (!/^\d{4}-\d{2}-\d{2}$/u.test(item.purchaseDate) || item.purchaseDate < '1000-01-01' || item.purchaseDate > today) return `${label}의 구매 날짜를 확인해 주세요.`
    if (item.expirationDate && (!/^\d{4}-\d{2}-\d{2}$/u.test(item.expirationDate) || item.expirationDate < item.purchaseDate || item.expirationDate > '9999-12-31')) return `${label}의 유통기한은 구매 날짜와 같거나 이후여야 해요.`
  }
  return null
}
