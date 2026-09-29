# FreshFridge - 냉장고 식재료 관리 웹페이지

웹프로젝트 캡스톤디자인용으로 바로 실행할 수 있도록 만든 프로젝트입니다.

## 1. 프로젝트 개요

FreshFridge는 사용자가 냉장고 속 식재료와 유통기한을 등록하면 다음 기능을 제공하는 웹 서비스입니다.

- 회원가입 / 로그인 / 로그아웃
- 식재료 등록 / 수정 / 삭제
- 식재료 사진 업로드
- 구매일, 유통기한, 수량, 단위, 보관 위치 관리
- 유통기한 D-Day 자동 계산
- 3일 이내 임박 / 만료 / 여유 상태 구분
- 식재료 검색 및 상태 필터
- 보유한 식재료 기반 레시피 추천
- 레시피별 보유 재료 일치율 계산

## 2. 사용 기술

- Frontend: HTML(EJS), CSS, JavaScript
- Backend: Node.js, Express
- Database: MySQL
- Authentication: express-session, bcrypt
- File Upload: multer

## 3. 폴더 구조

```text
freshfridge_capstone/
├─ app.js
├─ package.json
├─ .env.example
├─ config/
│  └─ db.js
├─ middleware/
│  └─ auth.js
├─ routes/
│  ├─ auth.js
│  ├─ ingredients.js
│  └─ recipes.js
├─ views/
│  ├─ home.ejs
│  ├─ dashboard.ejs
│  ├─ error.ejs
│  ├─ auth/
│  ├─ ingredients/
│  ├─ recipes/
│  └─ partials/
├─ public/
│  ├─ css/style.css
│  └─ js/main.js
├─ uploads/
│  └─ .gitkeep
└─ sql/
   └─ schema.sql
```

## 4. 실행 순서

### 4-1. MySQL 준비

MySQL Workbench 또는 MySQL 콘솔에서 `sql/schema.sql` 파일을 실행합니다.

그러면 자동으로 다음 데이터베이스가 생성됩니다.

```text
freshfridge
```

테이블은 다음과 같습니다.

- users: 회원
- ingredients: 사용자가 등록한 식재료
- recipes: 레시피
- recipe_ingredients: 레시피에 필요한 식재료

### 4-2. 환경설정 파일 만들기

프로젝트 폴더의 `.env.example` 파일을 복사해 이름을 `.env`로 바꿉니다.

예시:

```env
PORT=3000
DB_HOST=localhost
DB_PORT=3306
DB_USER=root
DB_PASSWORD=본인_MySQL_비밀번호
DB_NAME=freshfridge
SESSION_SECRET=my-secret-key
```

### 4-3. 필요한 프로그램 설치

Node.js가 설치되어 있어야 합니다.

프로젝트 폴더에서 터미널을 열고 다음 명령어를 실행합니다.

```bash
npm install
```

### 4-4. 서버 실행

```bash
npm start
```

성공하면 다음 주소로 접속합니다.

```text
http://localhost:3000
```

## 5. 주요 화면

1. 메인 페이지
2. 회원가입
3. 로그인
4. 대시보드
5. 식재료 목록
6. 식재료 등록
7. 식재료 수정
8. 레시피 추천

## 6. D-Day 처리 방식

MySQL의 `DATEDIFF()` 함수를 사용합니다.

```sql
DATEDIFF(expiry_date, CURDATE())
```

예시:

- 5일 남음 → D-5
- 오늘까지 → D-0
- 2일 지남 → D+2

상태 기준:

- 3일 초과: 여유
- 0~3일: 유통기한 임박
- 0일 미만: 유통기한 만료

## 7. 레시피 추천 방식

현재 프로젝트는 별도의 외부 AI API가 없어도 동작하도록 구현했습니다.

사용자가 가진 식재료 목록과 각 레시피에 필요한 식재료를 비교하여 아래처럼 계산합니다.

```text
레시피 재료 4개 중 내가 가진 재료 3개
→ 일치율 75%
```

일치율이 높은 레시피부터 화면에 표시됩니다.

이 방식은 캡스톤 발표에서 "1차 추천 알고리즘"으로 설명하면 됩니다.

추후 실제 AI 기능을 추가한다면 다음과 같이 확장할 수 있습니다.

- OpenAI/Gemini API를 이용한 레시피 생성
- 사용자가 입력한 식재료 목록을 AI에 전달
- 알레르기 / 다이어트 / 고단백 등 조건 추가
- 사진을 이용한 식재료 자동 인식

## 8. 발표할 때 설명하면 좋은 내용

### 문제점

자취생이나 일반 가정에서는 냉장고에 어떤 식재료가 있는지 잊어버리기 쉽고,
유통기한이 지나 음식을 버리는 경우가 발생합니다.

### 해결 방법

식재료와 유통기한을 웹에서 관리하고,
유통기한이 얼마 남지 않은 식재료를 우선적으로 보여줍니다.

또한 가지고 있는 재료를 기준으로 만들 수 있는 레시피를 추천하여
식재료 낭비를 줄이는 것을 목표로 합니다.

### 기대 효과

- 음식물 낭비 감소
- 유통기한 관리 편리
- 장보기 전 냉장고 재고 확인
- 보유 식재료 활용도 증가
- 자취생 식단 관리 편의성 향상

## 9. ERD 구조

```text
users
  1
  │
  │ N
ingredients


recipes
  1
  │
  │ N
recipe_ingredients
```

관계 설명:

- 회원 1명은 여러 식재료를 등록할 수 있습니다.
- 레시피 1개는 여러 개의 필요한 식재료를 가질 수 있습니다.

## 10. 향후 추가하기 좋은 기능

- 카카오 / 구글 소셜 로그인
- 냉장고별 그룹 관리
- 가족과 식재료 공유
- 모바일 반응형 고도화
- 소비 완료 버튼
- 음식물 폐기량 통계
- 월별 식재료 구매 통계
- 이메일/카카오 알림
- 바코드 스캔
- AI 식재료 사진 인식
- AI 맞춤 레시피 생성
- 알레르기 재료 제외
- 다이어트/고단백/저탄수 옵션

## 11. 캡스톤 발표용 한 줄 설명

"냉장고 속 식재료의 유통기한을 관리하고, 보유 재료를 기반으로 활용 가능한 레시피를 추천하여 음식물 낭비를 줄이는 웹 서비스입니다."
