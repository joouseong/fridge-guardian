package com.fridgeguardian.user.oauth;

import com.fridgeguardian.user.AuthProvider;
import java.util.Map;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/** Google(OIDC)과 Kakao(user-info) 응답 모양을 OAuthProfile로 정규화합니다. */
@Component
public class OAuthProfileExtractor {
    public OAuthProfile extract(String registrationId, OAuth2User user) {
        return extract(registrationId, user.getAttributes());
    }

    @SuppressWarnings("unchecked")
    public OAuthProfile extract(String registrationId, Map<String, Object> attributes) {
        return switch (registrationId) {
            case "google" -> new OAuthProfile(AuthProvider.GOOGLE, text(attributes.get("sub")),
                text(attributes.get("email")), Boolean.TRUE.equals(attributes.get("email_verified")),
                text(attributes.get("name")), text(attributes.get("picture")));
            case "kakao" -> {
                Map<String, Object> account = attributes.get("kakao_account") instanceof Map<?, ?> map
                    ? (Map<String, Object>) map : Map.of();
                Map<String, Object> profile = account.get("profile") instanceof Map<?, ?> map
                    ? (Map<String, Object>) map : Map.of();
                // 카카오는 이메일이 있어도 유효·인증 여부를 별도 필드로 알려 줍니다.
                boolean verified = Boolean.TRUE.equals(account.get("is_email_valid"))
                    && Boolean.TRUE.equals(account.get("is_email_verified"));
                yield new OAuthProfile(AuthProvider.KAKAO, text(attributes.get("id")), text(account.get("email")),
                    verified, text(profile.get("nickname")), text(profile.get("profile_image_url")));
            }
            default -> throw new IllegalArgumentException("지원하지 않는 로그인 방식입니다: " + registrationId);
        };
    }

    private static String text(Object value) { return value == null ? null : String.valueOf(value); }
}
