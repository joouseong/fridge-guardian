package com.fridgeguardian.shopping;

import com.fridgeguardian.user.UserAccount;
import com.fridgeguardian.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import static com.fridgeguardian.support.ApiTestSupport.asUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 부족한 재료를 앱 내 장바구니로 담고 관리하는 기능과 사용자 격리를 검증합니다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ShoppingIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    private UserAccount owner;
    private UserAccount stranger;

    @BeforeEach void createUsers() {
        owner = users.saveAndFlush(new UserAccount("shop-owner@example.com", "unused-hash", "주인"));
        stranger = users.saveAndFlush(new UserAccount("shop-stranger@example.com", "unused-hash", "타인"));
    }

    @Test void addListCheckAndClearFlow() throws Exception {
        mvc.perform(get("/api/shopping-items")).andExpect(status().isUnauthorized());
        long milk = idOf(post(owner, "/api/shopping-items", "{\"name\":\" 우유 \",\"quantity\":1,\"unit\":\"LITER\"}")
            .andExpect(status().isCreated()).andExpect(jsonPath("$[0].name").value("우유")).andExpect(jsonPath("$[0].checked").value(false)));
        // 같은 재료(동의어 포함)가 아직 안 산 상태로 있으면 다시 담지 않습니다.
        post(owner, "/api/shopping-items", "{\"name\":\"우유\"}").andExpect(status().isCreated()).andExpect(jsonPath("$").isEmpty());
        post(owner, "/api/shopping-items/bulk", "{\"items\":[{\"name\":\"달걀\"},{\"name\":\"계란\"},{\"name\":\"두부\",\"quantity\":1,\"unit\":\"BLOCK\"}]}")
            .andExpect(status().isCreated()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/shopping-items").with(asUser(owner))).andExpect(jsonPath("$.length()").value(3));

        // 산 항목은 체크하면 목록 아래로 내려갑니다.
        mvc.perform(put("/api/shopping-items/" + milk + "/checked").with(asUser(owner)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"checked\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.checked").value(true));
        mvc.perform(get("/api/shopping-items").with(asUser(owner)))
            .andExpect(jsonPath("$[2].name").value("우유")).andExpect(jsonPath("$[2].checked").value(true));
        // 산 재료는 다시 담을 수 있습니다.
        post(owner, "/api/shopping-items", "{\"name\":\"우유\"}").andExpect(jsonPath("$.length()").value(1));
        mvc.perform(delete("/api/shopping-items/checked").with(asUser(owner))).andExpect(status().isNoContent());
        mvc.perform(get("/api/shopping-items").with(asUser(owner))).andExpect(jsonPath("$.length()").value(3));
    }

    @Test void recipeShortagesAreAddedAutomaticallyWithMissingQuantities() throws Exception {
        // 재고가 없는 사용자에게 간장 계란밥(16번, 2인분 기준 밥 400g·계란 4개·간장 15ml·식용유 10ml)의 1인분을 담습니다.
        post(owner, "/api/shopping-items/from-recipe", "{\"recipeId\":16,\"servings\":1}")
            .andExpect(status().isCreated()).andExpect(jsonPath("$.length()").value(4))
            .andExpect(jsonPath("$[?(@.name=='계란')].quantity").value(2.0))
            .andExpect(jsonPath("$[?(@.name=='밥')].unit").value("GRAM"))
            .andExpect(jsonPath("$[0].recipeId").value(16));
        // 이미 담은 재료는 중복해서 담지 않습니다.
        post(owner, "/api/shopping-items/from-recipe", "{\"recipeId\":16,\"servings\":2}")
            .andExpect(status().isCreated()).andExpect(jsonPath("$").isEmpty());
        post(owner, "/api/shopping-items/from-recipe", "{\"recipeId\":999999,\"servings\":1}").andExpect(status().isNotFound());
        post(owner, "/api/shopping-items/from-recipe", "{\"recipeId\":16,\"servings\":3}").andExpect(status().isBadRequest());
    }

    @Test void itemsBelongToTheirOwnerOnly() throws Exception {
        long id = idOf(post(owner, "/api/shopping-items", "{\"name\":\"사과\"}").andExpect(status().isCreated()));
        mvc.perform(get("/api/shopping-items").with(asUser(stranger))).andExpect(jsonPath("$").isEmpty());
        mvc.perform(put("/api/shopping-items/" + id + "/checked").with(asUser(stranger)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"checked\":true}")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/shopping-items/" + id).with(asUser(stranger))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/shopping-items/" + id).with(asUser(owner))).andExpect(status().isNoContent());
        mvc.perform(delete("/api/shopping-items/" + id).with(asUser(owner))).andExpect(status().isNotFound());
    }

    @Test void invalidItemsAreRejected() throws Exception {
        post(owner, "/api/shopping-items", "{\"name\":\"  \"}").andExpect(status().isBadRequest());
        post(owner, "/api/shopping-items", "{\"name\":\"" + "가".repeat(81) + "\"}").andExpect(status().isBadRequest());
        post(owner, "/api/shopping-items", "{\"name\":\"우유\",\"quantity\":0}").andExpect(status().isBadRequest());
        post(owner, "/api/shopping-items", "{\"name\":\"우유\",\"unit\":\"BOX\"}").andExpect(status().isBadRequest());
        post(owner, "/api/shopping-items/bulk", "{\"items\":[]}").andExpect(status().isBadRequest());
        post(owner, "/api/shopping-items/bulk", "{\"items\":[" + String.join(",",
            java.util.Collections.nCopies(31, "{\"name\":\"우유\"}")) + "]}").andExpect(status().isBadRequest());
    }

    private ResultActions post(UserAccount user, String url, String body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url).with(asUser(user))
            .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static long idOf(ResultActions actions) throws Exception {
        return ((Number) JsonPath.read(actions.andReturn().getResponse().getContentAsString(), "$[0].id")).longValue();
    }
}
