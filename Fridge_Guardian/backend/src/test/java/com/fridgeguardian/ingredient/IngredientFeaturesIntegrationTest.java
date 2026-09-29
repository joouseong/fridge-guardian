package com.fridgeguardian.ingredient;

import com.fridgeguardian.user.UserAccount;
import com.fridgeguardian.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static com.fridgeguardian.support.ApiTestSupport.asUser;
import static com.fridgeguardian.support.ApiTestSupport.ingredientJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 검색·필터, 메모·바코드, 사진 업로드(5MB, 교체, 삭제 시 파일 정리), 대시보드 카드를 실제 필터 체인으로 검증합니다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IngredientFeaturesIntegrationTest {
    private static final Path UPLOADS = Path.of("target/test-uploads").toAbsolutePath();
    private static final byte[] PNG = png(64);
    private static final byte[] JPEG = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0};

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired IngredientRepository ingredients;
    @Autowired Clock clock;
    private UserAccount owner;
    private UserAccount stranger;
    private LocalDate today;

    @BeforeEach void setUp() throws IOException {
        ingredients.deleteAll();
        users.deleteAll();
        owner = users.saveAndFlush(new UserAccount("owner@example.com", "unused-hash", "주인"));
        stranger = users.saveAndFlush(new UserAccount("stranger@example.com", "unused-hash", "타인"));
        today = LocalDate.now(clock);
        if (Files.exists(UPLOADS)) {
            try (var files = Files.walk(UPLOADS)) {
                files.sorted(Comparator.reverseOrder()).filter(path -> !path.equals(UPLOADS)).forEach(path -> path.toFile().delete());
            }
        }
    }

    @Test void filtersCombineNameCategoryStorageAndStatus() throws Exception {
        create("우유", "DAIRY", "FRIDGE", today.plusDays(1));
        create("상한 우유", "DAIRY", "FRIDGE", today.minusDays(1));
        create("사과", "FRUIT", "FRIDGE", today.plusDays(10));
        create("냉동만두", "PROCESSED", "FREEZER", today.plusDays(30));
        create("소금", "SEASONING", "PANTRY", null);
        create("두부", "OTHER", "FRIDGE", today);

        assertNames("/api/ingredients", 6, null);
        assertNames("/api/ingredients?q=우유", 2, null);
        assertNames("/api/ingredients?q= 우유 &category=DAIRY&status=SOON", 1, "우유");
        assertNames("/api/ingredients?storage=FREEZER", 1, "냉동만두");
        assertNames("/api/ingredients?category=PROCESSED", 1, "냉동만두");
        assertNames("/api/ingredients?status=UNKNOWN", 1, "소금");
        assertNames("/api/ingredients?status=EXPIRED", 1, "상한 우유");
        assertNames("/api/ingredients?status=SAFE", 2, null);
        assertNames("/api/ingredients?status=SOON", 2, null);
        assertNames("/api/ingredients?status=TODAY", 1, "두부");
        assertNames("/api/ingredients?status=USABLE", 5, null);
        assertNames("/api/ingredients?status=ALL&q=", 6, null);
        assertNames("/api/ingredients?q=없는재료", 0, null);
        mvc.perform(get("/api/ingredients?status=BROKEN").with(asUser(owner))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/ingredients?category=BROKEN").with(asUser(owner))).andExpect(status().isBadRequest());
        // 다른 사용자의 재료는 어떤 조건에서도 보이지 않습니다.
        mvc.perform(get("/api/ingredients?q=우유").with(asUser(stranger))).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test void memoAndBarcodeAreTrimmedStoredAndValidated() throws Exception {
        String base = ingredientJson("과자", "PROCESSED", "PACK", "1", today.toString(), today.plusDays(90).toString(), "PANTRY");
        mvc.perform(post("/api/ingredients").with(asUser(owner)).contentType(MediaType.APPLICATION_JSON)
                .content(base.replace("}", ",\"memo\":\"  아이 간식  \",\"barcode\":\"8801234567890\"}")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.memo").value("아이 간식"))
            .andExpect(jsonPath("$.barcode").value("8801234567890")).andExpect(jsonPath("$.imageUrl").doesNotExist());
        mvc.perform(post("/api/ingredients").with(asUser(owner)).contentType(MediaType.APPLICATION_JSON)
                .content(base.replace("}", ",\"memo\":\"   \",\"barcode\":\"\"}")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.memo").doesNotExist()).andExpect(jsonPath("$.barcode").doesNotExist());
        for (String bad : new String[] {",\"barcode\":\"abc\"", ",\"barcode\":\"123\"", ",\"memo\":\"" + "가".repeat(501) + "\""}) {
            mvc.perform(post("/api/ingredients").with(asUser(owner)).contentType(MediaType.APPLICATION_JSON)
                    .content(base.replace("}", bad + "}")))
                .andExpect(status().isBadRequest());
        }
    }

    @Test void imageUploadReplaceKeepOnEditAndDeleteRemoveFiles() throws Exception {
        long id = create("계란", "DAIRY", "FRIDGE", today.plusDays(5));
        // 사진이 없으면 imageUrl이 없어 화면이 기본 이미지를 보여 줍니다.
        mvc.perform(get("/api/ingredients/" + id).with(asUser(owner))).andExpect(jsonPath("$.imageUrl").doesNotExist());

        String first = upload(id, "egg.png", PNG);
        assertThat(first).matches("/uploads/[0-9a-f-]{36}\\.png");
        assertThat(Files.exists(fileOf(first))).isTrue();
        // 업로드한 사진은 로그인 없이 <img>로 볼 수 있어야 합니다.
        mvc.perform(get(first)).andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));

        // 수정 시 새 사진을 올리지 않으면 기존 사진이 유지됩니다.
        MvcResult current = mvc.perform(get("/api/ingredients/" + id).with(asUser(owner))).andReturn();
        long version = ((Number) JsonPath.read(current.getResponse().getContentAsString(), "$.version")).longValue();
        mvc.perform(put("/api/ingredients/" + id).with(asUser(owner)).contentType(MediaType.APPLICATION_JSON)
                .content(ingredientJson("달걀", "DAIRY", "PIECE", "6", today.toString(), today.plusDays(5).toString(), "FRIDGE")
                    .replace("}", ",\"version\":" + version + "}")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.imageUrl").value(first));
        assertThat(Files.exists(fileOf(first))).isTrue();

        // 새 사진을 올리면 교체되고 이전 파일은 지워집니다. (사진만 바꿔도 목록 화면의 수정 버전은 유효)
        String second = upload(id, "egg.jpg", JPEG);
        assertThat(second).endsWith(".jpg").isNotEqualTo(first);
        assertThat(Files.exists(fileOf(first))).isFalse();
        assertThat(Files.exists(fileOf(second))).isTrue();
        mvc.perform(get("/api/ingredients/" + id).with(asUser(owner)))
            .andExpect(jsonPath("$.version").value(version + 1));

        // 사진만 지우면 기본 이미지로 돌아갑니다.
        mvc.perform(delete("/api/ingredients/" + id + "/image").with(asUser(owner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.imageUrl").doesNotExist());
        assertThat(Files.exists(fileOf(second))).isFalse();

        // 식재료를 삭제하면 서버에 올라간 사진 파일도 함께 삭제됩니다.
        String third = upload(id, "egg.png", PNG);
        assertThat(Files.exists(fileOf(third))).isTrue();
        mvc.perform(delete("/api/ingredients/" + id).with(asUser(owner))).andExpect(status().isNoContent());
        assertThat(Files.exists(fileOf(third))).isFalse();
    }

    @Test void invalidOversizedForeignAndAnonymousUploadsAreRejectedWithoutLeavingFiles() throws Exception {
        long id = create("계란", "DAIRY", "FRIDGE", today.plusDays(5));
        // 확장자와 Content-Type이 이미지여도 내용이 이미지가 아니면 거절합니다.
        mvc.perform(multipart("/api/ingredients/" + id + "/image")
                .file(new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes())).with(asUser(owner)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
        mvc.perform(multipart("/api/ingredients/" + id + "/image")
                .file(new MockMultipartFile("file", "empty.png", "image/png", new byte[0])).with(asUser(owner)))
            .andExpect(status().isBadRequest());
        // 5MB 초과
        byte[] big = png(5 * 1024 * 1024 + 1);
        mvc.perform(multipart("/api/ingredients/" + id + "/image")
                .file(new MockMultipartFile("file", "big.png", "image/png", big)).with(asUser(owner)))
            .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("IMAGE_TOO_LARGE"));
        // 정확히 5MB는 허용
        upload(id, "limit.png", png(5 * 1024 * 1024));
        mvc.perform(delete("/api/ingredients/" + id + "/image").with(asUser(owner))).andExpect(status().isOk());
        // 파일 파트가 없으면 400
        mvc.perform(multipart("/api/ingredients/" + id + "/image").with(asUser(owner))).andExpect(status().isBadRequest());
        // 남의 재료는 404, 로그인 없이는 401
        mvc.perform(multipart("/api/ingredients/" + id + "/image")
                .file(new MockMultipartFile("file", "egg.png", "image/png", PNG)).with(asUser(stranger)))
            .andExpect(status().isNotFound());
        mvc.perform(multipart("/api/ingredients/" + id + "/image")
                .file(new MockMultipartFile("file", "egg.png", "image/png", PNG))).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/ingredients/" + id + "/image").with(asUser(stranger))).andExpect(status().isNotFound());
        assertThat(uploadedFiles()).isEmpty();
    }

    @Test void pathTraversalCannotEscapeTheUploadFolder() throws Exception {
        Files.createDirectories(UPLOADS);
        Files.writeString(UPLOADS.getParent().resolve("secret.txt"), "secret");
        mvc.perform(get("/uploads/../secret.txt")).andExpect(status().is4xxClientError());
        mvc.perform(get("/uploads/%2e%2e/secret.txt")).andExpect(status().is4xxClientError());
        mvc.perform(get("/uploads/missing.png")).andExpect(status().isNotFound());
    }

    @Test void dashboardShowsTheSixNearestUnexpiredDatedIngredientsWithPhotosAvailable() throws Exception {
        for (int days = 8; days >= 1; days--) create("재료" + days, "OTHER", "FRIDGE", today.plusDays(days));
        create("만료", "OTHER", "FRIDGE", today.minusDays(2));
        create("기한없음", "OTHER", "FRIDGE", null);
        mvc.perform(get("/api/dashboard").with(asUser(owner))).andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(10)).andExpect(jsonPath("$.nearestIngredients.length()").value(6))
            .andExpect(jsonPath("$.nearestIngredients[0].name").value("재료1"))
            .andExpect(jsonPath("$.nearestIngredients[5].name").value("재료6"))
            .andExpect(jsonPath("$.nearestIngredients[0].daysUntilExpiration").value(1));
    }

    private void assertNames(String url, int size, String firstName) throws Exception {
        var result = mvc.perform(get(url).with(asUser(owner))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(size));
        if (firstName != null) result.andExpect(jsonPath("$[0].name").value(firstName));
    }

    private long create(String name, String category, String storage, LocalDate expiration) throws Exception {
        MvcResult result = mvc.perform(post("/api/ingredients").with(asUser(owner)).contentType(MediaType.APPLICATION_JSON)
            .content(ingredientJson(name, category, "PIECE", "2", today.minusDays(3).toString(),
                expiration == null ? null : expiration.toString(), storage))).andExpect(status().isCreated()).andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private String upload(long id, String filename, byte[] content) throws Exception {
        MvcResult result = mvc.perform(multipart("/api/ingredients/" + id + "/image")
                .file(new MockMultipartFile("file", filename, "application/octet-stream", content)).with(asUser(owner)))
            .andExpect(status().isOk()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.imageUrl");
    }

    private static Path fileOf(String imageUrl) { return UPLOADS.resolve(imageUrl.substring("/uploads/".length())); }

    private static java.util.List<String> uploadedFiles() throws IOException {
        if (!Files.exists(UPLOADS)) return java.util.List.of();
        try (var files = Files.list(UPLOADS)) { return files.map(path -> path.getFileName().toString()).toList(); }
    }

    /** 앞 8바이트가 PNG 서명이고 나머지는 채움 값인 파일. */
    private static byte[] png(int size) {
        byte[] bytes = new byte[size];
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(signature, 0, bytes, 0, Math.min(size, signature.length));
        Arrays.fill(bytes, Math.min(size, signature.length), size, (byte) 1);
        return bytes;
    }
}
