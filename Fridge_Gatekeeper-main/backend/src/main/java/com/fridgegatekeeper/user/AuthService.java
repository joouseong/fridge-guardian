package com.fridgegatekeeper.user;

import com.fridgegatekeeper.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final String dummyPasswordHash;

    public AuthService(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
        // 존재하지 않는 이메일도 BCrypt 검사를 수행하여 응답 시간 차이를 줄입니다.
        this.dummyPasswordHash = passwords.encode("unusable-dummy-password-for-timing");
    }

    @Transactional
    public AuthDtos.UserResponse register(AuthDtos.RegisterRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "비밀번호 길이를 확인해 주세요.",
                Map.of("password", "한글·기호를 포함한 비밀번호는 UTF-8 72바이트 이하여야 합니다."));
        }
        String email = canonicalEmail(request.email());
        if (users.existsByEmail(email)) throw duplicateEmail();
        try {
            // flush 시점에 UNIQUE 제약을 확인하여 동시 가입도 409 오류로 반환합니다.
            return AuthDtos.UserResponse.from(users.saveAndFlush(
                new UserAccount(email, passwords.encode(request.password()), request.nickname().strip())));
        } catch (DataIntegrityViolationException error) {
            throw duplicateEmail();
        }
    }

    @Transactional(readOnly = true)
    public UserAccount authenticate(AuthDtos.LoginRequest request) {
        UserAccount user = users.findByEmail(canonicalEmail(request.email())).orElse(null);
        boolean withinLimit = request.password().getBytes(StandardCharsets.UTF_8).length <= 72;
        boolean matches = passwords.matches(withinLimit ? request.password() : "invalid-too-long-password",
            user == null ? dummyPasswordHash : user.getPasswordHash());
        if (!withinLimit || user == null || !matches) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호를 확인해 주세요.");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public AuthDtos.UserResponse me(Long userId) {
        return AuthDtos.UserResponse.from(users.findById(userId).orElseThrow(() ->
            new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "다시 로그인해 주세요.")));
    }

    private static String canonicalEmail(String email) { return email.strip().toLowerCase(Locale.ROOT); }
    private static ApiException duplicateEmail() {
        return new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "이미 가입된 이메일입니다.",
            Map.of("email", "이미 가입된 이메일입니다."));
    }
}
