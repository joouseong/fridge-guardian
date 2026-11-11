package com.fridgeguardian.user.preference;

import com.fridgeguardian.user.UserAccount;
import com.fridgeguardian.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static com.fridgeguardian.support.ApiTestSupport.asUser;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 알레르기·비선호·매운맛 설정이 저장되고 추천 목록에 실제로 반영되는지, 사용자마다 분리되는지 검증합니다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PreferenceIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    private UserAccount owner;
    private UserAccount stranger;

    @BeforeEach void createUsers() {
        owner = users.saveAndFlush(new UserAccount("pref-owner@example.com", "unused-hash", "주인"));
        stranger = users.saveAndFlush(new UserAccount("pref-stranger@example.com", "unused-hash", "타인"));
    }

    @Test void defaultsAreUnrestrictedAndRequireLogin() throws Exception {
        mvc.perform(get("/api/users/me/preferences")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me/preferences").with(asUser(owner))).andExpect(status().isOk())
            .andExpect(jsonPath("$.spicyLevel").value(3)).andExpect(jsonPath("$.allergies").isEmpty())
            .andExpect(jsonPath("$.dislikes").isEmpty());
        // 설정하지 않은 사용자는 시드 레시피 21개가 모두 추천 대상입니다.
        mvc.perform(get("/api/recipes/recommendations?servings=1").with(asUser(owner)))
            .andExpect(jsonPath("$.length()").value(21));
    }

    @Test void savesReplacesAndDeduplicatesByIngredientAlias() throws Exception {
        save(owner, "{\"spicyLevel\":1,\"allergies\":[\" 계란 \",\"달걀\",\"땅콩\"],\"dislikes\":[\"오이\"]}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.spicyLevel").value(1))
            .andExpect(jsonPath("$.allergies.length()").value(2)).andExpect(jsonPath("$.allergies[0]").value("계란"));
        mvc.perform(get("/api/users/me/preferences").with(asUser(owner)))
            .andExpect(jsonPath("$.allergies[1]").value("땅콩")).andExpect(jsonPath("$.dislikes[0]").value("오이"));
        // 목록 전체를 교체합니다. 빈 목록은 모두 지웁니다.
        save(owner, "{\"spicyLevel\":2,\"allergies\":[],\"dislikes\":[]}").andExpect(status().isOk());
        mvc.perform(get("/api/users/me/preferences").with(asUser(owner)))
            .andExpect(jsonPath("$.spicyLevel").value(2)).andExpect(jsonPath("$.allergies").isEmpty());
        // 다른 사용자의 설정은 건드리지 않습니다.
        mvc.perform(get("/api/users/me/preferences").with(asUser(stranger))).andExpect(jsonPath("$.spicyLevel").value(3));
    }

    @Test void invalidInputIsRejected() throws Exception {
        save(owner, "{\"spicyLevel\":4}").andExpect(status().isBadRequest());
        save(owner, "{\"spicyLevel\":-1}").andExpect(status().isBadRequest());
        save(owner, "{\"spicyLevel\":1,\"allergies\":[\"  \"]}").andExpect(status().isBadRequest());
        save(owner, "{\"spicyLevel\":1,\"dislikes\":[\"" + "가".repeat(81) + "\"]}").andExpect(status().isBadRequest());
        save(owner, "{\"spicyLevel\":1,\"dislikes\":[" + String.join(",", java.util.Collections.nCopies(31, "\"오이\"")) + "]}")
            .andExpect(status().isBadRequest());
    }

    @Test void settingsFilterTheRecommendationListForThatUserOnly() throws Exception {
        // 우유 알레르기(치즈·버터 등 유제품 포함), 김치 비선호, 순한맛만
        save(owner, "{\"spicyLevel\":0,\"allergies\":[\"우유\"],\"dislikes\":[\"김치\"]}").andExpect(status().isOk());
        mvc.perform(get("/api/recipes/recommendations?servings=1").with(asUser(owner))).andExpect(status().isOk())
            // 김치찌개(1)·김치볶음밥(2)·참치 김치 덮밥(8)·두부김치(12) 는 김치/매운맛, 버섯 우유 파스타(10)는 우유
            .andExpect(jsonPath("$[?(@.id==1)]").isEmpty()).andExpect(jsonPath("$[?(@.id==2)]").isEmpty())
            .andExpect(jsonPath("$[?(@.id==8)]").isEmpty()).andExpect(jsonPath("$[?(@.id==10)]").isEmpty())
            .andExpect(jsonPath("$[?(@.id==12)]").isEmpty())
            .andExpect(jsonPath("$[*].id", hasItem(16)))
            .andExpect(jsonPath("$[*].spicyLevel", not(hasItem(1))));
        // 상세 조회는 숨기지 않고 겹치는 재료를 알려 줍니다.
        mvc.perform(get("/api/recipes/10?servings=1").with(asUser(owner))).andExpect(status().isOk())
            .andExpect(jsonPath("$.preferenceConflicts[0]").value("우유"));
        mvc.perform(get("/api/recipes/16?servings=1").with(asUser(owner)))
            .andExpect(jsonPath("$.preferenceConflicts").isEmpty());
        // 설정하지 않은 다른 사용자는 그대로 21개를 봅니다.
        mvc.perform(get("/api/recipes/recommendations?servings=1").with(asUser(stranger)))
            .andExpect(jsonPath("$.length()").value(21));
    }

    private org.springframework.test.web.servlet.ResultActions save(UserAccount user, String body) throws Exception {
        return mvc.perform(put("/api/users/me/preferences").with(asUser(user)).contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
