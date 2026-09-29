package com.fridgeguardian.user;

import com.fridgeguardian.user.oauth.OAuth2FailureHandler;
import com.fridgeguardian.user.oauth.OAuth2SuccessHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * API는 Bearer JWT(Access Token)만으로 인증하는 stateless 구조입니다. 소셜 로그인 핸드셰이크(state 검증)에만
 * 세션이 잠시 필요하며, 성공·실패 핸들러가 곧바로 폐기합니다.
 */
@Configuration
public class SecurityConfiguration {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    /** 등록하지 않은 제공자(/oauth2/authorization/github 등)는 500 대신 404로 처리합니다. */
    @Bean
    OAuth2AuthorizationRequestResolver authorizationRequestResolver(ClientRegistrationRepository registrations) {
        var delegate = new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
        return new OAuth2AuthorizationRequestResolver() {
            @Override public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
                try { return delegate.resolve(request); }
                catch (IllegalArgumentException unknownProvider) { return null; } // InvalidClientRegistrationIdException(비공개 클래스)
            }
            @Override public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registrationId) {
                try { return delegate.resolve(request, registrationId); }
                catch (IllegalArgumentException unknownProvider) { return null; } // InvalidClientRegistrationIdException(비공개 클래스)
            }
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, OAuth2SuccessHandler success,
                                           OAuth2FailureHandler failure,
                                           OAuth2AuthorizationRequestResolver authorizationResolver) throws Exception {
        AuthenticationEntryPoint unauthorized = (request, response, error) ->
            writeError(response, 401, "UNAUTHORIZED", "로그인이 필요합니다.");
        AccessDeniedHandler denied = (request, response, error) ->
            writeError(response, 403, "FORBIDDEN", "요청 권한을 확인해 주세요.");
        http
            // Bearer 토큰은 브라우저가 자동으로 붙이지 않으므로 CSRF 공격면이 없습니다.
            // 쿠키를 쓰는 /api/auth/refresh·logout은 SameSite=Strict + OriginGuard로 보호합니다.
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/health", "/api/auth/providers").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login",
                    "/api/auth/refresh", "/api/auth/logout").permitAll()
                // 식재료 사진은 <img>가 Authorization 헤더를 보낼 수 없어, 추측할 수 없는 파일명으로 공개 제공합니다.
                .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                .requestMatchers("/oauth2/**", "/login/oauth2/**", "/error").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll())
            .oauth2Login(login -> login.successHandler(success).failureHandler(failure)
                .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(authorizationResolver)))
            .oauth2ResourceServer(resource -> resource
                .jwt(jwt -> { })
                .authenticationEntryPoint(unauthorized).accessDeniedHandler(denied))
            .exceptionHandling(errors -> errors.authenticationEntryPoint(unauthorized).accessDeniedHandler(denied));
        return http.build();
    }

    private static void writeError(HttpServletResponse response, int status, String code, String message)
        throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"fieldErrors\":{}}");
    }
}
