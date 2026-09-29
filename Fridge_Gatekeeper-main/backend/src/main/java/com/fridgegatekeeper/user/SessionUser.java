package com.fridgegatekeeper.user;

import java.io.Serializable;

/** 세션에는 JPA Entity나 비밀번호 해시 대신 최소한의 식별 정보만 저장합니다. */
public record SessionUser(Long id, String email) implements Serializable { }
