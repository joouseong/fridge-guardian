# STEP 8 — React 화면과 공통 컴포넌트

## 1. 구현 목적

회원가입부터 식재료 관리, 유통기한 확인, 레시피 선택, 요리 도우미까지 하나의 웹 화면에서 사용할 수 있게 합니다. 데스크톱과 모바일에서 같은 기능을 제공하며, 조회 중·오류·빈 목록을 구분하여 표시합니다.

## 2. 파일과 전체 코드

- [main.jsx](../frontend/src/main.jsx): React 진입점과 공통 스타일 적용
- [App.jsx](../frontend/src/App.jsx): 로그인 상태, 공통 메뉴, 페이지 선택
- [router.js](../frontend/src/lib/router.js): 주소·검색 조건 변경과 뒤로 가기 처리
- [AuthPage.jsx](../frontend/src/pages/AuthPage.jsx): 회원가입과 로그인
- [DashboardPage.jsx](../frontend/src/pages/DashboardPage.jsx): 재고 집계와 우선 소비할 재료
- [IngredientsPage.jsx](../frontend/src/pages/IngredientsPage.jsx): 검색·필터·정렬과 식재료 관리
- [RecipesPage.jsx](../frontend/src/pages/RecipesPage.jsx): 추천 목록과 1·2인분 선택
- [ChatPage.jsx](../frontend/src/pages/ChatPage.jsx): 요리 도우미 대화와 추천 메뉴
- [IngredientRow.jsx](../frontend/src/components/IngredientRow.jsx), [IngredientEditor.jsx](../frontend/src/components/IngredientEditor.jsx): 식재료 표시와 입력창
- [IngredientAdder.jsx](../frontend/src/components/IngredientAdder.jsx): 다중 선택·목록 붙여넣기·한 번에 추가
- [ingredientBatch.js](../frontend/src/lib/ingredientBatch.js): 목록 해석·입력 검증·API 요청 변환
- [RecipeCard.jsx](../frontend/src/components/RecipeCard.jsx), [RecipeDetail.jsx](../frontend/src/components/RecipeDetail.jsx): 레시피 카드와 상세창
- [UI.jsx](../frontend/src/components/UI.jsx): 공통 링크, 로딩, 오류, 빈 목록, 대화상자, 인분 선택
- [format.js](../frontend/src/lib/format.js): 한국 날짜·수량·단위·상태 표시
- [global.css](../frontend/src/styles/global.css): 화면 배치와 반응형 스타일

위 링크에서 실행에 사용하는 전체 소스를 확인할 수 있습니다. API 통신과 인증 연결은 [STEP 9](step-09.md)에서 설명합니다.

## 3. 코드 설명

`main.jsx`가 `App`을 렌더링합니다. `App`은 처음에 로그인 세션을 확인하고, 미로그인 상태에서는 인증 화면을, 로그인 상태에서는 메뉴와 선택한 페이지를 표시합니다. 세션 확인 자체가 실패하면 오류와 재시도 버튼을 보여 줍니다.

| 주소 | 화면 | 주요 기능 |
| --- | --- | --- |
| `/login` | 로그인 | 이메일·비밀번호 입력, 가입 완료·세션 만료 안내 |
| `/signup` | 회원가입 | 이메일·비밀번호·닉네임 입력, 가입 후 로그인 화면으로 이동 |
| `/` | 대시보드 | 전체·오늘 만료·임박·만료 재료 집계, 추천 상위 메뉴 |
| `/ingredients` | 내 냉장고 | 재료 추가·수정·삭제, 검색·상태·카테고리·보관 위치 필터 |
| `/recipes` | 오늘의 레시피 | 요리 이름 검색, 조리 가능한 메뉴 필터, 인분 선택 |
| `/chat` | 요리 도우미 | 질문 보내기, 대화 지우기, 응답 출처와 추천 메뉴 표시 |

`router.js`는 브라우저 History API와 `popstate`를 사용합니다. `usePath()`는 경로를, `useSearch()`는 검색 문자열을 구독합니다. 대시보드의 ‘오늘까지’를 누르면 `/ingredients?status=TODAY`로 이동하며 해당 조건을 목록에 적용합니다. 브라우저 뒤로 가기·앞으로 가기에서도 주소의 상태 필터가 반영됩니다. 공통 `Link`는 일반 클릭을 앱 내부 이동으로 처리하면서 새 탭 열기 등 브라우저 기본 동작을 유지합니다.

식재료 목록의 정렬은 서버에 요청하고, 이름 검색과 상태·카테고리·보관 위치 필터는 받은 목록에서 적용합니다. 재고가 비었을 때는 재료 추가 버튼을, 필터 결과만 비었을 때는 조건 초기화 버튼을 보여 줍니다. 삭제 전에는 대상 재료명을 확인하는 대화상자를 표시합니다.

대시보드와 내 냉장고의 추가 버튼은 같은 `IngredientAdder`를 엽니다. 38종을 여러 개 고르거나 쉼표·줄바꿈으로 구분한 장본 목록을 붙여넣고 한 번에 저장합니다. 데스크톱은 선택 목록과 수량 편집을 나란히, 모바일은 ‘수량·기한’으로 전환하며 하단 저장 버튼과 선택 요약은 유지합니다. 수량·단위는 바로 편집하고 날짜·보관 상세는 필요한 경우에만 펼칩니다. 유통기한은 처음부터 비어 있습니다.

목록 해석은 로컬 JavaScript로 처리하며 외부 AI를 사용하지 않습니다. 정확한 별칭은 정규화하고, 오타 후보나 잘못된 수량 등은 확인을 요구합니다. 잘못된 줄이 있으면 해당 붙여넣기를 일부만 적용하지 않습니다. 기존 선택의 중복 이름은 건너뛰고 수량을 유지합니다. 전송 오류 시 선택을 유지하고 내용이 같으면 UUID도 유지해 서버의 중복 방지 기능과 연결합니다. 한 요청의 내용이 달라지면 새 UUID를 생성합니다.

레시피 목록과 상세창의 인분을 바꾸면 서버가 필요량과 부족량을 다시 계산합니다. 상세창은 필요한 재료별 필요·보유·부족 수량, 단위 불일치, 조리 순서, 시간과 난이도를 표시합니다. 영양값은 인분 선택과 관계없이 1인분당 예시 추정값으로 표시합니다.

요리 도우미는 응답의 `source`에 따라 ‘AI 답변’ 또는 ‘기본 추천’을 표시합니다. 대화는 현재 화면의 React 상태에만 저장되어 다른 화면으로 이동하거나 새로고침하면 사라집니다. 오류가 발생하면 전송했던 요청을 다시 시도할 수 있습니다. 서버 연동은 [STEP 10](step-10.md)에서 설명합니다.

공통 대화상자는 브라우저의 `<dialog>`를 사용하여 키보드 포커스를 관리하고 닫힐 때 이전 위치로 돌려줍니다. 저장·삭제 중에는 버튼과 닫기를 잠시 막습니다. 오류는 `role="alert"`, 로딩과 처리 완료는 상태 알림으로 표시하며, 입력에는 레이블을 제공합니다. 유통기한 상태는 색상과 글자를 함께 사용합니다.

## 4. 실행 방법

[README](../README.md)에 따라 DB와 백엔드를 실행한 뒤, 프론트엔드 폴더에서 실행합니다.

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

`http://localhost:5173`에 접속하여 회원가입하고 로그인합니다. 재료를 추가한 뒤 대시보드 상태 카드, 냉장고 필터, 레시피 상세의 인분 선택을 확인합니다. 브라우저 폭을 줄여 메뉴·입력창·상세 표도 확인할 수 있습니다.

```powershell
npm.cmd run lint
npm.cmd run build
```

빌드 결과는 `frontend/dist/`에 생성됩니다. 위 검사는 소스와 빌드를 확인하며 실제 동작 검증 결과는 [STEP 11](step-11.md)에 기록합니다.
