import test from 'node:test'
import assert from 'node:assert/strict'
import { batchPayload, ingredientDraft, parseIngredientList, validateDrafts } from '../src/lib/ingredientBatch.js'

const today = '2026-09-18'
test('shopping list accepts line breaks, Korean units, metric units and exact aliases', () => {
  const result = parseIngredientList('달걀 6개, 두부1모\n우유 1L; 돼지고기 0.5kg', today)
  assert.deepEqual(result.issues, [])
  assert.deepEqual(result.items.map(({ name, quantity, unit }) => [name, quantity, unit]), [
    ['계란', 6, 'PIECE'], ['두부', 1, 'BLOCK'], ['우유', 1, 'LITER'], ['돼지고기', 0.5, 'KILOGRAM'],
  ])
  assert.ok(result.items.every(item => item.expirationDate === '' && item.purchaseDate === today))
})
test('missing quantities use editable defaults and custom names remain visible', () => {
  const result = parseIngredientList('김치, 루콜라 30g', today)
  assert.equal(result.items[0].quantity, 200)
  assert.equal(result.items[1].name, '루콜라')
  assert.equal(result.items[1].unit, 'GRAM')
})
test('typos require confirmation and a mixed invalid list is never partially accepted', () => {
  const result = parseIngredientList('두부, 게란 6개', today)
  assert.deepEqual(result.items, [])
  assert.match(result.issues[0], /계란/)
})
test('invalid, unsupported, duplicate and excessive quantities or lists are rejected', () => {
  for (const text of ['계란 0개', '계란 -1개', '우유 1.0001L', '김치 1000000000g', '계란 1판', '달걀 2개, 계란 3개', Array(51).fill('두부').join(',')]) {
    const result = parseIngredientList(text, today)
    assert.equal(result.items.length, 0, text)
    assert.ok(result.issues.length > 0, text)
  }
})
test('batch payload contains only API fields and empty dates become null', () => {
  const draft = { ...ingredientDraft({ name: ' 두부 ' }, today), key: 'local-only' }
  assert.deepEqual(batchPayload([draft]), [{ name: '두부', category: 'OTHER', quantity: 1, unit: 'PIECE', storageType: 'FRIDGE', purchaseDate: today, expirationDate: null }])
})
test('draft validation catches hidden empty fields and invalid purchase or expiration order', () => {
  const good = ingredientDraft({ name: '두부' }, today)
  assert.equal(validateDrafts([good], today), null)
  for (const bad of [{ name: '' }, { quantity: '' }, { quantity: 0 }, { quantity: 1.2345 }, { purchaseDate: '2026-09-19' }, { expirationDate: '2026-09-17' }]) {
    assert.ok(validateDrafts([{ ...good, ...bad }], today))
  }
})
