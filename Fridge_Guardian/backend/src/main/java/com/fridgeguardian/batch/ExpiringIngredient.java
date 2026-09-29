package com.fridgeguardian.batch;

import java.time.LocalDate;

/** 배치가 읽는 최소 정보. Entity 대신 JPQL 생성자 프로젝션을 사용해 영속성 컨텍스트를 거치지 않습니다. */
public record ExpiringIngredient(Long id, Long userId, String name, LocalDate expirationDate) {
    // JpaPagingItemReader의 JPQL `select new ...` 프로젝션이 사용하는 생성자입니다.
}
