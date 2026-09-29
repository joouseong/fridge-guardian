package com.fridgegatekeeper.chat;

import com.fridgegatekeeper.recommendation.RecipeRecommendation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class ChatDtos {
    private ChatDtos() { }

    public record Request(
        @NotBlank(message = "질문을 입력해 주세요.")
        @Size(max = 2000, message = "질문은 2,000자 이내로 입력해 주세요.") String message,
        @Min(value = 1, message = "인분은 1 또는 2로 선택해 주세요.")
        @Max(value = 2, message = "인분은 1 또는 2로 선택해 주세요.") int servings,
        @Size(max = 10, message = "이전 대화는 최근 10개까지만 보낼 수 있습니다.")
        List<@NotNull @Valid Message> history,
        @Pattern(regexp = "AUTO|LOCAL", message = "대화 방식을 확인해 주세요.") String mode
    ) {
        public Request {
            history = history == null ? List.of() : history;
            mode = mode == null ? "AUTO" : mode;
        }
        public Request(String message, int servings, List<Message> history) {
            this(message, servings, history, "AUTO");
        }
    }

    public record Message(
        @NotBlank @Pattern(regexp = "user|assistant", message = "대화 역할을 확인해 주세요.") String role,
        @NotBlank(message = "이전 대화 내용은 비어 있을 수 없습니다.")
        @Size(max = 2000, message = "이전 대화는 각각 2,000자 이내여야 합니다.") String content
    ) { }

    public record Status(boolean available, String model) { }
    public record Response(String reply, String source, List<RecipeRecommendation> recommendedRecipes) { }
}
