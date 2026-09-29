package com.fridgeguardian.ingredient.barcode;

import com.fridgeguardian.config.AppProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 식품안전나라 OpenAPI의 바코드연계제품정보(서비스 C005)로 가공식품 품목명과 유통기간을 조회합니다.
 * 응답 형식은 공개 문서를 기준으로 방어적으로 파싱하며, 키가 없거나 실패하면 조용히 비어 있는 결과를 돌려줍니다.
 * (키를 발급받아 실제 응답으로 한 번 확인해야 합니다.)
 */
@Component
public class FoodSafetyBarcodeClient {
    private static final Logger log = LoggerFactory.getLogger(FoodSafetyBarcodeClient.class);
    private static final Pattern PERIOD = Pattern.compile("(\\d+)\\s*(일|개월|달|월|년)");
    public record Found(String productName, Integer shelfLifeDays) { }

    private final ObjectMapper json;
    private final HttpClient http;
    private final String apiKey;
    private final String baseUrl;

    @Autowired
    public FoodSafetyBarcodeClient(ObjectMapper json, AppProperties props) {
        this(json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build(),
            props.barcode().apiKey(), props.barcode().baseUrl());
    }

    FoodSafetyBarcodeClient(ObjectMapper json, HttpClient http, String apiKey, String baseUrl) {
        this.json = json; this.http = http;
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        String base = baseUrl == null ? "" : baseUrl.strip().replaceAll("/+$", "");
        URI uri = URI.create(base.isEmpty() ? "https://openapi.foodsafetykorea.go.kr" : base);
        boolean loopback = List.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost());
        if (!"https".equals(uri.getScheme()) && !(loopback && "http".equals(uri.getScheme()))) {
            throw new IllegalArgumentException("Barcode API base-url must use HTTPS except for loopback test servers.");
        }
        this.baseUrl = uri.toString().replaceAll("/+$", "");
    }

    public boolean available() { return !apiKey.isEmpty(); }

    public Optional<Found> find(String barcode) {
        if (!available()) return Optional.empty();
        try {
            // 인증 키는 URL 경로에 들어가므로 로그에 남기지 않습니다.
            URI uri = URI.create(baseUrl + "/api/" + apiKey + "/C005/json/1/5/BAR_CD=" + barcode);
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("바코드 조회 API가 {} 상태를 반환했습니다.", response.statusCode());
                return Optional.empty();
            }
            return parse(response.body());
        } catch (IOException | JacksonException error) {
            log.warn("바코드 조회에 실패했습니다: {}", error.getClass().getSimpleName());
            return Optional.empty();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    Optional<Found> parse(String body) {
        JsonNode rows = json.readTree(body).path("C005").path("row");
        if (!rows.isArray() || rows.isEmpty()) return Optional.empty();
        JsonNode row = rows.get(0);
        String name = row.path("PRDLST_NM").asString("").strip();
        if (name.isEmpty()) return Optional.empty();
        return Optional.of(new Found(name.length() > 200 ? name.substring(0, 200) : name,
            shelfLifeDays(row.path("POG_DAYCNT").asString(""))));
    }

    /** "제조일로부터 12개월", "90일", "2년" 같은 문구를 일 수로 바꿉니다. 해석할 수 없으면 null입니다. */
    static Integer shelfLifeDays(String text) {
        Matcher matcher = PERIOD.matcher(text == null ? "" : text);
        if (!matcher.find()) return null;
        long amount = Long.parseLong(matcher.group(1));
        long days = switch (matcher.group(2)) {
            case "일" -> amount;
            case "년" -> amount * 365;
            default -> amount * 30;
        };
        return days <= 0 || days > 3650 ? null : (int) days;
    }
}
