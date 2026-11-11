package com.fridgeguardian.user;

import com.fridgeguardian.common.ApiException;
import com.fridgeguardian.user.oauth.OAuthProfile;
import com.fridgeguardian.user.token.JwtService;
import com.fridgeguardian.user.token.RefreshTokenService;
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
    private final JwtService jwt;
    private final RefreshTokenService refreshTokens;
    private final String dummyPasswordHash;

    public AuthService(UserRepository users, PasswordEncoder passwords, JwtService jwt, RefreshTokenService refreshTokens) {
        this.users = users;
        this.passwords = passwords;
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
        // 존재하지 않는 이메일도 BCrypt 검사를 수행하여 응답 시간 차이를 줄입니다.
        this.dummyPasswordHash = passwords.encode("unusable-dummy-password-for-timing");
    }

    /** 로그인 직후 발급한 두 토큰입니다. refreshToken 원문은 쿠키로만 내려보냅니다. */
    public record Session(String accessToken, String refreshToken, UserAccount user) { }

    @Transactional
    public AuthDtos.UserResponse register(AuthDtos.RegisterRequest request) {
        if (!request.password().equals(request.passwordConfirm())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "비밀번호 확인이 일치하지 않습니다.",
                Map.of("passwordConfirm", "비밀번호와 비밀번호 확인이 일치하지 않습니다."));
        }
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
        // 소셜 전용 계정(비밀번호 없음)도 더미 해시로 비교해 계정 존재 여부를 응답 시간으로 알 수 없게 합니다.
        String hash = user == null || user.getPasswordHash() == null ? dummyPasswordHash : user.getPasswordHash();
        boolean matches = passwords.matches(withinLimit ? request.password() : "invalid-too-long-password", hash);
        if (!withinLimit || user == null || user.getPasswordHash() == null || !matches) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호를 확인해 주세요.");
        }
        return user;
    }

    /** Access Token(15분)과 Refresh Token(14일)을 함께 발급합니다. */
    @Transactional
    public Session startSession(UserAccount user) {
        return new Session(jwt.issueAccessToken(user), refreshTokens.issue(user), user);
    }

    /** 쿠키의 Refresh Token을 회전시키고 새 Access Token을 발급합니다. */
    @Transactional(noRollbackFor = ApiException.class)
    public Session refresh(String presentedRefreshToken) {
        RefreshTokenService.Rotated rotated = refreshTokens.rotate(presentedRefreshToken);
        return new Session(jwt.issueAccessToken(rotated.user()), rotated.token(), rotated.user());
    }

    public AuthDtos.TokenResponse tokenResponse(Session session) {
        return new AuthDtos.TokenResponse(session.accessToken(), "Bearer", jwt.accessTtlSeconds(),
            AuthDtos.UserResponse.from(session.user()));
    }

    @Transactional(readOnly = true)
    public AuthDtos.UserResponse me(Long userId) {
        return AuthDtos.UserResponse.from(users.findById(userId).orElseThrow(() ->
            new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "다시 로그인해 주세요.")));
    }

    /** 닉네임을 바꾸고, 새 비밀번호가 있으면 현재 비밀번호를 확인한 뒤 바꿉니다. 소셜 전용 계정은 비밀번호를 바꿀 수 없습니다. */
    @Transactional
    public AuthDtos.UserResponse updateAccount(Long userId, AuthDtos.AccountUpdateRequest request) {
        UserAccount user = users.findById(userId).orElseThrow(() ->
            new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "다시 로그인해 주세요."));
        String nickname = request.nickname().strip();
        if (nickname.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "닉네임을 입력해 주세요.",
                Map.of("nickname", "닉네임을 입력해 주세요."));
        }
        boolean changePassword = notEmpty(request.currentPassword()) || notEmpty(request.newPassword())
            || notEmpty(request.newPasswordConfirm());
        if (changePassword) {
            if (user.getPasswordHash() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "소셜 로그인 계정은 비밀번호를 변경할 수 없습니다.");
            }
            if (!notEmpty(request.currentPassword()) || !passwords.matches(request.currentPassword(), user.getPasswordHash())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "현재 비밀번호가 일치하지 않습니다.",
                    Map.of("currentPassword", "현재 비밀번호가 일치하지 않습니다."));
            }
            String next = request.newPassword() == null ? "" : request.newPassword();
            if (next.length() < 6 || next.getBytes(StandardCharsets.UTF_8).length > 72) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "새 비밀번호를 확인해 주세요.",
                    Map.of("newPassword", "새 비밀번호는 6자 이상, UTF-8 72바이트 이하여야 합니다."));
            }
            if (!next.equals(request.newPasswordConfirm())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "새 비밀번호 확인이 일치하지 않습니다.",
                    Map.of("newPasswordConfirm", "새 비밀번호와 확인이 일치하지 않습니다."));
            }
            user.changePasswordHash(passwords.encode(next));
        }
        user.changeNickname(nickname);
        return AuthDtos.UserResponse.from(users.saveAndFlush(user));
    }

    private static boolean notEmpty(String value) { return value != null && !value.isEmpty(); }

    /**
     * 소셜 로그인 사용자를 찾거나 만듭니다.
     * 1) (제공자, 제공자 ID)가 같으면 같은 계정입니다.
     * 2) 제공자가 검증한 이메일이 기존 계정과 같을 때만 그 계정에 연결합니다. (미검증 이메일 자동 연결은 계정 탈취 경로)
     * 3) 그 외에는 새 계정을 만들며, 검증되지 않은 이메일은 저장하지 않습니다.
     */
    @Transactional
    public UserAccount upsertSocialUser(OAuthProfile profile) {
        if (profile.providerId() == null || profile.providerId().isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "소셜 계정 정보를 확인할 수 없습니다.");
        }
        return users.findByProviderAndProviderId(profile.provider(), profile.providerId()).orElseGet(() -> {
            String email = profile.emailVerified() && profile.email() != null && !profile.email().isBlank()
                ? canonicalEmail(profile.email()) : null;
            if (email != null) {
                var existing = users.findByEmail(email);
                if (existing.isPresent()) {
                    existing.get().linkSocial(profile.provider(), profile.providerId(), profile.imageUrl());
                    return users.saveAndFlush(existing.get());
                }
            }
            return users.saveAndFlush(UserAccount.social(profile.provider(), profile.providerId(), email,
                nickname(profile), profile.imageUrl()));
        });
    }

    private static String nickname(OAuthProfile profile) {
        String name = profile.nickname() == null ? "" : profile.nickname().strip();
        if (name.isEmpty()) name = "냉장고 문지기";
        return name.length() > 30 ? name.substring(0, 30) : name;
    }

    private static String canonicalEmail(String email) { return email.strip().toLowerCase(Locale.ROOT); }
    private static ApiException duplicateEmail() {
        return new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "이미 가입된 이메일입니다.",
            Map.of("email", "이미 가입된 이메일입니다."));
    }
}
