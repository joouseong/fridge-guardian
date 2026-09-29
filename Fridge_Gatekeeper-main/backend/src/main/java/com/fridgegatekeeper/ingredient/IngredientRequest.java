package com.fridgegatekeeper.ingredient;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 생성과 수정의 공통 입력입니다. version은 생성에는 없어도 되지만 수정에는 필요합니다. */
public record IngredientRequest(
    @NotBlank(message = "식재료명을 입력해 주세요.")
    @Size(max = 80, message = "식재료명은 80자 이하여야 합니다.") String name,
    @NotNull(message = "카테고리를 선택해 주세요.") Category category,
    @NotNull(message = "수량을 입력해 주세요.")
    @DecimalMin(value = "0.001", message = "수량은 0.001 이상이어야 합니다.")
    @DecimalMax(value = "999999999", message = "수량은 999999999 이하여야 합니다.")
    @Digits(integer = 9, fraction = 3, message = "수량은 정수 9자리, 소수 3자리까지 입력할 수 있습니다.") BigDecimal quantity,
    @NotNull(message = "수량 단위를 선택해 주세요.") Unit unit,
    @NotNull(message = "구매 날짜를 입력해 주세요.") LocalDate purchaseDate,
    LocalDate expirationDate,
    @NotNull(message = "보관 위치를 선택해 주세요.") StorageType storageType,
    @PositiveOrZero(message = "수정 버전은 0 이상이어야 합니다.") Long version
) { }
