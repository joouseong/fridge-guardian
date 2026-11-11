import test from 'node:test'
import assert from 'node:assert/strict'
import { ingredientCatalog, ingredientSymbol, searchIngredients } from '../src/lib/ingredientCatalog.js'
import { formatDate, daysLabel } from '../src/lib/format.js'

test('aliases and whitespace select the canonical recipe ingredient', () => {
  assert.equal(searchIngredients(' 달 걀 ').items[0].name, '계란')
  assert.equal(searchIngredients('냉동만두').items[0].name, '만두')
  assert.equal(searchIngredients('양조간장').items[0].name, '간장')
})
test('typos offer candidates and unrelated names remain unmatched', () => {
  const result = searchIngredients('게란')
  assert.equal(result.approximate, true)
  assert.ok(result.items.some(item => item.name === '계란'))
  assert.deepEqual(searchIngredients('아주특별한나만의재료').items, [])
})
test('catalog defaults never fabricate expiry and have unique names', () => {
  assert.equal(new Set(ingredientCatalog.map(item => item.name)).size, ingredientCatalog.length)
  assert.ok(ingredientCatalog.every(item => item.quantity > 0 && !('expirationDate' in item)))
  assert.equal(ingredientCatalog.find(item => item.name === '두부').unit, 'BLOCK')
})
test('missing dates are not formatted as expired or zero days remaining', () => {
  assert.equal(formatDate(null), '미등록')
  assert.equal(daysLabel(null), '사용 전 상태 확인')
  assert.equal(daysLabel(0), '오늘까지')
})
test('ingredient icons: exact name, alias, keyword, then category fallback', () => {
  assert.equal(ingredientSymbol({ name: '계란' }), '🥚')
  assert.equal(ingredientSymbol({ name: '달걀' }), '🥚') // 카탈로그 별칭
  assert.equal(ingredientSymbol({ name: '냉동 삼겹살', category: 'MEAT' }), '🥓') // 이름 속 낱말
  assert.equal(ingredientSymbol({ name: '딸기잼' }), '🍓') // 먼저 맞는 낱말이 우선
  assert.equal(ingredientSymbol({ name: '김' }), '🍙') // 한 글자는 정확히 같은 이름만
  assert.equal(ingredientSymbol({ name: '김치' }), '🥬') // '김' 한 글자가 김치를 가로채지 않음
  assert.equal(ingredientSymbol({ name: '알 수 없는 재료', category: 'FRUIT' }), '🍎') // 카테고리
  assert.equal(ingredientSymbol({ name: '알 수 없는 재료', category: 'OTHER' }), '🧺') // 기타는 기본 바구니
})
