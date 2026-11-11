package com.fridgeguardian.user;

import com.fridgeguardian.common.ApiException;
import com.fridgeguardian.user.token.RefreshCookies;
import com.fridgeguardian.user.token.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final RefreshTokenService refreshTokens;
    private final RefreshCookies cookies;
    private final OriginGuard originGuard;
    private final boolean googleEnabled;
    private final boolean kakaoEnabled;

    public AuthController(AuthService auth, RefreshTokenService refreshTokens, RefreshCookies cookies, OriginGuard originGuard,
                          @Value("${spring.security.oauth2.client.registration.google.client-id:not-configured}") String googleId,
                          @Value("${spring.security.oauth2.client.registration.kakao.client-id:not-configured}") String kakaoId) {
        this.auth = auth; this.refreshTokens = refreshTokens; this.cookies = cookies; this.originGuard = originGuard;
        this.googleEnabled = configured(googleId);
        this.kakaoEnabled = configured(kakaoId);
    }

    /** 키가 설정된 소셜 로그인만 화면에 버튼으로 보여 줄 수 있게 알려 줍니다. */
    @GetMapping("/providers")
    public AuthDtos.Providers providers() { return new AuthDtos.Providers(googleEnabled, kakaoEnabled); }

    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.UserResponse register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return auth.register(request);
    }

    @PostMapping("/login")
    public AuthDtos.TokenResponse login(@Valid @RequestBody AuthDtos.LoginRequest body, HttpServletResponse response) {
        return issue(auth.startSession(auth.authenticate(body)), response);
    }

    /** 쿠키의 Refresh Token으로 새 Access Token을 받습니다. 사용한 Refresh Token은 즉시 교체됩니다. */
    @PostMapping("/refresh")
    public AuthDtos.TokenResponse refresh(@CookieValue(name = RefreshCookies.NAME, required = false) String token,
                                          HttpServletRequest request, HttpServletResponse response) {
        originGuard.check(request);
        if (token == null || token.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다.");
        }
        try {
            return issue(auth.refresh(token), response);
        } catch (ApiException expired) {
            // 만료·폐기된 쿠키는 브라우저에서도 지웁니다.
            response.addHeader(HttpHeaders.SET_COOKIE, cookies.clear().toString());
            throw expired;
        }
    }

    @GetMapping("/me")
    public AuthDtos.UserResponse me(Authentication authentication) { return auth.me(CurrentUser.id(authentication)); }

    /** 닉네임 변경과 (선택) 비밀번호 변경. */
    @PutMapping("/me")
    public AuthDtos.UserResponse updateMe(Authentication authentication, @Valid @RequestBody AuthDtos.AccountUpdateRequest request) {
        return auth.updateAccount(CurrentUser.id(authentication), request);
    }

    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = RefreshCookies.NAME, required = false) String token,
                       HttpServletRequest request, HttpServletResponse response) {
        originGuard.check(request);
        if (token != null && !token.isBlank()) refreshTokens.revokeFamilyOf(token);
        response.addHeader(HttpHeaders.SET_COOKIE, cookies.clear().toString());
    }

    private AuthDtos.TokenResponse issue(AuthService.Session session, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookies.create(session.refreshToken()).toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return auth.tokenResponse(session);
    }

    private static boolean configured(String clientId) {
        return clientId != null && !clientId.isBlank() && !"not-configured".equals(clientId);
    }
}
