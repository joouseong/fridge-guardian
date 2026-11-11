package com.fridgeguardian.recommendation;
import com.fridgeguardian.common.ApiException;
import com.fridgeguardian.ingredient.Ingredient;
import com.fridgeguardian.ingredient.IngredientRepository;
import com.fridgeguardian.recipe.Recipe;
import com.fridgeguardian.recipe.RecipeCatalog;
import com.fridgeguardian.recipe.RecipeData;
import com.fridgeguardian.user.preference.PreferenceService;
import com.fridgeguardian.user.preference.UserFilter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class RecommendationService {
    private final RecipeCatalog catalog;
    private final IngredientRepository ingredients;
    private final PreferenceService preferences;
    private final ShoppingLinkBuilder links;
    private final Clock clock;
    public RecommendationService(RecipeCatalog catalog,IngredientRepository ingredients,PreferenceService preferences,
                                 ShoppingLinkBuilder links,Clock clock) {
        this.catalog=catalog; this.ingredients=ingredients; this.preferences=preferences; this.links=links; this.clock=clock;
    }

    /**
     * 만료되지 않은 내 재고와 레시피를 비교해 일치율(%)이 높은 순으로 돌려줍니다.
     * 알레르기·비선호 재료가 들어 있거나 내 매운맛 한도를 넘는 레시피는 제외합니다.
     */
    public List<RecipeRecommendation> recommend(Long userId,int servings) {
        validateServings(servings);
        LocalDate today=LocalDate.now(clock);
        List<Ingredient> inventory=ingredients.findAllByUserId(userId);
        UserFilter filter=preferences.filterOf(userId);
        // 가중치 합산 대신 우선순위를 차례로 비교합니다: 일치율 → 임박 재료 활용 → 부족 종류 → 조리 시간.
        List<RecipeRecommendation> ranked=catalog.all().stream().filter(r->conflicts(r,filter).isEmpty()&&r.spicyLevel()<=filter.spicyLevel())
            .map(r->score(r,inventory,servings,today))
            .sorted(Comparator.comparingInt(RecipeRecommendation::matchRate).reversed()
                .thenComparing(Comparator.comparingInt((RecipeRecommendation r)->r.urgentIngredients().size()).reversed())
                .thenComparingInt(RecipeRecommendation::missingCount)
                .thenComparingInt(RecipeRecommendation::cookingTime)
                .thenComparing(RecipeRecommendation::id)).toList();
        return markBest(ranked);
    }

    /** 상세 조회는 개인화로 숨기지 않되, 내 알레르기·비선호 재료가 있으면 preferenceConflicts로 알려 줍니다. */
    public RecipeRecommendation detail(Long userId,Long recipeId,int servings) {
        validateServings(servings);
        RecipeData recipe=catalog.get(recipeId);
        UserFilter filter=preferences.filterOf(userId);
        return score(recipe,ingredients.findAllByUserId(userId),servings,LocalDate.now(clock))
            .withPreferenceConflicts(conflicts(recipe,filter));
    }
    public static void validateServings(int servings) {
        if (servings<1 || servings>2) throw ApiException.badRequest("인분은 1 또는 2로 선택해 주세요.");
    }

    /** 1위이면서 실제로 보유 재료가 하나라도 겹칠 때만 BEST MATCH로 표시합니다. */
    private static List<RecipeRecommendation> markBest(List<RecipeRecommendation> ranked) {
        if (ranked.isEmpty() || ranked.get(0).matchRate()==0) return ranked;
        List<RecipeRecommendation> result=new ArrayList<>(ranked);
        result.set(0,result.get(0).withBest(true));
        return List.copyOf(result);
    }

    /** 이름 기반 규칙이므로 모든 성분을 보장하지 않습니다. (예: '우유' 알레르기 → 치즈·버터·요거트도 제외) */
    private static List<String> conflicts(RecipeData recipe,UserFilter filter) {
        return recipe.ingredients().stream().map(RecipeData.Item::name)
            .filter(name->filter.allergies().matches(name)||filter.dislikes().matches(name)).toList();
    }

    RecipeRecommendation score(Recipe recipe,List<Ingredient> inventory,int servings,LocalDate today) {
        return score(RecipeData.from(recipe),inventory,servings,today);
    }
    RecipeRecommendation score(RecipeData recipe,List<Ingredient> inventory,int servings,LocalDate today) {
        List<RecipeRecommendation.Requirement> required=recipe.ingredients().stream()
            .map(item->requirement(item,inventory,servings,recipe.servings(),today)).toList();
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
        return new RecipeRecommendation(recipe.id(),recipe.name(),recipe.description(),
            recipe.cookingTime(),recipe.difficulty(),servings,required,available,missing,urgent,
            available.size(),missing.size(),missing.isEmpty(),recipe.steps(),
            new RecipeRecommendation.Nutrition(recipe.calories(),recipe.protein(),recipe.carbs(),recipe.fat(),true,true),reason,
            RecipeRecommendation.rate(available.size(),required.size()),false,recipe.spicyLevel(),
            links.forMissing(missing.stream().map(RecipeRecommendation.Requirement::name).toList()),List.of());
    }
    private RecipeRecommendation.Requirement requirement(RecipeData.Item item,List<Ingredient> inventory,
                                                         int servings,int baseServings,LocalDate today) {
        BigDecimal required=item.quantity().multiply(BigDecimal.valueOf(servings))
            .divide(BigDecimal.valueOf(baseServings),3,RoundingMode.HALF_UP);
        String canonicalName=IngredientNames.normalize(item.name());
        BigDecimal available=BigDecimal.ZERO;
        boolean urgent=false;
        boolean mismatch=false;
        // 같은 재료를 여러 번 샀다면 사용 가능한 수량을 합칩니다. 만료된 재고는 제외합니다.
        for (Ingredient ingredient:inventory) {
            if ((ingredient.getExpirationDate() != null && ingredient.getExpirationDate().isBefore(today))
                || !IngredientNames.normalize(ingredient.getName()).equals(canonicalName)) continue;
            var converted=Quantities.convert(ingredient.getQuantity(),ingredient.getUnit(),item.unit());
            if (converted.isEmpty()) { mismatch=true; continue; }
            available=available.add(converted.get());
            if (ingredient.getExpirationDate() != null && !ingredient.getExpirationDate().isAfter(today.plusDays(3)) && converted.get().signum()>0) urgent=true;
        }
        return new RecipeRecommendation.Requirement(item.name(),required,available,
            required.subtract(available).max(BigDecimal.ZERO),item.unit(),urgent,mismatch);
    }
}
