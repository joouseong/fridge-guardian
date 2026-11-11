package com.fridgeguardian.user.token;

import com.fridgeguardian.config.AppProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Refresh Token 쿠키: JavaScript가 읽을 수 없고(HttpOnly) 다른 사이트 요청에는 전송되지 않습니다(Strict). */
@Component
public class RefreshCookies {
    public static final String NAME = "refresh_token";
    private final AppProperties props;

    public RefreshCookies(AppProperties props) { this.props = props; }

    public ResponseCookie create(String token) { return build(token, props.jwt().refreshTtl()); }
    public ResponseCookie clear() { return build("", Duration.ZERO); }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value).httpOnly(true).secure(props.jwt().cookieSecure())
            .sameSite("Strict").path("/api/auth").maxAge(maxAge).build();
    }
}
