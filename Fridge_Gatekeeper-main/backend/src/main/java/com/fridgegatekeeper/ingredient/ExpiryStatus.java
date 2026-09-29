package com.fridgegatekeeper.ingredient;

import java.time.LocalDate;

/** 날짜 기준 상태이며 실제 식품의 안전 여부를 판정하는 값은 아닙니다. */
public enum ExpiryStatus {
    SAFE, SOON, EXPIRED, UNKNOWN;

    public static ExpiryStatus of(LocalDate expirationDate, LocalDate today) {
        if (expirationDate == null) return UNKNOWN;
        if (expirationDate.isBefore(today)) return EXPIRED;
        // 오늘까지인 식재료도 만료가 아니라 임박에 포함합니다. 3일 후까지 포함됩니다.
        if (!expirationDate.isAfter(today.plusDays(3))) return SOON;
        return SAFE;
    }
}
