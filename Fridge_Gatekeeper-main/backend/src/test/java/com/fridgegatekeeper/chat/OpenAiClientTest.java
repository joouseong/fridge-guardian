package com.fridgegatekeeper.chat;

import com.fridgegatekeeper.common.ApiException;
import com.fridgegatekeeper.ingredient.Category;
import com.fridgegatekeeper.ingredient.ExpiryStatus;
import com.fridgegatekeeper.ingredient.IngredientResponse;
import com.fridgegatekeeper.ingredient.StorageType;
import com.fridgegatekeeper.ingredient.Unit;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 실제 외부 API나 유료 호출 없이 로컬 HTTP 응답으로 전송 계약과 실패 처리를 검증합니다. */
class OpenAiClientTest {
    private final ObjectMapper json = JsonMapper.builder().build();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> requestAuthorization = new AtomicReference<>();
    private final AtomicReference<String> responseBody = new AtomicReference<>();
    private final AtomicInteger responseStatus = new AtomicInteger(200);
    private HttpServer server;
    private String baseUrl;

    @BeforeEach void startLocalServer() throws Exception {
        responseBody.set(json.writeValueAsString(java.util.Map.of("status", "completed", "output", List.of(
            java.util.Map.of("type", "reasoning", "summary", List.of()),
            java.util.Map.of("type", "message", "role", "assistant", "content", List.of(
                java.util.Map.of("type", "output_text", "text", "{\"reply\":" + json.writeValueAsString("계란을 먼저 활용해요.\n밥은 추가로 준비해 주세요.") + ","),
                java.util.Map.of("type", "output_text", "text", "\"recipeIds\":[]}")))))));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            requestAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] content = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(responseStatus.get(), content.length);
            try (var output = exchange.getResponseBody()) { output.write(content); }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach void stopLocalServer() { server.stop(0); }

    @Test void sendsResponsesContractAndOnlyNecessaryFridgeFieldsAndCollectsAllOutputText() {
        OpenAiClient client = client();
        var request = new ChatDtos.Request("  두 명이 먹을 메뉴는?  ", 2,
            List.of(new ChatDtos.Message("user", "이전 질문"), new ChatDtos.Message("assistant", "이전 답변")));

        String reply = client.reply(request, List.of(ingredient()), List.of()).reply();

        assertThat(reply).isEqualTo("계란을 먼저 활용해요.\n밥은 추가로 준비해 주세요.");
        assertThat(requestAuthorization.get()).isEqualTo("Bearer test-key-never-real");
        JsonNode sent = json.readTree(requestBody.get());
        assertThat(sent.path("model").asString()).isEqualTo("gpt-5-mini");
        assertThat(sent.path("store").asBoolean()).isFalse();
        assertThat(sent.path("max_output_tokens").asInt()).isEqualTo(2400);
        assertThat(sent.path("reasoning").path("effort").asString()).isEqualTo("low");
        assertThat(sent.path("text").path("verbosity").asString()).isEqualTo("low");
        assertThat(sent.path("text").path("format").path("type").asString()).isEqualTo("json_schema");
        assertThat(sent.path("text").path("format").path("strict").asBoolean()).isTrue();
        assertThat(sent.has("temperature")).isFalse();
        assertThat(sent.path("instructions").asString()).contains("만료", "unitMismatch");
        JsonNode input = sent.path("input");
        assertThat(input.size()).isEqualTo(4);
        assertThat(input.get(1).path("role").asString()).isEqualTo("user");
        assertThat(input.get(2).path("role").asString()).isEqualTo("assistant");
        assertThat(input.get(3).path("content").asString()).isEqualTo("두 명이 먹을 메뉴는?");
        String context = input.get(0).path("content").asString();
        assertThat(context).contains("계란", "임박", "3개", "\"servings\":2")
            .doesNotContain("PIECE", "SOON", "3.000")
            .doesNotContain("email", "password", "purchaseDate", "version", "userId", "test-key-never-real");
    }

    @Test void acceptsVersionedBaseUrlWithoutDuplicatingV1() {
        OpenAiClient client = new OpenAiClient(json, HttpClient.newHttpClient(), "test-key-never-real",
            "gpt-4.1-mini", baseUrl + "/v1/", Duration.ofSeconds(3));
        assertThat(client.reply(request(), List.of(), List.of()).reply()).contains("계란을 먼저");
    }

    @Test void unconfiguredStatusDoesNotSendRequests() {
        OpenAiClient client = new OpenAiClient(json, HttpClient.newHttpClient(), "  ",
            "gpt-4.1-mini", baseUrl, Duration.ofSeconds(3));
        assertThat(client.available()).isFalse();
        assertThatThrownBy(() -> client.reply(request(), List.of(), List.of()))
            .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode()).isEqualTo("AI_NOT_CONFIGURED"));
        assertThat(requestBody.get()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"400,AI_CONFIGURATION_ERROR", "401,AI_CONFIGURATION_ERROR", "403,AI_CONFIGURATION_ERROR", "404,AI_CONFIGURATION_ERROR", "429,AI_RATE_LIMITED", "500,AI_UNAVAILABLE", "302,AI_UNAVAILABLE"})
    void upstreamFailuresUseSafeErrorsAndDoNotExposeProviderBody(int status, String code) {
        responseStatus.set(status);
        responseBody.set("{\"error\":\"sensitive server details test-key-never-real\"}");

        assertThatThrownBy(() -> client().reply(request(), List.of(), List.of()))
            .isInstanceOfSatisfying(ApiException.class, error -> {
                assertThat(error.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(error.getCode()).isEqualTo(code);
                assertThat(error.getMessage()).doesNotContain("sensitive", "test-key-never-real");
            });
    }

    @Test void incompleteAnswersAreNotReportedAsSuccess() {
        responseBody.set("{\"status\":\"incomplete\",\"output\":[]}");
        assertFailure("AI_INCOMPLETE_RESPONSE");
    }

    @Test void quotaFailuresExplainBillingWithoutLeakingRawErrorDetails() {
        responseStatus.set(429);
        responseBody.set("{\"error\":{\"type\":\"insufficient_quota\",\"code\":\"insufficient_quota\",\"message\":\"test-key-never-real\"}}");
        assertThatThrownBy(() -> client().reply(request(), List.of(), List.of()))
            .isInstanceOfSatisfying(ApiException.class, error -> {
                assertThat(error.getCode()).isEqualTo("AI_QUOTA_EXCEEDED");
                assertThat(error.getMessage()).contains("결제 잔액").doesNotContain("test-key-never-real");
            });
        responseBody.set("not JSON and test-key-never-real");
        assertFailure("AI_RATE_LIMITED");
    }

    @Test void rejectsPlainHttpForRemoteServersBeforeSendingCredentials() {
        assertThatThrownBy(() -> new OpenAiClient(json, HttpClient.newHttpClient(), "test-key-never-real",
            "gpt-5-mini", "http://example.com", Duration.ofSeconds(3)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("HTTPS");
        assertThat(requestBody.get()).isNull();
    }

    @Test void olderNonReasoningModelDoesNotReceiveGpt5Parameters() {
        new OpenAiClient(json, HttpClient.newHttpClient(), "test-key-never-real", "gpt-4.1-mini", baseUrl,
            Duration.ofSeconds(3)).reply(request(), List.of(), List.of());
        assertThat(json.readTree(requestBody.get()).has("reasoning")).isFalse();
    }

    @Test void emptyAndMalformedResponsesAreNotReportedAsSuccess() {
        responseBody.set("{\"status\":\"completed\",\"output\":[]}");
        assertFailure("AI_EMPTY_RESPONSE");
        responseBody.set("this is not json");
        assertFailure("AI_INVALID_RESPONSE");
        responseBody.set("null");
        assertFailure("AI_INVALID_RESPONSE");
    }

    @Test void providerRefusalIsPreservedAsAnHonestAiAnswer() {
        responseBody.set("""
            {"status":"completed","output":[{"type":"message","role":"assistant",
             "content":[{"type":"refusal","refusal":"그 요청은 도와드릴 수 없어요."}]}]}
            """);
        assertThat(client().reply(request(), List.of(), List.of()).reply()).isEqualTo("그 요청은 도와드릴 수 없어요.");
    }

    @Test void networkTimeoutReturnsRetryableErrorAndRequestHasConfiguredDeadline() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new HttpTimeoutException("timeout"));
        OpenAiClient client = new OpenAiClient(json, http, "test-key-never-real", "gpt-4.1-mini", baseUrl, Duration.ofSeconds(7));
        assertThatThrownBy(() -> client.reply(request(), List.of(), List.of()))
            .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode()).isEqualTo("AI_TIMEOUT"));
        var captured = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captured.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(captured.getValue().timeout()).contains(Duration.ofSeconds(7));
    }

    @Test void interruptedRequestsRestoreThreadInterruption() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new InterruptedException());
        OpenAiClient client = new OpenAiClient(json, http, "test-key-never-real", "gpt-4.1-mini", baseUrl, Duration.ofSeconds(3));
        try {
            assertThatThrownBy(() -> client.reply(request(), List.of(), List.of())).isInstanceOf(ApiException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test void unknownExpirationIsSentWithoutInventingAnExpirationDate() {
        var stock = new IngredientResponse(1L, "계란", Category.OTHER, BigDecimal.ONE, Unit.PIECE,
            LocalDate.of(2026, 9, 1), null, StorageType.FRIDGE, 0, ExpiryStatus.UNKNOWN, null);
        assertThat(client().reply(request(), List.of(stock), List.of()).reply()).isNotBlank();
        assertThat(requestBody.get()).contains("기한 미등록", "1개").doesNotContain("UNKNOWN", "PIECE");
    }

    @Test void rejectsInventedRecipeIdsAndMalformedStructuredAnswers() {
        for (String value : List.of("{\"reply\":\"추천\",\"recipeIds\":[9999]}",
            "{\"reply\":\"추천\",\"recipeIds\":[1.5]}", "{\"reply\":\"추천\"}", "{\"reply\":\" \",\"recipeIds\":[]}")) {
            responseBody.set(json.writeValueAsString(java.util.Map.of("status", "completed", "output", List.of(
                java.util.Map.of("type", "message", "role", "assistant", "content", List.of(
                    java.util.Map.of("type", "output_text", "text", value)))))));
            assertFailure("AI_INVALID_RESPONSE");
        }
    }

    private void assertFailure(String code) {
        assertThatThrownBy(() -> client().reply(request(), List.of(), List.of()))
            .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode()).isEqualTo(code));
    }

    private OpenAiClient client() {
        return new OpenAiClient(json, HttpClient.newHttpClient(), "test-key-never-real", "gpt-5-mini", baseUrl, Duration.ofSeconds(3));
    }

    private static ChatDtos.Request request() { return new ChatDtos.Request("계란 요리", 1, List.of()); }

    private static IngredientResponse ingredient() {
        return new IngredientResponse(99L, "계란", Category.OTHER, BigDecimal.valueOf(3), Unit.PIECE,
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 12), StorageType.FRIDGE, 10, ExpiryStatus.SOON, 0L);
    }
}
