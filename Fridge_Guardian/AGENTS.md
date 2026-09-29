# Fridge Guardian 작업 안내

작업 전에 [README](README.md)(실행·검증·미확인 항목), [설계 문서](docs/DESIGN.md), [API 계약](docs/api.md), [DB 설계](database/README.md), 요구사항 원본 `../냉문.md`를 읽으세요. 기능·구조가 바뀌면 이 문서들과 테스트를 함께 갱신하세요.

## 프로젝트 기준

- Java 21 / Spring Boot 4.1.1 / MySQL 8 / React 19 / Vite 8. 프런트엔드는 JavaScript(JSX)와 자체 History 라우터입니다.
- 인증은 **Bearer JWT(Access 15분, 메모) + HttpOnly Refresh 쿠키(회전)** 입니다. 사용자 ID는 토큰의 `sub`에서만 얻고(`CurrentUser`) 요청 본문을 믿지 않습니다. 토큰을 localStorage·URL에 두지 마세요.
- 사용자별 재고 격리(타인 ID는 404), 한국 날짜(Asia/Seoul `Clock`), 만료 재료 추천 제외, 수량·단위·인분 계산 규칙을 유지하세요.
- D-Day·상태(임박/여유/만료)는 저장하지 않고 계산합니다.
- 적용된 Flyway 마이그레이션(V1~V9)은 수정하지 말고 **V10부터 추가**하세요. Spring Batch 스키마는 MySQL 전용 `db/vendor/mysql`에 있습니다.
- AI 출력은 신뢰하지 않습니다(서버 검증·보유/부족 재계산). AI 실패를 성공처럼 보이거나 자동으로 다른 방식으로 바꾸지 마세요.
- API 계약을 바꾸면 백엔드·프런트엔드·테스트·문서를 함께 바꾸세요.

## 실행과 검증

- 공통(Windows·macOS): `npm run dev`(DB+백엔드+프런트), `npm run db:stop`. 개별 실행은 README 참고. 기존 `scripts/*.ps1`은 Windows 전용 대안.
- 기본 검증: `npm run verify` (H2로 실행, MySQL·서버 불필요)
- 실서버 검증: `node scripts/smoke-api.mjs`, `npm run e2e`(백엔드·Vite·MySQL 실행 필요, Chrome 자동 탐지, `BROWSER_EXECUTABLE`로 지정 가능)
- Playwright에서 `<label>` 안에 `select`·`textarea`가 있으면 `getByLabel(exact)`가 옵션·값 텍스트까지 비교해 실패합니다. `getByRole('combobox'|'textbox', { name })`을 쓰세요.
- 변경 범위에 맞는 검증을 실행하고 결과를 사실대로 기록하세요. 과거 통과 결과를 새 변경의 결과로 대신하지 마세요. 실제 유료 AI 호출(`LIVE_OPENAI=1`)은 명시적으로 요청받았을 때만 실행하세요.

## 로컬 데이터

- `.local/mysql/data/`는 실제 회원·재고가 저장되는 디렉터리입니다. 초기화·삭제로 문제를 해결하지 마세요. 기존 PC MySQL 서비스(3306)와 프로젝트 DB(3307)는 별개이며, 프로세스 이름만으로 모든 MySQL·Java 프로세스를 종료하지 마세요.
- `.env`, `.local/`, API 키, 로그, 빌드 결과, 업로드 사진은 커밋하지 마세요. `.env.example`에는 공개 가능한 예시만 넣습니다.
- 테스트 회원(`smoke-`, `e2e-` 이메일)은 DB에 남을 수 있습니다. 넓은 범위의 사용자 데이터 삭제로 정리하지 마세요.
