const entries = [
  ['계란','OTHER',1,'PIECE','FRIDGE',['달걀']], ['두부','OTHER',1,'BLOCK','FRIDGE'],
  ['대파','VEGETABLE',1,'BUNCH','FRIDGE',['파']], ['양파','VEGETABLE',1,'PIECE','PANTRY'],
  ['김치','VEGETABLE',200,'GRAM','FRIDGE',['배추김치','신김치']], ['돼지고기','MEAT',200,'GRAM','FRIDGE'],
  ['소고기','MEAT',200,'GRAM','FRIDGE'], ['밥','GRAIN',200,'GRAM','FRIDGE',['쌀밥','흰쌀밥']],
  ['감자','VEGETABLE',300,'GRAM','PANTRY'], ['당근','VEGETABLE',100,'GRAM','FRIDGE'],
  ['우유','DAIRY',200,'MILLILITER','FRIDGE'], ['버섯','VEGETABLE',100,'GRAM','FRIDGE',['양송이버섯','표고버섯']],
  ['양배추','VEGETABLE',200,'GRAM','FRIDGE'], ['토마토','VEGETABLE',1,'PIECE','FRIDGE'],
  ['닭고기','MEAT',200,'GRAM','FRIDGE'], ['닭가슴살','MEAT',100,'GRAM','FRIDGE'],
  ['애호박','VEGETABLE',1,'PIECE','FRIDGE'], ['오이','VEGETABLE',1,'PIECE','FRIDGE'],
  ['마늘','VEGETABLE',50,'GRAM','FRIDGE'], ['시금치','VEGETABLE',1,'BUNCH','FRIDGE'],
  ['콩나물','VEGETABLE',200,'GRAM','FRIDGE'], ['참치','SEAFOOD',150,'GRAM','FRIDGE'],
  ['새우','SEAFOOD',200,'GRAM','FREEZER'], ['만두','FROZEN',8,'PIECE','FREEZER',['냉동만두']],
  ['파스타면','GRAIN',100,'GRAM','PANTRY'], ['요거트','DAIRY',150,'GRAM','FRIDGE',['플레인요거트']],
  ['치즈','DAIRY',100,'GRAM','FRIDGE'], ['바나나','FRUIT',1,'PIECE','PANTRY'],
  ['사과','FRUIT',1,'PIECE','FRIDGE'], ['견과류','OTHER',20,'GRAM','PANTRY'],
  ['간장','SEASONING',100,'MILLILITER','FRIDGE',['진간장','양조간장']], ['식용유','SEASONING',100,'MILLILITER','PANTRY'],
  ['소금','SEASONING',100,'GRAM','PANTRY'], ['고춧가루','SEASONING',50,'GRAM','FREEZER'],
  ['설탕','SEASONING',100,'GRAM','PANTRY'], ['고추장','SEASONING',100,'GRAM','FRIDGE'],
  ['된장','SEASONING',100,'GRAM','FRIDGE'], ['참기름','SEASONING',100,'MILLILITER','PANTRY'],
]
// 편집 가능한 시작값이며 실제 보유량이나 유통기한을 추정하지 않습니다.
export const ingredientCatalog = entries.map(([name, category, quantity, unit, storageType, aliases = []]) => ({ name, category, quantity, unit, storageType, aliases }))
const ingredientSymbols = { 계란: '🥚', 두부: '◻️', 대파: '🌱', 양파: '🧅', 김치: '🥬', 돼지고기: '🥩', 소고기: '🥩', 밥: '🍚', 감자: '🥔', 당근: '🥕', 우유: '🥛', 버섯: '🍄', 양배추: '🥬', 토마토: '🍅', 닭고기: '🍗', 닭가슴살: '🍗', 애호박: '🥒', 오이: '🥒', 마늘: '🧄', 시금치: '🥬', 콩나물: '🌱', 참치: '🐟', 새우: '🦐', 만두: '🥟', 파스타면: '🍝', 요거트: '🥣', 치즈: '🧀', 바나나: '🍌', 사과: '🍎', 견과류: '🥜', 간장: '🫙', 식용유: '🫙', 소금: '🧂', 고춧가루: '🌶️', 설탕: '🍬', 고추장: '🌶️', 된장: '🫘', 참기름: '🫙' }
export function ingredientSymbol(item) { return ingredientSymbols[item.name] || '🧺' }
const normalize = value => value.normalize('NFKC').replace(/\s+/gu, '').toLocaleLowerCase('ko')
export function findCatalogIngredient(name) {
  const text = normalize(name)
  return ingredientCatalog.find(item => [item.name, ...item.aliases].some(alias => normalize(alias) === text))
}
function distance(a, b) {
  let previous = Array.from({ length: b.length + 1 }, (_, index) => index)
  for (let i = 1; i <= a.length; i++) {
    const next = [i]
    for (let j = 1; j <= b.length; j++) next[j] = Math.min(next[j - 1] + 1, previous[j] + 1, previous[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1))
    previous = next
  }
  return previous[b.length]
}
// 비슷한 이름은 후보로만 제안하며 사용자가 직접 선택해야 합니다.
export function searchIngredients(query) {
  const text = normalize(query).slice(0, 80)
  const names = item => [item.name, ...item.aliases].map(normalize)
  const exact = ingredientCatalog.filter(item => names(item).some(name => name.includes(text)))
  if (exact.length || text.length < 2) return { items: exact, approximate: false }
  return { items: ingredientCatalog.filter(item => names(item).some(name => distance(text, name) <= 1)), approximate: true }
}
