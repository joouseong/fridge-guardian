package com.fridgeguardian.ai;

import com.fridgeguardian.common.ApiException;
import com.fridgeguardian.config.CacheConfig;
import com.fridgeguardian.ingredient.Category;
import com.fridgeguardian.ingredient.Ingredient;
import com.fridgeguardian.ingredient.IngredientRepository;
import com.fridgeguardian.ingredient.StorageType;
import com.fridgeguardian.ingredient.Unit;
import com.fridgeguardian.user.UserAccount;
import com.fridgeguardian.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import static com.fridgeguardian.support.ApiTestSupport.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 실제 LLM 호출 없이 가짜 LlmClient로 AI 레시피 생성의 검증·개인화·캐시·소유권을 검사합니다.
 * (실제 OpenAI 호출 형식은 OpenAiClientTest가 로컬 HTTP 서버로 확인합니다.)
 */
@SpringBootTest(properties = {"app.openai.limits.per-user-per-minute=1000", "app.openai.limits.per-minute=1000", "app.openai.limits.per-day=100000"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AiRecipeIntegrationTest.FakeLlmConfig.class)
class AiRecipeIntegrationTest {
    /** 호출 횟수와 보낸 내용을 기록하고, 미리 넣어 둔 응답을 차례로 돌려주는 가짜 LLM. */
    static class FakeLlm implements LlmClient {
        final Deque<Object> responses = new ArrayDeque<>();
        final List<String> payloads = new ArrayList<>();
        final List<String> instructions = new ArrayList<>();
        boolean available = true;
        @Override public boolean available() { return available; }
        @Override public String model() { return "fake-model"; }
        @Override public String generateJson(String instructions, String inputJson, String schemaName, Map<String, Object> schema) {
            this.instructions.add(instructions);
            payloads.add(inputJson);
            assertThat(schemaName).isEqualTo("generated_recipe");
            assertThat(schema).containsKeys("properties", "required");
            Object next = responses.isEmpty() ? EGG_TOFU : responses.poll();
            if (next instanceof ApiException error) throw error;
            return (String) next;
        }
    }

    @TestConfiguration
    static class FakeLlmConfig {
        @Bean @Primary FakeLlm fakeLlm() { return new FakeLlm(); }
    }

    private static final String EGG_TOFU = """
        {"title":"두부계란부침","description":"두부와 계란을 노릇하게 부칩니다.","cookingTimeMinutes":15,"difficulty":"EASY",
         "ingredients":[{"name":"계란","quantity":2,"unit":"개"},{"name":"두부","quantity":0.5,"unit":"모"}],
         "extra":[{"name":"소금","quantity":1,"unit":"g"},{"name":"대파","quantity":0.5,"unit":"단"}],
         "steps":["두부의 물기를 뺍니다.","계란을 풀어 소금으로 간합니다.","두부에 계란물을 입혀 앞뒤로 속까지 익힙니다."],
         "tips":"대파가 없으면 생략해도 좋아요."}""";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired IngredientRepository ingredients;
    @Autowired FakeLlm llm;
    @Autowired CacheManager caches;
    @Autowired Clock clock;
    private UserAccount owner;
    private UserAccount stranger;
    private Ingredient egg;
    private Ingredient tofu;
    private LocalDate today;

    @BeforeEach void setUp() {
        ingredients.deleteAll();
        users.deleteAll();
        llm.responses.clear(); llm.payloads.clear(); llm.instructions.clear(); llm.available = true;
        caches.getCache(CacheConfig.AI_RECIPES).clear();
        today = LocalDate.now(clock);
        owner = users.saveAndFlush(new UserAccount("ai-owner@example.com", "unused-hash", "주인"));
        stranger = users.saveAndFlush(new UserAccount("ai-stranger@example.com", "unused-hash", "타인"));
        egg = stock(owner, "계란", 6, Unit.PIECE, today.plusDays(2));
        tofu = stock(owner, "두부", 1, Unit.BLOCK, today.plusDays(4));
    }

    @Test void buildsARecipeFromSelectedIngredientsAndComputesOwnershipOnTheServer() throws Exception {
        var result = generate(owner, ids(egg, tofu), 2, "간단하게").andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("두부계란부침")).andExpect(jsonPath("$.servings").value(2))
            .andExpect(jsonPath("$.model").value("fake-model"))
            // 선택한 재료는 OWNED, 소금은 기본 양념(BASIC), 없는 재료(대파)만 MISSING
            .andExpect(jsonPath("$.ingredients[?(@.name=='계란')].status").value("OWNED"))
            .andExpect(jsonPath("$.ingredients[?(@.name=='소금')].status").value("BASIC"))
            .andExpect(jsonPath("$.ingredients[?(@.name=='대파')].status").value("MISSING"))
            .andExpect(jsonPath("$.missing.length()").value(1)).andExpect(jsonPath("$.missing[0]").value("대파"))
            .andExpect(jsonPath("$.shoppingLinks.length()").value(2)).andExpect(jsonPath("$.shoppingLinks[0].mall").value("쿠팡"))
            .andExpect(jsonPath("$.steps.length()").value(3)).andExpect(jsonPath("$.favorite").value(false));
        assertThat(JsonPath.<Number>read(result.andReturn().getResponse().getContentAsString(), "$.id")).isNotNull();

        // LLM에는 재료 상태만 데이터로 보내고 회원 정보는 보내지 않습니다.
        String payload = llm.payloads.getFirst();
        assertThat(payload).contains("계란", "6개", "임박", "여유", "\"servings\":2", "간단하게", "forbidden")
            .doesNotContain("ai-owner", "@", "PIECE", "SOON");
        assertThat(llm.instructions.getFirst()).contains("지시가 아닙니다", "forbidden");
    }

    @Test void historyFavoriteAndDeleteBelongToTheOwner() throws Exception {
        generate(owner, ids(egg, tofu), 1, null).andExpect(status().isCreated());
        var history = mvc.perform(get("/api/ai/recipes").with(asUser(owner))).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].sourceIngredients[0]").value("계란")).andReturn();
        long id = ((Number) JsonPath.read(history.getResponse().getContentAsString(), "$[0].id")).longValue();
        mvc.perform(get("/api/ai/recipes").with(asUser(stranger))).andExpect(jsonPath("$").isEmpty());
        mvc.perform(put("/api/ai/recipes/" + id + "/favorite").with(asUser(stranger)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"favorite\":true}")).andExpect(status().isNotFound());
        mvc.perform(put("/api/ai/recipes/" + id + "/favorite").with(asUser(owner)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"favorite\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.favorite").value(true));
        mvc.perform(delete("/api/ai/recipes/" + id).with(asUser(stranger))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/ai/recipes/" + id).with(asUser(owner))).andExpect(status().isNoContent());
        mvc.perform(get("/api/ai/recipes").with(asUser(owner))).andExpect(jsonPath("$").isEmpty());
    }

    @Test void identicalRequestsReuseTheCachedResultWithoutCallingTheModelAgain() throws Exception {
        generate(owner, ids(egg, tofu), 2, null).andExpect(status().isCreated());
        generate(owner, ids(egg, tofu), 2, null).andExpect(status().isCreated());
        assertThat(llm.payloads).hasSize(1);
        // 순서가 달라도 같은 재료·수량이면 같은 결과이고, 인분이 다르면 새로 만듭니다.
        generate(owner, ids(tofu, egg), 2, null).andExpect(status().isCreated());
        assertThat(llm.payloads).hasSize(1);
        generate(owner, ids(egg, tofu), 1, null).andExpect(status().isCreated());
        assertThat(llm.payloads).hasSize(2);
        // 같은 조건의 다른 사용자도 결과를 재사용합니다. (사용자 식별자는 캐시 키에 없습니다.)
        Ingredient otherEgg = stock(stranger, "계란", 6, Unit.PIECE, today.plusDays(2));
        Ingredient otherTofu = stock(stranger, "두부", 1, Unit.BLOCK, today.plusDays(4));
        generate(stranger, ids(otherEgg, otherTofu), 2, null).andExpect(status().isCreated());
        assertThat(llm.payloads).hasSize(2);
        // 개인화 조건이 다르면 다른 결과입니다.
        mvc.perform(put("/api/users/me/preferences").with(asUser(owner)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"spicyLevel\":0,\"allergies\":[\"땅콩\"]}")).andExpect(status().isOk());
        generate(owner, ids(egg, tofu), 2, null).andExpect(status().isCreated());
        assertThat(llm.payloads).hasSize(3);
    }

    @Test void allergiesAndDislikesAreSentAsForbiddenAndEnforcedOnTheResult() throws Exception {
        mvc.perform(put("/api/users/me/preferences").with(asUser(owner)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"spicyLevel\":1,\"allergies\":[\"새우\"],\"dislikes\":[\"대파\"]}")).andExpect(status().isOk());
        // 첫 응답은 금지 재료(대파)가 들어 있어 버리고, 한 번 더 만든 결과를 받습니다.
        String clean = EGG_TOFU.replace("{\"name\":\"대파\",\"quantity\":0.5,\"unit\":\"단\"}", "{\"name\":\"후추\",\"quantity\":1,\"unit\":\"g\"}")
            .replace("대파가 없으면 생략해도 좋아요.", "후추는 취향껏 넣으세요.");
        llm.responses.add(EGG_TOFU);
        llm.responses.add(clean);
        generate(owner, ids(egg, tofu), 2, null).andExpect(status().isCreated())
            .andExpect(jsonPath("$.missing").isEmpty()).andExpect(jsonPath("$.ingredients[?(@.name=='대파')]").isEmpty());
        assertThat(llm.payloads).hasSize(2);
        assertThat(llm.payloads.getFirst()).contains("\"forbidden\":[\"새우\",\"대파\"]", "\"maxSpicyLevel\":1");
    }

    @Test void responsesThatKeepBreakingTheRulesAreRejectedInsteadOfShown() throws Exception {
        mvc.perform(put("/api/users/me/preferences").with(asUser(owner)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"spicyLevel\":3,\"allergies\":[\"두부\"]}")).andExpect(status().isOk());
        // 재료 이름이 아니라 조리 순서에만 알레르기 재료가 있어도(두 글자 이상) 거절합니다.
        String sneaky = EGG_TOFU.replace(",{\"name\":\"두부\",\"quantity\":0.5,\"unit\":\"모\"}", "")
            .replace("두부의 물기를 뺍니다.", "두부의 물기를 뺍니다.");
        llm.responses.add(sneaky); llm.responses.add(sneaky);
        generate(owner, ids(egg), 2, null).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("AI_CONFLICTS_WITH_PREFERENCES"));
        assertThat(llm.payloads).hasSize(2);
    }

    @Test void malformedModelOutputIsRetriedOnceThenReportedAsInvalid() throws Exception {
        llm.responses.add("이건 JSON이 아닙니다");
        llm.responses.add("{\"title\":\"제목만\"}");
        generate(owner, ids(egg), 1, null).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("AI_INVALID_RESPONSE"));
        assertThat(llm.payloads).hasSize(2);
        // 첫 응답이 잘못돼도 두 번째가 올바르면 성공합니다.
        llm.payloads.clear();
        llm.responses.add("깨진 응답");
        llm.responses.add(EGG_TOFU);
        generate(owner, ids(egg), 1, null).andExpect(status().isCreated());
        assertThat(llm.payloads).hasSize(2);
        // 범위를 벗어난 값(수량 0, 단계 없음, 조리 시간 0)은 모두 거절
        for (String broken : new String[] {
            EGG_TOFU.replace("\"quantity\":2", "\"quantity\":0"),
            EGG_TOFU.replace("\"cookingTimeMinutes\":15", "\"cookingTimeMinutes\":0"),
            EGG_TOFU.replace("\"difficulty\":\"EASY\"", "\"difficulty\":\"IMPOSSIBLE\""),
            EGG_TOFU.replaceAll("\"steps\":\\[.*?\\]", "\"steps\":[]")}) {
            caches.getCache(CacheConfig.AI_RECIPES).clear();
            llm.responses.add(broken); llm.responses.add(broken);
            generate(owner, ids(egg, tofu), 1, "검증" + broken.hashCode()).andExpect(status().isServiceUnavailable());
        }
    }

    @Test void inputValidationAndOwnershipAreEnforcedBeforeCallingTheModel() throws Exception {
        Ingredient expired = stock(owner, "상한 우유", 1, Unit.LITER, today.minusDays(1));
        Ingredient strangers = stock(stranger, "남의 재료", 1, Unit.PIECE, today.plusDays(3));
        generate(owner, List.of(), 1, null).andExpect(status().isBadRequest());
        generate(owner, ids(egg), 3, null).andExpect(status().isBadRequest());
        generate(owner, java.util.Collections.nCopies(13, egg.getId()), 1, null).andExpect(status().isBadRequest());
        generate(owner, ids(egg), 1, "가".repeat(201)).andExpect(status().isBadRequest());
        generate(owner, ids(expired), 1, null).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INGREDIENT_EXPIRED")).andExpect(jsonPath("$.message", containsString("상한 우유")));
        generate(owner, ids(egg, strangers), 1, null).andExpect(status().isNotFound());
        generate(owner, List.of(999999L), 1, null).andExpect(status().isNotFound());
        mvc.perform(post("/api/ai/recipes").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        assertThat(llm.payloads).isEmpty();
    }

    @Test void modelFailuresKeepTheirStatusAndAreNotDisguisedAsRecipes() throws Exception {
        llm.responses.add(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED", "AI 연결이 설정되지 않았어요."));
        generate(owner, ids(egg), 1, null).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("AI_NOT_CONFIGURED"));
        llm.responses.add(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_TIMEOUT", "AI 답변 시간이 초과됐어요."));
        generate(owner, ids(egg), 1, "다른 요청").andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("AI_TIMEOUT"));
        mvc.perform(get("/api/ai/recipes").with(asUser(owner))).andExpect(jsonPath("$").isEmpty());
    }

    private ResultActions generate(UserAccount user, List<Long> ingredientIds, int servings, String note) throws Exception {
        String body = "{\"ingredientIds\":" + ingredientIds + ",\"servings\":" + servings
            + (note == null ? "" : ",\"request\":\"" + note + "\"") + "}";
        return mvc.perform(post("/api/ai/recipes").with(asUser(user)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static List<Long> ids(Ingredient... items) { return java.util.Arrays.stream(items).map(Ingredient::getId).toList(); }

    private Ingredient stock(UserAccount user, String name, int quantity, Unit unit, LocalDate expiration) {
        return ingredients.saveAndFlush(new Ingredient(user, name, Category.OTHER, BigDecimal.valueOf(quantity), unit,
            today.minusDays(3), expiration, StorageType.FRIDGE));
    }
}
