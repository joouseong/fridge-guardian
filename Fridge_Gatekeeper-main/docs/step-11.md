# STEP 11 — 빌드와 동작 검증

## 구현 목적

회원가입부터 식재료 관리, 레시피 추천과 채팅까지 연결되는지 확인하고, 같은 검증을 다시 실행할 수 있도록 자동 테스트와 실행 스크립트를 제공합니다.

## 검증 결과

### 2026-09-18 — 여러 재료 한 번에 등록

다중 선택·장본 목록 붙여넣기·대시보드 바로 등록, 일괄 등록 API와 V4 변경 후 새로 검증했습니다. Maven verify **69개**, 프론트 테스트 **18개**, lint·build, Playwright **12개(6개 시나리오 × 데스크톱/모바일)**가 모두 통과했습니다. 이번 등록 개선에서는 OpenAI 유료 호출 없이 LOCAL과 모의 HTTP 서버를 사용했습니다.

- 백엔드 신규 6개: 일괄 저장·선택적 날짜·사용자 격리, 잘못된 행 전체 거부, 인증·CSRF·최대 50개 검증, 동일 요청 재시도·다른 내용 409·삭제 후 재생성 방지, 사용자별 요청 번호 분리, 동시 재시도 한 번만 반영.
- 프론트 신규 6개: 쉼표·줄바꿈·단위·별칭 해석, 기본값·직접 입력, 오타 확인·부분 적용 방지, 잘못된 수량·단위·중복·50개 한도, API 필드 변환, 숨겨진 상세 입력 검증.
- E2E: 기존 4개 시나리오를 새 등록 흐름에 맞게 수정하고 2개를 추가했습니다. 대시보드 다중 선택·선택 해제·개별 수량/날짜·한 번 저장·집계, 붙여넣기 오류 수정·응답 유실 뒤 동일 UUID 재시도·DB 중복 없음·새로고침 유지까지 확인했습니다.
- 실제 MySQL 8.0.44에서 V4 적용 성공, 기존 데이터 유지. `API_BASE_URL=http://127.0.0.1:8081`, `LIVE_OPENAI=0`으로 기존 HTTP smoke도 통과했습니다.
- 데스크톱 1440×1000·모바일 390×844에서 등록 창의 선택 목록·수량 편집·하단 저장 버튼과 가로 넘침을 확인하고 캡처를 육안 검토했습니다.

첫 화면 검증에서 Vite가 변경 전 CSS를 계속 제공했습니다. 디스크 파일과 `/src/styles/global.css` 응답을 비교해 확인한 뒤 해당 프로젝트 Vite만 재시작했습니다. 붙여넣기 textarea의 접근성 이름도 명시했습니다. 이후 남은 실패는 저장 완료와 로딩 상태를 함께 찾은 테스트 선택자 문제였으며 완료 안내로 범위를 좁혔습니다. 최종 12개는 모두 통과했습니다.

내장 브라우저는 연결 가능한 브라우저가 없어 저장소의 Playwright와 설치된 Chrome으로 검증했습니다. 백엔드는 프로젝트 소유 프로세스를 확인한 뒤 최신 코드로 8081에 재시작했고 웹은 5173, DB는 3307입니다. 로그는 Git 제외 `.local/verify-batch-backend.log`, `.local/e2e-batch-pass.log` 등에 있습니다. 테스트는 검증용 계정만 사용하며 재료는 종료 시 삭제하고 계정·일괄 요청 완료 기록은 남습니다.

### 이전 식재료 선택·AI 답변 개선 기록

후속 식재료 선택·유통기한 선택 입력·AI 관련성 개선 후 결과입니다. 전체 Maven verify 63개 통과 후 마지막 한국어 AI 문맥 변경에 대해서는 채팅 테스트 37개를 다시 실행해 통과했습니다. V3는 실제 MySQL과 H2에 적용했고 기존 날짜 데이터는 유지했습니다. 새 UI는 분류 검색·오타 후보·수량 ±·미등록 저장·날짜 추가/제거·모바일 하단 저장 버튼을 검사했습니다.

실제 AI 품질 검증을 3문항씩 두 차례 실행했습니다. 첫 번째는 내부 단위 코드(BLOCK/UNKNOWN)와 1.000 표기 때문에 실패했고 전송 문맥을 한국어로 정리했습니다. 최종 검증은 `게란`→계란말이·정확한 부족 재료, 후속 ‘계란·고기 제외’→두부김치, 보유량 질문→‘두부 1모’·카드 없음으로 통과했습니다. 최종 응답 시간은 약 8.4초/7.7초/2.4초였고 5173 웹 프록시를 통과했습니다. 오타·알레르기 조건을 모든 입력에서 완벽히 처리한다는 보장은 아닙니다.

유료 품질 검증은 프로젝트 루트에서 명시적으로 실행합니다(3회 호출). 테스트 재료는 마지막에 지우지만 검증 회원은 남습니다. API 키를 스크립트에 넣지 않으며 로그인한 테스트 세션으로 앱을 호출합니다.

```powershell
$env:LIVE_OPENAI='1'
$env:API_BASE_URL='http://127.0.0.1:5173'
node scripts/check-ai-quality.mjs
Remove-Item Env:LIVE_OPENAI
```

이 PC의 최신 백엔드는 `.env`의 SERVER_PORT=8081이며 Vite는 이를 읽어 연결합니다. 기존 8080 프로세스 종료가 자동 승인 검토에 차단되어 별도 포트에서 진행했습니다. DB는 3307을 유지했습니다. 일반 HTTP 검증도 최신 서버를 대상으로 하려면 API_BASE_URL을 8081 또는 웹 프록시 5173으로 지정하세요.

2026-09-17 GPT-5 mini 연결 변경 후 Windows에서 재검증했습니다. JDK 21.0.12.1, Node.js 24.15.0, MySQL 8.0.44와 Playwright 1.63.0을 사용했습니다. DB 시작·종료와 포트 변경의 별도 검증은 2026-09-14~15 기록입니다.

| 검사 | 결과 | 검증 범위 |
| --- | --- | --- |
| Maven `verify` | 63개 통과, JAR 생성 | 인증·재고·추천·채팅·요청 제한·앱 시작 |
| 프론트엔드 `npm test` | 12개 통과 | 재료 별칭·오타 후보·미등록 날짜, CSRF·세션·네트워크 오류 |
| 프론트엔드 `npm run lint` | 통과 | JavaScript·React 규칙 |
| 프론트엔드 `npm run build` | 통과 | Vite 배포용 정적 파일 생성 |
| 실제 MySQL HTTP 검증 | 통과 | 회원·세션·CSRF·CRUD·사용자 격리·한국 날짜·단위·인분·실제 GPT-5 mini 답변 |
| 브라우저 E2E | 8개 통과 | 데스크톱 1440×1000, 모바일 390×844에서 각각 4개 시나리오. 재료 선택·미등록 기한·LOCAL·오류 복구 |
| Windows DB 스크립트 | 이전 검증 통과 | 9월 14~15 반복 시작·종료, 연결 설정 복구, 포트 변경·데이터 보존 |

`verify.ps1`의 백엔드 단계는 통과했으나 실행 중인 Vite가 네이티브 모듈 파일을 사용 중이어서 `npm ci`가 EPERM으로 중단됐습니다. 해당 프로젝트 Vite 프로세스만 종료한 뒤 `npm ci`, `npm test`, `npm run lint`, `npm run build`를 순서대로 실행하여 모두 통과했습니다. DB는 초기화하거나 삭제하지 않았습니다. Windows에서 전체 검증을 돌릴 때는 프론트엔드 개발 서버를 먼저 종료하세요.

처음 `.env`를 읽을 때 Windows PowerShell 5.1이 `String.Split` 오버로드를 잘못 선택하는 문제를 `-split '=', 2`로 수정하고 실제 키가 있는 백엔드를 재시작해 확인했습니다. 새 키 등록 스크립트는 `.env`의 Git 제외 상태와 ACL(현재 사용자·SYSTEM만 접근)을 확인했습니다. 제공된 키가 버전 관리 대상 소스·프론트 빌드·로컬 앱 로그에 없고 Vite의 `.env` 접근으로 노출되지 않는 것도 검사했습니다.

백엔드 자동 테스트는 격리된 H2 MySQL 모드에서 Flyway 마이그레이션과 JPA를 실행합니다. 실제 MySQL 검증은 별도로 실행한 프로젝트 전용 DB에서 수행했습니다. 모바일은 Chromium의 화면 크기·터치 에뮬레이션이며 실제 휴대폰이나 Safari 검증은 아닙니다.

9월 14~15에는 생성된 JAR를 직접 실행한 서버에서도 HTTP·브라우저 테스트가 통과했습니다. DB 포트를 3307→3308→3307로 바꾸어 재시작한 뒤 저장한 식재료와 회원·레시피 수가 동일함을 확인했습니다. Windows MySQL의 감시·작업 프로세스를 구분하여 중복 실행 오판을 수정했습니다. 한글·공백 폴더에서는 NTFS 짧은 경로를 사용하며, 짧은 경로를 사용할 수 없는 경우 명확한 오류를 표시합니다. 9월 17일 서버는 `start-backend.ps1`의 Maven 실행 경로로 검증했습니다.

### 백엔드 테스트

- [앱 시작](../backend/src/test/java/com/fridgegatekeeper/FridgeGatekeeperApplicationTests.java): 1개
- [회원·식재료 통합](../backend/src/test/java/com/fridgegatekeeper/user/AuthIngredientIntegrationTest.java): 현재 17개(기존 11개 + 일괄 등록 6개). 세션·CSRF, 회원 중복, 비밀번호 해시, 입력 검증, 사용자별 재고 격리, 수정 버전 충돌, 실제 레시피 16개·상세·인분 계산, 선택적 유통기한과 HTTP 오류 상태를 확인합니다.
- [추천 계산](../backend/src/test/java/com/fridgegatekeeper/recommendation/RecommendationServiceTest.java): 14개. 한국 날짜 경계, 만료 재료 제외, 재고 합산, 동의어, 단위 환산·불일치, 수량 부족과 추천 순서를 확인합니다.
- [채팅 서비스](../backend/src/test/java/com/fridgegatekeeper/chat/ChatServiceTest.java): 10개
- [OpenAI HTTP 클라이언트](../backend/src/test/java/com/fridgegatekeeper/chat/OpenAiClientTest.java): 20개
- [채팅 통합](../backend/src/test/java/com/fridgegatekeeper/chat/ChatIntegrationTest.java): 3개
- [AI 요청 제한](../backend/src/test/java/com/fridgegatekeeper/chat/AiRequestLimiterTest.java): 4개. 최근 60초 경계, 한국 날짜 자정, 중복·동시 요청, 다중 스레드 경쟁과 제한 해제를 검증합니다.

자동 테스트는 localhost 모의 HTTP 서버로 GPT-5 mini의 요청 설정, 출력 항목, 오류·결제 한도와 비밀 값 미노출을 검증합니다. 별도로 사용자의 명시적 요청에 따라 `LIVE_OPENAI=1 node scripts/smoke-api.mjs`를 한 번 실행해 **실제 GPT-5 mini 응답 678자, source=OPENAI**를 확인했습니다. 이때 검증용 회원과 재료만 사용했고 재료는 테스트 종료 시 정리했습니다. 모든 일반 테스트가 유료 호출을 수행한다는 의미는 아닙니다.

### 프론트엔드와 브라우저 테스트

[API 회귀 테스트](../frontend/tests/api.test.js)는 실제 화면과 서버 사이에서 잘못된 성공 응답이나 끝없는 재시도가 발생하지 않도록 검증합니다.

[브라우저 시나리오](../e2e/tests/fridge.spec.js)는 다음 흐름을 데스크톱과 모바일 설정에서 반복합니다.

1. 회원가입 후 로그인 화면 이동, 이메일 유지, 비밀번호 재입력 로그인
2. 빈 냉장고 확인, 재료 4종 추가, 수량 수정
3. 오늘·만료 필터, 뒤로가기와 URL 동기화, 이름 검색
4. 레시피 16개 확인, 김치찌개 상세의 1kg→1,000g 보유량 및 1·2인분 필요량 확인
5. LOCAL 채팅 답변과 추천 카드 확인
6. 재료 삭제, 새로고침·로그아웃·재로그인 후 저장된 재고 확인
7. 화면 가로 넘침과 브라우저 JavaScript 오류 확인
8. 일시적 API 실패 후 재시도, 서버에서 종료한 세션의 로그인 복귀와 회원가입 화면 재진입

실제 회원·재고·레시피·채팅 API를 사용하며, 오류 복구 시나리오에서만 식재료 조회의 503을 주입합니다. 성공 화면을 캡처하여 모바일 입력창, 버튼, 재료 목록 배치도 확인합니다.

## 다시 실행하는 방법

프로젝트 루트에서 실행합니다. 기본 빌드·테스트는 MySQL 서버 없이 실행할 수 있습니다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify.ps1
```

실제 MySQL과 브라우저 검증은 [README](../README.md)의 DB·백엔드·프론트엔드를 먼저 실행합니다.

```powershell
node scripts/smoke-api.mjs
cd e2e
npm.cmd ci
npx.cmd playwright install chromium
npm.cmd test
```

이미 설치된 Chrome을 사용하면 설치 명령 대신 다음 환경 변수를 지정할 수 있습니다.

```powershell
$env:BROWSER_EXECUTABLE = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
npm.cmd test
```

`APP_BASE_URL`은 브라우저 테스트 대상(기본 `http://127.0.0.1:5173`), `API_BASE_URL`은 HTTP 테스트 대상(기본 `http://127.0.0.1:8080`)을 변경합니다. OpenAI 키가 설정된 서버의 유료 채팅 호출은 기본적으로 생략하며 명시적으로 `LIVE_OPENAI=1`을 설정한 경우만 수행합니다.

테스트는 고유한 테스트 회원을 생성하고 생성한 식재료를 정리합니다. 테스트 회원은 남습니다. 중간에 실행을 강제 종료하면 테스트 재료도 남을 수 있으므로 개발 DB에서 실행하세요.

## 결과 파일

- 백엔드 JAR: `backend/target/fridge-gatekeeper-0.0.1-SNAPSHOT.jar`
- 백엔드 테스트 보고서: `backend/target/surefire-reports/`
- 프론트엔드 정적 파일: `frontend/dist/`
- 브라우저 화면 캡처·실패 추적: `e2e/test-results/`

빌드·테스트 산출물과 로컬 DB·JDK는 Git에 포함하지 않습니다. DB 운영 설정, HTTPS·리버스 프록시 배포, 실제 OpenAI 연결은 사용하는 환경에 맞춰 설정해야 합니다.
