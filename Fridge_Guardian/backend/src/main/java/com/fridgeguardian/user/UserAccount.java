package com.fridgeguardian.user;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 응답에는 Entity 대신 DTO를 사용하여 비밀번호 해시 노출을 방지합니다. */
@Entity @Table(name = "users")
public class UserAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    // 소셜 로그인은 이메일 동의를 받지 못할 수 있어 비어 있을 수 있습니다.
    @Column(unique=true, length=254) private String email;
    // 소셜 계정은 비밀번호가 없습니다.
    @Column(name="password", length=100) private String passwordHash;
    @Column(nullable=false, length=30) private String nickname;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private AuthProvider provider = AuthProvider.LOCAL;
    @Column(length=100) private String providerId;
    @Column(length=500) private String profileImageUrl;
    @Column(name="created_at", nullable=false, insertable=false, updatable=false) private LocalDateTime createdAt;
    protected UserAccount() {}
    public UserAccount(String email, String passwordHash, String nickname) {
        this.email=email; this.passwordHash=passwordHash; this.nickname=nickname;
    }
    public static UserAccount social(AuthProvider provider, String providerId, String email, String nickname, String imageUrl) {
        UserAccount user = new UserAccount(email, null, nickname);
        user.provider = provider; user.providerId = providerId; user.profileImageUrl = imageUrl;
        return user;
    }
    /** 검증된 이메일이 같은 기존 계정에 소셜 로그인을 연결합니다. 비밀번호 로그인도 계속 사용할 수 있습니다. */
    public void linkSocial(AuthProvider provider, String providerId, String imageUrl) {
        this.provider = provider; this.providerId = providerId;
        if (this.profileImageUrl == null) this.profileImageUrl = imageUrl;
    }
    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getNickname() { return nickname; }
    public AuthProvider getProvider() { return provider; }
    public String getProviderId() { return providerId; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
