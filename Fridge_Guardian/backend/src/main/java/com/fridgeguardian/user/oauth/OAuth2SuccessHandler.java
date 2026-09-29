package com.fridgeguardian.user.oauth;

import com.fridgeguardian.config.AppProperties;
import com.fridgeguardian.user.AuthService;
import com.fridgeguardian.user.UserAccount;
import com.fridgeguardian.user.token.RefreshCookies;
import com.fridgeguardian.user.token.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * 소셜 로그인 성공 후 사용자를 만들거나 찾고 Refresh Token 쿠키만 발급합니다.
 * Access Token은 URL에 싣지 않습니다. 프론트엔드가 /api/auth/refresh를 호출해 받아 갑니다.
 */
@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {
    private final OAuthProfileExtractor extractor;
    private final AuthService auth;
    private final RefreshTokenService refreshTokens;
    private final RefreshCookies cookies;
    private final AppProperties props;

    public OAuth2SuccessHandler(OAuthProfileExtractor extractor, AuthService auth, RefreshTokenService refreshTokens,
                                RefreshCookies cookies, AppProperties props) {
        this.extractor = extractor; this.auth = auth; this.refreshTokens = refreshTokens;
        this.cookies = cookies; this.props = props;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        var token = (OAuth2AuthenticationToken) authentication;
        OAuthProfile profile = extractor.extract(token.getAuthorizedClientRegistrationId(), token.getPrincipal());
        UserAccount user = auth.upsertSocialUser(profile);
        response.addHeader(HttpHeaders.SET_COOKIE, cookies.create(refreshTokens.issue(user)).toString());
        // 세션은 OAuth state 검증에만 필요했으므로 즉시 폐기합니다. 이후 API는 Bearer 토큰만 사용합니다.
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        response.sendRedirect(props.frontendUrl() + "/oauth/callback");
    }
}
