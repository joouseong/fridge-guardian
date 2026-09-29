package com.fridgeguardian.shopping;

import com.fridgeguardian.common.ApiException;
import com.fridgeguardian.recommendation.IngredientNames;
import com.fridgeguardian.recommendation.RecipeRecommendation;
import com.fridgeguardian.recommendation.RecommendationService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ShoppingService {
    private final ShoppingItemRepository items;
    private final RecommendationService recommendations;

    public ShoppingService(ShoppingItemRepository items, RecommendationService recommendations) {
        this.items = items; this.recommendations = recommendations;
    }

    public List<ShoppingDtos.Response> list(Long userId) {
        return items.findAllByUserIdOrderByCheckedAscIdDesc(userId).stream().map(ShoppingDtos.Response::from).toList();
    }

    @Transactional
    public List<ShoppingDtos.Response> add(Long userId, List<ShoppingDtos.CreateRequest> requests) {
        // 아직 사지 않은 같은 재료(동의어 포함)가 이미 있으면 다시 담지 않습니다.
        Set<String> pending = new HashSet<>();
        items.findAllByUserIdOrderByCheckedAscIdDesc(userId).stream().filter(item -> !item.isChecked())
            .forEach(item -> pending.add(IngredientNames.normalize(item.getName())));
        List<ShoppingItem> created = new ArrayList<>();
        for (ShoppingDtos.CreateRequest request : requests) {
            String name = request.name().strip();
            if (!pending.add(IngredientNames.normalize(name))) continue;
            created.add(new ShoppingItem(userId, name, request.quantity(), request.unit(), request.recipeId()));
        }
        return items.saveAllAndFlush(created).stream().map(ShoppingDtos.Response::from).toList();
    }

    /** 내 재고 기준으로 부족한 재료(부족 수량이 있는 재료)를 모두 담습니다. */
    @Transactional
    public List<ShoppingDtos.Response> addMissingFromRecipe(Long userId, ShoppingDtos.FromRecipeRequest request) {
        RecipeRecommendation recipe = recommendations.detail(userId, request.recipeId(), request.servings());
        List<ShoppingDtos.CreateRequest> missing = recipe.missingIngredients().stream()
            .map(item -> new ShoppingDtos.CreateRequest(item.name(), item.missingQuantity(), item.unit(), recipe.id())).toList();
        return add(userId, missing);
    }

    @Transactional
    public ShoppingDtos.Response setChecked(Long userId, Long id, boolean checked) {
        ShoppingItem item = owned(userId, id);
        item.setChecked(checked);
        return ShoppingDtos.Response.from(items.saveAndFlush(item));
    }

    @Transactional
    public void delete(Long userId, Long id) { items.delete(owned(userId, id)); }

    @Transactional
    public int clearChecked(Long userId) { return items.deleteChecked(userId); }

    private ShoppingItem owned(Long userId, Long id) {
        return items.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("장바구니 항목을 찾을 수 없습니다."));
    }
}
