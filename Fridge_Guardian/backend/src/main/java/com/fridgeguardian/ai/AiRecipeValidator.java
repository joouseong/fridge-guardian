package com.fridgeguardian.ai;

import com.fridgeguardian.common.ApiException;
import com.fridgeguardian.recipe.Difficulty;
import com.fridgeguardian.recommendation.IngredientNames;
import com.fridgeguardian.user.preference.UserFilter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** LLM 출력은 신뢰하지 않습니다. 스키마·길이·범위를 확인하고 알레르기·비선호 재료 포함 여부를 서버가 다시 검사합니다. */
@Component
public class AiRecipeValidator {
    /** 소금·기름 같은 기본 양념. 없는 재료(MISSING)로 세지 않고 '집에 있는지 확인'으로 안내합니다. */
    static final Set<String> BASIC_SEASONINGS = Set.of("소금", "후추", "후춧가루", "물", "설탕", "식용유", "올리브유", "간장",
        "참기름", "들기름", "식초", "고춧가루", "다진마늘", "깨").stream()
        .map(IngredientNames::normalize).collect(java.util.stream.Collectors.toUnmodifiableSet());

    private final ObjectMapper json;

    public AiRecipeValidator(ObjectMapper json) { this.json = json; }

    public static boolean isBasic(String name) { return BASIC_SEASONINGS.contains(IngredientNames.normalize(name)); }

    /** 형식이 맞지 않으면 AI_INVALID_RESPONSE(503)를 던집니다. */
    public AiRecipeDtos.Generated parse(String text) {
        try {
            JsonNode root = json.readTree(text);
            if (root == null || !root.isObject()) throw invalid();
            String title = text(root, "title", 100);
            String description = text(root, "description", 300);
            String tips = text(root, "tips", 500);
            int minutes = root.path("cookingTimeMinutes").isIntegralNumber() ? root.path("cookingTimeMinutes").asInt() : -1;
            if (minutes < 1 || minutes > 600) throw invalid();
            Difficulty difficulty;
            try { difficulty = Difficulty.valueOf(root.path("difficulty").asString("")); }
            catch (IllegalArgumentException unknown) { throw invalid(); }
            List<AiRecipeDtos.GeneratedItem> ingredients = items(root.path("ingredients"), 1, 25);
            List<AiRecipeDtos.GeneratedItem> extra = items(root.path("extra"), 0, 6);
            JsonNode stepNodes = root.path("steps");
            if (!stepNodes.isArray() || stepNodes.isEmpty() || stepNodes.size() > 15) throw invalid();
            List<String> steps = new ArrayList<>();
            for (JsonNode step : stepNodes) {
                String value = step.isString() ? step.asString().strip() : "";
                if (value.isEmpty() || value.length() > 300) throw invalid();
                steps.add(value);
            }
            return new AiRecipeDtos.Generated(title, description, minutes, difficulty, ingredients, extra, steps, tips);
        } catch (JacksonException error) {
            throw invalid();
        }
    }

    /** 알레르기·비선호 이름이 재료 이름이나 본문에 들어 있으면 true입니다. */
    public boolean violatesExclusions(AiRecipeDtos.Generated recipe, UserFilter filter) {
        if (filter.allergies().isEmpty() && filter.dislikes().isEmpty()) return false;
        List<String> names = new ArrayList<>();
        recipe.ingredients().forEach(item -> names.add(item.name()));
        recipe.extra().forEach(item -> names.add(item.name()));
        if (names.stream().anyMatch(name -> filter.allergies().matches(name) || filter.dislikes().matches(name))) return true;
        // 문장에서는 한 글자 이름('게', '밀')을 일반 단어와 구분할 수 없어 두 글자 이상만 비교합니다.
        List<String> texts = new ArrayList<>(List.of(recipe.title(), recipe.description(), recipe.tips()));
        texts.addAll(recipe.steps());
        return texts.stream().anyMatch(text -> filter.allergies().matchesText(text) || filter.dislikes().matchesText(text));
    }

    private static String text(JsonNode root, String field, int max) {
        JsonNode node = root.path(field);
        String value = node.isString() ? node.asString().strip() : "";
        if (value.isEmpty() && !field.equals("tips")) throw invalid();
        if (value.length() > max) throw invalid();
        return value;
    }

    private static List<AiRecipeDtos.GeneratedItem> items(JsonNode array, int min, int max) {
        if (!array.isArray() || array.size() < min || array.size() > max) throw invalid();
        List<AiRecipeDtos.GeneratedItem> items = new ArrayList<>();
        for (JsonNode node : array) {
            String name = node.path("name").isString() ? node.path("name").asString().strip() : "";
            String unit = node.path("unit").isString() ? node.path("unit").asString().strip() : "";
            if (name.isEmpty() || name.length() > 80 || unit.length() > 20 || !node.path("quantity").isNumber()) throw invalid();
            BigDecimal quantity = node.path("quantity").decimalValue();
            if (quantity.signum() <= 0 || quantity.compareTo(BigDecimal.valueOf(100_000)) > 0) throw invalid();
            items.add(new AiRecipeDtos.GeneratedItem(name, quantity.stripTrailingZeros(), unit));
        }
        return List.copyOf(items);
    }

    static ApiException invalid() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_INVALID_RESPONSE",
            "AI 답변 형식이 올바르지 않아요. 다시 시도해 주세요.");
    }
}
