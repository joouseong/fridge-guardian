package com.fridgeguardian.ingredient;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {
    static final int NEAREST_LIMIT = 6;
    private final IngredientRepository ingredients;
    private final Clock clock;
    public DashboardService(IngredientRepository ingredients, Clock clock) {
        this.ingredients = ingredients;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(Long userId) {
        // 한 요청 안에서 날짜를 한 번만 정하여 자정 경계에서도 합계와 목록이 일치합니다.
        LocalDate today = LocalDate.now(clock);
        List<IngredientResponse> all = ingredients.findAllByUserId(userId).stream()
            .sorted(Comparator.comparing(Ingredient::getExpirationDate, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Ingredient::getId))
            .map(ingredient -> IngredientResponse.from(ingredient, today)).toList();
        List<IngredientResponse> expiring = all.stream().filter(i -> i.status() == ExpiryStatus.SOON).toList();
        List<IngredientResponse> expired = all.stream().filter(i -> i.status() == ExpiryStatus.EXPIRED).toList();
        long todayCount = all.stream().filter(i -> Long.valueOf(0).equals(i.daysUntilExpiration())).count();
        long safeCount = all.stream().filter(i -> i.status() == ExpiryStatus.SAFE).count();
        long unknownCount = all.stream().filter(i -> i.status() == ExpiryStatus.UNKNOWN).count();
        // 대시보드 카드: 아직 만료되지 않았고 기한이 등록된 재료 중 유통기한이 가장 가까운 최대 6개
        List<IngredientResponse> nearest = all.stream()
            .filter(i -> i.expirationDate() != null && i.status() != ExpiryStatus.EXPIRED).limit(NEAREST_LIMIT).toList();
        return new DashboardResponse(all.size(), safeCount,
            expiring.size(), expired.size(), todayCount, expiring, expired, today, unknownCount, nearest);
    }
}
