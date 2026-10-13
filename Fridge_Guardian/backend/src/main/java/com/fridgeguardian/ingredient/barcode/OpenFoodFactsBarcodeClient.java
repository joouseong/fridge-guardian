package com.fridgeguardian.ingredient.barcode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 키 없이 바코드 상품명·브랜드·정면 사진을 보완 조회합니다. */
@Component
public class OpenFoodFactsBarcodeClient {
    private static final Logger log = LoggerFactory.getLogger(OpenFoodFactsBarcodeClient.class);
    private static final String BASE_URL = "https://world.openfoodfacts.org";
    private static final String USER_AGENT = "FridgeGuardian/0.1 (local web application)";
    public record Found(String productName, String brand, String imageUrl) { }

    private final ObjectMapper json;
    private final HttpClient http;

    @Autowired
    public OpenFoodFactsBarcodeClient(ObjectMapper json) {
        this(json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL).build());
    }

    OpenFoodFactsBarcodeClient(ObjectMapper json, HttpClient http) {
        this.json = json;
        this.http = http;
    }

    public Optional<Found> find(String barcode) {
        try {
            String fields = "code,product_name,product_name_ko,generic_name,brands,selected_images";
            URI uri = URI.create(BASE_URL + "/api/v3/product/" + barcode + ".json?lc=ko&cc=kr&fields=" + fields);
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(6))
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Open Food Facts 바코드 조회가 {} 상태를 반환했습니다.", response.statusCode());
                return Optional.empty();
            }
            return parse(response.body());
        } catch (IOException | JacksonException error) {
            log.warn("Open Food Facts 조회에 실패했습니다: {}", error.getClass().getSimpleName());
            return Optional.empty();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IllegalArgumentException error) {
            log.warn("Open Food Facts 응답 주소가 올바르지 않습니다.");
            return Optional.empty();
        }
    }

    Optional<Found> parse(String body) {
        JsonNode root = json.readTree(body);
        JsonNode product = root.path("product");
        String status = root.path("status").asString("");
        boolean successful = status.startsWith("success") || status.equals("1") || root.path("status").asInt(-1) == 1;
        if (!successful || !product.isObject()) return Optional.empty();
        String name = firstText(product, "product_name_ko", "product_name", "generic_name");
        String brand = product.path("brands").asString("").strip();
        if (brand.length() > 200) brand = brand.substring(0, 200);
        JsonNode display = product.path("selected_images").path("front").path("display");
        String image = firstText(display, "ko", "en", "fr", "de", "es", "it", "ja", "zh");
        if (image.isEmpty()) image = display.asString("").strip();
        if (name.isEmpty() && image.isEmpty()) return Optional.empty();
        if (!isOpenFoodFactsImage(image)) image = "";
        return Optional.of(new Found(name.length() > 200 ? name.substring(0, 200) : name,
            brand.isEmpty() ? null : brand, image.isEmpty() ? null : image));
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asString("").strip();
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static boolean isOpenFoodFactsImage(String value) {
        if (value.isEmpty()) return false;
        try {
            URI uri = URI.create(value);
            return "https".equals(uri.getScheme()) && "images.openfoodfacts.org".equalsIgnoreCase(uri.getHost());
        } catch (IllegalArgumentException error) {
            return false;
        }
    }
}
