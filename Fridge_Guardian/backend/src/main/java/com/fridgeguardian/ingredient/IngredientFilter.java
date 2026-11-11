package com.fridgeguardian.ingredient;

import com.fridgeguardian.common.ApiException;
import java.util.Locale;
import java.util.Set;

/**
 * 식재료 목록의 검색·필터 조건입니다. 값이 없는 항목은 조건에서 제외합니다.
 * status: SOON(임박) / SAFE(여유) / EXPIRED(만료) / UNKNOWN(기한 미등록) / TODAY(오늘까지) / USABLE(만료 제외) / ALL
 */
public record IngredientFilter(String q, Category category, StorageType storage, String status) {
    public static final IngredientFilter NONE = new IngredientFilter(null, null, null, null);
    private static final Set<String> STATUSES = Set.of("ALL", "SOON", "SAFE", "EXPIRED", "UNKNOWN", "TODAY", "USABLE");

    public IngredientFilter {
        q = q == null || q.isBlank() ? null : q.strip();
        if (status == null || status.isBlank()) {
            status = null;
        } else {
            status = status.strip().toUpperCase(Locale.ROOT);
            if (!STATUSES.contains(status)) {
                throw ApiException.badRequest("상태는 SOON, SAFE, EXPIRED, UNKNOWN, TODAY, USABLE 중에서 선택해 주세요.");
            }
            if (status.equals("ALL")) status = null;
        }
    }

    public boolean matches(IngredientResponse item) {
        if (q != null && !item.name().toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))) return false;
        if (category != null && item.category() != category) return false;
        if (storage != null && item.storageType() != storage) return false;
        if (status == null) return true;
        return switch (status) {
            case "TODAY" -> Long.valueOf(0).equals(item.daysUntilExpiration());
            case "USABLE" -> item.status() != ExpiryStatus.EXPIRED;
            default -> item.status().name().equals(status);
        };
    }
}
