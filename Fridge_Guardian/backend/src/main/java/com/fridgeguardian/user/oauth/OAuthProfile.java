package com.fridgeguardian.user.oauth;

import com.fridgeguardian.user.AuthProvider;

/** 제공자마다 다른 속성 이름을 하나로 맞춘 값입니다. emailVerified가 참일 때만 이메일을 계정 식별에 사용합니다. */
public record OAuthProfile(AuthProvider provider, String providerId, String email, boolean emailVerified,
                           String nickname, String imageUrl) { }
