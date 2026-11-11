# API 통신

`api.js`는 인증, 식재료, 대시보드, 레시피, AI, 장바구니, 알림의 REST API를 호출합니다.

인증은 **Bearer JWT**입니다. Access Token(15분)은 메모에만 두고 `Authorization` 헤더로 보내며, Refresh Token은 HttpOnly 쿠키라 코드가 직접 읽지 않습니다. 401을 받으면 `/api/auth/refresh`를 **한 번만**(동시 요청이 여러 개여도) 보낸 뒤 원래 요청을 다시 시도하고, 갱신에 실패하면 `session-expired` 이벤트로 로그인 화면에 연결합니다. 토큰을 localStorage·URL에 두지 마세요. 통신 실패·잘못된 응답·요청 시간 초과는 화면에서 다시 시도할 수 있는 오류로 처리합니다.

개발·미리보기 서버는 `/api`, `/oauth2`, `/auth/{google|kakao}/callback`, `/uploads`를 백엔드에 전달합니다. 루트 `.env`의 `SERVER_PORT`를 반영하고, 별도 서버가 필요하면 `API_PROXY_TARGET`으로 주소 전체를 지정할 수 있습니다. 이 설정은 Vite 서버에서만 읽습니다. OpenAI API 키는 프런트엔드에 저장하거나 노출하지 않습니다.

요청·응답 형식은 [REST API 계약](../../../docs/api.md)을 참고하세요.
