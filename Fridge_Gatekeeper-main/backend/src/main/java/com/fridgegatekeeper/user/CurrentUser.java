package com.fridgegatekeeper.user;

import com.fridgegatekeeper.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

public final class CurrentUser {
    private CurrentUser() { }

    /** 사용자 ID는 요청 본문이 아니라 서버가 검증한 로그인 세션에서 가져옵니다. */
    public static Long id(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()
            && authentication.getPrincipal() instanceof SessionUser user) {
            return user.id();
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다.");
    }
}
