# STEP 10 — 냉장고 기반 채팅과 OpenAI 연결

## 1. 구현 목적

로그인한 사용자의 최신 냉장고 재료를 바탕으로 메뉴를 추천합니다. OpenAI 키가 없으면 외부 요청 없이 기본 추천을 제공하고, 키가 설정되어 있으면 서버에서 Responses API를 호출합니다. 화면에는 답변마다 `LOCAL` 또는 `OPENAI` 출처를 표시합니다.

## 2. 파일과 전체 코드

- [ChatController.java](../backend/src/main/java/com/fridgegatekeeper/chat/ChatController.java): 인증 세션을 사용하는 상태·채팅 API
- [ChatDtos.java](../backend/src/main/java/com/fridgegatekeeper/chat/ChatDtos.java): 요청 검증과 응답 필드
- [ChatService.java](../backend/src/main/java/com/fridgegatekeeper/chat/ChatService.java): 최신 재고 조회, 메뉴 선택, 기본 추천 답변
- [OpenAiClient.java](../backend/src/main/java/com/fridgegatekeeper/chat/OpenAiClient.java): Responses API 요청, 시간 제한, 응답·오류 처리
- [RecommendationService.java](../backend/src/main/java/com/fridgegatekeeper/recommendation/RecommendationService.java): 사용자별 재료 수량·임박·부족 계산
- [application.yml](../backend/src/main/resources/application.yml): OpenAI 연결 설정
- [ChatPage.jsx](../frontend/src/pages/ChatPage.jsx): 질문·대화·추천 카드·재시도 화면
- [ChatServiceTest.java](../backend/src/test/java/com/fridgegatekeeper/chat/ChatServiceTest.java): 기본 추천과 출처 구분 검증
- [OpenAiClientTest.java](../backend/src/test/java/com/fridgegatekeeper/chat/OpenAiClientTest.java): 로컬 HTTP 서버로 외부 API 계약·장애 검증
- [ChatIntegrationTest.java](../backend/src/test/java/com/fridgegatekeeper/chat/ChatIntegrationTest.java): 보안 필터·세션·입력 검증·사용자별 재고 격리 검증

## 3. 코드 설명

### 요청과 인증

`GET /api/ai/status`는 `{ "available": false, "model": null }` 또는 `{ "available": true, "model": "gpt-5-mini" }`를 반환합니다. `available`은 **서버에 비어 있지 않은 API 키가 설정되어 있는지**를 뜻합니다. 키 유효성, 모델 접근 권한, 잔액이나 실제 연결 성공 여부를 미리 검사하지 않으며, 이 조회로 외부 API를 호출하지 않습니다.

`POST /api/ai/chat`의 요청 예시는 다음과 같습니다.

```json
{
  "message": "임박한 재료로 간단한 요리를 추천해 줘",
  "servings": 2,
  "mode": "AUTO",
  "history": [
    { "role": "user", "content": "오늘은 집에서 요리할 거야" },
    { "role": "assistant", "content": "냉장고 재료를 활용해 볼게요." }
  ]
}
```

`message`는 공백만으로 구성될 수 없고 최대 2,000자입니다. `servings`는 1 또는 2여야 합니다. `history`는 생략하거나 `null`로 보내면 빈 목록으로 처리하며, 최대 10개까지 받습니다. 각 항목은 `user` 또는 `assistant` 역할과 공백만으로 구성되지 않은 최대 2,000자의 `content`를 갖습니다. `system`·`developer` 역할과 `null` 항목은 거절합니다.

두 API 모두 로그인이 필요하며 POST 요청에는 CSRF 토큰도 필요합니다. 사용자 ID는 요청 본문 대신 인증 세션에서 가져옵니다. 매 질문마다 재고와 추천을 다시 조회하므로 직전에 추가·수정·삭제한 재료를 반영합니다. DB 조회 트랜잭션은 외부 AI 응답을 기다리기 전에 끝납니다.

`mode`는 AUTO 또는 LOCAL이며 생략·null은 AUTO입니다. LOCAL을 명시하면 키가 있어도 AI를 호출하지 않습니다. 사용자가 화면의 대화 방식 또는 오류 후 ‘기본 추천으로 답변 받기’를 선택할 수 있습니다.

응답은 `{reply, source, recommendedRecipes}`입니다. OPENAI는 현재 재고 기준으로 계산한 전체 레시피 후보(최대 40개)에서 모델이 고른 ID 최대 3개를 서버 데이터에 연결합니다. 모델 응답은 strict JSON Schema의 `{reply,recipeIds}`이며 없는 ID·중복·형식 오류를 거절합니다. API의 카드 수량·단위·조리법을 AI 생성값으로 덮어쓰지 않습니다. 일반 조리법·보유량 질문이나 맞는 메뉴가 없는 경우 카드는 비어 있을 수 있습니다. LOCAL은 기존 키워드 기반 메뉴 선택을 유지합니다. [OpenAI Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs)를 사용합니다.

### 키가 없을 때의 기본 추천

`LOCAL` 모드는 등록된 레시피 중 현재 사용할 수 있는 보유 재료를 적어도 한 종류 활용하는 메뉴를 고릅니다. 만료 재료 제외, 수량 합산, 단위 변환, 인분 계산은 기존 추천 서비스를 이용합니다.

- 기본 질문은 [STEP 7](step-07.md)의 추천 순서를 따릅니다.
- ‘임박’, ‘유통기한’, ‘먼저’, ‘급한’이 있으면 임박 재료를 활용하는 메뉴를 우선 선택합니다. 해당 메뉴가 없으면 다른 보유 재료를 활용하는 메뉴를 제시하고 이유를 알립니다.
- ‘단백질’, ‘고단백’, ‘protein’이 있으면 1인분당 추정 단백질이 높은 순으로 선택합니다.
- 단백질 조건이 없고 ‘간단’, ‘빨리’, ‘빠른’, ‘짧은’, ‘quick’이 있으면 조리 시간이 짧은 순으로 선택합니다.

답변에는 선택한 인분, 메뉴별 조리 시간, 보유 재료 활용 이유와 정확한 부족 수량을 표시합니다. 재료를 아직 등록하지 않았거나 활용 가능한 레시피가 없으면 그 상태를 설명합니다.

기본 추천은 **이번 질문의 정해진 키워드만** 처리합니다. 이전 대화의 문맥, ‘10분 이내’ 같은 임의 시간 제한, 특정 재료 제외나 모든 식단 조건을 이해하는 대화형 AI는 아닙니다. 화면에도 기본 추천 모드와 처리 범위를 안내합니다.

### OpenAI 요청과 전송 정보

키는 백엔드 환경 변수 `OPENAI_API_KEY`에서 읽습니다. 기본 모델은 사용자가 요청한 `gpt-5-mini`이며 `OPENAI_MODEL`로 변경할 수 있습니다. 기본 서버 주소는 `https://api.openai.com`이고 `OPENAI_BASE_URL`로 바꿀 수 있습니다. 주소 끝에 `/v1`이 있는 형식도 지원합니다. HTTPS만 허용하며 로컬 모의 서버의 HTTP(loopback 주소)만 예외입니다. Windows 키 등록·교체는 [setup-openai.ps1](../scripts/setup-openai.ps1)의 숨김 입력을 사용합니다.

서버는 `POST /v1/responses`에 `model`, `instructions`, `input`, `max_output_tokens: 2400`, `store: false`를 보냅니다. `store: false`는 응답 저장을 사용하지 않도록 요청하는 설정이며, 모든 서비스 로그나 보관 정책이 없어짐을 뜻하지는 않습니다. 연동 형식과 여러 출력 항목 처리 방법은 [OpenAI 공식 텍스트 생성 문서](https://developers.openai.com/api/docs/guides/text)를 참고했습니다.

외부 API에 보내는 애플리케이션 데이터는 다음과 같습니다.

- 선택한 인분과 유통기한 순으로 정렬한 현재 사용자 재고 최대 100건: 이름, 수량, 단위, 유통기한, 상태, 보관 위치
- 재고 목록이 잘렸는지 나타내는 `inventoryTruncated`
- 서버가 계산한 레시피 후보 최대 40개의 재료 수량·부족량·조리 순서·영양 추정값. 현재 seed는 16개이며 AI에는 사전 상위 3개로 좁히지 않고 전달
- 검증된 이전 대화와 현재 질문

서버는 회원 이메일·비밀번호·회원 ID·재고 ID·구매 날짜·수정 버전을 재고 문맥에 붙이지 않습니다. 질문과 이전 대화에 사용자가 직접 쓴 내용은 그대로 전송됩니다. API 키는 서버의 인증 헤더에만 넣으며 프론트엔드 응답이나 브라우저 저장소에 보관하지 않습니다.

대화는 현재 React 화면 상태에만 유지합니다. 이 프로그램은 채팅 내역을 DB에 저장하거나 OpenAI의 이전 응답 ID를 이용해 대화를 이어가지 않습니다. 새로고침하거나 해당 화면을 나가면 대화가 지워집니다.

### 시간 제한과 오류 처리

HTTP 연결에는 10초, 요청에는 기본 45초 제한을 둡니다. 요청 제한은 `app.openai.timeout-seconds` 설정으로 조정합니다. 리다이렉트는 따라가지 않습니다. GPT-5 계열에는 `reasoning.effort=low`, `text.verbosity=low`를 보내며 출력 상한은 추론 포함 2,400토큰입니다. 미지원 `temperature`는 보내지 않습니다. [공식 GPT-5 안내](https://developers.openai.com/api/docs/guides/latest-model?model=gpt-5)를 참고했습니다.

[AiRequestLimiter](../backend/src/main/java/com/fridgegatekeeper/chat/AiRequestLimiter.java)는 사용자별 최근 60초 5회, 서버 전체 최근 60초 20회·한국 날짜 하루 100회, 동시 2개를 기본으로 제한합니다. 한 사용자의 동시 호출은 하나만 허용합니다. 거절 시 외부 호출 전에 HTTP 429와 `AI_REQUEST_LIMIT`·`AI_DAILY_LIMIT`·`AI_BUSY`·`AI_REQUEST_IN_PROGRESS`를 반환합니다. 실제 시도한 호출은 실패해도 집계하며 종료·예외 시 동시 처리 자리를 해제합니다. LOCAL 요청은 이 제한과 무관합니다. 환경 변수는 README를 참고하세요. 단일 서버 메모리 기준이며 재시작 시 초기화되므로 결제 금액 한도나 분산 환경의 제한으로 간주하지 않습니다.

Responses API의 `output` 전체를 순회해 assistant 메시지의 모든 `output_text`를 합칩니다. 첫 번째 출력이 reasoning 항목이어도 이후 텍스트를 읽습니다. 모델이 거절 답변을 제공한 경우 그 답변을 전달합니다. 응답 상태가 `completed`가 아니거나 내용이 비어 있으면 완료된 답변으로 표시하지 않습니다.

외부 호출 실패는 공통 `{code, message, fieldErrors}` 형태와 HTTP 503으로 반환합니다.

| 상황 | 오류 코드 |
| --- | --- |
| OpenAI 400·401·403·404 | `AI_CONFIGURATION_ERROR` |
| OpenAI 429, 결제·크레딧·계정 한도 | `AI_QUOTA_EXCEEDED` |
| OpenAI 429, 일시적 요청 제한 | `AI_RATE_LIMITED` |
| 요청 시간 초과 | `AI_TIMEOUT` |
| 연결 실패·요청 중단·기타 비정상 HTTP 상태 | `AI_UNAVAILABLE` |
| 잘못된 JSON 등 해석할 수 없는 응답 | `AI_INVALID_RESPONSE` |
| 완료되지 않은 응답 | `AI_INCOMPLETE_RESPONSE` |
| 완료 상태지만 답변이 없음 | `AI_EMPTY_RESPONSE` |

서버는 외부 오류 본문이나 인증 헤더를 사용자에게 그대로 노출하지 않습니다. AI 실패를 성공한 답변으로 표시하거나 자동으로 LOCAL 답변으로 바꾸지 않습니다. 화면에서 오류·재시도·기본 추천 선택을 제공하며, 일반 레시피 추천은 계속 이용할 수 있습니다. 결제·잔액 오류는 기다렸다 재시도하는 것으로 해결되지 않습니다([공식 오류 안내](https://developers.openai.com/api/docs/guides/error-codes)).

## 4. 실행 방법

1. [README](../README.md)에 따라 DB·백엔드·프론트엔드를 실행하고 회원가입 후 로그인합니다.
2. 식재료를 등록한 뒤 요리 도우미에서 1·2인분을 선택하여 질문합니다. `OPENAI_API_KEY`가 비어 있으면 ‘기본 추천’ 출처와 메뉴 카드가 표시됩니다.
3. OpenAI를 사용하려면 루트 `.env`에 유효한 `OPENAI_API_KEY`와 필요한 `OPENAI_MODEL`을 설정한 뒤 백엔드를 다시 시작합니다. 직접 Maven/JAR를 실행할 때는 환경 변수를 실행 프로세스에 전달해야 합니다.
4. 키 설정 후 상태가 사용 가능으로 바뀌더라도 실제 답변에는 모델 접근 권한과 API 사용 한도가 필요합니다. 실패하면 해당 오류가 화면에 나타납니다.

채팅 관련 자동 테스트만 실행하려면 `backend`에서 다음 명령을 사용합니다.

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress '-Dtest=ChatServiceTest,OpenAiClientTest,ChatIntegrationTest,AiRequestLimiterTest' test
```

2026-09-17 최신 채팅 관련 테스트 37개가 통과했습니다(서비스 10, HTTP 20, 통합 3, 요청 제한 4). 자동 테스트는 H2와 모의 HTTP 서버를 사용하며 유료 호출을 하지 않습니다. 별도 실제 GPT-5 mini 품질 검증에서 오타 질문, 후속 제외 조건, 현재 재고량 답변을 확인했습니다. 재현은 LIVE_OPENAI=1을 명시한 `scripts/check-ai-quality.mjs`를 사용합니다. 프로젝트 전체 검증 결과는 [STEP 11](step-11.md)을 참고하세요.
