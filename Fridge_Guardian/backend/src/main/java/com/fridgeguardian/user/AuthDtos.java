package com.fridgeguardian.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() { }

    public record RegisterRequest(
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "올바른 이메일을 입력해 주세요.")
        @Size(max = 254, message = "이메일은 254자 이하여야 합니다.") String email,
        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Size(min = 6, max = 72, message = "비밀번호는 6자 이상, UTF-8 72바이트 이하여야 합니다.") String password,
        @NotBlank(message = "비밀번호 확인을 입력해 주세요.") String passwordConfirm,
        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Size(max = 30, message = "닉네임은 30자 이하여야 합니다.") String nickname
    ) {
        // Spring MVC의 DEBUG 로그가 DTO를 출력해도 비밀번호 원문은 남기지 않습니다.
        @Override public String toString() { return "RegisterRequest[redacted]"; }
    }

    public record LoginRequest(
        @NotBlank(message = "이메일을 입력해 주세요.") @Size(max = 254) String email,
        @NotBlank(message = "비밀번호를 입력해 주세요.") @Size(max = 72) String password
    ) {
        @Override public String toString() { return "LoginRequest[redacted]"; }
    }

    public record UserResponse(Long id, String email, String nickname, AuthProvider provider, String profileImageUrl) {
        public static UserResponse from(UserAccount user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getNickname(), user.getProvider(),
                user.getProfileImageUrl());
        }
    }

    /** Access Token은 응답 본문으로, Refresh Token은 HttpOnly 쿠키로만 내려갑니다. */
    public record TokenResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
        @Override public String toString() { return "TokenResponse[redacted]"; }
    }

    public record Providers(boolean google, boolean kakao) { }
}
