# STEP 9 — 화면과 API 연결

## 1. 구현 목적

React 화면의 모든 회원·식재료·추천 작업을 Spring Boot API와 연결합니다. 로그인 세션과 CSRF 토큰을 함께 관리하고, 잘못된 입력·접속 오류·수정 충돌을 사용자가 확인하고 다시 처리할 수 있게 합니다.

## 2. 파일과 전체 코드

- [api.js](../frontend/src/services/api.js): 공통 요청, CSRF, 세션 만료, 오류 변환
- [useResource.js](../frontend/src/hooks/useResource.js): 조회 상태와 새로고침, 이전 요청 취소
- [vite.config.js](../frontend/vite.config.js): 개발·미리보기 서버의 API 프록시
- [App.jsx](../frontend/src/App.jsx), [AuthPage.jsx](../frontend/src/pages/AuthPage.jsx): 세션 확인과 가입·로그인·로그아웃
- [IngredientsPage.jsx](../frontend/src/pages/IngredientsPage.jsx), [IngredientEditor.jsx](../frontend/src/components/IngredientEditor.jsx): 쓰기 요청과 수정 충돌 처리
- [RecipeDetail.jsx](../frontend/src/components/RecipeDetail.jsx): 인분에 따른 상세 재조회
- [UI.jsx](../frontend/src/components/UI.jsx): 오류와 필드 검증 메시지 표시
- [AuthController.java](../backend/src/main/java/com/fridgegatekeeper/user/AuthController.java), [SecurityConfiguration.java](../backend/src/main/java/com/fridgegatekeeper/user/SecurityConfiguration.java): 세션·CSRF 처리
- [IngredientService.java](../backend/src/main/java/com/fridgegatekeeper/ingredient/IngredientService.java): 소유권 확인과 수정 버전 검사
- [GlobalExceptionHandler.java](../backend/src/main/java/com/fridgegatekeeper/common/GlobalExceptionHandler.java): 공통 오류 응답
- [AuthIngredientIntegrationTest.java](../backend/src/test/java/com/fridgegatekeeper/user/AuthIngredientIntegrationTest.java): 인증·CSRF·재고·추천 API 통합 검증

전체 요청·응답 필드는 [REST API 계약](api.md)을 참고하세요.

## 3. 코드 설명

### 같은 출처로 API 요청하기

화면은 `api('/ingredients')`처럼 호출합니다. 공통 함수가 `/api`를 붙이고, JSON 헤더와 `credentials: 'include'`를 설정합니다. 세션 쿠키는 브라우저가 관리하며 비밀번호나 로그인 세션을 `localStorage`에 저장하지 않습니다.

개발 서버는 `/api` 요청을 백엔드로 전달합니다. Vite 설정은 루트 `.env`의 `SERVER_PORT` 또는 `API_PROXY_TARGET`을 읽어 대상을 정하며, 기본 백엔드 주소는 `http://127.0.0.1:8080`입니다. 설정은 Vite 서버에서 사용합니다. OpenAI 키는 서버에서만 읽고 화면 요청에 넣지 않습니다. 배포에서도 정적 화면과 `/api`를 같은 출처로 제공하도록 웹 서버를 설정해야 합니다.

### 로그인과 CSRF 흐름

1. 앱 시작 시 `GET /api/auth/me`로 기존 로그인 여부를 확인합니다. 이 요청의 401은 정상적인 미로그인 상태로 처리합니다.
2. 최초 쓰기 요청 전에 `GET /api/auth/csrf`에서 `token`과 `headerName`을 받습니다. 동시에 요청이 발생해도 진행 중인 토큰 발급 요청을 공유합니다.
3. 회원가입은 `POST /api/auth/register`로 처리합니다. 성공하면 가입한 이메일을 채운 로그인 화면으로 이동합니다.
4. 로그인은 CSRF 헤더를 포함한 `POST /api/auth/login`으로 처리합니다. 서버가 세션 ID와 CSRF 토큰을 교체하고, 프론트엔드는 토큰을 다시 받아 다음 요청을 준비합니다.
5. 로그인 후의 POST·PUT·DELETE에도 CSRF 헤더를 붙입니다. 쓰기 요청이 403을 받으면 토큰을 갱신하고 한 번만 재시도합니다.
6. 로그아웃 요청이 성공하면 인증 상태와 페이지를 초기화합니다. 서버는 세션을 무효화하고 세션 쿠키를 제거합니다.

로그인 화면의 잘못된 비밀번호 응답에는 `silentAuth: true`를 사용하여 해당 화면에 오류를 표시합니다. 일반 API의 401은 `session-expired` 이벤트를 발생시켜 로그인 화면으로 이동하고 세션 만료를 안내합니다.

### 조회·저장과 수정 충돌

`useResource(path)`는 `data`, `loading`, `error`, `reload`를 반환합니다. 주소나 새로고침 번호가 바뀌면 새 요청을 보내며, 이전 요청을 `AbortController`로 취소합니다. 결과에 요청 식별값을 함께 저장하여 정렬·인분 변경 직후 이전 결과를 새 조건의 데이터로 표시하지 않습니다. 화면이 사라진 뒤 도착한 응답도 반영하지 않습니다.

식재료 생성은 POST, 수정은 PUT, 삭제는 DELETE를 사용합니다. 성공하면 입력창을 닫고 목록을 재조회합니다. 삭제·로그아웃처럼 204를 반환하는 API는 빈 본문을 JSON으로 해석하지 않습니다.

수정 요청에는 편집창을 열 때 받은 `version`을 포함합니다. 서버는 로그인 사용자의 식재료인지 확인한 뒤 현재 버전과 비교하고, JPA의 `@Version`으로 동시 수정도 검사합니다. 다른 탭에서 먼저 수정했다면 409를 반환합니다. 화면은 입력창에서 충돌을 안내하고 ‘최신 목록 다시 불러오기’를 제공하며, 오래된 내용을 자동으로 덮어쓰지 않습니다.

### 오류 표시와 재시도

`ApiError`는 HTTP 상태, 오류 코드, 메시지, 필드별 오류를 보관합니다. `ErrorBox`가 메시지와 필드 검증 내용을 함께 표시합니다. 서버의 400·401·403·404·409·503 등을 구별할 수 있으며, JSON이 아닌 요청 형식과 지원하지 않는 응답 형식에는 각각 415·406을 사용합니다.

공통 요청에는 60초 제한이 있습니다. 접속 실패, 시간 초과, JSON을 읽을 수 없는 응답은 화면용 메시지로 변환합니다. 조회 오류에는 다시 시도 버튼을 제공하고, 입력 오류가 나면 입력창을 유지합니다. CSRF 갱신을 위한 한 번의 재전송을 제외하면 저장·삭제 요청을 자동 반복하지 않습니다.

## 4. 실행 방법

[README](../README.md)의 순서로 서버를 실행하고 다음 흐름을 확인합니다.

1. 회원가입 후 로그인하고 재료를 추가·수정·삭제합니다. 새로고침한 뒤에도 저장한 재료가 유지되는지 확인합니다.
2. 별도 브라우저 세션에서 다른 계정으로 로그인하여 첫 계정의 식재료가 보이지 않는지 확인합니다.
3. 같은 계정의 두 탭에서 같은 재료의 편집창을 열고, 한 탭에서 저장한 뒤 다른 탭에서 저장하면 충돌 안내가 나타납니다.
4. 한 탭에서 로그아웃한 뒤 다른 탭에서 조회하면 다시 로그인하라는 안내가 표시됩니다.
5. 추천 상세에서 1·2인분을 바꾸어 필요량과 부족량이 갱신되는지 확인합니다.

자동 통합 검증은 `backend` 폴더에서 실행합니다.

```powershell
cd backend
.\mvnw.cmd --batch-mode --no-transfer-progress test
```

자동 테스트의 DB는 격리된 H2 MySQL 모드입니다. 실제 MySQL 및 브라우저 검증과는 구분하며 결과는 [STEP 11](step-11.md)을 참고하세요.
