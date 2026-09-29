package com.fridgeguardian.user;

import com.fridgeguardian.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

public final class CurrentUser {
    private CurrentUser() { }

    /** 사용자 ID는 요청 본문이 아니라 서버가 서명을 검증한 Access Token의 sub에서 가져옵니다. */
    public static Long id(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()
            && authentication.getPrincipal() instanceof Jwt jwt) {
            try { return Long.valueOf(jwt.getSubject()); }
            catch (NumberFormatException ignored) { /* 아래에서 401 */ }
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다.");
    }
}
