import assert from 'node:assert/strict'
import { test } from 'node:test'
import { categories, ddayText, quantity, spicyLevels, statuses, units } from '../src/lib/format.js'
import { MAX_IMAGE_BYTES, validateImage } from '../src/lib/image.js'
import { parseIngredientList } from '../src/lib/ingredientBatch.js'

test('D-Day text follows the PRD examples', () => {
  assert.equal(ddayText(5), 'D-5')
  assert.equal(ddayText(1), 'D-1')
  assert.equal(ddayText(0), 'D-0')
  assert.equal(ddayText(-2), 'D+2')
  assert.equal(ddayText(null), '')
})

test('labels match the PRD categories, units, and expiry states', () => {
  assert.deepEqual(Object.values(categories), ['채소', '과일', '육류', '수산물', '유제품', '곡류', '가공식품', '조미료', '기타'])
  for (const unit of ['개', '팩', '병', '봉', 'g', 'kg', 'ml', 'L']) assert.ok(Object.values(units).includes(unit), unit)
  assert.equal(statuses.SOON, '임박')
  assert.equal(statuses.SAFE, '여유')
  assert.equal(statuses.EXPIRED, '만료')
  assert.equal(quantity(1500, 'GRAM'), '1,500g')
  assert.equal(quantity(2, 'BOTTLE'), '2병')
  assert.deepEqual(Object.keys(spicyLevels), ['0', '1', '2', '3'])
})

test('image validation mirrors the server limits (5MB, JPG/PNG/WEBP)', () => {
  const image = (type, size) => ({ type, size })
  assert.equal(validateImage(image('image/png', 1024)), '')
  assert.equal(validateImage(image('image/jpeg', MAX_IMAGE_BYTES)), '')
  assert.equal(validateImage(image('image/webp', 10)), '')
  assert.match(validateImage(image('image/png', MAX_IMAGE_BYTES + 1)), /5MB/)
  assert.match(validateImage(image('image/gif', 10)), /JPG, PNG, WEBP/)
  assert.match(validateImage(image('application/pdf', 10)), /JPG, PNG, WEBP/)
})

test('pasted shopping lists understand the bottle unit', () => {
  const result = parseIngredientList('우유 2병', '2026-09-28')
  assert.deepEqual(result.issues, [])
  assert.equal(result.items[0].unit, 'BOTTLE')
  assert.equal(result.items[0].quantity, 2)
})
