package com.fridgeguardian.user.token;

import com.fridgeguardian.user.UserAccount;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 원문은 브라우저 쿠키에만 있고 DB에는 SHA-256 해시만 저장합니다. */
@Entity @Table(name = "refresh_tokens")
public class RefreshToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false) private UserAccount user;
    @Column(nullable = false, length = 64) private String tokenHash;
    @Column(nullable = false, length = 36) private String familyId;
    @Column(nullable = false) private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;

    protected RefreshToken() { }
    public RefreshToken(UserAccount user, String tokenHash, String familyId, LocalDateTime expiresAt) {
        this.user = user; this.tokenHash = tokenHash; this.familyId = familyId; this.expiresAt = expiresAt;
    }
    public UserAccount getUser() { return user; }
    public String getFamilyId() { return familyId; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public boolean isRevoked() { return revokedAt != null; }
    public boolean isExpired(LocalDateTime now) { return !expiresAt.isAfter(now); }
    public void revoke(LocalDateTime now) { if (revokedAt == null) revokedAt = now; }
}
