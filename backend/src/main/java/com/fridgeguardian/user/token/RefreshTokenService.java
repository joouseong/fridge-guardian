package com.fridgeguardian.user.token;

import com.fridgeguardian.common.ApiException;
import com.fridgeguardian.config.AppProperties;
import com.fridgeguardian.user.UserAccount;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {
    /** 방금 회전된 토큰이 다시 오면(여러 탭의 동시 갱신) 탈취로 단정하지 않고 재시도만 안내합니다. */
    static final Duration RACE_GRACE = Duration.ofSeconds(10);
    private final RefreshTokenRepository tokens;
    private final AppProperties props;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository tokens, AppProperties props, Clock clock) {
        this.tokens = tokens; this.props = props; this.clock = clock;
    }

    public record Rotated(UserAccount user, String token) { }

    /** 새 로그인마다 새 family로 발급합니다. 반환값은 쿠키에만 실을 원문 토큰입니다. */
    @Transactional
    public String issue(UserAccount user) { return issue(user, UUID.randomUUID().toString()); }

    private String issue(UserAccount user, String familyId) {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        tokens.save(new RefreshToken(user, sha256(token), familyId,
            LocalDateTime.now(clock).plus(props.jwt().refreshTtl())));
        return token;
    }

    /** 사용한 토큰은 폐기하고 같은 family의 새 토큰을 발급합니다. 폐기된 토큰이 재사용되면 family 전체를 무효화합니다. */
    @Transactional(noRollbackFor = ApiException.class)
    public Rotated rotate(String presented) {
        LocalDateTime now = LocalDateTime.now(clock);
        RefreshToken current = tokens.findForRotation(sha256(presented)).orElseThrow(RefreshTokenService::expired);
        if (current.isRevoked()) {
            if (current.getRevokedAt().plus(RACE_GRACE).isBefore(now)) {
                tokens.revokeFamily(current.getFamilyId(), now);
            }
            throw expired();
        }
        if (current.isExpired(now)) throw expired();
        current.revoke(now);
        return new Rotated(current.getUser(), issue(current.getUser(), current.getFamilyId()));
    }

    /** 로그아웃: 이 토큰이 속한 로그인 세션(family)을 모두 폐기합니다. */
    @Transactional
    public void revokeFamilyOf(String presented) {
        tokens.findByTokenHash(sha256(presented))
            .ifPresent(token -> tokens.revokeFamily(token.getFamilyId(), LocalDateTime.now(clock)));
    }

    /** 만료된 지 하루가 지난 기록을 정리합니다. */
    @Transactional
    public int purgeExpired() { return tokens.deleteExpiredBefore(LocalDateTime.now(clock).minusDays(1)); }

    private static ApiException expired() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 만료되었습니다. 다시 로그인해 주세요.");
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
