package com.fridgegatekeeper.ingredient;

import java.time.LocalDate;
import java.util.List;

public record DashboardResponse(long total, long safeCount, long soonCount, long expiredCount, long todayCount,
                                List<IngredientResponse> expiringIngredients,
                                List<IngredientResponse> expiredIngredients, LocalDate today, long unknownCount) { }
