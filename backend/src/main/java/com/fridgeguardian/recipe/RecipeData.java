package com.fridgeguardian.recipe;

import com.fridgeguardian.ingredient.Unit;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 캐시(Caffeine/Redis)에 저장할 수 있는 읽기 전용 레시피 스냅샷입니다. JPA Entity는 지연 로딩 컬렉션 때문에
 * 직렬화할 수 없으므로 추천 계산에는 이 값을 사용합니다. 사용자 데이터는 포함하지 않는 공통 데이터입니다.
 */
public record RecipeData(Long id, String name, String description, int cookingTime, Difficulty difficulty, int servings,
                         List<String> steps, int calories, BigDecimal protein, BigDecimal carbs, BigDecimal fat,
                         int spicyLevel, List<Item> ingredients) implements Serializable {
    public record Item(String name, BigDecimal quantity, Unit unit) implements Serializable { }

    public static RecipeData from(Recipe recipe) {
        return new RecipeData(recipe.getId(), recipe.getName(), recipe.getDescription(), recipe.getCookingTime(),
            recipe.getDifficulty(), recipe.getServings(), List.copyOf(recipe.getSteps()), recipe.getCalories(),
            recipe.getProtein(), recipe.getCarbs(), recipe.getFat(), recipe.getSpicyLevel(),
            recipe.getIngredients().stream()
                .map(item -> new Item(item.getIngredientName(), item.getRequiredQuantity(), item.getUnit())).toList());
    }
}
