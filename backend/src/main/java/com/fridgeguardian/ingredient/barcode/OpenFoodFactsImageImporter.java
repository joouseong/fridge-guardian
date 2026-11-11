package com.fridgeguardian.ingredient.barcode;

import com.fridgeguardian.ingredient.image.ImageValidator;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Imports a cached Open Food Facts product image for local ingredient storage. */
@Component
public class OpenFoodFactsImageImporter {
    private static final Logger log = LoggerFactory.getLogger(OpenFoodFactsImageImporter.class);
    private static final String USER_AGENT = "FridgeGuardian/0.1 (local web application)";
    private final BarcodeProductRepository products;
    private final HttpClient http;

    public record ImportedImage(byte[] content, String attributionUrl) { }

    public OpenFoodFactsImageImporter(BarcodeProductRepository products) {
        this.products = products;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    }

    public Optional<ImportedImage> importForBarcode(String barcode) {
        if (barcode == null || barcode.isBlank()) return Optional.empty();
        BarcodeProduct product = products.findById(barcode).orElse(null);
        if (product == null || product.getProductImageUrl() == null) return Optional.empty();
        if (!"OPEN_FOOD_FACTS".equals(product.getSource()) && !"FOODSAFETY_C005".equals(product.getSource())) {
            return Optional.empty();
        }

        URI imageUri;
        try {
            imageUri = URI.create(product.getProductImageUrl());
        } catch (IllegalArgumentException | NullPointerException error) {
            return Optional.empty();
        }
        if (!"https".equals(imageUri.getScheme()) || !"images.openfoodfacts.org".equalsIgnoreCase(imageUri.getHost())
            || imageUri.getUserInfo() != null || (imageUri.getPort() != -1 && imageUri.getPort() != 443)) {
            return Optional.empty();
        }

        HttpRequest request = HttpRequest.newBuilder(imageUri).timeout(Duration.ofSeconds(6))
            .header("Accept", "image/jpeg,image/png,image/webp")
            .header("User-Agent", USER_AGENT)
            .GET().build();
        try {
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) return Optional.empty();
                long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
                if (contentLength > ImageValidator.MAX_BYTES) return Optional.empty();
                byte[] content = body.readNBytes((int) ImageValidator.MAX_BYTES + 1);
                if (content.length == 0 || content.length > ImageValidator.MAX_BYTES) return Optional.empty();
                return Optional.of(new ImportedImage(content,
                    "https://world.openfoodfacts.org/product/" + barcode));
            }
        } catch (IOException error) {
            log.info("Open Food Facts image import skipped ({})", error.getClass().getSimpleName());
            return Optional.empty();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
