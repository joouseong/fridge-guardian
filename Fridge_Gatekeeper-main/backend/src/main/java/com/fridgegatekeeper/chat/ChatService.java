package com.fridgegatekeeper.chat;

import com.fridgegatekeeper.ingredient.ExpiryStatus;
import com.fridgegatekeeper.ingredient.IngredientResponse;
import com.fridgegatekeeper.ingredient.IngredientService;
import com.fridgegatekeeper.ingredient.Unit;
import com.fridgegatekeeper.recommendation.RecipeRecommendation;
import com.fridgegatekeeper.recommendation.RecommendationService;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/** 매 질문마다 로그인한 사용자의 최신 재고를 읽습니다. 대화와 타인 재고를 서버에 섞어 저장하지 않습니다. */
@Service
public class ChatService {
    private final IngredientService ingredients;
    private final RecommendationService recommendations;
    private final OpenAiClient openAi;
    private final AiRequestLimiter limiter;

    public ChatService(IngredientService ingredients, RecommendationService recommendations, OpenAiClient openAi,
                       AiRequestLimiter limiter) {
        this.ingredients = ingredients;
        this.recommendations = recommendations;
        this.openAi = openAi;
        this.limiter = limiter;
    }

    /** 설정 여부만 확인하며 비용이 발생하는 외부 API를 호출하지 않습니다. */
    public boolean available() { return openAi.available(); }
    public String model() { return available() ? openAi.model() : null; }

    public ChatDtos.Response reply(Long userId, ChatDtos.Request request) {
        RecommendationService.validateServings(request.servings());
        List<IngredientResponse> inventory = ingredients.list(userId, "expiration");
        List<RecipeRecommendation> ranked = recommendations.recommend(userId, request.servings());
        List<RecipeRecommendation> selected = select(ranked, request.message());
        // 트랜잭션은 각각의 조회에서 끝나므로 AI 응답을 기다리는 동안 DB 연결을 잡아두지 않습니다.
        if (!"LOCAL".equals(request.mode()) && openAi.available()) {
            try (var permit = limiter.acquire(userId)) {
                List<RecipeRecommendation> candidates = ranked.stream().limit(40).toList();
                OpenAiClient.Answer answer = openAi.reply(request, inventory, candidates);
                // 수량·단위·조리법은 AI 생성값으로 덮어쓰지 않고 검증한 후보 ID에 연결합니다.
                var byId = candidates.stream().collect(java.util.stream.Collectors.toMap(RecipeRecommendation::id, recipe -> recipe));
                List<RecipeRecommendation> chosen = answer.recipeIds().stream().map(byId::get).toList();
                return new ChatDtos.Response(answer.reply(), "OPENAI", chosen);
            }
        }
        return new ChatDtos.Response(localReply(request, inventory, selected), "LOCAL", selected);
    }

    private List<RecipeRecommendation> select(List<RecipeRecommendation> ranked, String question) {
        var candidates = ranked.stream().filter(recipe -> recipe.matchedCount() > 0);
        String text = question.toLowerCase(Locale.ROOT);
        if (contains(text, "임박", "유통기한", "먼저", "급한")) {
            // 임박 재료로 만들 수 있는 메뉴가 없을 때도 보유 재료로 가능한 대안은 제공합니다.
            if (ranked.stream().anyMatch(recipe -> !recipe.urgentIngredients().isEmpty())) {
                candidates = candidates.filter(recipe -> !recipe.urgentIngredients().isEmpty());
            }
        }
        if (contains(text, "단백질", "고단백", "protein")) {
            candidates = candidates.sorted(Comparator.comparing(
                (RecipeRecommendation recipe) -> recipe.nutrition().protein()).reversed());
        } else if (contains(text, "간단", "빨리", "빠른", "짧은", "quick")) {
            candidates = candidates.sorted(Comparator.comparingInt(RecipeRecommendation::cookingTime));
        }
        return candidates.limit(3).toList();
    }

    private String localReply(ChatDtos.Request request, List<IngredientResponse> inventory,
                              List<RecipeRecommendation> selected) {
        StringBuilder reply = new StringBuilder("기본 추천 모드에서 현재 냉장고를 살펴봤어요. ");
        if (inventory.isEmpty()) {
            return reply.append("아직 등록한 식재료가 없어요. 내 냉장고에 재료와 수량, 유통기한을 등록하면 "
                + request.servings() + "인분에 맞춰 메뉴를 추천해 드릴게요.").toString();
        }
        long expired = inventory.stream().filter(item -> item.status() == ExpiryStatus.EXPIRED).count();
        if (inventory.stream().anyMatch(item -> item.status() == ExpiryStatus.UNKNOWN)) {
            reply.append("기한을 등록하지 않은 재료는 사용 전에 실제 상태를 확인해 주세요. ");
        }
        if (expired > 0) reply.append("유통기한이 지난 재료 ").append(expired).append("건은 추천에서 제외했어요. ");
        if (selected.isEmpty()) {
            return reply.append("지금 사용할 수 있는 재료와 연결되는 등록 레시피가 없어요. "
                + "재료 이름·단위·유통기한을 확인하거나 전체 레시피에서 필요한 재료를 확인해 주세요.").toString();
        }
        reply.append(request.servings()).append("인분 기준으로 추천해 드릴게요.\n");
        for (int index = 0; index < selected.size(); index++) {
            RecipeRecommendation recipe = selected.get(index);
            reply.append("\n").append(index + 1).append(". ").append(recipe.name()).append(" · ")
                .append(recipe.cookingTime()).append("분\n").append(recipe.reason()).append(".\n");
            if (recipe.canCook()) {
                reply.append("등록한 재료 수량이 모두 충분해요.");
            } else {
                reply.append("추가로 필요한 재료: ").append(String.join(", ", recipe.missingIngredients().stream()
                    .map(item -> item.name() + " " + item.missingQuantity().stripTrailingZeros().toPlainString()
                        + unit(item.unit()) + (item.unitMismatch() ? " (보유 단위 확인 필요)" : ""))
                    .toList())).append(".");
            }
            if (contains(request.message(), "단백질", "고단백", "protein")) {
                reply.append(" 1인분 단백질 약 ").append(recipe.nutrition().protein().stripTrailingZeros().toPlainString())
                    .append("g (예시 추정값).");
            }
            reply.append("\n");
        }
        if (contains(request.message(), "임박", "유통기한", "먼저", "급한")
            && selected.stream().allMatch(recipe -> recipe.urgentIngredients().isEmpty())) {
            reply.append("\n임박 재료를 활용하는 등록 메뉴가 없어 다른 보유 재료를 활용하는 메뉴를 골랐어요.\n");
        }
        return reply.append("\n메뉴 카드에서 재료별 수량과 조리 순서를 확인해 주세요. "
            + "기본 추천은 보유 재료와 간단·단백질·임박 조건을 제한적으로 반영해요.").toString();
    }

    private static boolean contains(String text, String... words) {
        for (String word : words) if (text.contains(word)) return true;
        return false;
    }

    private static String unit(Unit unit) {
        return switch (unit) {
            case PIECE -> "개";
            case GRAM -> "g";
            case KILOGRAM -> "kg";
            case MILLILITER -> "ml";
            case LITER -> "L";
            case PACK -> "팩";
            case BAG -> "봉";
            case BLOCK -> "모";
            case BUNCH -> "단";
        };
    }
}
