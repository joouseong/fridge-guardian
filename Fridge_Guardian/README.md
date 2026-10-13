# 🧊 냉장고 문지기 · Fridge Guardian

냉장고 속 식재료와 유통기한을 관리하고, 보유 재료 기반 **매칭 알고리즘**과 **AI 레시피 생성**으로 식재료 낭비를 줄이는 반응형 웹 애플리케이션입니다. 요구사항 원본은 [`냉문.md`](../냉문.md)(PRD)입니다.

**기술 스택** — Java 21 · Spring Boot 4.1.1 (Security · OAuth2 Client · Resource Server · Data JPA · Batch 6 · Scheduler · Cache) · MySQL 8 · Redis(선택, 없으면 메모리 캐시) · React 19 · Vite 8 · `html5-qrcode` · OpenAI Responses API

이 프로젝트는 두 프로젝트를 합쳐 새로 만들었습니다. 기반은 `Fridge_Gatekeeper-main`(Spring Boot + React, 재고·추천·채팅·테스트 구현)이고, `freshfridge_capstone`(Node/EJS 프로토타입)에서는 **PRD 샘플 레시피 5종의 조리 순서, 이미지 업로드 규칙(5MB·교체 시 기존 파일 삭제·수정 때 미선택이면 유지), 일치율 계산 방식, 발표 자료**를 가져왔습니다. 자세한 대응은 [docs/DESIGN.md](docs/DESIGN.md)를 보세요.

## PRD 기능 구현 현황

| PRD | 구현 | 위치 |
| --- | --- | --- |
| 3.1 이메일 회원가입 (중복 확인, 6자 이상, 확인 일치, BCrypt) | ✅ | `user/AuthService`, `AuthPage.jsx` |
| 3.1 Google · Kakao OAuth 2.0 | ✅ 코드·설정·자동 테스트 완료. **실제 제공자 로그인은 키를 넣어 직접 확인 필요** | `user/oauth/*`, `application.yml` |
| 3.1 JWT 인증, 비로그인 접근 제한·로그인 이동 | ✅ Access 15분(메모) + Refresh 14일(HttpOnly 쿠키, 회전·재사용 감지) | `user/token/*`, `services/api.js` |
| 3.2 식재료 CRUD, 카테고리·단위·보관 위치 | ✅ | `ingredient/*`, `IngredientEditor.jsx` |
| 3.2 이미지(5MB, 기본 이미지, 교체 유지, 삭제 시 파일 삭제) | ✅ | `ingredient/image/*`, `ImageUploader.jsx` |
| 3.2 바코드 스캔 자동 등록 (`html5-qrcode`) | ✅ 스캔·상품명 자동 입력. 식품안전나라 우선 조회 후 Open Food Facts 보완(상품명·브랜드·사진), 등록 시 사진 자동 저장 | `ingredient/barcode/*`, `BarcodeScanner.jsx` |
| 3.3 D-Day(D-5, D-0, D+2)·임박/여유/만료 배지·임박순 정렬 | ✅ | `ExpiryBadge`, `ExpiryStatus` |
| 3.3 Scheduler + Batch 자정 처리, 알림 큐 | ✅ (D-Day는 저장하지 않고 계산 — 아래 설계 결정 참고) | `batch/*`, `notification/*` |
| 3.4 대시보드(요약 + 가장 가까운 6개 카드) | ✅ | `DashboardPage.jsx` |
| 3.4 검색·카테고리/보관/상태 필터·초기화 | ✅ (서버 필터) | `IngredientsPage.jsx` |
| 3.5 매칭 알고리즘(일치율 %, ✓/부족, BEST MATCH, 난이도·시간·조리법) | ✅ | `recommendation/*`, `RecipeCard.jsx` |
| 3.5 AI 실시간 레시피 생성 (재료 선택) | ✅ 서버 검증·캐시·한도 완료. **실제 OpenAI 호출은 키를 넣어 직접 확인 필요** | `ai/*`, `AiRecipePage.jsx` |
| 3.5 부족 재료 쇼핑 링크(쿠팡·마켓컬리) / 앱 내 장바구니 | ✅ | `MissingIngredients.jsx`, `shopping/*` |
| 3.5 개인화(비선호·알레르기·매운맛) 자동 필터 | ✅ | `user/preference/*`, `ProfilePage.jsx` |
| 3.5 샘플 레시피 8종 | ✅ PRD 8종 + 기존 13종 = 21종 | `V2`, `V7` |
| 기술: Redis 캐시 | ✅ 공통 레시피·AI 결과. 기본은 메모리(Caffeine), `CACHE_TYPE=redis`로 전환 | `config/CacheConfig` |
| 배포: Vercel(FE) + AWS(BE) | ⏳ 구성 방법만 [docs/DESIGN.md](docs/DESIGN.md)에 정리, 실제 배포는 하지 않음 | — |

## 설계 결정 (PRD와 다르거나 판단한 부분)

1. **D-Day를 DB에 저장·차감하지 않습니다.** PRD의 "자정마다 전체 D-Day 차감 업데이트"는 유통기한에서 언제든 계산할 수 있는 값을 매일 전체 행에 다시 쓰는 구조라 낭비이고, 배치가 실패하면 화면이 틀어집니다. 대신 `expiration_date`와 한국 시간 `Clock`으로 **요청 때마다 계산**하고, 자정 Spring Batch는 "알림 대상 추출 → 알림 큐 적재"를 맡습니다. 과제 요건상 DB 갱신이 꼭 필요하면 알려 주세요.
2. **JWT 저장:** Access Token은 메모, Refresh Token은 HttpOnly·SameSite=Strict 쿠키(DB에는 해시만). localStorage에 토큰을 두지 않습니다.
3. **일치율 = 보유한 필요 재료 종류 ÷ 필요 재료 종류.** 기존 규칙(임박 재료 활용 → 부족 종류)은 뒤 순위 tie-break로 유지합니다. 소금·기름 같은 양념도 재고에 있어야 보유로 셉니다.
4. **소셜 계정 연결:** 제공자가 **검증한 이메일**이 기존 계정과 같을 때만 연결합니다. 미검증 이메일은 저장도 연결도 하지 않습니다(계정 탈취 방지).
5. **AI 출력은 신뢰하지 않습니다.** 재료의 보유/부족 판정과 알레르기·비선호 검사는 서버가 다시 하고, 실패를 성공처럼 보이거나 다른 방식으로 바꿔 보여 주지 않습니다.
6. 알레르기·비선호는 **재료 이름 기준**입니다(예: 우유 → 치즈·버터 포함, 새우 → 새우만). 모든 성분을 보장하지 않으며 화면에도 안내합니다.

## 폴더 구조

```text
Fridge_Guardian/
├─ backend/   Spring Boot (com.fridgeguardian)
│  └─ src/main/java/com/fridgeguardian/
│     ├─ user/         인증(AuthController·JWT·Refresh 회전)·OAuth(user/oauth)·개인화 설정(user/preference)
│     ├─ ingredient/   재고 CRUD·검색·대시보드·사진(image/)·바코드(barcode/)
│     ├─ recipe/       공통 레시피, 캐시되는 스냅샷(RecipeCatalog)
│     ├─ recommendation/ 일치율 매칭·개인화 필터·쇼핑 링크
│     ├─ ai/           AI 레시피 생성(LlmClient·프롬프트·검증·캐시)
│     ├─ chat/         재고 기반 대화형 도우미, OpenAI 클라이언트, AI 요청 한도
│     ├─ shopping/     장바구니
│     ├─ notification/ 알림함
│     ├─ batch/        자정 스케줄러 + 유통기한 알림 Job
│     ├─ config/       JWT·캐시·웹 설정
│     └─ common/       오류 응답, 한국 시간 Clock
├─ frontend/  React 19 + Vite (JSX, 자체 라우터)
│  └─ src/  pages/ components/ hooks/ services/api.js lib/ styles/
├─ e2e/       Playwright(데스크톱·모바일) 시나리오
├─ database/  DB 설계·ERD   docs/  API 계약·설계 문서·발표 자료
├─ scripts/   OS 공통 실행 스크립트(dev.mjs), Windows용 .ps1, HTTP 스모크 테스트
├─ package.json  npm run dev / verify / e2e … 공통 명령
└─ compose.yaml  MySQL 8.4 (+ 선택 Redis)
```

## 실행 (Windows · macOS · Linux 공통)

필요한 것: **JDK 21**, **Node.js 22.13+**, **MySQL 8.4**(또는 Docker). 프로젝트 루트에서 실행합니다. 스크립트는 Node로 만들어 OS와 상관없이 같은 명령입니다.

| 설치 | macOS | Windows |
| --- | --- | --- |
| JDK 21 | `brew install openjdk@21` | `winget install EclipseAdoptium.Temurin.21.JDK` (또는 `scripts/setup-java.ps1`) |
| MySQL 8.4 | `brew install mysql@8.4` | MySQL Installer 8.4 (서비스로 등록하지 않아도 돼요) |
| Node.js | `brew install node` | `winget install OpenJS.NodeJS.LTS` |

```bash
npm run dev        # DB(3307) + 백엔드(8080) + 프런트(3000)를 한 번에. Ctrl+C로 백엔드·프런트 종료 → http://localhost:3000
npm run db:stop    # 프로젝트 MySQL 종료 (데이터 보존)
```

개별 실행: `npm run db` / `npm run backend` / `npm run frontend` (각각 다른 터미널). 도움말: `node scripts/dev.mjs help`.

- JDK와 MySQL은 자동으로 찾습니다(`JAVA_HOME`, Homebrew, Program Files 등). 못 찾으면 `JAVA_HOME` 또는 `MYSQL_BIN=<mysql bin 폴더>`를 지정하세요.
- 프로젝트 DB는 이미 쓰는 MySQL(3306)과 별개인 **3307**을 쓰고, 데이터는 `.local/mysql/data`(Git 제외)에 저장됩니다. **각자 PC의 DB는 서로 공유되지 않아요.**
- 처음 실행하면 Flyway가 테이블·레시피를 만듭니다. `.env.example`을 `.env`로 복사해 값을 채우면 백엔드 실행 명령이 읽습니다.
- Docker가 있으면 `docker compose up -d --wait mysql`(3306)로도 DB를 띄울 수 있고, 이때는 `.env`에 `DB_URL`을 지정하세요.
- 기존 Windows용 `scripts/*.ps1`도 그대로 동작하며 같은 `.local/mysql` 데이터를 씁니다.

### 소셜 로그인·AI·바코드 켜기 (모두 선택)

| 기능 | 설정 | 안 하면 |
| --- | --- | --- |
| Google | 콘솔에서 OAuth 클라이언트 생성 → Redirect URI `http://localhost:3000/auth/google/callback` → `GOOGLE_CLIENT_ID/SECRET` | 로그인 화면에 버튼이 보이지 않음 |
| Kakao | 카카오 로그인 활성화, Redirect URI `http://localhost:3000/auth/kakao/callback`, 동의 항목(닉네임·이메일) → `KAKAO_CLIENT_ID/SECRET` | 위와 같음. 이메일 동의가 없으면 이메일 없이 가입됨 |
| AI | `OPENAI_API_KEY` (`npm run setup:openai`로 화면에 표시하지 않고 저장 가능) | AI 레시피는 503 `AI_NOT_CONFIGURED` 안내, 채팅은 기본 추천 |
| 바코드 | 선택: `FOODSAFETY_API_KEY`(식품안전나라 OpenAPI). 키가 없어도 Open Food Facts를 조회 | 상품 정보가 두 데이터베이스에 없으면 직접 입력 안내. Open Food Facts 정보는 사용자 기여 자료라 한국 상품이 빠지거나 부정확할 수 있음 |
| 운영 JWT | `JWT_SECRET`(Base64 32바이트 이상) | 재시작 때마다 임시 키 → 모두 로그아웃 |

키는 서버에서만 읽고 `VITE_*` 변수나 저장소에 넣지 마세요.

## 테스트와 검증

```bash
npm run verify     # 백엔드 verify(H2, MySQL 불필요) + 프런트 ci·test·lint·build
# DB·백엔드·프런트엔드를 켠 뒤(npm run dev)
node scripts/smoke-api.mjs   # 실제 MySQL 대상 HTTP 스모크
npm run e2e                  # 브라우저 E2E. Chrome은 기본 위치에서 자동 탐지, 다르면 BROWSER_EXECUTABLE 지정
```

2026-09-28 이 폴더에서 실제로 실행한 결과입니다.

| 검증 | 결과 |
| --- | --- |
| 백엔드 `mvnw test` (H2, MySQL 모드) | **125개 통과** — 인증·JWT·Refresh 회전·OAuth, 재고·사진·필터, 추천·개인화, AI(가짜 LLM), 배치, 장바구니, 바코드, Redis 장애 폴백 |
| 프런트엔드 `npm test` / lint / build | **26개 통과**, lint 경고 0, 빌드 성공 |
| 실제 MySQL 8.0 기동 | Flyway V1~V9(Spring Batch 스키마 포함) 적용, JPA 검증 통과 |
| `scripts/smoke-api.mjs` (실제 MySQL·서버) | 통과 |
| Playwright E2E (Chrome, 데스크톱 + 모바일 390px) | **28개 통과** |

**아직 확인하지 못한 것 (직접 확인이 필요합니다)**
- 실제 Google/Kakao 로그인 왕복 — 제공자 키가 없어, 인증 시작 주소·콜백 실패 처리·성공 핸들러·프로필 해석을 자동 테스트로만 확인했습니다.
- 실제 OpenAI 호출(`LIVE_OPENAI=1`은 유료 호출이라 실행하지 않음), 실제 식품안전나라 API 응답(문서 기준으로 방어적으로 해석).
- 실제 Redis 서버 — 서버가 없을 때의 폴백과 직렬화 왕복만 확인했습니다.
- 자정 스케줄러의 실제 자정 실행(배치 Job 자체는 통합 테스트로 확인), 다중 서버 환경.
- 카메라 바코드 스캔(하드웨어 필요), Vercel/AWS 배포, S3 저장소, Safari·Firefox·실물 모바일 기기.

## 주의

- `.local/`(DB 데이터·JDK)와 `.env`, 업로드 사진은 저장소에 넣지 않습니다. `.local/mysql/data`를 임의로 지우지 마세요.
- 유통기한의 '여유'는 날짜 기준 표시이며 실제 식품 상태를 보증하지 않습니다. 영양값은 1인분 예시 추정값입니다.
- 구조·API·DB를 바꾸면 [docs/api.md](docs/api.md), [database/README.md](database/README.md), [docs/DESIGN.md](docs/DESIGN.md)와 테스트를 함께 갱신하세요.
