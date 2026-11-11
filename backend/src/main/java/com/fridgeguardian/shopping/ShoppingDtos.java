package com.fridgeguardian.shopping;

import com.fridgeguardian.ingredient.Unit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ShoppingDtos {
    private ShoppingDtos() { }

    public record CreateRequest(
        @NotBlank(message = "재료 이름을 입력해 주세요.") @Size(max = 80, message = "재료 이름은 80자 이하여야 합니다.") String name,
        @DecimalMin(value = "0.001", message = "수량은 0.001 이상이어야 합니다.")
        @DecimalMax(value = "999999999", message = "수량은 999999999 이하여야 합니다.") BigDecimal quantity,
        Unit unit,
        Long recipeId
    ) { }

    public record BulkRequest(
        @NotEmpty(message = "담을 재료를 선택해 주세요.") @Size(max = 30, message = "한 번에 30개까지 담을 수 있어요.")
        List<@NotNull @Valid CreateRequest> items
    ) { }

    /** 레시피에서 부족한 재료를 한꺼번에 장바구니에 담습니다. */
    public record FromRecipeRequest(
        @NotNull(message = "레시피를 선택해 주세요.") Long recipeId,
        @Min(value = 1, message = "인분은 1 또는 2로 선택해 주세요.") @Max(value = 2, message = "인분은 1 또는 2로 선택해 주세요.") int servings
    ) { }

    public record CheckRequest(boolean checked) { }

    public record Response(Long id, String name, BigDecimal quantity, Unit unit, Long recipeId, boolean checked,
                           LocalDateTime createdAt) {
        static Response from(ShoppingItem item) {
            return new Response(item.getId(), item.getName(), item.getQuantity(), item.getUnit(), item.getRecipeId(),
                item.isChecked(), item.getCreatedAt());
        }
    }
}
