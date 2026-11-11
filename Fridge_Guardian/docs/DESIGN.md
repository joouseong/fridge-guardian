# 설계 문서

## 1. 두 프로젝트를 어떻게 합쳤나

| 출처 | 가져온 것 | 바꾼 것 |
| --- | --- | --- |
| `Fridge_Gatekeeper-main` (기반) | Spring Boot + React 구조, 재고 CRUD·일괄 등록, 한국 날짜 계산, 단위 환산·추천 계산, OpenAI 채팅과 요청 한도, 테스트·실행 스크립트 | 이름(`Fridge Guardian`, 패키지 `com.fridgeguardian`), **세션+CSRF → JWT**, 소셜 로그인·이미지·바코드·개인화·AI 레시피·장바구니·알림·배치·캐시 추가, 카테고리(냉동식품 → 가공식품)·단위(병 추가), 일치율 정렬, 대시보드 카드 |
| `freshfridge_capstone` (참고) | PRD 샘플 5종(두부계란전·닭가슴살 샐러드·감자계란국·참치마요 덮밥·소고기 양파볶음)의 조리 순서, 이미지 업로드 규칙, 일치율 계산, 발표 자료 | Node/EJS 코드는 재사용하지 않음. 레시피에 수량·영양 추정값을 더함 |
| PRD 8종 중 기존과 겹친 것 | 계란볶음밥 = 기존 4번(이름 정리), 토마토 계란볶음 = 기존 9번(이름 정리), 김치볶음밥 = 기존 2번 | 기존 13종도 유지해 총 21종 |

기반 프로젝트의 원본 폴더(`Fridge_Gatekeeper-main`, `freshfridge_capstone`)는 수정하지 않았습니다.

## 2. 인증 흐름 (Google/Kakao + JWT)

```text
브라우저            프런트(3000)              Spring Boot                     Google/Kakao
  │ "Google로 계속하기" (a href=/oauth2/authorization/google)
  │──────────────────────────────▶│ (Vite 프록시) ──────────▶│ 302 → 제공자 인증 화면
  │◀────────────────────────────────────────────────────────────────────────────│
  │ 동의 후 302 → /auth/google/callback?code=…&state=…
  │──────────────────────────────▶│ ─────────────────────────▶│ code→token 교환, 사용자 정보 조회 ─▶
  │                               │        OAuth2SuccessHandler:
  │                               │         ① 제공자별 속성 정규화(OAuthProfileExtractor)
  │                               │         ② 사용자 찾기/만들기(AuthService.upsertSocialUser)
  │                               │         ③ Refresh Token 발급(해시 저장) → HttpOnly 쿠키
  │                               │         ④ OAuth 핸드셰이크용 세션 폐기
  │◀── 302 → /oauth/callback (주소에 토큰 없음) ─────────────────────────────────
  │ App.jsx가 POST /api/auth/refresh (쿠키 자동 전송) → { accessToken, user }
  │ 이후 API: Authorization: Bearer <accessToken>  (401이면 refresh 1회 → 원 요청 재시도)
```

- API는 stateless Bearer 인증이고, 소셜 로그인 핸드셰이크(state 검증)에만 세션이 잠시 쓰입니다.
- `services/api.js`는 동시에 여러 요청이 401을 받아도 refresh를 **한 번만** 보냅니다(로테이션 때문에 중복 호출하면 세션이 끊길 수 있음).
- JWT 검증은 발급과 같은 `Clock`으로 만료를 검사하고 issuer도 확인합니다.

## 3. 매칭 알고리즘 (`RecommendationService`)

1. 내 재고에서 **만료된 재료 제외**(기한 미등록은 포함하되 임박으로 세지 않음), 동의어 정규화(계란=달걀), 같은 재료 수량 합산, kg↔g·L↔ml 환산
2. 공통 레시피 목록은 캐시된 `RecipeData` 스냅샷(사용자 데이터 없음)
3. **개인화 필터**: 알레르기·비선호 재료가 든 레시피, 내 매운맛 한도(0~3)보다 매운 레시피 제외
4. 레시피마다 필요 재료를 보유량과 비교: `matchRate = round(100 × 보유 종류 / 필요 종류)`, 일부만 있어도 ✓로 세고 부족량을 별도 표시, `canCook` = 전부 충분
5. 정렬 `matchRate ↓ → 임박 재료 활용 ↓ → 부족 종류 ↑ → 조리 시간 ↑ → id`. 1위이면서 일치율 > 0 이면 `best`(BEST MATCH)
6. 부족한 재료가 1~2개면 쿠팡·마켓컬리 검색 링크, `POST /shopping-items/from-recipe`로 부족 재료를 장바구니에 자동 추가

## 4. AI 레시피 생성 (`ai/`)

`선택 재료 검증(본인·미만료) → 개인화 조건 로드 → 캐시 조회 → (미스일 때만) 한도 확인 후 LLM 호출 → 서버 검증 → 저장·응답`

- 프롬프트는 시스템 지시와 **JSON 데이터**를 분리합니다. 재료 이름·요청 사항은 데이터로만 전달하고, 이메일·회원 번호·구매 이력은 보내지 않습니다(`store:false`).
- 응답은 strict JSON Schema로 받고, 서버가 스키마·길이·범위·알레르기/비선호 포함 여부를 다시 검사합니다(한 번 재시도). AI가 적은 보유/부족 표시는 버리고 서버가 선택 재료와 비교해 `OWNED/BASIC/MISSING`을 계산합니다.
- `LlmClient` 인터페이스 뒤에 OpenAI 구현(`chat/OpenAiClient`)만 있습니다. Gemini 등은 같은 인터페이스의 구현을 추가하면 서비스 코드를 바꾸지 않고 교체할 수 있습니다(이 저장소에는 구현하지 않았습니다).
- LLM 대기 중에는 DB 연결을 잡지 않습니다(서비스에 트랜잭션을 열지 않음).

## 5. 유통기한 배치 (`batch/`)

`ExpiryScheduler`(`@Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")`) → `ExpiryJobRunner` → `expiryNotificationJob`(reader: JPQL 프로젝션 페이징 → processor: 이미 있는 알림이면 건너뜀 → writer: 저장)

- 대상: 그날 기준 오늘~3일 뒤(임박), 어제 만료(만료). 기한 미등록은 제외.
- `runDate` 잡 파라미터로 같은 날짜의 JobInstance는 한 번만 성공하고, `UNIQUE(ingredient_id, type, notify_date)`가 2차 방어입니다.
- 서버가 자정에 꺼져 있으면 그날 알림은 만들어지지 않습니다. 여러 서버를 켜면 각자 스케줄러가 돌지만 알림은 중복되지 않습니다. 안정적 다중 서버 운영에는 ShedLock 같은 분산 잠금이나 `SCHEDULER_ENABLED=false`인 인스턴스 분리를 권장합니다.
- 만료된 Refresh Token 기록도 매일 03:30에 정리합니다.
- 알림 발송(푸시·이메일)은 범위 밖입니다. 이후 `PENDING → SENT`로 바꾸는 Step을 추가하면 됩니다.

## 6. 캐시

| 대상 | 키 | 유효 | 비고 |
| --- | --- | --- | --- |
| 공통 레시피 전체·개별 | `recipes` | 1시간 | 사용자 무관 데이터 |
| AI 레시피 결과 | `aiRecipes` (재료·수량·개인화·모델의 SHA-256) | Redis 24시간 / 메모리 1시간 | 사용자 식별자·개인정보 없음 |

사용자 재고와 추천 결과는 캐시하지 않습니다(최신·격리가 우선). Redis가 죽으면 캐시 오류를 삼키고 DB로 계속 처리합니다(`CacheConfig`).

## 7. 배포 시 주의 (Vercel FE + AWS BE)

- 지금 구조는 브라우저 기준 **같은 출처**(`/api`, `/oauth2`, `/auth/{google|kakao}/callback`, `/uploads`가 프런트 도메인)를 전제합니다. Vercel에서는 `vercel.json`의 `rewrites`로 이 네 경로를 AWS 백엔드로 넘기면 CORS 없이 SameSite=Strict 쿠키가 동작합니다. (이 저장소에는 `vercel.json`을 넣지 않았습니다.)
- 프런트와 백엔드를 서로 다른 도메인으로 직접 호출하면 Strict 쿠키가 전송되지 않아 로그인 유지가 깨집니다. 같은 상위 도메인으로 맞추거나 쿠키 정책을 다시 설계해야 합니다.
- HTTPS 종단 뒤에서는 `COOKIE_SECURE=true`, `server.forward-headers-strategy=framework`(이미 설정)가 필요하고 Google/Kakao 콘솔의 Redirect URI를 실제 도메인으로 바꿉니다.
- 업로드 사진은 로컬 폴더에 저장합니다. 서버 교체·다중 서버에서는 유실/불일치되므로 `ImageStorage`의 S3 구현으로 바꿔야 합니다(인터페이스만 준비됨).
- `JWT_SECRET`, 소셜·OpenAI 키는 환경 변수로만 주입하고 HTTPS에서만 서비스하세요.

## 8. 알려진 한계

- 알레르기·비선호 필터는 재료 **이름** 기준이라 원재료의 모든 성분을 보장하지 않습니다.
- Access Token은 서버 로그아웃 후에도 만료(최대 15분)까지 유효합니다(stateless JWT의 성질). Refresh는 즉시 폐기됩니다.
- 쇼핑몰 검색 URL 형식은 쇼핑몰이 바꿀 수 있고, 제휴·딥링크는 지원하지 않습니다.
- 바코드는 식품안전나라 C005(키 설정 시)를 먼저 조회하고, 결과가 없으면 Open Food Facts에서 상품명·브랜드·정면 사진을 보완합니다. 식품안전나라에서 이름을 찾더라도 사진이 없으면 Open Food Facts에서 사진을 보완합니다. Open Food Facts 사진이 있는 바코드로 식재료를 추가하면 서버가 이미지를 로컬 업로드 저장소에 복사하고 출처 URL을 기록해 표시합니다. Open Food Facts는 사용자 기여 데이터라 누락·오류가 있을 수 있고, 유통기한은 임의로 계산하지 않습니다.
- 프런트엔드는 기존 구조대로 JavaScript(JSX)와 자체 History 라우터를 씁니다(React Router·TypeScript 미도입).
- 공통 레시피 즐겨찾기, 비밀번호 재설정, 가족 공유, 통계는 범위 밖입니다.
