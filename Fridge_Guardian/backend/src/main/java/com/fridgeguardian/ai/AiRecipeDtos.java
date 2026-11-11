package com.fridgeguardian.ai;

import com.fridgeguardian.recipe.Difficulty;
import com.fridgeguardian.recommendation.ShoppingLink;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class AiRecipeDtos {
    private AiRecipeDtos() { }

    /** 사용자가 고른 보유 재료(최대 12개)로 레시피를 만듭니다. 인분은 1 또는 2입니다. */
    public record Request(
        @NotEmpty(message = "레시피에 사용할 재료를 선택해 주세요.")
        @Size(max = 12, message = "재료는 한 번에 12개까지 선택할 수 있어요.")
        List<@NotNull(message = "재료 번호를 확인해 주세요.") Long> ingredientIds,
        @Min(value = 1, message = "인분은 1 또는 2로 선택해 주세요.")
        @Max(value = 2, message = "인분은 1 또는 2로 선택해 주세요.") int servings,
        @Size(max = 200, message = "요청 사항은 200자 이내로 입력해 주세요.") String request
    ) {
        public Request {
            ingredientIds = ingredientIds == null ? List.of() : ingredientIds;
            request = request == null || request.isBlank() ? null : request.strip();
        }
    }

    /** LLM이 만든 레시피 본문. 검증을 통과한 값만 저장·캐시합니다. */
    public record Generated(String title, String description, int cookingTimeMinutes, Difficulty difficulty,
                            List<GeneratedItem> ingredients, List<GeneratedItem> extra, List<String> steps,
                            String tips) implements Serializable { }

    public record GeneratedItem(String name, BigDecimal quantity, String unit) implements Serializable { }

    /**
     * status: OWNED(선택한 보유 재료) / BASIC(소금·기름 같은 기본 양념, 집에 있는지 확인) / MISSING(없는 재료).
     * 상태는 AI가 아니라 서버가 선택 재료와 비교해 다시 계산합니다.
     */
    public record ResponseItem(String name, BigDecimal quantity, String unit, String status) { }

    public record Response(Long id, String title, String description, int servings, int cookingTimeMinutes,
                           Difficulty difficulty, List<ResponseItem> ingredients, List<String> missing,
                           List<String> steps, String tips, String model, List<String> sourceIngredients,
                           List<ShoppingLink> shoppingLinks, boolean favorite, LocalDateTime createdAt) { }

    public record FavoriteRequest(boolean favorite) { }
}
