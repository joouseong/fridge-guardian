# REST API 계약

모든 경로는 /api 아래이며 JSON을 사용합니다. 사용자 ID는 요청 본문에서 받지 않고 인증 세션에서 확인합니다.
세션 쿠키는 HttpOnly입니다. POST/PUT/DELETE 요청은 먼저 GET /api/auth/csrf의 token을 headerName 헤더로 전달합니다.
로그인·로그아웃 후 토큰을 다시 발급받습니다. 프론트엔드 개발 서버의 /api 프록시를 통해 같은 출처로 요청합니다.

| 메서드 | 경로 | 요청 / 응답 |
| --- | --- | --- |
| GET | /auth/csrf | {token,headerName} |
| POST | /auth/register | {email,password,nickname} → 201 {id,email,nickname} |
| POST | /auth/login | {email,password} → {id,email,nickname} |
| GET | /auth/me | {id,email,nickname} 또는 401 |
| POST | /auth/logout | 204 |
| GET | /ingredients?sort=expiration | 식재료 배열. sort: expiration/category/storage |
| POST | /ingredients | 식재료 생성 → 201 식재료 |
| POST | /ingredients/batch | {requestId,items:[식재료 요청]} → 201 식재료 배열, 한 번에 1~50개 |
| GET/PUT/DELETE | /ingredients/{id} | 조회/수정/삭제. 타인 소유 ID는 404 |
| GET | /dashboard | {total,safeCount,soonCount,expiredCount,unknownCount,todayCount,expiringIngredients,expiredIngredients,today} |
| GET | /recipes/recommendations?servings=1 | 추천 배열. 인분은 1 또는 2 |
| GET | /recipes/{id}?servings=2 | 추천과 같은 형식의 상세 |
| GET | /ai/status | {available,model}. model은 키가 없으면 null, 기본 gpt-5-mini. 실제 연결 성공을 보장하지 않음 |
| POST | /ai/chat | {message,servings,history:[{role,content}],mode} → {reply,source,recommendedRecipes}. 추천 최대 3개 |

식재료 요청: name,category,quantity,unit,purchaseDate,expirationDate,storageType. 수정에는 version 필수.
응답에는 id,version,status,daysUntilExpiration이 추가됩니다. 날짜는 YYYY-MM-DD입니다.
expirationDate는 선택 입력이며 생략 또는 null이면 미등록입니다(빈 문자열 대신 null을 전송하세요). 미등록 응답은 expirationDate=null, daysUntilExpiration=null, status=UNKNOWN입니다. 유통기한 정렬에서 미등록은 마지막에 옵니다. 날짜를 입력한 경우 기존 구매 날짜 이후 검증을 유지합니다. PUT에서 null/생략하면 기존 기한을 제거합니다.
status: SAFE/SOON/EXPIRED/UNKNOWN. dashboard의 unknownCount는 UNKNOWN 건수이고 safeCount에 섞지 않습니다. 기한 미등록 재료는 추천 수량 계산에는 포함하되 임박으로 처리하지 않습니다. 단위 및 카테고리는 [DB 설계](../database/README.md)를 참고하세요.

추천: id,name,description,cookingTime,difficulty,servings,requiredIngredients,availableIngredients,missingIngredients,urgentIngredients,matchedCount,missingCount,canCook,steps,nutrition,reason.
재료 상세: name,requiredQuantity,availableQuantity,missingQuantity,unit,urgent,unitMismatch.
영양: calories,protein,carbs,fat,perServing=true,estimated=true. 모든 영양값은 1인분당 추정치입니다.
source는 OPENAI 또는 LOCAL이며 LOCAL은 API 키가 없거나 mode=LOCAL을 명시한 경우의 규칙 기반 추천입니다.
mode는 AUTO 또는 LOCAL입니다. 생략·null은 AUTO이며 그 외 값은 400입니다. AUTO는 키가 있을 때만 AI를 호출합니다.

채팅 API는 모두 인증 세션이 필요하며 POST에는 CSRF 토큰이 필요합니다.
message는 공백만으로 구성될 수 없고 최대 2,000자, servings는 1 또는 2입니다.
history는 최대 10개이며 생략·null은 빈 목록으로 처리합니다. 각 항목의 role은 user 또는 assistant이고,
content는 공백만으로 구성될 수 없는 최대 2,000자의 문자열이어야 합니다. null 항목은 허용하지 않습니다.
recommendedRecipes는 서버가 현재 사용자 재고로 계산한 위 추천 형식의 배열이며 최대 3개, 해당 메뉴가 없으면 빈 배열입니다.
OPENAI 모드에서는 서버가 계산한 후보 최대 40개 중 AI가 현재 질문·이전 대화 조건에 맞춰 고른 ID(최대 3개)에 연결되는 카드를 반환합니다. ID가 후보 밖에 있거나 중복되면 AI_INVALID_RESPONSE로 거절합니다. 재료 수량·단위·조리법은 서버 데이터 그대로이며 AI가 덮어쓰지 않습니다. 일반 질문은 카드가 비어 있을 수 있습니다. LOCAL은 기존 키워드 기준의 기본 추천입니다.
available은 비어 있지 않은 API 키 설정 여부만 표시하며 키 검증이나 외부 API 사전 호출은 하지 않습니다.
OpenAI 호출 실패 시 자동 LOCAL 전환 없이 503 오류를 반환합니다. 조건 처리 범위와 오류 코드는 [STEP 10](step-10.md)을 참고하세요.
클라이언트가 같은 질문을 mode=LOCAL로 다시 요청하면 외부 호출 없이 기본 추천을 받을 수 있습니다.
AI 요청 횟수·동시 요청 제한은 429(AI_REQUEST_LIMIT, AI_DAILY_LIMIT, AI_BUSY, AI_REQUEST_IN_PROGRESS)입니다.
기본 제한은 사용자별 최근 60초 5회·서버 전체 최근 60초 20회·한국 날짜 하루 100회·서버 동시 2개·사용자 동시 1개입니다. 실패한 외부 호출도 집계하며 서버 재시작 시 초기화됩니다. LOCAL은 이 제한을 사용하지 않습니다.

## 식재료 일괄 등록

`POST /api/ingredients/batch`는 로그인 세션과 CSRF 토큰이 필요합니다. `requestId`는 클라이언트가 생성하는 필수 UUID이고 `items`는 null이 아닌 식재료 요청 1~50개입니다. 각 항목에 단건 등록과 같은 수량·날짜·분류 검증을 적용하며, 유통기한은 null/생략 가능합니다. 구매일·수량 기본값은 화면에서 채워 전달합니다. 서버가 요청자 이외의 사용자 ID를 받지 않습니다.

```json
{
  "requestId": "78c996fb-f920-4466-930c-b8fdbe40a6ef",
  "items": [
    {"name":"계란","category":"OTHER","quantity":6,"unit":"PIECE","purchaseDate":"2026-09-18","expirationDate":null,"storageType":"FRIDGE"},
    {"name":"두부","category":"OTHER","quantity":1,"unit":"BLOCK","purchaseDate":"2026-09-18","expirationDate":null,"storageType":"FRIDGE"}
  ]
}
```

예시 구매 날짜는 실행일에 맞춥니다. 응답은 요청 순서의 `IngredientResponse[]`입니다. 검증 오류가 있으면 전체를 저장하지 않고 400을 반환합니다. 행별 오류 키는 `items[0].quantity`, `items[1].expirationDate`처럼 인덱스를 포함합니다.

동일 사용자·requestId·items 재전송은 최초 완료 응답과 같은 201을 반환하며 새 재고를 만들지 않습니다. 같은 번호에 다른 내용을 보내면 409 `BATCH_REQUEST_CONFLICT`입니다. 요청 번호는 사용자별로 분리되고, 완료 기록은 V4의 `ingredient_batch_requests`에 재고와 함께 커밋됩니다. 사용자 행 잠금으로 동시 재시도도 직렬화합니다. 번호가 새로우면 같은 이름의 재료도 별도 구매 건으로 등록합니다.

재전송 응답은 **등록 당시의 스냅샷**입니다. 이후 수정·삭제한 재고를 되돌리거나 재생성하지 않으며, 최신 상태는 GET 목록으로 확인합니다. 현재 완료 기록은 자동 만료하지 않습니다. 운영 시 저장량·보관 기간을 고려한 정리 정책은 후속 과제입니다.

오류 응답: {code,message,fieldErrors}. 검증400, 인증401, CSRF403, 없음404, 중복·수정충돌409, AI 요청 제한429, AI 서비스 장애503을 구분합니다.
