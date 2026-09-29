package com.fridgegatekeeper.recommendation;
import com.fridgegatekeeper.common.ApiException;
import com.fridgegatekeeper.ingredient.Ingredient;
import com.fridgegatekeeper.ingredient.IngredientRepository;
import com.fridgegatekeeper.recipe.Recipe;
import com.fridgegatekeeper.recipe.RecipeIngredient;
import com.fridgegatekeeper.recipe.RecipeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class RecommendationService {
    private final RecipeRepository recipes;
    private final IngredientRepository ingredients;
    private final Clock clock;
    public RecommendationService(RecipeRepository recipes,IngredientRepository ingredients,Clock clock) {
        this.recipes=recipes; this.ingredients=ingredients; this.clock=clock;
    }
    public List<RecipeRecommendation> recommend(Long userId,int servings) {
        validateServings(servings);
        LocalDate today=LocalDate.now(clock);
        List<Ingredient> inventory=ingredients.findAllByUserId(userId);
        // 가중치 합산 대신 우선순위를 차례로 비교하여 사용자 요구 순서를 지킵니다.
        return recipes.findAll().stream().map(r->score(r,inventory,servings,today))
            .sorted(Comparator.comparingInt(RecipeRecommendation::matchedCount).reversed()
                .thenComparing(Comparator.comparingInt((RecipeRecommendation r)->r.urgentIngredients().size()).reversed())
                .thenComparingInt(RecipeRecommendation::missingCount)
                .thenComparing(RecipeRecommendation::id)).toList();
    }
    public RecipeRecommendation detail(Long userId,Long recipeId,int servings) {
        validateServings(servings);
        Recipe recipe=recipes.findById(recipeId).orElseThrow(()->ApiException.notFound("레시피를 찾을 수 없습니다."));
        return score(recipe,ingredients.findAllByUserId(userId),servings,LocalDate.now(clock));
    }
    public static void validateServings(int servings) {
        if (servings<1 || servings>2) throw ApiException.badRequest("인분은 1 또는 2로 선택해 주세요.");
    }
    RecipeRecommendation score(Recipe recipe,List<Ingredient> inventory,int servings,LocalDate today) {
        List<RecipeRecommendation.Requirement> required=recipe.getIngredients().stream()
            .map(item->requirement(item,inventory,servings,recipe.getServings(),today)).toList();
        List<RecipeRecommendation.Requirement> available=required.stream()
            .filter(item->item.availableQuantity().signum()>0).toList();
        List<RecipeRecommendation.Requirement> missing=required.stream()
            .filter(item->item.missingQuantity().signum()>0).toList();
        List<String> urgent=available.stream().filter(RecipeRecommendation.Requirement::urgent)
            .map(RecipeRecommendation.Requirement::name).toList();
        String reason=available.isEmpty() ? "활용 가능한 재료가 없습니다. 부족한 재료를 확인해 주세요."
            : "보유 재료 "+available.size()+"종 활용"
                +(urgent.isEmpty() ? "" : " · 임박 재료 "+String.join(", ",urgent)+" 우선 활용")
                +(missing.isEmpty() ? " · 재료가 모두 준비됐어요" : " · "+missing.size()+"종 보충 필요");
        return new RecipeRecommendation(recipe.getId(),recipe.getName(),recipe.getDescription(),
            recipe.getCookingTime(),recipe.getDifficulty(),servings,required,available,missing,urgent,
            available.size(),missing.size(),missing.isEmpty(),recipe.getSteps(),
            new RecipeRecommendation.Nutrition(recipe.getCalories(),recipe.getProtein(),recipe.getCarbs(),recipe.getFat(),true,true),reason);
    }
    private RecipeRecommendation.Requirement requirement(RecipeIngredient item,List<Ingredient> inventory,
                                                         int servings,int baseServings,LocalDate today) {
        BigDecimal required=item.getRequiredQuantity().multiply(BigDecimal.valueOf(servings))
            .divide(BigDecimal.valueOf(baseServings),3,RoundingMode.HALF_UP);
        String canonicalName=IngredientNames.normalize(item.getIngredientName());
        BigDecimal available=BigDecimal.ZERO;
        boolean urgent=false;
        boolean mismatch=false;
        // 같은 재료를 여러 번 샀다면 사용 가능한 수량을 합칩니다. 만료된 재고는 제외합니다.
        for (Ingredient ingredient:inventory) {
            if ((ingredient.getExpirationDate() != null && ingredient.getExpirationDate().isBefore(today))
                || !IngredientNames.normalize(ingredient.getName()).equals(canonicalName)) continue;
            var converted=Quantities.convert(ingredient.getQuantity(),ingredient.getUnit(),item.getUnit());
            if (converted.isEmpty()) { mismatch=true; continue; }
            available=available.add(converted.get());
            if (ingredient.getExpirationDate() != null && !ingredient.getExpirationDate().isAfter(today.plusDays(3)) && converted.get().signum()>0) urgent=true;
        }
        return new RecipeRecommendation.Requirement(item.getIngredientName(),required,available,
            required.subtract(available).max(BigDecimal.ZERO),item.getUnit(),urgent,mismatch);
    }
}
