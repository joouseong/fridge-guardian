# Fridge Gatekeeper 작업 안내

이 저장소에서 작업을 시작할 때 [Codex 인수인계 문서](docs/CODEX_HANDOFF.md)를 먼저 읽으세요.
실행 방법은 [README](README.md), API 계약은 [docs/api.md](docs/api.md), 기존 검증 결과는 [STEP 11](docs/step-11.md)에 있습니다.
발표 자료는 [PRESENT.md](PRESENT.md)입니다. 기능·구조가 바뀌면 실제 구현과 일치하도록 함께 갱신하세요.

## 프로젝트 기준

- Java 21 / Spring Boot 4.1.1 / MySQL 8 / React 19 / Vite 8을 사용하는 냉장고 재료 관리·레시피 서비스입니다.
- 기존 구현을 이어서 수정하세요. 다음 작업의 범위는 사용자의 최신 요청으로 정합니다.
- 세션 인증·CSRF·사용자별 재고 격리, 한국 날짜 기준, 만료 재료 제외, 수량·단위·인분 계산을 유지하세요.
- API 계약을 바꾸면 백엔드·프론트엔드·관련 테스트·문서를 함께 갱신하세요.
- 이미 적용된 Flyway 마이그레이션을 수정하는 대신 후속 마이그레이션을 추가하세요.

## 실행과 검증

- Windows에서는 `scripts/start-db.ps1`, `scripts/start-backend.ps1`, `scripts/start-frontend.ps1`을 사용합니다. PowerShell 호출 예시는 README를 따르세요.
- 현재 PC의 시스템 Java는 작업 당시 17이었고, 프로젝트 전용 JDK 21은 `.local/`에 준비했습니다. 환경이 유지되어 있다고 가정하지 말고 확인하세요.
- 기본 검증: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify.ps1`
- DB와 서버가 필요한 검증: `node scripts/smoke-api.mjs`, `e2e` 폴더의 `npm.cmd test`.
- 변경 범위에 맞는 검증을 실행하고 결과를 사실대로 기록하세요. 과거 통과 결과를 새 변경의 검증 결과로 대신하지 마세요.

## 로컬 데이터

- `.local/mysql/data/`는 실제 회원·재고가 저장되는 디렉터리입니다. 초기화·삭제로 실행 문제를 해결하지 마세요.
- 기존 PC MySQL 서비스와 프로젝트 DB는 별개입니다. 프로세스 이름만으로 모든 MySQL 또는 Java 프로세스를 종료하지 마세요.
- `.env`, `.local/`, API 키·개인 DB 설정·로그·빌드 산출물은 커밋하지 마세요. `.env.example`은 공개 가능한 예시만 담습니다.
- OpenAI 테스트는 기본적으로 모의 HTTP 서버와 LOCAL 모드를 사용합니다. 실제 유료 호출이 필요한 작업은 별도로 구분하세요.

작업 후 동작이나 검증 방법이 바뀌면 인수인계 문서와 관련 안내를 함께 최신화하세요.
