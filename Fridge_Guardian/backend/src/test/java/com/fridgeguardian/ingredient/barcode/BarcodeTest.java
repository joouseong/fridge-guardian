package com.fridgeguardian.ingredient.barcode;

import com.fridgeguardian.common.ApiException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 식품안전나라 응답 해석과 저장 → 외부 조회 순서를 실제 외부 호출 없이 검증합니다. */
class BarcodeTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-08T15:00:00Z"), ZoneId.of("Asia/Seoul")); // 서울 9월 9일
    private static final String C005 = """
        {"C005":{"total_count":"1","row":[{"BAR_CD":"8801234567890","PRDLST_NM":"맛있는 크래커","POG_DAYCNT":"제조일로부터 12개월","CMPNY_NM":"테스트식품"}],
        "RESULT":{"MSG":"정상처리되었습니다.","CODE":"INFO-000"}}}""";

    @ParameterizedTest(name = "\"{0}\" → {1}일")
    @CsvSource(delimiter = '|', nullValues = "null", value = {
        "제조일로부터 24개월|720", "12개월|360", "90일|90", "2년|730", "제조일로부터 1년 6개월|365",
        "상세설명참조|null", "0일|null", "99999일|null", "|null"})
    void shelfLifeTextBecomesDaysOrNull(String text, Integer days) {
        assertThat(FoodSafetyBarcodeClient.shelfLifeDays(text)).isEqualTo(days);
    }

    @Test void parsesTheDocumentedResponseAndIgnoresEmptyOnes() {
        var client = new FoodSafetyBarcodeClient(JsonMapper.builder().build(), HttpClient.newHttpClient(), "key", "https://openapi.example.kr");
        assertThat(client.parse(C005)).contains(new FoodSafetyBarcodeClient.Found("맛있는 크래커", 360));
        assertThat(client.parse("{\"C005\":{\"RESULT\":{\"CODE\":\"INFO-200\"}}}")).isEmpty();
        assertThat(client.parse("{\"C005\":{\"row\":[]}}")).isEmpty();
        assertThat(client.parse("{\"C005\":{\"row\":[{\"PRDLST_NM\":\"  \"}]}}")).isEmpty();
        assertThat(client.parse("{}")).isEmpty();
    }

    @Test void withoutAKeyNothingIsSentAndWithAKeyTheDocumentedUrlIsCalled() throws Exception {
        assertThat(new FoodSafetyBarcodeClient(JsonMapper.builder().build(), HttpClient.newHttpClient(), "  ", "https://openapi.example.kr")
            .find("8801234567890")).isEmpty();
        AtomicReference<String> path = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getRawPath());
            byte[] body = C005.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        try {
            var client = new FoodSafetyBarcodeClient(JsonMapper.builder().build(), HttpClient.newHttpClient(), "abc123",
                "http://127.0.0.1:" + server.getAddress().getPort());
            assertThat(client.available()).isTrue();
            assertThat(client.find("8801234567890")).contains(new FoodSafetyBarcodeClient.Found("맛있는 크래커", 360));
            assertThat(path.get()).isEqualTo("/api/abc123/C005/json/1/5/BAR_CD=8801234567890");
        } finally {
            server.stop(0);
        }
        // 서버가 없으면 예외 대신 빈 결과를 돌려주어 사용자가 직접 입력할 수 있게 합니다.
        var down = new FoodSafetyBarcodeClient(JsonMapper.builder().build(), HttpClient.newHttpClient(), "abc123", "http://127.0.0.1:1");
        assertThat(down.find("8801234567890")).isEmpty();
    }

    @Test void onlyHttpsOrLoopbackBaseUrlsAreAccepted() {
        assertThatThrownBy(() -> new FoodSafetyBarcodeClient(JsonMapper.builder().build(), HttpClient.newHttpClient(), "k", "http://openapi.example.kr"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void storedResultsAreUsedBeforeTheExternalApiAndNewOnesAreSaved() {
        var products = mock(BarcodeProductRepository.class);
        var client = mock(FoodSafetyBarcodeClient.class);
        var service = new BarcodeService(products, client, CLOCK);
        when(products.findById("8801234567890")).thenReturn(Optional.of(
            new BarcodeProduct("8801234567890", "저장된 과자", "PROCESSED", 30, "FOODSAFETY_C005", java.time.LocalDateTime.now(CLOCK))));

        var stored = service.lookup("8801234567890");
        assertThat(stored.name()).isEqualTo("저장된 과자");
        assertThat(stored.suggestedExpirationDate()).isEqualTo(LocalDate.of(2026, 10, 9));
        verifyNoInteractions(client);

        when(products.findById("8809999999999")).thenReturn(Optional.empty());
        when(client.find("8809999999999")).thenReturn(Optional.of(new FoodSafetyBarcodeClient.Found("새 과자", null)));
        when(products.save(any(BarcodeProduct.class))).thenAnswer(call -> call.getArgument(0));
        var fetched = service.lookup("8809999999999");
        assertThat(fetched.name()).isEqualTo("새 과자");
        assertThat(fetched.category()).isEqualTo("PROCESSED");
        assertThat(fetched.suggestedExpirationDate()).isNull(); // 유통기간을 모르면 날짜를 지어내지 않습니다.
        verify(products).save(any(BarcodeProduct.class));
    }

    @Test void unknownBarcodesAreNotFoundAndMalformedOnesAreBadRequests() {
        var products = mock(BarcodeProductRepository.class);
        var client = mock(FoodSafetyBarcodeClient.class);
        var service = new BarcodeService(products, client, CLOCK);
        when(products.findById(any())).thenReturn(Optional.empty());
        when(client.find(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.lookup("8801234567890")).isInstanceOfSatisfying(ApiException.class,
            error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        for (String bad : new String[] {"abc", "123", "12345678901234567890", "88012345678a0", " 8801234567890"}) {
            assertThatThrownBy(() -> service.lookup(bad)).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verify(products, never()).save(any());
    }
}
