package com.fridgegatekeeper.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final SecurityContextRepository contexts;
    private final SessionAuthenticationStrategy sessions;

    public AuthController(AuthService auth, SecurityContextRepository contexts, SessionAuthenticationStrategy sessions) {
        this.auth = auth;
        this.contexts = contexts;
        this.sessions = sessions;
    }

    @GetMapping("/csrf")
    public AuthDtos.CsrfResponse csrf(CsrfToken token) {
        // getToken()을 호출해야 지연 생성되는 CSRF 토큰이 세션에 저장됩니다.
        return new AuthDtos.CsrfResponse(token.getToken(), token.getHeaderName());
    }

    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.UserResponse register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return auth.register(request);
    }

    @PostMapping("/login")
    public AuthDtos.UserResponse login(@Valid @RequestBody AuthDtos.LoginRequest body,
                                      HttpServletRequest request, HttpServletResponse response) {
        UserAccount user = auth.authenticate(body);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
            new SessionUser(user.getId(), user.getEmail()), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        sessions.onAuthentication(authentication, request, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        // JSON 로그인은 기본 로그인 필터를 사용하지 않으므로 세션 저장을 직접 수행합니다.
        contexts.saveContext(context, request, response);
        return AuthDtos.UserResponse.from(user);
    }

    @GetMapping("/me")
    public AuthDtos.UserResponse me(Authentication authentication) { return auth.me(CurrentUser.id(authentication)); }

    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        new CookieClearingLogoutHandler("JSESSIONID").logout(request, response, authentication);
    }
}
