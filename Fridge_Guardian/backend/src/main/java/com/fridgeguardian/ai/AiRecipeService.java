package com.fridgeguardian.ai;

import com.fridgeguardian.common.ApiException;
import com.fridgeguardian.ingredient.ExpiryStatus;
import com.fridgeguardian.ingredient.Ingredient;
import com.fridgeguardian.ingredient.IngredientRepository;
import com.fridgeguardian.ingredient.IngredientResponse;
import com.fridgeguardian.recommendation.IngredientNames;
import com.fridgeguardian.recommendation.RecommendationService;
import com.fridgeguardian.recommendation.ShoppingLinkBuilder;
import com.fridgeguardian.user.preference.PreferenceService;
import com.fridgeguardian.user.preference.UserFilter;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 선택한 보유 재료로 AI 레시피를 만듭니다. LLM을 기다리는 동안 DB 연결을 잡아 두지 않도록 이 클래스에는
 * 트랜잭션을 열지 않고, 조회·저장은 각 리포지토리 호출 단위로 처리합니다.
 */
@Service
public class AiRecipeService {
    private static final int HISTORY_LIMIT = 30;
    private final IngredientRepository ingredients;
    private final PreferenceService preferences;
    private final AiRecipeGenerator generator;
    private final AiRecipePromptBuilder prompts;
    private final AiRecipeRepository recipes;
    private final ShoppingLinkBuilder links;
    private final LlmClient llm;
    private final ObjectMapper json;
    private final Clock clock;

    public AiRecipeService(IngredientRepository ingredients, PreferenceService preferences, AiRecipeGenerator generator,
                           AiRecipePromptBuilder prompts, AiRecipeRepository recipes, ShoppingLinkBuilder links,
                           LlmClient llm, ObjectMapper json, Clock clock) {
        this.ingredients = ingredients; this.preferences = preferences; this.generator = generator;
        this.prompts = prompts; this.recipes = recipes; this.links = links; this.llm = llm; this.json = json; this.clock = clock;
    }

    public boolean available() { return llm.available(); }

    public AiRecipeDtos.Response generate(Long userId, AiRecipeDtos.Request request) {
        RecommendationService.validateServings(request.servings());
        List<IngredientResponse> selected = loadUsable(userId, request.ingredientIds());
        UserFilter filter = preferences.filterOf(userId);
        String key = prompts.cacheKey(selected, filter, request.servings(), request.request(), llm.model());
        AiRecipeDtos.Generated generated = generator.generate(userId, key, selected, filter, request.servings(), request.request());
        List<String> sourceNames = selected.stream().map(IngredientResponse::name).toList();
        AiRecipe saved = recipes.save(new AiRecipe(userId, generated.title(), json.writeValueAsString(generated),
            json.writeValueAsString(sourceNames), request.servings(), llm.model()));
        return toResponse(saved, generated, sourceNames);
    }

    public List<AiRecipeDtos.Response> history(Long userId) {
        return recipes.findAllByUserIdOrderByIdDesc(userId, PageRequest.of(0, HISTORY_LIMIT)).stream().map(this::toResponse).toList();
    }

    public AiRecipeDtos.Response setFavorite(Long userId, Long id, boolean favorite) {
        AiRecipe recipe = owned(userId, id);
        recipe.setFavorite(favorite);
        return toResponse(recipes.save(recipe));
    }

    public void delete(Long userId, Long id) { recipes.delete(owned(userId, id)); }

    private AiRecipe owned(Long userId, Long id) {
        return recipes.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("AI 레시피를 찾을 수 없습니다."));
    }

    /** 본인 재료만 허용하고, 유통기한이 지난 재료는 레시피에 쓰지 않습니다. */
    private List<IngredientResponse> loadUsable(Long userId, List<Long> ids) {
        Map<Long, Ingredient> mine = new LinkedHashMap<>();
        ingredients.findAllByUserId(userId).forEach(item -> mine.put(item.getId(), item));
        LocalDate today = LocalDate.now(clock);
        List<IngredientResponse> selected = new ArrayList<>();
        List<String> expired = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(ids)) {
            Ingredient item = mine.get(id);
            // 다른 사람의 ID도 없는 재료와 똑같이 404로 처리합니다.
            if (item == null) throw ApiException.notFound("선택한 식재료를 찾을 수 없어요. 목록을 새로고침해 주세요.");
            IngredientResponse response = IngredientResponse.from(item, today);
            if (response.status() == ExpiryStatus.EXPIRED) expired.add(response.name()); else selected.add(response);
        }
        if (!expired.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INGREDIENT_EXPIRED",
                "유통기한이 지난 재료는 레시피에 사용할 수 없어요: " + String.join(", ", expired));
        }
        return selected;
    }

    private AiRecipeDtos.Response toResponse(AiRecipe entity) {
        AiRecipeDtos.Generated generated = json.readValue(entity.getContent(), AiRecipeDtos.Generated.class);
        List<String> sourceNames = json.readValue(entity.getSourceIngredients(), new TypeReference<List<String>>() { });
        return toResponse(entity, generated, sourceNames);
    }

    /** 재료 상태(OWNED/BASIC/MISSING)는 AI의 표기가 아니라 선택한 재료와 서버가 비교한 결과입니다. */
    private AiRecipeDtos.Response toResponse(AiRecipe entity, AiRecipeDtos.Generated generated, List<String> sourceNames) {
        Set<String> owned = new LinkedHashSet<>();
        sourceNames.forEach(name -> owned.add(IngredientNames.normalize(name)));
        List<AiRecipeDtos.ResponseItem> items = new ArrayList<>();
        Set<String> missing = new LinkedHashSet<>();
        for (AiRecipeDtos.GeneratedItem item : java.util.stream.Stream.concat(
            generated.ingredients().stream(), generated.extra().stream()).toList()) {
            String status = owned.contains(IngredientNames.normalize(item.name())) ? "OWNED"
                : AiRecipeValidator.isBasic(item.name()) ? "BASIC" : "MISSING";
            if (status.equals("MISSING")) missing.add(item.name());
            items.add(new AiRecipeDtos.ResponseItem(item.name(), item.quantity(), item.unit(), status));
        }
        List<String> missingNames = List.copyOf(missing);
        return new AiRecipeDtos.Response(entity.getId(), generated.title(), generated.description(), entity.getServings(),
            generated.cookingTimeMinutes(), generated.difficulty(), items, missingNames, generated.steps(), generated.tips(),
            entity.getModel(), sourceNames, links.forMissing(missingNames), entity.isFavorite(), entity.getCreatedAt());
    }
}
