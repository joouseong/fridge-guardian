package com.fridgeguardian.batch;

import com.fridgeguardian.ingredient.Category;
import com.fridgeguardian.ingredient.Ingredient;
import com.fridgeguardian.ingredient.IngredientRepository;
import com.fridgeguardian.ingredient.StorageType;
import com.fridgeguardian.ingredient.Unit;
import com.fridgeguardian.notification.Notification;
import com.fridgeguardian.notification.NotificationRepository;
import com.fridgeguardian.user.UserAccount;
import com.fridgeguardian.user.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static com.fridgeguardian.support.ApiTestSupport.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 자정 배치가 임박·전날 만료 재료만 알림 큐에 한 번씩 넣고, 재실행해도 중복되지 않는지 실제 Spring Batch로 검증합니다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExpiryBatchIntegrationTest {
    private static final LocalDate RUN_DATE = LocalDate.of(2026, 9, 9);
    // 같은 날짜의 JobInstance는 한 번만 실행되고 테스트 DB는 클래스 안에서 공유되므로 테스트마다 다른 날짜를 씁니다.
    private static final LocalDate INBOX_DATE = LocalDate.of(2026, 11, 1);
    @Autowired ExpiryJobRunner runner;
    @Autowired NotificationRepository notifications;
    @Autowired IngredientRepository ingredients;
    @Autowired UserRepository users;
    @Autowired MockMvc mvc;
    private UserAccount owner;
    private UserAccount stranger;

    @BeforeEach void setUp() {
        notifications.deleteAll();
        ingredients.deleteAll();
        users.deleteAll();
        owner = users.saveAndFlush(new UserAccount("batch-owner@example.com", "unused-hash", "주인"));
        stranger = users.saveAndFlush(new UserAccount("batch-stranger@example.com", "unused-hash", "타인"));
    }

    @Test void queuesExpiringAndYesterdaysExpiredIngredientsOnceAndIsIdempotent() throws Exception {
        Ingredient today = stock(owner, "오늘까지", RUN_DATE);
        Ingredient third = stock(owner, "3일 뒤", RUN_DATE.plusDays(3));
        stock(owner, "4일 뒤", RUN_DATE.plusDays(4));
        Ingredient yesterday = stock(owner, "어제 만료", RUN_DATE.minusDays(1));
        stock(owner, "그저께 만료", RUN_DATE.minusDays(2));
        stock(owner, "기한 미등록", null);
        Ingredient others = stock(stranger, "타인의 재료", RUN_DATE.plusDays(1));

        var execution = runner.runFor(RUN_DATE).orElseThrow();
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        List<Notification> queued = notifications.findAll();
        assertThat(queued).extracting(Notification::getIngredientName, Notification::getType, Notification::getStatus,
            Notification::getNotifyDate).containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple("오늘까지", Notification.Type.EXPIRING, Notification.Status.PENDING, RUN_DATE),
                org.assertj.core.groups.Tuple.tuple("3일 뒤", Notification.Type.EXPIRING, Notification.Status.PENDING, RUN_DATE),
                org.assertj.core.groups.Tuple.tuple("어제 만료", Notification.Type.EXPIRED, Notification.Status.PENDING, RUN_DATE),
                org.assertj.core.groups.Tuple.tuple("타인의 재료", Notification.Type.EXPIRING, Notification.Status.PENDING, RUN_DATE));
        assertThat(queued).extracting(Notification::getUserId).contains(owner.getId(), stranger.getId());
        assertThat(queued).extracting(Notification::getIngredientId)
            .containsExactlyInAnyOrder(today.getId(), third.getId(), yesterday.getId(), others.getId());

        // 같은 날짜로 다시 실행해도 새로 만들지 않습니다. (JobInstance가 이미 완료됨)
        assertThat(runner.runFor(RUN_DATE)).isEmpty();
        assertThat(notifications.count()).isEqualTo(4);

        // 다음 날은 새 JobInstance입니다. 아직 임박한 재료만 새로 알리고, 전날 알림은 그대로 남습니다.
        assertThat(runner.runFor(RUN_DATE.plusDays(1)).orElseThrow().getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(notifications.findAll()).extracting(Notification::getIngredientName)
            .filteredOn("3일 뒤"::equals).hasSize(2);
        // 오늘까지였던 재료는 이제 '어제 만료'로 한 번 더 알립니다.
        assertThat(notifications.findAll()).filteredOn(n -> n.getIngredientId().equals(today.getId()))
            .extracting(Notification::getType).containsExactlyInAnyOrder(Notification.Type.EXPIRING, Notification.Type.EXPIRED);
    }

    @Test void recordsAreRemovedWithTheirIngredientAndInboxIsPerUser() throws Exception {
        Ingredient tofu = stock(owner, "두부", INBOX_DATE);
        stock(stranger, "우유", INBOX_DATE);
        runner.runFor(INBOX_DATE);

        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        var inbox = mvc.perform(get("/api/notifications").with(asUser(owner))).andExpect(status().isOk())
            .andExpect(jsonPath("$.unreadCount").value(1)).andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].ingredientName").value("두부")).andExpect(jsonPath("$.items[0].type").value("EXPIRING"))
            .andExpect(jsonPath("$.items[0].status").value("PENDING")).andReturn();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(inbox.getResponse().getContentAsString(), "$.items[0].id")).longValue();

        mvc.perform(put("/api/notifications/" + id + "/read").with(asUser(stranger))).andExpect(status().isNotFound());
        mvc.perform(put("/api/notifications/" + id + "/read").with(asUser(owner))).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("READ"));
        mvc.perform(get("/api/notifications").with(asUser(owner))).andExpect(jsonPath("$.unreadCount").value(0));
        mvc.perform(get("/api/notifications").with(asUser(stranger))).andExpect(jsonPath("$.unreadCount").value(1));
        mvc.perform(put("/api/notifications/read-all").with(asUser(stranger))).andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications").with(asUser(stranger))).andExpect(jsonPath("$.unreadCount").value(0));

        ingredients.delete(tofu);
        ingredients.flush();
        assertThat(notifications.findAll()).extracting(Notification::getIngredientName).containsExactly("우유");
    }

    private Ingredient stock(UserAccount user, String name, LocalDate expiration) {
        return ingredients.saveAndFlush(new Ingredient(user, name, Category.OTHER, BigDecimal.ONE, Unit.PIECE,
            RUN_DATE.minusDays(10), expiration, StorageType.FRIDGE));
    }
}
