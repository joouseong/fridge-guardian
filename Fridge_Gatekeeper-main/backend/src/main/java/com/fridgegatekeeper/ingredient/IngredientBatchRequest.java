package com.fridgegatekeeper.ingredient;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record IngredientBatchRequest(
    @NotNull(message = "등록 요청 번호가 필요합니다.") UUID requestId,
    @NotNull(message = "추가할 재료를 선택해 주세요.")
    @Size(min = 1, max = 50, message = "한 번에 1~50개 재료를 추가할 수 있습니다.")
    List<@NotNull @Valid IngredientRequest> items
) { }
