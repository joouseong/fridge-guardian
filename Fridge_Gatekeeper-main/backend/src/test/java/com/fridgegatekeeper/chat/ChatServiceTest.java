package com.fridgegatekeeper.chat;

import com.fridgegatekeeper.common.ApiException;
import com.fridgegatekeeper.ingredient.Category;
import com.fridgegatekeeper.ingredient.ExpiryStatus;
import com.fridgegatekeeper.ingredient.IngredientResponse;
import com.fridgegatekeeper.ingredient.IngredientService;
import com.fridgegatekeeper.ingredient.StorageType;
import com.fridgegatekeeper.ingredient.Unit;
import com.fridgegatekeeper.recipe.Difficulty;
import com.fridgegatekeeper.recommendation.RecipeRecommendation;
import com.fridgegatekeeper.recommendation.RecommendationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class ChatServiceTest {
    private IngredientService ingredients;
    private RecommendationService recommendations;
    private OpenAiClient openAi;
    private ChatService service;

    @BeforeEach void setUp() {
        ingredients = mock(IngredientService.class);
        recommendations = mock(RecommendationService.class);
        openAi = mock(OpenAiClient.class);
        service = new ChatService(ingredients, recommendations, openAi,
            new AiRequestLimiter(java.time.Clock.systemUTC(), 5, 20, 100, 2));
        when(ingredients.list(7L, "expiration")).thenReturn(List.of(stock("계란", ExpiryStatus.SOON)));
    }

    @Test void localModeUsesAuthenticatedUsersServingsAndAccurateShortagesWithoutCallingOpenAi() {
        var recipe = recipe(1L, "계란밥", 15, 12, true, 1);
        when(recommendations.recommend(7L, 2)).thenReturn(List.of(recipe));

        ChatDtos.Response result = service.reply(7L, new ChatDtos.Request("뭘 먹을까?", 2, null));

        assertThat(result.source()).isEqualTo("LOCAL");
        assertThat(result.reply()).contains("2인분", "계란밥", "밥 100g").doesNotContain("등록한 재료 수량이 모두 충분해요", "OPENAI");
        assertThat(result.recommendedRecipes()).containsExactly(recipe);
    }

    @Test void noKeyKeepsStatusAndChatOffline() {
        when(recommendations.recommend(7L, 1)).thenReturn(List.of(recipe(1L, "계란밥", 15, 12, true, 1)));
        assertThat(service.available()).isFalse();
        service.reply(7L, request("추천해 줘"));
        verify(openAi, never()).reply(any(), anyList(), anyList());
        verify(ingredients).list(7L, "expiration");
        verify(recommendations).recommend(7L, 1);
    }

    @Test void emptyFridgeExplainsRegistrationWithoutShowingUnrelatedRecipes() {
        when(ingredients.list(7L, "expiration")).thenReturn(List.of());
        when(recommendations.recommend(7L, 1)).thenReturn(List.of(recipe(1L, "비어 있는 메뉴", 15, 12, false, 0)));
        ChatDtos.Response result = service.reply(7L, request("추천해 줘"));
        assertThat(result.reply()).contains("아직 등록한 식재료가 없어요");
        assertThat(result.recommendedRecipes()).isEmpty();
        verify(openAi, never()).reply(any(), anyList(), anyList());
    }

    @Test void localModeExcludesUnmatchedRecipesAndReflectsSimpleProteinAndUrgentRequests() {
        var longMeal = recipe(1L, "느린 계란요리", 30, 30, true, 1);
        var quickMeal = recipe(2L, "빠른 계란요리", 5, 6, false, 1);
        var unrelated = recipe(3L, "미보유 단백질요리", 1, 99, false, 0);
        var normalMeal = recipe(4L, "보통 계란요리", 15, 12, false, 1);
        when(recommendations.recommend(7L, 1)).thenReturn(List.of(longMeal, quickMeal, unrelated, normalMeal));

        assertThat(service.reply(7L, request("간단한 요리")).recommendedRecipes())
            .extracting(RecipeRecommendation::id).containsExactly(2L, 4L, 1L);
        ChatDtos.Response protein = service.reply(7L, request("단백질 요리"));
        assertThat(protein.recommendedRecipes()).extracting(RecipeRecommendation::id).containsExactly(1L, 4L, 2L);
        assertThat(protein.reply()).contains("1인분 단백질 약 30g (예시 추정값)");
        assertThat(service.reply(7L, request("유통기한 임박한 재료")).recommendedRecipes()).containsExactly(longMeal);
    }

    @Test void expiredInventoryIsExplainedEvenWhenNoMatchedMenuRemains() {
        when(ingredients.list(7L, "expiration")).thenReturn(List.of(stock("계란", ExpiryStatus.EXPIRED)));
        when(recommendations.recommend(7L, 1)).thenReturn(List.of(recipe(1L, "계란밥", 15, 12, false, 0)));
        ChatDtos.Response result = service.reply(7L, request("임박 재료 요리"));
        assertThat(result.reply()).contains("유통기한이 지난 재료 1건은 추천에서 제외", "연결되는 등록 레시피가 없어요");
        assertThat(result.recommendedRecipes()).isEmpty();
    }

    @Test void configuredAiFailureIsNeverReportedAsSuccessfulLocalOrAiAnswer() {
        when(recommendations.recommend(7L, 1)).thenReturn(List.of(recipe(1L, "계란밥", 15, 12, true, 1)));
        when(openAi.available()).thenReturn(true);
        ApiException upstream = new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_TIMEOUT", "응답 시간 초과");
        when(openAi.reply(any(), anyList(), anyList())).thenThrow(upstream);

        assertThatThrownBy(() -> service.reply(7L, request("추천해 줘"))).isSameAs(upstream);
    }

    @Test void configuredAiAnswerPreservesServerCalculatedRecipeCards() {
        var recipe = recipe(1L, "계란밥", 15, 12, true, 1);
        when(recommendations.recommend(7L, 1)).thenReturn(List.of(recipe));
        when(openAi.available()).thenReturn(true);
        when(openAi.reply(any(), anyList(), anyList())).thenReturn(new OpenAiClient.Answer("계란밥을 추천해요.", List.of(recipe.id())));

        ChatDtos.Response result = service.reply(7L, request("추천해 줘"));

        assertThat(result.source()).isEqualTo("OPENAI");
        assertThat(result.reply()).isEqualTo("계란밥을 추천해요.");
        assertThat(result.recommendedRecipes()).containsExactly(recipe);
    }

    @Test void explicitLocalModeNeverUsesConfiguredAi() {
        when(openAi.available()).thenReturn(true);
        var result = service.reply(7L, new ChatDtos.Request("추천", 1, List.of(), "LOCAL"));
        assertThat(result.source()).isEqualTo("LOCAL");
        verify(openAi, never()).reply(any(), anyList(), anyList());
    }

    @Test void failureReleasesConcurrencyPermitButAttemptsStillCount() {
        when(openAi.available()).thenReturn(true);
        when(openAi.reply(any(), anyList(), anyList()))
            .thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_TIMEOUT", "timeout"))
            .thenReturn(new OpenAiClient.Answer("다시 연결됐어요.", List.of()));
        assertThatThrownBy(() -> service.reply(7L, request("추천"))).isInstanceOf(ApiException.class);
        assertThat(service.reply(7L, request("추천")).source()).isEqualTo("OPENAI");
        verify(openAi, times(2)).reply(any(), anyList(), anyList());
    }

    @Test void aiCanChooseBeyondFirstThreeAndGeneralAnswersHaveNoUnrelatedCards() {
        var candidates = List.of(recipe(1L, "계란밥", 15, 12, true, 1), recipe(2L, "계란국", 15, 12, false, 1),
            recipe(3L, "계란말이", 15, 12, false, 1), recipe(4L, "두부부침", 15, 12, false, 1));
        when(recommendations.recommend(7L, 1)).thenReturn(candidates);
        when(openAi.available()).thenReturn(true);
        when(openAi.reply(any(), anyList(), anyList())).thenReturn(new OpenAiClient.Answer("두부부침을 추천해요.", List.of(4L)))
            .thenReturn(new OpenAiClient.Answer("두부를 약불에 익혀 주세요.", List.of()));
        var question = new ChatDtos.Request("계란 말고 두부 요리", 1, List.of(new ChatDtos.Message("user", "계란 요리")));
        assertThat(service.reply(7L, question).recommendedRecipes()).containsExactly(candidates.get(3));
        verify(openAi).reply(eq(question), anyList(), eq(candidates));
        assertThat(service.reply(7L, request("불은 어느 정도로 해?")).recommendedRecipes()).isEmpty();
    }

    private static ChatDtos.Request request(String question) { return new ChatDtos.Request(question, 1, List.of()); }

    private static IngredientResponse stock(String name, ExpiryStatus status) {
        return new IngredientResponse(1L, name, Category.OTHER, BigDecimal.ONE, Unit.PIECE,
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 9), StorageType.FRIDGE, 0, status, 0L);
    }

    private static RecipeRecommendation recipe(Long id, String name, int minutes, int protein, boolean urgent, int matched) {
        var egg = new RecipeRecommendation.Requirement("계란", BigDecimal.ONE,
            BigDecimal.valueOf(matched), BigDecimal.valueOf(matched > 0 ? 0 : 1), Unit.PIECE, urgent, false);
        var rice = new RecipeRecommendation.Requirement("밥", BigDecimal.valueOf(200),
            BigDecimal.valueOf(100), BigDecimal.valueOf(100), Unit.GRAM, false, false);
        return new RecipeRecommendation(id, name, "테스트 메뉴", minutes, Difficulty.EASY, 1,
            List.of(egg, rice), matched > 0 ? List.of(egg) : List.of(), List.of(rice),
            urgent ? List.of("계란") : List.of(), matched, 1, false, List.of("재료를 익혀요."),
            new RecipeRecommendation.Nutrition(300, BigDecimal.valueOf(protein), BigDecimal.TEN, BigDecimal.TEN, true, true),
            "보유 재료 " + matched + "종 활용");
    }
}
