package com.fridgeguardian.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml의 app.* 설정 중 인증·업로드·바코드·배치 관련 값입니다. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(String frontendUrl, Jwt jwt, Upload upload, Barcode barcode, Batch batch) {
    public record Jwt(String secret, Duration accessTtl, Duration refreshTtl, boolean cookieSecure) { }
    public record Upload(String dir) { }
    public record Barcode(String apiKey, String baseUrl) { }
    public record Batch(String expiryCron, boolean schedulerEnabled) { }
}
