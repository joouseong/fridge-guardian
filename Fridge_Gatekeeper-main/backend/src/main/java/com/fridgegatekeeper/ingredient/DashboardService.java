package com.fridgegatekeeper.ingredient;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {
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
        return new DashboardResponse(all.size(), safeCount,
            expiring.size(), expired.size(), todayCount, expiring, expired, today, unknownCount);
    }
}
