package com.fridgegatekeeper.user;

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
        @Size(min = 8, max = 72, message = "비밀번호는 8자 이상, UTF-8 72바이트 이하여야 합니다.") String password,
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

    public record UserResponse(Long id, String email, String nickname) {
        public static UserResponse from(UserAccount user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getNickname());
        }
    }

    public record CsrfResponse(String token, String headerName) {
        @Override public String toString() { return "CsrfResponse[redacted]"; }
    }
}
