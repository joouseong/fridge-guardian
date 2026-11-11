import { symbols as categorySymbols } from './format.js'

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
  ['새우','SEAFOOD',200,'GRAM','FREEZER'], ['만두','PROCESSED',8,'PIECE','FREEZER',['냉동만두']],
  ['파스타면','GRAIN',100,'GRAM','PANTRY'], ['요거트','DAIRY',150,'GRAM','FRIDGE',['플레인요거트']],
  ['치즈','DAIRY',100,'GRAM','FRIDGE'], ['바나나','FRUIT',1,'PIECE','PANTRY'],
  ['사과','FRUIT',1,'PIECE','FRIDGE'], ['견과류','OTHER',20,'GRAM','PANTRY'],
  ['간장','SEASONING',100,'MILLILITER','FRIDGE',['진간장','양조간장']], ['식용유','SEASONING',100,'MILLILITER','PANTRY'],
  ['소금','SEASONING',100,'GRAM','PANTRY'], ['고춧가루','SEASONING',50,'GRAM','FREEZER'],
  ['설탕','SEASONING',100,'GRAM','PANTRY'], ['고추장','SEASONING',100,'GRAM','FRIDGE'],
  ['된장','SEASONING',100,'GRAM','FRIDGE'], ['참기름','SEASONING',100,'MILLILITER','PANTRY'],
  ['양상추','VEGETABLE',100,'GRAM','FRIDGE'], ['마요네즈','SEASONING',100,'GRAM','FRIDGE'], ['김','SEAFOOD',1,'PACK','PANTRY'],
]
// 편집 가능한 시작값이며 실제 보유량이나 유통기한을 추정하지 않습니다.
export const ingredientCatalog = entries.map(([name, category, quantity, unit, storageType, aliases = []]) => ({ name, category, quantity, unit, storageType, aliases }))
const ingredientSymbols = { 계란: '🥚', 두부: '⬜', 대파: '🌱', 양파: '🧅', 김치: '🥬', 돼지고기: '🥩', 소고기: '🥩', 밥: '🍚', 감자: '🥔', 당근: '🥕', 우유: '🥛', 버섯: '🍄', 양배추: '🥬', 토마토: '🍅', 닭고기: '🍗', 닭가슴살: '🍗', 애호박: '🥒', 오이: '🥒', 마늘: '🧄', 시금치: '🥬', 콩나물: '🌱', 참치: '🐟', 새우: '🦐', 만두: '🥟', 파스타면: '🍝', 요거트: '🥣', 치즈: '🧀', 바나나: '🍌', 사과: '🍎', 견과류: '🥜', 간장: '🫙', 식용유: '🫙', 소금: '🧂', 고춧가루: '🌶️', 설탕: '🍬', 고추장: '🌶️', 된장: '🫘', 참기름: '🫙' }
// 이름에 이 낱말이 들어 있으면 그 아이콘을 씁니다. 위에 있을수록 먼저 맞춥니다. (한 글자 낱말은 오탐이 많아 정확히 같은 이름만 아래 exactSymbols에 둡니다.)
const keywordSymbols = [
  ['삼겹', '🥓'], ['베이컨', '🥓'], ['소시지', '🌭'], ['햄', '🥓'], ['스팸', '🥫'], ['통조림', '🥫'], ['닭', '🍗'], ['오리', '🍗'], ['소고기', '🥩'], ['돼지', '🥩'], ['한우', '🥩'], ['목살', '🥩'], ['갈비', '🥩'], ['불고기', '🥩'], ['고기', '🥩'],
  ['연어', '🍣'], ['오징어', '🦑'], ['문어', '🐙'], ['낙지', '🐙'], ['새우', '🦐'], ['꽃게', '🦀'], ['대게', '🦀'], ['조개', '🦪'], ['어묵', '🍢'], ['고등어', '🐟'], ['갈치', '🐟'], ['멸치', '🐟'], ['참치', '🐟'], ['생선', '🐟'], ['명태', '🐟'], ['동태', '🐟'],
  ['김치', '🥬'], ['배추', '🥬'], ['상추', '🥬'], ['양상추', '🥬'], ['시금치', '🥬'], ['샐러드', '🥗'], ['깻잎', '🌿'], ['부추', '🌿'], ['미나리', '🌿'], ['쑥갓', '🌿'], ['허브', '🌿'], ['바질', '🌿'], ['쪽파', '🌱'], ['대파', '🌱'], ['숙주', '🌱'], ['콩나물', '🌱'],
  ['파프리카', '🫑'], ['피망', '🫑'], ['고추', '🌶️'], ['브로콜리', '🥦'], ['콜리플라워', '🥦'], ['고구마', '🍠'], ['옥수수', '🌽'], ['가지', '🍆'], ['애호박', '🥒'], ['오이', '🥒'], ['호박', '🎃'], ['생강', '🫚'], ['마늘', '🧄'], ['양파', '🧅'], ['감자', '🥔'], ['당근', '🥕'], ['토마토', '🍅'], ['버섯', '🍄'], ['아보카도', '🥑'], ['올리브', '🫒'],
  ['딸기', '🍓'], ['포도', '🍇'], ['귤', '🍊'], ['오렌지', '🍊'], ['레몬', '🍋'], ['수박', '🍉'], ['참외', '🍈'], ['멜론', '🍈'], ['복숭아', '🍑'], ['자두', '🍑'], ['망고', '🥭'], ['파인애플', '🍍'], ['키위', '🥝'], ['체리', '🍒'], ['블루베리', '🫐'], ['바나나', '🍌'], ['사과', '🍎'],
  ['요거트', '🥣'], ['요구르트', '🥣'], ['시리얼', '🥣'], ['오트밀', '🥣'], ['두유', '🥛'], ['우유', '🥛'], ['크림', '🥛'], ['치즈', '🧀'], ['버터', '🧈'],
  ['식빵', '🍞'], ['빵', '🍞'], ['라면', '🍜'], ['국수', '🍜'], ['우동', '🍜'], ['파스타', '🍝'], ['스파게티', '🍝'], ['떡', '🍡'], ['쌀', '🍚'], ['밥', '🍚'], ['피자', '🍕'], ['햄버거', '🍔'], ['만두', '🥟'], ['카레', '🍛'],
  ['달걀', '🥚'], ['계란', '🥚'], ['메추리알', '🥚'], ['순두부', '⬜'], ['두부', '⬜'], ['콩', '🫘'], ['견과', '🥜'], ['땅콩', '🥜'], ['아몬드', '🥜'],
  ['소금', '🧂'], ['후추', '🧂'], ['설탕', '🍬'], ['꿀', '🍯'], ['케첩', '🍅'], ['식초', '🫙'], ['간장', '🫙'], ['기름', '🫙'], ['소스', '🫙'], ['잼', '🫙'], ['액젓', '🫙'], ['장', '🫙'],
  ['아이스크림', '🍨'], ['초콜릿', '🍫'], ['과자', '🍪'], ['쿠키', '🍪'], ['커피', '☕'], ['주스', '🧃'], ['콜라', '🥤'], ['사이다', '🥤'], ['맥주', '🍺'], ['와인', '🍷'], ['생수', '💧'],
]
const exactSymbols = { 김: '🍙', 무: '🥕', 배: '🍐', 감: '🍊', 파: '🌱', 물: '💧', 차: '🍵' }
// 추가 화면에서 보이던 아이콘을 목록·대시보드에서도 쓰기 위한 조회입니다: 같은 이름 → 이름이 들어간 낱말 → 카테고리 → 기본 바구니.
export function ingredientSymbol(item) {
  const name = (item.name || '').trim()
  const known = ingredientSymbols[name] || ingredientSymbols[findCatalogIngredient(name)?.name] || exactSymbols[name]
  if (known) return known
  const hit = keywordSymbols.find(([keyword]) => name.includes(keyword))
  return hit ? hit[1] : (item.category !== 'OTHER' && categorySymbols[item.category]) || '🧺'
}
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

// 아이콘 그림: Twemoji(CC-BY 4.0, https://github.com/jdecked/twemoji) SVG를 CDN에서 불러옵니다. 두부는 맞는 이모지가 없어 직접 그린 그림을 씁니다.
const TWEMOJI_BASE = 'https://cdn.jsdelivr.net/gh/jdecked/twemoji@15.1.0/assets/svg/'
const customIcons = { '⬜': '/icons/tofu.svg' }
export function ingredientIconSrc(item) {
  const symbol = ingredientSymbol(item)
  if (customIcons[symbol]) return customIcons[symbol]
  const points = [...symbol].map(char => char.codePointAt(0).toString(16))
  // Twemoji 파일 이름은 ZWJ 조합이 아니면 변형 선택자(fe0f)를 뺍니다.
  const name = (points.includes('200d') ? points : points.filter(point => point !== 'fe0f')).join('-')
  return `${TWEMOJI_BASE}${name}.svg`
}
