package com.fridgegatekeeper.user;
import jakarta.persistence.*;

/** 응답에는 Entity 대신 DTO를 사용하여 비밀번호 해시 노출을 방지합니다. */
@Entity @Table(name = "users")
public class UserAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable=false, unique=true, length=254) private String email;
    @Column(name="password", nullable=false, length=100) private String passwordHash;
    @Column(nullable=false, length=30) private String nickname;
    protected UserAccount() {}
    public UserAccount(String email, String passwordHash, String nickname) {
        this.email=email; this.passwordHash=passwordHash; this.nickname=nickname;
    }
    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getNickname() { return nickname; }
}
