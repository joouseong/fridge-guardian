package com.fridgegatekeeper.recommendation;

import com.fridgegatekeeper.common.ApiException;
import com.fridgegatekeeper.ingredient.Category;
import com.fridgegatekeeper.ingredient.Ingredient;
import com.fridgegatekeeper.ingredient.IngredientRepository;
import com.fridgegatekeeper.ingredient.StorageType;
import com.fridgegatekeeper.ingredient.Unit;
import com.fridgegatekeeper.recipe.Difficulty;
import com.fridgegatekeeper.recipe.Recipe;
import com.fridgegatekeeper.recipe.RecipeIngredient;
import com.fridgegatekeeper.recipe.RecipeRepository;
import com.fridgegatekeeper.user.UserAccount;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** 실제 재료와 레시피를 평가하여 순위, 수량, 날짜 및 사용자별 추천 결과를 검증합니다. */
class RecommendationServiceTest {
    private static final Long OWNER_ID = 7L;
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 9);
    private static final UserAccount OWNER = new UserAccount("cook@example.com", "unused", "요리사");
    private RecipeRepository recipes;
    private IngredientRepository ingredients;
    private RecommendationService service;

    @BeforeEach
    void setUp() {
        recipes = mock(RecipeRepository.class);
        ingredients = mock(IngredientRepository.class);
        // UTC에서는 아직 8일이지만 한국에서는 9일인 순간을 사용합니다.
        Clock clock = Clock.fixed(Instant.parse("2026-09-08T15:00:00Z"), ZoneId.of("Asia/Seoul"));
        service = new RecommendationService(recipes, ingredients, clock);
    }

    @Test
    void matchingThenUrgencyThenShortageControlsOrderWithoutWeightedTradeoffs() {
        Recipe mostMatches = recipe(900L, "보유 재료 우선", 1);
        require(mostMatches, "밥", "1", Unit.PIECE);
        require(mostMatches, "김치", "1", Unit.PIECE);
        require(mostMatches, "두부", "1", Unit.PIECE);
        addMissingIngredients(mostMatches, 25);

        Recipe urgent = recipe(800L, "임박 재료 우선", 1);
        require(urgent, "밥", "1", Unit.PIECE);
        require(urgent, "계란", "1", Unit.PIECE);
        addMissingIngredients(urgent, 15);

        Recipe readyLaterId = riceAndKimchi(40L);
        Recipe readyEarlierId = riceAndKimchi(20L);
        Recipe needsOneMore = riceAndKimchi(10L);
        require(needsOneMore, "간장", "1", Unit.PIECE);

        when(ingredients.findAllByUserId(OWNER_ID)).thenReturn(List.of(
            stock("밥", "1", Unit.PIECE, 4), stock("김치", "1", Unit.PIECE, 4),
            stock("두부", "1", Unit.PIECE, 4), stock("계란", "1", Unit.PIECE, 0)));
        when(recipes.findAll()).thenReturn(List.of(needsOneMore, readyLaterId, urgent, readyEarlierId, mostMatches));

        List<RecipeRecommendation> result = service.recommend(OWNER_ID, 1);

        // 부족 재료가 매우 많아도 앞선 우선순위가 뒤집히지 않아야 합니다.
        assertThat(result).extracting(RecipeRecommendation::id,
            RecipeRecommendation::matchedCount, r -> r.urgentIngredients().size(),
            RecipeRecommendation::missingCount).containsExactly(
                tuple(900L, 3, 0, 25), tuple(800L, 2, 1, 15),
                tuple(20L, 2, 0, 0), tuple(40L, 2, 0, 0), tuple(10L, 2, 0, 1));
    }

    @Test
    void aliasesWhitespaceAndUnicodeFormsAggregateAsOneIngredient() {
        Recipe recipe = recipe(1L, "계란밥", 1);
        require(recipe, "계란", "5", Unit.PIECE);
        require(recipe, "배추 김치", "100", Unit.GRAM);

        RecipeRecommendation result = service.score(recipe, List.of(
            stock(" 달 걀\u00a0", "1", Unit.PIECE, 0),
            stock("계\t란", "2", Unit.PIECE, 5),
            stock(Normalizer.normalize("계란", Normalizer.Form.NFD), "2", Unit.PIECE, 5),
            stock("신 김치", "0.05", Unit.KILOGRAM, 5),
            stock("김치", "50", Unit.GRAM, 5)), 1, TODAY);

        assertThat(result.matchedCount()).isEqualTo(2);
        assertThat(result.canCook()).isTrue();
        assertThat(result.missingIngredients()).isEmpty();
        assertThat(result.urgentIngredients()).containsExactly("계란");
        assertQuantity(requirement(result, "계란"), "5", "5", "0");
        assertQuantity(requirement(result, "배추 김치"), "100", "100", "0");
    }

    @ParameterizedTest(name = "{0} {1} 재고를 {3} 기준으로 비교")
    @CsvSource({
        "0.125, KILOGRAM, 150, GRAM, 125, 25",
        "125, GRAM, 0.150, KILOGRAM, 0.125, 0.025",
        "0.125, LITER, 150, MILLILITER, 125, 25",
        "125, MILLILITER, 0.150, LITER, 0.125, 0.025"
    })
    void compatibleMassAndVolumeUnitsProduceExactShortages(String amount, Unit inventoryUnit,
            String needed, Unit recipeUnit, String available, String shortage) {
        Recipe recipe = recipe(1L, "단위 환산 요리", 1);
        require(recipe, "재료", needed, recipeUnit);

        RecipeRecommendation result = service.score(recipe,
            List.of(stock("재료", amount, inventoryUnit, 1)), 1, TODAY);

        RecipeRecommendation.Requirement item = requirement(result, "재료");
        assertQuantity(item, needed, available, shortage);
        assertThat(item.unit()).isEqualTo(recipeUnit);
        assertThat(item.unitMismatch()).isFalse();
        // 일부만 보유해도 활용 종류에 포함하면서 정확한 부족분을 표시합니다.
        assertThat(result.matchedCount()).isEqualTo(1);
        assertThat(result.missingIngredients()).containsExactly(item);
        assertThat(result.canCook()).isFalse();
    }

    @Test
    void packageCountAndMassOrVolumeDoNotReceiveInventedConversions() {
        Recipe recipe = recipe(1L, "두부 우유 요리", 1);
        require(recipe, "두부", "100", Unit.GRAM);
        require(recipe, "우유", "100", Unit.MILLILITER);

        RecipeRecommendation result = service.score(recipe, List.of(
            stock("두부", "2", Unit.PACK, 0), stock("우유", "100", Unit.GRAM, 0)), 1, TODAY);

        assertThat(result.matchedCount()).isZero();
        assertThat(result.canCook()).isFalse();
        assertThat(result.urgentIngredients()).isEmpty();
        assertThat(result.missingIngredients()).hasSize(2).allSatisfy(item -> {
            assertQuantity(item, "100", "0", "100");
            assertThat(item.unitMismatch()).isTrue();
            assertThat(item.urgent()).isFalse();
        });
    }

    @Test
    void usableStockIsRetainedAlongsideAnIncompatiblePackage() {
        Recipe recipe = recipe(1L, "두부 요리", 1);
        require(recipe, "두부", "150", Unit.GRAM);

        RecipeRecommendation result = service.score(recipe, List.of(
            stock("두부", "100", Unit.GRAM, 4), stock("두부", "2", Unit.PACK, 0)), 1, TODAY);

        RecipeRecommendation.Requirement tofu = requirement(result, "두부");
        assertQuantity(tofu, "150", "100", "50");
        assertThat(tofu.unitMismatch()).isTrue();
        assertThat(tofu.urgent()).isFalse();
        assertThat(result.matchedCount()).isEqualTo(1);
    }

    @Test
    void koreanTodayExcludesExpiredStockAndIncludesTodayAndThirdDayAsUrgent() {
        Recipe recipe = recipe(1L, "날짜 경계 요리", 1);
        for (String name : List.of("계란", "김치", "두부", "우유")) {
            require(recipe, name, "1", Unit.PIECE);
        }
        when(recipes.findAll()).thenReturn(List.of(recipe));
        when(ingredients.findAllByUserId(OWNER_ID)).thenReturn(List.of(
            stock("계란", "999", Unit.PIECE, -1), stock("김치", "1", Unit.PIECE, 0),
            stock("두부", "1", Unit.PIECE, 3), stock("우유", "1", Unit.PIECE, 4)));

        RecipeRecommendation result = service.recommend(OWNER_ID, 1).getFirst();

        assertThat(result.matchedCount()).isEqualTo(3);
        assertThat(result.urgentIngredients()).containsExactly("김치", "두부");
        assertThat(result.missingIngredients()).extracting(RecipeRecommendation.Requirement::name)
            .containsExactly("계란");
        assertQuantity(requirement(result, "계란"), "1", "0", "1");
        assertThat(requirement(result, "우유").urgent()).isFalse();
    }

    @Test
    void expiredDuplicateStockDoesNotHideShortageOrMarkFreshStockUrgent() {
        Recipe recipe = recipe(1L, "계란 요리", 1);
        require(recipe, "계란", "3", Unit.PIECE);

        RecipeRecommendation result = service.score(recipe, List.of(
            stock("달걀", "20", Unit.PIECE, -1), stock("계란", "1", Unit.PIECE, 4)), 1, TODAY);

        assertQuantity(requirement(result, "계란"), "3", "1", "2");
        assertThat(result.urgentIngredients()).isEmpty();
        assertThat(result.canCook()).isFalse();
    }

    @Test
    void servingSelectionScalesRecipeBaselineAndChangesWhetherInventoryIsEnough() {
        Recipe recipe = recipe(1L, "두 사람의 볶음밥", 2);
        require(recipe, "밥", "300", Unit.GRAM);
        List<Ingredient> inventory = List.of(stock("밥", "200", Unit.GRAM, 4));

        RecipeRecommendation one = service.score(recipe, inventory, 1, TODAY);
        RecipeRecommendation two = service.score(recipe, inventory, 2, TODAY);

        assertQuantity(requirement(one, "밥"), "150", "200", "0");
        assertThat(one.canCook()).isTrue();
        assertThat(one.missingIngredients()).isEmpty();
        assertQuantity(requirement(two, "밥"), "300", "200", "100");
        assertThat(two.canCook()).isFalse();
        assertThat(two.missingCount()).isEqualTo(1);
        assertThat(two.matchedCount()).isEqualTo(1);
        assertThat(one.servings()).isEqualTo(1);
        assertThat(two.servings()).isEqualTo(2);
        assertThat(two.nutrition()).isEqualTo(one.nutrition());
        assertThat(two.nutrition().perServing()).isTrue();
    }

    @Test
    void listAndDetailBothUseOnlyTheRequestedUsersInventory() {
        Recipe recipe = recipe(1L, "계란 요리", 1);
        require(recipe, "계란", "2", Unit.PIECE);
        when(recipes.findAll()).thenReturn(List.of(recipe));
        when(recipes.findById(1L)).thenReturn(Optional.of(recipe));
        when(ingredients.findAllByUserId(OWNER_ID)).thenReturn(List.of(stock("달걀", "2", Unit.PIECE, 0)));
        when(ingredients.findAllByUserId(8L)).thenReturn(List.of(stock("우유", "1", Unit.LITER, 0)));

        RecipeRecommendation ownersList = service.recommend(OWNER_ID, 1).getFirst();
        RecipeRecommendation strangersList = service.recommend(8L, 1).getFirst();
        RecipeRecommendation ownersDetail = service.detail(OWNER_ID, 1L, 1);
        RecipeRecommendation strangersDetail = service.detail(8L, 1L, 1);

        assertThat(ownersList.canCook()).isTrue();
        assertThat(ownersList.urgentIngredients()).containsExactly("계란");
        assertThat(strangersList.matchedCount()).isZero();
        assertThat(strangersList.urgentIngredients()).isEmpty();
        assertQuantity(requirement(strangersList, "계란"), "2", "0", "2");
        assertThat(ownersDetail).isEqualTo(ownersList);
        assertThat(strangersDetail).isEqualTo(strangersList);
        verify(ingredients, times(2)).findAllByUserId(OWNER_ID);
        verify(ingredients, times(2)).findAllByUserId(8L);
        verifyNoMoreInteractions(ingredients);
    }

    @Test
    void emptyFridgeStillReturnsRecipesWithCompleteShortages() {
        Recipe recipe = riceAndKimchi(1L);
        when(recipes.findAll()).thenReturn(List.of(recipe));
        when(ingredients.findAllByUserId(OWNER_ID)).thenReturn(List.of());

        RecipeRecommendation result = service.recommend(OWNER_ID, 2).getFirst();

        assertThat(result.canCook()).isFalse();
        assertThat(result.availableIngredients()).isEmpty();
        assertThat(result.urgentIngredients()).isEmpty();
        assertThat(result.missingIngredients()).hasSize(2).allSatisfy(item ->
            assertQuantity(item, "2", "0", "2"));
    }

    @Test
    void unsupportedServingCountsAreRejectedBeforeReadingEitherRepository() {
        for (int servings : new int[] {-1, 0, 3, Integer.MAX_VALUE}) {
            assertThatThrownBy(() -> service.recommend(OWNER_ID, servings))
                .isInstanceOfSatisfying(ApiException.class, error ->
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
            assertThatThrownBy(() -> service.detail(OWNER_ID, 1L, servings))
                .isInstanceOfSatisfying(ApiException.class, error ->
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verifyNoInteractions(recipes, ingredients);
    }

    private static Recipe recipe(Long id, String name, int servings) {
        Recipe recipe = new Recipe(name, "테스트 레시피", 10, Difficulty.EASY, servings,
            "재료를 준비합니다.\n익혀서 완성합니다.", 300, new BigDecimal("12"),
            new BigDecimal("40"), new BigDecimal("10"));
        ReflectionTestUtils.setField(recipe, "id", id);
        return recipe;
    }

    private static Recipe riceAndKimchi(Long id) {
        Recipe recipe = recipe(id, "김치볶음밥 " + id, 1);
        require(recipe, "밥", "1", Unit.PIECE);
        require(recipe, "김치", "1", Unit.PIECE);
        return recipe;
    }

    private static void require(Recipe recipe, String name, String amount, Unit unit) {
        recipe.addIngredient(new RecipeIngredient(recipe, name, new BigDecimal(amount), unit));
    }

    private static void addMissingIngredients(Recipe recipe, int count) {
        for (int i = 1; i <= count; i++) require(recipe, "미보유 재료 " + i, "1", Unit.PIECE);
    }

    private static Ingredient stock(String name, String amount, Unit unit, int daysUntilExpiration) {
        return new Ingredient(OWNER, name, Category.OTHER, new BigDecimal(amount), unit,
            TODAY.minusDays(10), TODAY.plusDays(daysUntilExpiration), StorageType.FRIDGE);
    }

    private static RecipeRecommendation.Requirement requirement(RecipeRecommendation result, String name) {
        return result.requiredIngredients().stream().filter(item -> item.name().equals(name))
            .findFirst().orElseThrow();
    }

    private static void assertQuantity(RecipeRecommendation.Requirement item,
                                       String needed, String available, String shortage) {
        assertThat(item.requiredQuantity()).isEqualByComparingTo(needed);
        assertThat(item.availableQuantity()).isEqualByComparingTo(available);
        assertThat(item.missingQuantity()).isEqualByComparingTo(shortage);
    }
}
