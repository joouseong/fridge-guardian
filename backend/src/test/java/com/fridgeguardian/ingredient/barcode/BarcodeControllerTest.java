package com.fridgeguardian.ingredient.barcode;

import com.fridgeguardian.user.UserAccount;
import com.fridgeguardian.user.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static com.fridgeguardian.support.ApiTestSupport.asUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BarcodeControllerTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired BarcodeProductRepository products;

    @Test void lookupRequiresLoginAndUsesStoredResultsWithoutAnApiKey() throws Exception {
        UserAccount user = users.saveAndFlush(new UserAccount("barcode@example.com", "unused-hash", "스캐너"));
        products.saveAndFlush(new BarcodeProduct("8801111222233", "저장된 라면", "PROCESSED", 180, "FOODSAFETY_C005", LocalDateTime.now()));
        mvc.perform(get("/api/barcodes/8801111222233")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/barcodes/8801111222233").with(asUser(user))).andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("저장된 라면")).andExpect(jsonPath("$.category").value("PROCESSED"))
            .andExpect(jsonPath("$.shelfLifeDays").value(180)).andExpect(jsonPath("$.suggestedExpirationDate").isNotEmpty());
        // 키가 없으면 외부 조회를 하지 않으므로 모르는 바코드는 404이고 화면은 직접 입력을 안내합니다.
        mvc.perform(get("/api/barcodes/8809999999999").with(asUser(user))).andExpect(status().isNotFound());
        mvc.perform(get("/api/barcodes/not-a-code").with(asUser(user))).andExpect(status().isBadRequest());
    }
}
