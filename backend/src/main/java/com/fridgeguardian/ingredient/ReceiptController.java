package com.fridgeguardian.ingredient;

import com.fridgeguardian.chat.AiRequestLimiter;
import com.fridgeguardian.chat.OpenAiClient;
import com.fridgeguardian.common.ApiException;
import com.fridgeguardian.ingredient.image.ImageValidator;
import com.fridgeguardian.user.CurrentUser;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/receipts")
public class ReceiptController {
    private final OpenAiClient ai;
    private final ImageValidator images;
    private final AiRequestLimiter limiter;
    private final ObjectMapper json;
    private final Clock clock;

    public ReceiptController(OpenAiClient ai, ImageValidator images, AiRequestLimiter limiter, ObjectMapper json, Clock clock) {
        this.ai = ai; this.images = images; this.limiter = limiter; this.json = json; this.clock = clock;
    }

    @PostMapping(path = "/scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public JsonNode scan(Authentication authentication, @RequestPart("file") MultipartFile file) {
        var validated = images.validate(file);
        if (!ai.available()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED", "영수증 인식 기능을 사용하려면 AI 설정이 필요해요.");
        String mediaType = switch (validated.extension()) {
            case "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> throw new IllegalStateException("Unsupported validated image");
        };
        try (var permit = limiter.acquire(CurrentUser.id(authentication))) {
            return json.readTree(ai.readReceipt(validated.content(), mediaType, LocalDate.now(clock)));
        }
    }
}
