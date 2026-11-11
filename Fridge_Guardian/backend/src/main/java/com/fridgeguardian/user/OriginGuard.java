package com.fridgeguardian.user;

import com.fridgeguardian.common.ApiException;
import com.fridgeguardian.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 쿠키만으로 동작하는 /api/auth/refresh·logout은 CSRF 토큰이 없으므로, 브라우저가 보내는 Origin이
 * 우리 프론트엔드(또는 같은 호스트)가 아니면 거절합니다. SameSite=Strict 쿠키와 함께 이중으로 방어합니다.
 */
@Component
public class OriginGuard {
    private final String frontendAuthority;

    public OriginGuard(AppProperties props) {
        this.frontendAuthority = URI.create(props.frontendUrl()).getAuthority();
    }

    public void check(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) return; // 브라우저가 아닌 클라이언트(테스트 도구 등)
        String authority;
        try { authority = URI.create(origin).getAuthority(); }
        catch (IllegalArgumentException invalid) { authority = null; }
        if (authority == null || !(authority.equalsIgnoreCase(frontendAuthority)
            || authority.equalsIgnoreCase(request.getHeader("Host")))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "허용되지 않은 요청 출처입니다.");
        }
    }
}
