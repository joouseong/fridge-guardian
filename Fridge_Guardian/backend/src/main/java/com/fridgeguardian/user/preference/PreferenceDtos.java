package com.fridgeguardian.user.preference;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class PreferenceDtos {
    private PreferenceDtos() { }

    /** 목록 전체를 교체하는 요청입니다. 빈 목록을 보내면 해당 항목을 모두 지웁니다. */
    public record Request(
        @Min(value = 0, message = "매운맛은 0~3 중에서 선택해 주세요.")
        @Max(value = 3, message = "매운맛은 0~3 중에서 선택해 주세요.") int spicyLevel,
        @Size(max = 30, message = "알레르기는 30개까지 등록할 수 있어요.")
        List<@NotBlank(message = "이름을 입력해 주세요.") @Size(max = 80, message = "이름은 80자 이하여야 합니다.") String> allergies,
        @Size(max = 30, message = "비선호 재료는 30개까지 등록할 수 있어요.")
        List<@NotBlank(message = "이름을 입력해 주세요.") @Size(max = 80, message = "이름은 80자 이하여야 합니다.") String> dislikes
    ) {
        public Request {
            allergies = allergies == null ? List.of() : allergies;
            dislikes = dislikes == null ? List.of() : dislikes;
        }
    }

    public record Response(int spicyLevel, List<String> allergies, List<String> dislikes) { }
}
