package com.fridgeguardian.ai;

import com.fridgeguardian.chat.AiRequestLimiter;
import com.fridgeguardian.common.ApiException;
import com.fridgeguardian.config.CacheConfig;
import com.fridgeguardian.ingredient.IngredientResponse;
import com.fridgeguardian.user.preference.UserFilter;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 캐시에 없을 때만 LLM을 호출합니다. 캐시 키에는 재료·수량·개인화 조건·모델만 들어가고 사용자 식별자는 없으므로,
 * 같은 조건이면 다른 사용자의 결과도 재사용하며 이 경우 AI 요청 한도도 소모하지 않습니다.
 * 캐시 값은 이미 알레르기·비선호 검증을 통과한 결과입니다.
 */
@Component
public class AiRecipeGenerator {
    private final LlmClient llm;
    private final AiRecipePromptBuilder prompts;
    private final AiRecipeValidator validator;
    private final AiRequestLimiter limiter;

    public AiRecipeGenerator(LlmClient llm, AiRecipePromptBuilder prompts, AiRecipeValidator validator, AiRequestLimiter limiter) {
        this.llm = llm; this.prompts = prompts; this.validator = validator; this.limiter = limiter;
    }

    @Cacheable(cacheNames = CacheConfig.AI_RECIPES, key = "#cacheKey")
    public AiRecipeDtos.Generated generate(Long userId, String cacheKey, List<IngredientResponse> selected,
                                           UserFilter filter, int servings, String note) {
        try (var permit = limiter.acquire(userId)) {
            String payload = prompts.userPayload(selected, filter, servings, note);
            ApiException last = null;
            // 형식이 어긋나거나 금지 재료가 들어가면 한 번만 다시 만듭니다.
            for (int attempt = 0; attempt < 2; attempt++) {
                String text = llm.generateJson(prompts.instructions(), payload, AiRecipePromptBuilder.SCHEMA_NAME, prompts.schema());
                try {
                    AiRecipeDtos.Generated recipe = validator.parse(text);
                    if (!validator.violatesExclusions(recipe, filter)) return recipe;
                    last = new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_CONFLICTS_WITH_PREFERENCES",
                        "알레르기·비선호 재료를 피한 레시피를 만들지 못했어요. 다른 재료로 다시 시도해 주세요.");
                } catch (ApiException invalid) {
                    if (!"AI_INVALID_RESPONSE".equals(invalid.getCode())) throw invalid;
                    last = invalid;
                }
            }
            throw last;
        }
    }
}
