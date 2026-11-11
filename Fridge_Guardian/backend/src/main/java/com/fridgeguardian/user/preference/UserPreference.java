package com.fridgeguardian.user.preference;

import jakarta.persistence.*;

/** 사용자가 먹을 수 있는 최대 매운맛(0=순한맛 ~ 3=아주 매운맛까지 가능). 행이 없으면 제한이 없는 것으로 봅니다. */
@Entity @Table(name = "user_preferences")
public class UserPreference {
    public static final int UNRESTRICTED = 3;
    @Id @Column(name = "user_id") private Long userId;
    @Column(nullable = false) private int spicyLevel = UNRESTRICTED;

    protected UserPreference() { }
    public UserPreference(Long userId, int spicyLevel) { this.userId = userId; this.spicyLevel = spicyLevel; }
    public Long getUserId() { return userId; }
    public int getSpicyLevel() { return spicyLevel; }
    public void setSpicyLevel(int spicyLevel) { this.spicyLevel = spicyLevel; }
}
