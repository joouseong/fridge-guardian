import test from 'node:test'
import assert from 'node:assert/strict'
import { ingredientCatalog, searchIngredients } from '../src/lib/ingredientCatalog.js'
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
