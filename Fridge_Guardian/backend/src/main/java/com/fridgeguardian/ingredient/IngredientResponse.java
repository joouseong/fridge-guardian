package com.fridgeguardian.ingredient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** imageUrl이 null이면 화면이 기본 이미지를 보여 줍니다. */
public record IngredientResponse(Long id, String name, Category category, BigDecimal quantity, Unit unit,
                                 LocalDate purchaseDate, LocalDate expirationDate, StorageType storageType,
                                 long version, ExpiryStatus status, Long daysUntilExpiration,
                                 String imageUrl, String memo, String barcode) {
    /** 사진·메모·바코드가 없는 재료(테스트·AI 문맥용)를 간단히 만드는 생성자입니다. */
    public IngredientResponse(Long id, String name, Category category, BigDecimal quantity, Unit unit,
                              LocalDate purchaseDate, LocalDate expirationDate, StorageType storageType,
                              long version, ExpiryStatus status, Long daysUntilExpiration) {
        this(id, name, category, quantity, unit, purchaseDate, expirationDate, storageType, version, status,
            daysUntilExpiration, null, null, null);
    }

    public static IngredientResponse from(Ingredient ingredient, LocalDate today) {
        return new IngredientResponse(ingredient.getId(), ingredient.getName(), ingredient.getCategory(),
            ingredient.getQuantity(), ingredient.getUnit(), ingredient.getPurchaseDate(), ingredient.getExpirationDate(),
            ingredient.getStorageType(), ingredient.getVersion(), ExpiryStatus.of(ingredient.getExpirationDate(), today),
            ingredient.getExpirationDate() == null ? null : ChronoUnit.DAYS.between(today, ingredient.getExpirationDate()),
            ingredient.getImagePath() == null ? null : "/uploads/" + ingredient.getImagePath(),
            ingredient.getMemo(), ingredient.getBarcode());
    }
}
