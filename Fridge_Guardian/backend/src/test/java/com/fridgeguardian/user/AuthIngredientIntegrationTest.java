package com.fridgeguardian.user;

import com.fridgeguardian.ingredient.IngredientRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 보안 필터·세션·CSRF·JPA를 함께 통과하여 사용자 데이터 격리를 검증합니다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AuthIngredientIntegrationTest.FixedClock.class)
class AuthIngredientIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired IngredientRepository ingredients;

    @TestConfiguration
    static class FixedClock {
        @Bean @Primary Clock testClock() {
            // UTC 8일 15시이지만 서울에서는 9일 0시입니다.
            return Clock.fixed(Instant.parse("2026-09-08T15:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    @BeforeEach void clearUsers() {
        ingredients.deleteAll();
        users.deleteAll();
    }

    @Test void registrationLoginAndLogoutPreserveSecurityBoundaries() throws Exception {
        Browser browser = anonymous();
        register(browser, "COOK@example.com", "fridge1234!", "요리사");
        assertThat(users.findByEmail("cook@example.com")).isPresent();
        assertThat(users.findByEmail("cook@example.com").orElseThrow().getPasswordHash()).startsWith("$2");
        // 가입만으로는 로그인되지 않습니다.
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        MvcResult login = mvc.perform(secure(post("/api/auth/login"), browser)
            .content("{\"email\":\"cook@example.com\",\"password\":\"fridge1234!\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.user.email").value("cook@example.com"))
            .andExpect(jsonPath("$.tokenType").value("Bearer")).andExpect(jsonPath("$.expiresIn").value(900))
            .andExpect(jsonPath("$.user.password").doesNotExist()).andExpect(jsonPath("$.user.passwordHash").doesNotExist())
            // Refresh Token은 JavaScript가 읽을 수 없는 쿠키로만 내려갑니다.
            .andExpect(cookie().httpOnly("refresh_token", true)).andExpect(cookie().path("refresh_token", "/api/auth"))
            .andExpect(cookie().attribute("refresh_token", "SameSite", "Strict"))
            .andReturn();
        String accessToken = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
        assertThat(login.getResponse().getContentAsString()).doesNotContain(login.getResponse().getCookie("refresh_token").getValue());
        Browser session = new Browser(accessToken);
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(session))).andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("cook@example.com")).andExpect(jsonPath("$.provider").value("LOCAL"));
        mvc.perform(secure(post("/api/ingredients"), session).content(ingredient("계란", "2026-09-09", null)))
            .andExpect(status().isCreated());
        // 변조되었거나 형식이 틀린 토큰은 401입니다.
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken + "x"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")).andExpect(status().isUnauthorized());
        // 로그아웃하면 Refresh 쿠키가 지워지고 그 Refresh Token은 더 이상 쓸 수 없습니다.
        var refreshCookie = login.getResponse().getCookie("refresh_token");
        mvc.perform(post("/api/auth/logout").cookie(refreshCookie)).andExpect(status().isNoContent())
            .andExpect(cookie().maxAge("refresh_token", 0));
        mvc.perform(post("/api/auth/refresh").cookie(refreshCookie)).andExpect(status().isUnauthorized());
    }

    @Test void refreshRotatesTokensAndDetectsReuse() throws Exception {
        register(anonymous(), "cook@example.com", "fridge1234!", "요리사");
        MvcResult login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"cook@example.com\",\"password\":\"fridge1234!\"}")).andExpect(status().isOk()).andReturn();
        var first = login.getResponse().getCookie("refresh_token");
        MvcResult refreshed = mvc.perform(post("/api/auth/refresh").cookie(first)).andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty()).andExpect(cookie().exists("refresh_token")).andReturn();
        var second = refreshed.getResponse().getCookie("refresh_token");
        assertThat(second.getValue()).isNotEqualTo(first.getValue());
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer "
            + JsonPath.<String>read(refreshed.getResponse().getContentAsString(), "$.accessToken"))).andExpect(status().isOk());
        // 이미 사용한 토큰을 다시 내면(동시 갱신 유예 시간 안) 거절만 하고, 새 토큰은 계속 유효합니다.
        mvc.perform(post("/api/auth/refresh").cookie(first)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(second)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(new jakarta.servlet.http.Cookie("refresh_token", "forged")))
            .andExpect(status().isUnauthorized());
    }

    @Test void refreshAndLogoutRejectForeignOrigins() throws Exception {
        register(anonymous(), "cook@example.com", "fridge1234!", "요리사");
        var cookie = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"cook@example.com\",\"password\":\"fridge1234!\"}")).andReturn().getResponse().getCookie("refresh_token");
        mvc.perform(post("/api/auth/refresh").cookie(cookie).header("Origin", "https://evil.example"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").cookie(cookie).header("Origin", "https://evil.example"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/refresh").cookie(cookie).header("Origin", "http://localhost:3000"))
            .andExpect(status().isOk());
    }

    @Test void ingredientEndpointsRequireLoginAndPublicEndpointsStayOpen() throws Exception {
        mvc.perform(get("/api/ingredients")).andExpect(status().isUnauthorized());
        Browser browser = anonymous();
        mvc.perform(secure(post("/api/ingredients"), browser).content(ingredient("계란", "2026-09-09", null)))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/health")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/providers")).andExpect(status().isOk())
            .andExpect(jsonPath("$.google").value(false)).andExpect(jsonPath("$.kakao").value(false));
        mvc.perform(get("/api/unknown-endpoint").header(HttpHeaders.AUTHORIZATION, bearer(login("x@example.com"))))
            .andExpect(status().isNotFound());
    }

    @Test void duplicateEmailAndInvalidPasswordsHaveSafeErrors() throws Exception {
        Browser browser = anonymous();
        register(browser, "cook@example.com", "fridge1234!", "요리사");
        mvc.perform(secure(post("/api/auth/register"), browser)
            .content("{\"email\":\"COOK@example.com\",\"password\":\"fridge1234!\",\"passwordConfirm\":\"fridge1234!\",\"nickname\":\"두번째\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
        mvc.perform(secure(post("/api/auth/register"), browser)
            .content("{\"email\":\"unicode@example.com\",\"password\":\"" + "가".repeat(25) + "\",\"passwordConfirm\":\"" + "가".repeat(25) + "\",\"nickname\":\"길이검사\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.password").exists());
        for (String email : new String[] {"cook@example.com", "missing@example.com"}) {
            mvc.perform(secure(post("/api/auth/login"), browser)
                .content("{\"email\":\"" + email + "\",\"password\":\"wrongpassword\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
    }

    @Test void passwordRulesFollowThePrdSixCharactersAndConfirmation() throws Exception {
        String base = "{\"email\":\"rule@example.com\",\"nickname\":\"검사\",\"password\":\"%s\",\"passwordConfirm\":\"%s\"}";
        // 5자는 거절, 6자는 허용
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(base.formatted("abc12", "abc12")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.password").exists());
        // 비밀번호 확인이 다르면 거절
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(base.formatted("abc123", "abc124")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.passwordConfirm").exists());
        assertThat(users.findByEmail("rule@example.com")).isEmpty();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(base.formatted("abc123", "abc123")))
            .andExpect(status().isCreated());
    }

    @Test void everyIngredientOperationChecksOwnership() throws Exception {
        Browser owner = login("owner@example.com");
        Browser stranger = login("stranger@example.com");
        long id = create(owner, "계란", "2026-09-12");
        mvc.perform(get("/api/ingredients").header(HttpHeaders.AUTHORIZATION, bearer(stranger))).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/ingredients/" + id).header(HttpHeaders.AUTHORIZATION, bearer(stranger))).andExpect(status().isNotFound());
        mvc.perform(secure(put("/api/ingredients/" + id), stranger)
            .content(ingredient("훔친재료", "2026-09-12", 0L))).andExpect(status().isNotFound());
        mvc.perform(secure(delete("/api/ingredients/" + id), stranger)).andExpect(status().isNotFound());
        mvc.perform(get("/api/ingredients/" + id).header(HttpHeaders.AUTHORIZATION, bearer(owner))).andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("계란"));
        mvc.perform(secure(delete("/api/ingredients/" + id), owner)).andExpect(status().isNoContent());
        mvc.perform(get("/api/ingredients/" + id).header(HttpHeaders.AUTHORIZATION, bearer(owner))).andExpect(status().isNotFound());
    }

    @Test void staleUpdatesReturnConflictAndRequireVersion() throws Exception {
        Browser owner = login("owner@example.com");
        long id = create(owner, "계란", "2026-09-12");
        mvc.perform(secure(put("/api/ingredients/" + id), owner)
            .content(ingredient("달걀", "2026-09-12", 0L))).andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(1));
        mvc.perform(secure(put("/api/ingredients/" + id), owner)
            .content(ingredient("오래된수정", "2026-09-12", 0L))).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("STALE_VERSION"));
        mvc.perform(secure(put("/api/ingredients/" + id), owner)
            .content(ingredient("버전없음", "2026-09-12", null))).andExpect(status().isBadRequest());
    }

    @Test void invalidDatesNumbersAndEnumsReturnValidationErrors() throws Exception {
        Browser owner = login("owner@example.com");
        String valid = ingredient("계란", "2026-09-12", null);
        for (String invalid : new String[] {
            valid.replace("2026-09-01", "2026-09-10"),
            valid.replace("2026-09-12", "2026-08-31"),
            valid.replace("\"quantity\":6", "\"quantity\":0"),
            valid.replace("\"quantity\":6", "\"quantity\":1.0001"),
            valid.replace("DAIRY", "INVALID"), valid.replace("2026-09-12", "2026-02-30")
        }) {
            mvc.perform(secure(post("/api/ingredients"), owner).content(invalid))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors").exists());
        }
        mvc.perform(get("/api/ingredients?sort=invalid").header(HttpHeaders.AUTHORIZATION, bearer(owner))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/ingredients/not-a-number").header(HttpHeaders.AUTHORIZATION, bearer(owner))).andExpect(status().isBadRequest());
    }

    @Test void dashboardUsesKoreanDateAndInclusiveThreeDayWindow() throws Exception {
        Browser owner = login("owner@example.com");
        create(owner, "만료", "2026-09-08");
        create(owner, "오늘", "2026-09-09");
        create(owner, "삼일후", "2026-09-12");
        create(owner, "사일후", "2026-09-13");
        Browser stranger = login("stranger@example.com");
        create(stranger, "다른사용자", "2026-09-09");
        mvc.perform(get("/api/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(owner))).andExpect(status().isOk())
            .andExpect(jsonPath("$.today").value("2026-09-09"))
            .andExpect(jsonPath("$.total").value(4)).andExpect(jsonPath("$.safeCount").value(1))
            .andExpect(jsonPath("$.soonCount").value(2)).andExpect(jsonPath("$.expiredCount").value(1))
            .andExpect(jsonPath("$.todayCount").value(1))
            .andExpect(jsonPath("$.expiringIngredients[0].name").value("오늘"))
            .andExpect(jsonPath("$.expiringIngredients[0].daysUntilExpiration").value(0))
            .andExpect(jsonPath("$.expiringIngredients[1].daysUntilExpiration").value(3))
            .andExpect(jsonPath("$.expiredIngredients[0].daysUntilExpiration").value(-1));
        mvc.perform(get("/api/ingredients").header(HttpHeaders.AUTHORIZATION, bearer(owner))).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("EXPIRED"))
            .andExpect(jsonPath("$[3].status").value("SAFE"));
    }

    @Test void emptyDashboardHasZeroCountsAndEmptyArrays() throws Exception {
        Browser owner = login("owner@example.com");
        mvc.perform(get("/api/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(owner))).andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.todayCount").value(0))
            .andExpect(jsonPath("$.expiringIngredients.length()").value(0))
            .andExpect(jsonPath("$.expiredIngredients.length()").value(0));
    }

    @Test void unsupportedRequestMediaTypeKeepsItsClientErrorStatus() throws Exception {
        Browser owner = login("owner@example.com");
        mvc.perform(secure(post("/api/ingredients"), owner)
            .contentType(MediaType.APPLICATION_XML).content("<ingredient>계란</ingredient>"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        mvc.perform(get("/api/ingredients").header(HttpHeaders.AUTHORIZATION, bearer(owner)).accept(MediaType.APPLICATION_XML))
            .andExpect(status().isNotAcceptable());
        assertThat(ingredients.count()).isZero();
    }

    @Test void seededRecipeListAndDetailUseTheLoggedInUsersStockAndSelectedServings() throws Exception {
        Browser owner = login("owner@example.com");
        create(owner, "달걀", "2026-09-09");
        mvc.perform(secure(post("/api/ingredients"), owner).content(
            ingredient("밥", "2026-09-12", null).replace("\"quantity\":6", "\"quantity\":0.25")
                .replace("PIECE", "KILOGRAM")))
            .andExpect(status().isCreated());

        mvc.perform(get("/api/recipes/recommendations?servings=1").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(21))
            // 달걀·밥만 가진 경우 필요 재료 4종 중 2종(50%)이 겹치는 간장 계란밥이 BEST MATCH입니다.
            .andExpect(jsonPath("$[0].id").value(16)).andExpect(jsonPath("$[0].matchedCount").value(2))
            .andExpect(jsonPath("$[0].matchRate").value(50)).andExpect(jsonPath("$[0].best").value(true))
            .andExpect(jsonPath("$[1].best").value(false));
        mvc.perform(get("/api/recipes/16?servings=1").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("간장 계란밥"))
            .andExpect(jsonPath("$.servings").value(1))
            .andExpect(jsonPath("$.matchedCount").value(2))
            .andExpect(jsonPath("$.missingCount").value(2))
            .andExpect(jsonPath("$.requiredIngredients[0].requiredQuantity").value(200))
            .andExpect(jsonPath("$.requiredIngredients[0].availableQuantity").value(250))
            .andExpect(jsonPath("$.requiredIngredients[0].missingQuantity").value(0))
            .andExpect(jsonPath("$.urgentIngredients[0]").value("밥"))
            .andExpect(jsonPath("$.steps.length()").value(3))
            .andExpect(jsonPath("$.nutrition.perServing").value(true));
        mvc.perform(get("/api/recipes/16?servings=2").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requiredIngredients[0].requiredQuantity").value(400))
            .andExpect(jsonPath("$.requiredIngredients[0].missingQuantity").value(150))
            .andExpect(jsonPath("$.missingCount").value(3));

        Browser stranger = login("stranger@example.com");
        mvc.perform(get("/api/recipes/16?servings=1").header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.matchedCount").value(0))
            .andExpect(jsonPath("$.missingCount").value(4))
            .andExpect(jsonPath("$.urgentIngredients.length()").value(0));
        mvc.perform(get("/api/recipes/999999").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/recipes/16?servings=3").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/recipes/recommendations"))
            .andExpect(status().isUnauthorized());
    }

    @Test void optionalExpirationSurvivesCrudSortingDashboardRecommendationsAndChat() throws Exception {
        Browser owner = login("optional@example.com");
        String noDate = ingredient("계란", null, null).replace("\"null\"", "null");
        var result = mvc.perform(secure(post("/api/ingredients"), owner).content(noDate))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("UNKNOWN"))
            .andExpect(jsonPath("$.expirationDate").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.daysUntilExpiration").value(org.hamcrest.Matchers.nullValue())).andReturn();
        long id = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        create(owner, "두부", "2026-09-09");
        mvc.perform(get("/api/ingredients?sort=expiration").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("두부"))
            .andExpect(jsonPath("$[1].status").value("UNKNOWN"));
        mvc.perform(get("/api/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(owner))).andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.safeCount").value(0))
            .andExpect(jsonPath("$.unknownCount").value(1)).andExpect(jsonPath("$.todayCount").value(1));
        mvc.perform(get("/api/recipes/16?servings=1").header(HttpHeaders.AUTHORIZATION, bearer(owner))).andExpect(status().isOk())
            .andExpect(jsonPath("$.matchedCount").value(1)).andExpect(jsonPath("$.urgentIngredients").isEmpty());
        mvc.perform(secure(post("/api/ai/chat"), owner).content("{\"message\":\"추천\",\"servings\":1,\"mode\":\"LOCAL\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.source").value("LOCAL"))
            .andExpect(jsonPath("$.reply", org.hamcrest.Matchers.containsString("기한을 등록하지 않은")));
        mvc.perform(secure(put("/api/ingredients/" + id), owner).content(ingredient("계란", "2026-09-20", 0L)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SAFE"));
        mvc.perform(secure(put("/api/ingredients/" + id), owner).content(
            ingredient("계란", "2026-09-20", 1L).replace(",\"expirationDate\":\"2026-09-20\"", "")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNKNOWN"));
        mvc.perform(get("/api/ingredients/" + id).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.expirationDate").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test void batchCreatesAllRowsWithOptionalDatesAndKeepsUsersIsolated() throws Exception {
        Browser owner = login("owner@example.com");
        String rows = ingredient("계란", "2026-09-09", null) + "," +
            ingredient("두부", "2026-09-20", null).replace("\"2026-09-20\"", "null");
        mvc.perform(secure(post("/api/ingredients/batch"), owner).content(batch(java.util.UUID.randomUUID().toString(), rows)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("계란")).andExpect(jsonPath("$[1].status").value("UNKNOWN"));
        Browser stranger = login("stranger@example.com");
        mvc.perform(get("/api/ingredients").header(HttpHeaders.AUTHORIZATION, bearer(stranger))).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(owner))).andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.unknownCount").value(1));
    }

    @Test void batchRejectsAnInvalidRowWithoutSavingAnyIngredients() throws Exception {
        Browser owner = login("owner@example.com");
        String valid = ingredient("계란", "2026-09-09", null);
        for (String invalid : new String[] {
            ingredient("두부", "2026-08-30", null), valid.replace("\"quantity\":6", "\"quantity\":0"), "null"
        }) {
            mvc.perform(secure(post("/api/ingredients/batch"), owner).content(batch(java.util.UUID.randomUUID().toString(), valid + "," + invalid)))
                .andExpect(status().isBadRequest());
            assertThat(ingredients.count()).isZero();
        }
    }

    @Test void batchValidatesBoundsAndRequiresAuthentication() throws Exception {
        Browser anonymous = anonymous();
        String row = ingredient("계란", "2026-09-09", null);
        String body = batch(java.util.UUID.randomUUID().toString(), row);
        mvc.perform(secure(post("/api/ingredients/batch"), anonymous).content(body)).andExpect(status().isUnauthorized());
        Browser owner = login("owner@example.com");
        for (String bad : new String[] { batch(java.util.UUID.randomUUID().toString(), ""),
            batch(java.util.UUID.randomUUID().toString(), String.join(",", java.util.Collections.nCopies(51, row))),
            "{\"items\":[" + row + "]}", batch("not-a-uuid", row) }) {
            mvc.perform(secure(post("/api/ingredients/batch"), owner).content(bad)).andExpect(status().isBadRequest());
        }
        assertThat(ingredients.count()).isZero();
    }

    @Test void batchRetryReturnsOriginalResponseAndDoesNotRecreateDeletedStock() throws Exception {
        Browser owner = login("owner@example.com");
        String key = java.util.UUID.randomUUID().toString();
        String body = batch(key, ingredient("계란", "2026-09-09", null));
        String response = mvc.perform(secure(post("/api/ingredients/batch"), owner).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(secure(post("/api/ingredients/batch"), owner).content(body)).andExpect(status().isCreated())
            .andExpect(content().json(response));
        assertThat(ingredients.count()).isEqualTo(1);
        mvc.perform(secure(post("/api/ingredients/batch"), owner).content(batch(key, ingredient("두부", "2026-09-09", null))))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BATCH_REQUEST_CONFLICT"));
        long id = ((Number) JsonPath.read(response, "$[0].id")).longValue();
        mvc.perform(secure(delete("/api/ingredients/" + id), owner)).andExpect(status().isNoContent());
        mvc.perform(secure(post("/api/ingredients/batch"), owner).content(body)).andExpect(status().isCreated())
            .andExpect(content().json(response));
        assertThat(ingredients.count()).isZero();
    }

    @Test void theSameBatchKeyBelongsToEachUserSeparately() throws Exception {
        Browser owner = login("owner@example.com");
        Browser stranger = login("stranger@example.com");
        String body = batch(java.util.UUID.randomUUID().toString(), ingredient("계란", "2026-09-09", null));
        String first = mvc.perform(secure(post("/api/ingredients/batch"), owner).content(body)).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String second = mvc.perform(secure(post("/api/ingredients/batch"), stranger).content(body)).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        assertThat((Number) JsonPath.read(first, "$[0].id")).isNotEqualTo((Number) JsonPath.read(second, "$[0].id"));
        assertThat(ingredients.count()).isEqualTo(2);
    }

    @Test void concurrentRetriesCommitOnlyOneBatch() throws Exception {
        Browser owner = login("owner@example.com");
        String body = batch(java.util.UUID.randomUUID().toString(), ingredient("계란", "2026-09-09", null));
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<String> send = () -> {
                start.await();
                return mvc.perform(secure(post("/api/ingredients/batch"), owner).content(body))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            };
            var first = executor.submit(send);
            var second = executor.submit(send);
            start.countDown();
            assertThat(first.get(15, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(second.get(15, java.util.concurrent.TimeUnit.SECONDS));
        }
        assertThat(ingredients.count()).isEqualTo(1);
    }

    private String batch(String requestId, String rows) {
        return "{\"requestId\":\"" + requestId + "\",\"items\":[" + rows + "]}";
    }

    /** 로그인하지 않은 상태는 token이 null입니다. */
    private record Browser(String token) { }

    private static String bearer(Browser browser) { return browser.token() == null ? "" : "Bearer " + browser.token(); }

    private Browser anonymous() { return new Browser(null); }

    private MockHttpServletRequestBuilder secure(MockHttpServletRequestBuilder request, Browser browser) {
        if (browser.token() != null) request.header(HttpHeaders.AUTHORIZATION, bearer(browser));
        return request.contentType(MediaType.APPLICATION_JSON);
    }

    private void register(Browser browser, String email, String password, String nickname) throws Exception {
        mvc.perform(secure(post("/api/auth/register"), browser).content(
            "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"passwordConfirm\":\"" + password
                + "\",\"nickname\":\"" + nickname + "\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    /** 가입 후 로그인해서 Access Token을 받습니다. */
    private Browser login(String email) throws Exception {
        register(anonymous(), email, "fridge1234!", "요리사");
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"fridge1234!\"}"))
            .andExpect(status().isOk()).andReturn();
        return new Browser(JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken"));
    }

    private long create(Browser owner, String name, String expirationDate) throws Exception {
        MvcResult result = mvc.perform(secure(post("/api/ingredients"), owner)
            .content(ingredient(name, expirationDate, null))).andExpect(status().isCreated()).andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private String ingredient(String name, String expirationDate, Long version) {
        return "{\"name\":\"" + name + "\",\"category\":\"DAIRY\",\"quantity\":6,\"unit\":\"PIECE\","
            + "\"purchaseDate\":\"2026-09-01\",\"expirationDate\":\"" + expirationDate
            + "\",\"storageType\":\"FRIDGE\"" + (version == null ? "" : ",\"version\":" + version) + "}";
    }
}
