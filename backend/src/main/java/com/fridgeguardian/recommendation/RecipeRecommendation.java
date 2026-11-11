package com.fridgeguardian.recommendation;
import com.fridgeguardian.ingredient.Unit;
import com.fridgeguardian.recipe.Difficulty;
import java.math.BigDecimal;
import java.util.List;

/**
 * 한 레시피의 추천 결과.
 * matchRate는 필요 재료 종류 중 보유한 종류의 비율(%)이고, best는 목록에서 일치율이 가장 높은 레시피(BEST MATCH)입니다.
 * preferenceConflicts는 내 알레르기·비선호 재료와 겹치는 재료이며, 추천 목록에서는 이런 레시피를 제외하므로
 * 상세 조회에서만 채워질 수 있습니다.
 */
public record RecipeRecommendation(
    Long id,String name,String description,int cookingTime,Difficulty difficulty,int servings,
    List<Requirement> requiredIngredients,List<Requirement> availableIngredients,List<Requirement> missingIngredients,
    List<String> urgentIngredients,int matchedCount,int missingCount,boolean canCook,
    List<String> steps,Nutrition nutrition,String reason,
    int matchRate,boolean best,int spicyLevel,List<ShoppingLink> shoppingLinks,List<String> preferenceConflicts
) {
    public record Requirement(String name,BigDecimal requiredQuantity,BigDecimal availableQuantity,
        BigDecimal missingQuantity,Unit unit,boolean urgent,boolean unitMismatch) {}
    /** 영양값은 1인분 기준이며 실제 재료·조리 방식에 따라 달라집니다. */
    public record Nutrition(int calories,BigDecimal protein,BigDecimal carbs,BigDecimal fat,
        boolean perServing,boolean estimated) {}

    /** 일치율·개인화 정보 없이 만드는 기본 생성자입니다. 일치율은 보유/필요 종류 수로 계산합니다. */
    public RecipeRecommendation(Long id,String name,String description,int cookingTime,Difficulty difficulty,int servings,
        List<Requirement> requiredIngredients,List<Requirement> availableIngredients,List<Requirement> missingIngredients,
        List<String> urgentIngredients,int matchedCount,int missingCount,boolean canCook,
        List<String> steps,Nutrition nutrition,String reason) {
        this(id,name,description,cookingTime,difficulty,servings,requiredIngredients,availableIngredients,
            missingIngredients,urgentIngredients,matchedCount,missingCount,canCook,steps,nutrition,reason,
            rate(matchedCount,requiredIngredients.size()),false,0,List.of(),List.of());
    }

    public static int rate(int matched,int required) {
        return required == 0 ? 0 : Math.round(100f * matched / required);
    }

    public RecipeRecommendation withBest(boolean value) {
        return new RecipeRecommendation(id,name,description,cookingTime,difficulty,servings,requiredIngredients,
            availableIngredients,missingIngredients,urgentIngredients,matchedCount,missingCount,canCook,steps,nutrition,
            reason,matchRate,value,spicyLevel,shoppingLinks,preferenceConflicts);
    }

    public RecipeRecommendation withPreferenceConflicts(List<String> conflicts) {
        return new RecipeRecommendation(id,name,description,cookingTime,difficulty,servings,requiredIngredients,
            availableIngredients,missingIngredients,urgentIngredients,matchedCount,missingCount,canCook,steps,nutrition,
            reason,matchRate,best,spicyLevel,shoppingLinks,conflicts);
    }
}
