package com.fridgegatekeeper.recommendation;
import com.fridgegatekeeper.ingredient.Unit;
import com.fridgegatekeeper.recipe.Difficulty;
import java.math.BigDecimal;
import java.util.List;

public record RecipeRecommendation(
    Long id,String name,String description,int cookingTime,Difficulty difficulty,int servings,
    List<Requirement> requiredIngredients,List<Requirement> availableIngredients,List<Requirement> missingIngredients,
    List<String> urgentIngredients,int matchedCount,int missingCount,boolean canCook,
    List<String> steps,Nutrition nutrition,String reason
) {
    public record Requirement(String name,BigDecimal requiredQuantity,BigDecimal availableQuantity,
        BigDecimal missingQuantity,Unit unit,boolean urgent,boolean unitMismatch) {}
    /** 영양값은 1인분 기준이며 실제 재료·조리 방식에 따라 달라집니다. */
    public record Nutrition(int calories,BigDecimal protein,BigDecimal carbs,BigDecimal fat,
        boolean perServing,boolean estimated) {}
}
