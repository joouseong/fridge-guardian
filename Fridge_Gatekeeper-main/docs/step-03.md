# STEP 3 — 회원 기능

## 1. 구현 목적

이메일과 비밀번호로 회원가입·로그인하고, 서버 세션으로 사용자를 구분합니다. 식재료를 조회할 때 클라이언트가 보낸 사용자 ID를 믿지 않고 로그인 세션의 ID를 사용합니다.

## 2. 생성 파일

- [UserRepository.java](../backend/src/main/java/com/fridgegatekeeper/user/UserRepository.java): 이메일 조회와 회원 저장
- [AuthDtos.java](../backend/src/main/java/com/fridgegatekeeper/user/AuthDtos.java): 입력 검증과 비밀번호 없는 응답
- [AuthService.java](../backend/src/main/java/com/fridgegatekeeper/user/AuthService.java): 가입·비밀번호 확인
- [AuthController.java](../backend/src/main/java/com/fridgegatekeeper/user/AuthController.java): REST API와 세션 처리
- [SecurityConfiguration.java](../backend/src/main/java/com/fridgegatekeeper/user/SecurityConfiguration.java): 인증·CSRF 설정
- [SessionUser.java](../backend/src/main/java/com/fridgegatekeeper/user/SessionUser.java), [CurrentUser.java](../backend/src/main/java/com/fridgegatekeeper/user/CurrentUser.java): 로그인 사용자 식별
- [GlobalExceptionHandler.java](../backend/src/main/java/com/fridgegatekeeper/common/GlobalExceptionHandler.java): 일관된 JSON 오류 응답

## 3. 전체 코드

각 링크가 실행 가능한 전체 소스입니다. 공통 오류에는 [ApiException.java](../backend/src/main/java/com/fridgegatekeeper/common/ApiException.java), [ApiError.java](../backend/src/main/java/com/fridgegatekeeper/common/ApiError.java)를 사용합니다.

## 4. 코드 설명

이메일은 소문자로 저장하고 UNIQUE 제약으로 중복 가입을 막습니다. 비밀번호는 BCrypt로 해시하며 8자 이상, UTF-8 기준 72바이트 이하만 허용합니다. `users.password`에는 원문을 저장하지 않습니다. 가입은 자동 로그인하지 않으므로 별도로 로그인합니다.

로그인 성공 시 세션 ID와 CSRF 토큰을 교체하고 `SecurityContextRepository`에 인증 결과를 저장합니다. 브라우저는 HttpOnly 세션 쿠키를 자동 전송합니다. 화면은 처음에 `GET /api/auth/csrf`로 받은 `token`을 응답의 `headerName` 헤더에 넣어 POST/PUT/DELETE 요청을 보냅니다. 로그인·로그아웃 후에는 토큰을 다시 받아야 합니다. 프론트엔드 개발 서버의 프록시를 사용하므로 전체 도메인을 허용하는 CORS 설정은 없습니다.

| 요청 | 결과 |
| --- | --- |
| `GET /api/auth/csrf` | `{token, headerName}` |
| `POST /api/auth/register` | 201, `{id, email, nickname}` |
| `POST /api/auth/login` | 200, `{id, email, nickname}` 및 로그인 세션 |
| `GET /api/auth/me` | 현재 사용자 또는 401 |
| `POST /api/auth/logout` | 204, 세션 무효화 |

가입 입력 예: `{"email":"cook@example.com","password":"fridge1234!","nickname":"냉장고지기"}`. 로그인은 `email`, `password`만 보냅니다. 오류는 `{code,message,fieldErrors}`이며 비밀번호 해시와 내부 SQL을 포함하지 않습니다.

## 5. 실행 방법

루트 README의 MySQL 실행·환경변수 설정을 마친 후 `backend`에서 `./mvnw.cmd spring-boot:run`을 실행합니다. STEP 8~9 화면 완성 후 로그인 화면에서 가입과 로그인을 확인할 수 있습니다. 자동 검증은 `./mvnw.cmd test`입니다.
