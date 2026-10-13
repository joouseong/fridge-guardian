package com.fridgeguardian.user.oauth;

import com.fridgeguardian.common.ApiException;
import com.fridgeguardian.user.AuthProvider;
import com.fridgeguardian.user.AuthService;
import com.fridgeguardian.user.UserAccount;
import com.fridgeguardian.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Google/Kakao 응답 해석, 계정 연결 규칙, 성공 핸들러(토큰을 URL에 싣지 않음), 인증 시작 주소를 검증합니다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SocialLoginTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired OAuthProfileExtractor extractor;
    @Autowired OAuth2SuccessHandler successHandler;

    @BeforeEach void clean() { users.deleteAll(); }

    @Test void googleAndKakaoProfilesAreNormalized() {
        OAuthProfile google = extractor.extract("google", Map.of("sub", "1234567890", "email", "Cook@Gmail.com",
            "email_verified", true, "name", "구글 요리사", "picture", "https://example.com/g.png"));
        assertThat(google).isEqualTo(new OAuthProfile(AuthProvider.GOOGLE, "1234567890", "Cook@Gmail.com", true,
            "구글 요리사", "https://example.com/g.png"));

        Map<String, Object> kakao = Map.of("id", 987654321L, "kakao_account", Map.of("email", "cook@kakao.com",
            "is_email_valid", true, "is_email_verified", true,
            "profile", Map.of("nickname", "카카오 요리사", "profile_image_url", "https://example.com/k.png")));
        assertThat(extractor.extract("kakao", kakao)).isEqualTo(new OAuthProfile(AuthProvider.KAKAO, "987654321",
            "cook@kakao.com", true, "카카오 요리사", "https://example.com/k.png"));
        // 이메일 동의가 없거나 인증되지 않았다면 verified는 false이고 없는 항목은 null입니다.
        assertThat(extractor.extract("kakao", Map.of("id", 1L, "kakao_account", Map.of("email", "x@kakao.com", "is_email_valid", true))))
            .satisfies(profile -> { assertThat(profile.emailVerified()).isFalse(); assertThat(profile.nickname()).isNull(); });
        assertThat(extractor.extract("kakao", Map.of("id", 2L)).email()).isNull();
        assertThat(extractor.extract("google", Map.of("sub", "1", "email", "a@b.c")).emailVerified()).isFalse();
        assertThatThrownBy(() -> extractor.extract("github", Map.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void sameProviderAccountIsReusedAndNewOnesHaveNoPassword() {
        UserAccount first = auth.upsertSocialUser(new OAuthProfile(AuthProvider.KAKAO, "111", null, false, "카카오", null));
        UserAccount again = auth.upsertSocialUser(new OAuthProfile(AuthProvider.KAKAO, "111", null, false, "이름이 바뀜", null));
        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(first.getPasswordHash()).isNull();
        assertThat(first.getEmail()).isNull();
        assertThat(first.getProvider()).isEqualTo(AuthProvider.KAKAO);
        // 같은 번호라도 제공자가 다르면 다른 계정입니다.
        UserAccount google = auth.upsertSocialUser(new OAuthProfile(AuthProvider.GOOGLE, "111", null, false, "구글", null));
        assertThat(google.getId()).isNotEqualTo(first.getId());
        assertThat(users.count()).isEqualTo(2);
        assertThatThrownBy(() -> auth.upsertSocialUser(new OAuthProfile(AuthProvider.GOOGLE, " ", null, false, "이름", null)))
            .isInstanceOf(ApiException.class);
    }

    @Test void onlyAVerifiedEmailLinksToAnExistingPasswordAccount() throws Exception {
        UserAccount local = users.saveAndFlush(new UserAccount("cook@example.com", "$2a$12$unused", "기존"));
        // 인증되지 않은 이메일로는 기존 계정을 가져갈 수 없습니다. 이메일을 저장하지 않고 별도 계정을 만듭니다.
        UserAccount stranger = auth.upsertSocialUser(new OAuthProfile(AuthProvider.KAKAO, "att", "COOK@example.com", false, "공격자", null));
        assertThat(stranger.getId()).isNotEqualTo(local.getId());
        assertThat(stranger.getEmail()).isNull();
        // 제공자가 검증한 같은 이메일이면 기존 계정에 연결하고 이후 같은 소셜 계정은 그 계정으로 로그인합니다.
        UserAccount linked = auth.upsertSocialUser(new OAuthProfile(AuthProvider.GOOGLE, "g-1", "COOK@example.com", true, "구글", "https://img"));
        assertThat(linked.getId()).isEqualTo(local.getId());
        assertThat(linked.getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(linked.getPasswordHash()).isEqualTo("$2a$12$unused"); // 비밀번호 로그인도 계속 가능
        assertThat(auth.upsertSocialUser(new OAuthProfile(AuthProvider.GOOGLE, "g-1", null, false, "구글", null)).getId())
            .isEqualTo(local.getId());
        // 소셜 전용 계정은 비밀번호 로그인이 불가능합니다.
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"cook@example.com\",\"password\":\"anything1\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test void successHandlerIssuesOnlyARefreshCookieAndTheFrontendExchangesItForAnAccessToken() throws Exception {
        var attributes = Map.<String, Object>of("sub", "g-777", "email", "new@gmail.com", "email_verified", true, "name", "새 사용자");
        var principal = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")), attributes, "sub");
        var authentication = new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
        var request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        var response = new MockHttpServletResponse();

        successHandler.onAuthenticationSuccess(request, response, authentication);

        // 성공 후 프론트엔드로만 이동하고 주소에 토큰이 없습니다. OAuth 핸드셰이크용 세션은 폐기됩니다.
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/oauth/callback");
        assertThat(session.isInvalid()).isTrue();
        String header = response.getHeader("Set-Cookie");
        assertThat(header).contains("refresh_token=", "HttpOnly", "SameSite=Strict", "Path=/api/auth");
        assertThat(users.findByEmail("new@gmail.com")).isPresent();

        Cookie refresh = response.getCookie("refresh_token");
        var exchanged = mvc.perform(post("/api/auth/refresh").cookie(refresh)).andExpect(status().isOk())
            .andExpect(jsonPath("$.user.provider").value("GOOGLE")).andExpect(jsonPath("$.user.nickname").value("새 사용자"))
            .andReturn();
        String accessToken = JsonPath.read(exchanged.getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken)).andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("new@gmail.com"));
    }

    @Test void providerAuthorizationEndpointsRedirectToTheProviders() throws Exception {
        mvc.perform(get("/oauth2/authorization/google")).andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("https://accounts.google.com/o/oauth2/v2/auth?")))
            .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("scope=openid%20profile%20email")))
            .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("redirect_uri=http://localhost/auth/google/callback")));
        mvc.perform(get("/oauth2/authorization/kakao")).andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("https://kauth.kakao.com/oauth/authorize?")))
            .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("scope=profile_nickname%20account_email")))
            .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("redirect_uri=http://localhost/auth/kakao/callback")));
        // 등록하지 않은 제공자는 인증을 시작하지 않습니다.
        mvc.perform(get("/oauth2/authorization/github")).andExpect(status().is4xxClientError());
    }

    @Test void failedProviderLoginReturnsToTheLoginPageWithoutLeakingDetails() throws Exception {
        // 인증 코드가 잘못된 콜백은 실패 핸들러가 프론트엔드 로그인 화면으로 돌려보냅니다.
        var result = mvc.perform(get("/auth/google/callback").param("code", "bad").param("state", "bad")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("http://localhost:3000/login?error=oauth");
        assertThat(result.getResponse().getCookie("refresh_token")).isNull();
    }
}
