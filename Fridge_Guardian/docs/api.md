# REST API 계약

모든 경로는 `/api` 아래이며 JSON을 사용합니다(사진 업로드만 `multipart/form-data`). 사용자 ID는 요청 본문에서 받지 않고 **Access Token(JWT)의 `sub`**에서 확인합니다.
오류 응답은 항상 `{code, message, fieldErrors}`입니다. 검증 400 · 인증 401 · 권한/출처 403 · 없음 404 · 중복·수정 충돌 409 · 이미지 크기 413 · AI 요청 제한 429 · AI/외부 서비스 장애 503을 구분합니다.

## 인증 방식

| 토큰 | 유효 | 보관 | 사용 |
| --- | --- | --- | --- |
| Access Token (JWT, HS256) | 15분 | 브라우저 **메모**(localStorage 금지) | `Authorization: Bearer <token>` |
| Refresh Token (무작위 값) | 14일 | **HttpOnly · SameSite=Strict** 쿠키 `refresh_token` (경로 `/api/auth`), DB에는 SHA-256 해시만 | `/auth/refresh`로 새 Access Token 발급 |

- Refresh Token은 **한 번 쓰면 교체**(로테이션)됩니다. 이미 쓴 토큰이 10초 뒤에 다시 오면 탈취로 보고 그 로그인 세션 전체를 폐기합니다. 여러 탭이 동시에 갱신하는 경우를 위해 10초 안의 재사용은 거절만 합니다.
- 쿠키를 쓰는 `/auth/refresh`, `/auth/logout`은 `Origin` 헤더가 프런트엔드(또는 같은 호스트)가 아니면 403입니다.
- Bearer 토큰 API에는 CSRF 토큰이 없습니다(브라우저가 자동으로 붙이지 않는 헤더이므로).
- 소셜 로그인 성공 뒤에는 `FRONTEND_URL/oauth/callback`으로 이동하며 **주소에 토큰을 싣지 않습니다.** 화면이 `/auth/refresh`를 호출해 Access Token을 받아 갑니다.

## 인증·회원

| 메서드 | 경로 | 요청 → 응답 |
| --- | --- | --- |
| POST | /auth/register | `{email,password,passwordConfirm,nickname}` → 201 `{id,email,nickname,provider,profileImageUrl}`. 비밀번호 6자 이상(UTF-8 72바이트 이하)·확인 일치. 가입은 자동 로그인이 아님 |
| POST | /auth/login | `{email,password}` → `{accessToken,tokenType:"Bearer",expiresIn:900,user}` + Refresh 쿠키 |
| POST | /auth/refresh | (쿠키) → 위와 같은 응답 + 새 Refresh 쿠키. 만료·폐기 시 401 |
| POST | /auth/logout | (쿠키) → 204, 쿠키 삭제·로그인 세션 폐기 |
| GET | /auth/me | → `{id,email,nickname,provider,profileImageUrl}` |
| GET | /auth/providers | → `{google,kakao}` 키가 설정된 소셜 로그인만 true (인증 불필요) |
| GET | /oauth2/authorization/{google\|kakao} | 제공자 인증 화면으로 302. 등록되지 않은 제공자는 404 |
| GET | /login/oauth2/code/{id} | 제공자 콜백(브라우저 이동). 성공 → `FRONTEND_URL/oauth/callback`, 실패 → `FRONTEND_URL/login?error=oauth` |
| GET/PUT | /users/me/preferences | `{spicyLevel(0~3), allergies[], dislikes[]}`. PUT은 목록 전체 교체(각 30개·80자 이하). 기본값은 매운맛 3(제한 없음) |

## 식재료

| 메서드 | 경로 | 요청 → 응답 |
| --- | --- | --- |
| GET | /ingredients?sort=&q=&category=&storage=&status= | 식재료 배열. `sort`: expiration(기본)·category·storage. `q`: 이름 포함 검색. `status`: SOON·SAFE·EXPIRED·UNKNOWN·TODAY·USABLE(만료 제외)·ALL. 모두 선택 사항이며 조합 가능 |
| POST | /ingredients | 식재료 생성 → 201 |
| POST | /ingredients/batch | `{requestId(UUID),items:[식재료 요청]}` 1~50개 → 201 배열. 같은 requestId·내용 재전송은 최초 응답, 다른 내용은 409 `BATCH_REQUEST_CONFLICT` |
| GET/PUT/DELETE | /ingredients/{id} | 조회/수정/삭제. 타인 소유는 404. 수정에는 `version` 필수(불일치 409 `STALE_VERSION`). 삭제하면 **사진 파일도 삭제** |
| POST | /ingredients/{id}/image | `multipart` 필드 `file`(JPG·PNG·WEBP, **5MB 이하**, 내용의 매직 바이트로 판별) → 식재료. 기존 사진은 교체·삭제. 실패 시 400 `INVALID_IMAGE` / 413 `IMAGE_TOO_LARGE` |
| DELETE | /ingredients/{id}/image | 사진 삭제(기본 이미지로) → 식재료 |
| GET | /uploads/{uuid}.{jpg\|png\|webp} | 업로드한 사진(로그인 불필요, 추측할 수 없는 파일명) |
| GET | /barcodes/{code} | 숫자 6~14자리 → `{barcode,name,category,shelfLifeDays,suggestedExpirationDate,source}`. 저장된 결과 → 식품안전나라 API 순. 없으면 404 |
| GET | /dashboard | `{total,safeCount,soonCount,expiredCount,unknownCount,todayCount,expiringIngredients,expiredIngredients,nearestIngredients(최대 6),today}` |

식재료 요청: `name,category,quantity,unit,purchaseDate,expirationDate,storageType,version(수정 시),memo(500자),barcode(숫자 6~14)`.
응답에는 `id,version,status,daysUntilExpiration,imageUrl,memo,barcode`가 더해집니다. 날짜는 `YYYY-MM-DD`입니다.

- 카테고리: VEGETABLE 채소 · FRUIT 과일 · MEAT 육류 · SEAFOOD 수산물 · DAIRY 유제품 · GRAIN 곡류 · PROCESSED 가공식품 · SEASONING 조미료 · OTHER 기타
- 단위: PIECE 개 · PACK 팩 · BOTTLE 병 · BAG 봉 · GRAM g · KILOGRAM kg · MILLILITER ml · LITER L · BLOCK 모 · BUNCH 단
- 보관: FRIDGE 냉장 · FREEZER 냉동 · PANTRY 실온
- `expirationDate`는 선택 입력이며 null이면 `status=UNKNOWN`(기한 미등록)입니다. 날짜를 지어내지 않고 임박·만료 집계에도 넣지 않습니다.
- `status`: **SOON(임박) = 오늘~3일 뒤 / SAFE(여유) = 4일 이상 / EXPIRED(만료) = 오늘 이전**. 한국 시간(Asia/Seoul) 날짜 기준이며 **저장하지 않고 요청 때마다 계산**합니다. `daysUntilExpiration`이 D-Day(5 → D-5, 0 → D-0, -2 → D+2)입니다.

## 레시피 추천

| 메서드 | 경로 | 요청 → 응답 |
| --- | --- | --- |
| GET | /recipes/recommendations?servings=1 | 추천 배열(인분 1 또는 2). 만료 재료 제외, **내 알레르기·비선호·매운맛에 맞지 않는 레시피 제외**. 정렬: 일치율 ↓ → 임박 재료 활용 ↓ → 부족 종류 ↑ → 조리 시간 ↑ → id |
| GET | /recipes/{id}?servings=2 | 같은 형식의 상세. 개인화로 숨기지 않고 `preferenceConflicts`로 겹치는 재료를 알림 |

추천 항목: `id,name,description,cookingTime,difficulty,servings,requiredIngredients,availableIngredients,missingIngredients,urgentIngredients,matchedCount,missingCount,canCook,steps,nutrition,reason,`
**`matchRate`**(필요 재료 종류 중 보유 종류의 %) · **`best`**(목록 1위이면서 일치율 0 초과인 BEST MATCH) · `spicyLevel` · **`shoppingLinks`**(부족한 재료가 1~2개일 때 쿠팡·마켓컬리 검색 링크) · `preferenceConflicts`.
재료 상세: `name,requiredQuantity,availableQuantity,missingQuantity,unit,urgent,unitMismatch`. 영양: `calories,protein,carbs,fat,perServing=true,estimated=true`(1인분 추정값).
같은 재료는 수량을 합산하고 kg↔g, L↔ml만 환산합니다. 팩↔g처럼 환산할 수 없으면 추측하지 않고 `unitMismatch`로 알립니다.

## AI

| 메서드 | 경로 | 요청 → 응답 |
| --- | --- | --- |
| GET | /ai/status | `{available,model}`. 키 설정 여부만 뜻하며 연결 성공을 보장하지 않음 |
| POST | /ai/recipes | `{ingredientIds(1~12),servings(1|2),request?(200자)}` → 201 AI 레시피. 본인의 만료되지 않은 재료만 가능 |
| GET | /ai/recipes | 최근 30개 생성 이력 |
| PUT | /ai/recipes/{id}/favorite | `{favorite}` |
| DELETE | /ai/recipes/{id} | 204 |
| POST | /ai/chat | `{message,servings,history,mode(AUTO\|LOCAL)}` → `{reply,source,recommendedRecipes}` (재고 기반 대화형 도우미) |

AI 레시피 응답: `id,title,description,servings,cookingTimeMinutes,difficulty,ingredients[{name,quantity,unit,status}],missing[],steps[],tips,model,sourceIngredients[],shoppingLinks[],favorite,createdAt`.
`status`는 AI의 표기가 아니라 **서버가 선택한 재료와 비교**해 OWNED(선택한 보유 재료) · BASIC(소금·기름 같은 기본 양념, 집에 있는지 확인) · MISSING(없는 재료)로 다시 계산합니다.

- 서버는 AI 출력을 신뢰하지 않습니다: 스키마·길이·범위를 검증하고, 알레르기·비선호 재료가 재료 이름·제목·조리 순서에 들어 있으면 한 번 다시 만들고 그래도 어기면 503 `AI_CONFLICTS_WITH_PREFERENCES`로 거절합니다. 형식이 틀리면 503 `AI_INVALID_RESPONSE`.
- 같은 재료·수량·개인화 조건·모델이면 저장된 결과를 재사용하며 이때 AI 요청 한도를 쓰지 않습니다(캐시 키에 사용자 식별자 없음).
- 키가 없으면 503 `AI_NOT_CONFIGURED`. 실패를 성공한 AI 응답으로 위장하거나 자동으로 다른 방식으로 바꾸지 않습니다. 오류 코드: `AI_NOT_CONFIGURED`, `AI_CONFIGURATION_ERROR`, `AI_QUOTA_EXCEEDED`, `AI_RATE_LIMITED`, `AI_TIMEOUT`, `AI_UNAVAILABLE`, `AI_INCOMPLETE_RESPONSE`, `AI_EMPTY_RESPONSE`, `AI_REFUSED`.
- 요청 한도(429 `AI_REQUEST_LIMIT`·`AI_DAILY_LIMIT`·`AI_BUSY`·`AI_REQUEST_IN_PROGRESS`): 사용자별 60초 5회 · 서버 전체 60초 20회 · 한국 날짜 하루 100회 · 동시 2개(사용자당 1개). 단일 서버 메모리 기준이라 재시작하면 초기화됩니다.

## 장바구니

| 메서드 | 경로 | 요청 → 응답 |
| --- | --- | --- |
| GET | /shopping-items | 안 산 항목 먼저 |
| POST | /shopping-items | `{name,quantity?,unit?,recipeId?}` → 201 배열(이미 안 산 같은 재료가 있으면 빈 배열) |
| POST | /shopping-items/bulk | `{items:[…]}` 1~30개 |
| POST | /shopping-items/from-recipe | `{recipeId,servings}` — 내 재고 기준 **부족한 재료를 자동으로 담기** |
| PUT | /shopping-items/{id}/checked | `{checked}` |
| DELETE | /shopping-items/{id} · /shopping-items/checked | 한 개 삭제 · 산 항목 모두 삭제 |

## 알림

| 메서드 | 경로 | 요청 → 응답 |
| --- | --- | --- |
| GET | /notifications | `{unreadCount,items[{id,ingredientId,ingredientName,type(EXPIRING\|EXPIRED),notifyDate,status,createdAt}]}` 최근 50개 |
| PUT | /notifications/{id}/read · /notifications/read-all | 읽음 처리 |

알림은 **매일 자정(한국 시간) Spring Batch 배치**가 적재합니다: 그날 임박(오늘~3일 뒤)인 재료는 `EXPIRING`, 전날 만료된 재료는 `EXPIRED`. 같은 (재료, 종류, 날짜)는 한 번만 만들어지며 같은 날짜로 다시 실행해도 중복되지 않습니다. 알림 **발송(푸시·이메일)은 구현 범위 밖**이고 앱 안 알림함까지만 제공합니다.

## 기타

| 메서드 | 경로 | 응답 |
| --- | --- | --- |
| GET | /health | `{status:"UP",service:"fridge-guardian"}` (DB·외부 API 연결 진단은 아님) |
