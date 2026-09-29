package com.fridgeguardian.user.oauth;

import com.fridgeguardian.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuth2FailureHandler implements AuthenticationFailureHandler {
    private static final Logger log = LoggerFactory.getLogger(OAuth2FailureHandler.class);
    private final AppProperties props;

    public OAuth2FailureHandler(AppProperties props) { this.props = props; }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        // 제공자 오류 본문에는 코드·토큰이 섞일 수 있어 종류만 기록합니다.
        log.warn("소셜 로그인에 실패했습니다: {}", exception.getClass().getSimpleName());
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        response.sendRedirect(props.frontendUrl() + "/login?error=oauth");
    }
}
