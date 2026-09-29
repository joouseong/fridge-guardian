package com.fridgegatekeeper.ingredient;

import com.fridgegatekeeper.common.ApiException;
import com.fridgegatekeeper.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class IngredientBatchService {
    private final IngredientService validation;
    private final IngredientRepository ingredients;
    private final UserRepository users;
    private final IngredientBatchRepository batches;
    private final ObjectMapper json;
    private final Clock clock;

    public IngredientBatchService(IngredientService validation, IngredientRepository ingredients,
                                  UserRepository users, IngredientBatchRepository batches, ObjectMapper json, Clock clock) {
        this.validation = validation; this.ingredients = ingredients; this.users = users;
        this.batches = batches; this.json = json; this.clock = clock;
    }

    @Transactional
    public List<IngredientResponse> create(Long userId, IngredientBatchRequest request) {
        // Serialize batches for this user, including simultaneous retries on different server threads.
        var user = users.findForIngredientBatch(userId).orElseThrow(() ->
            new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "다시 로그인해 주세요."));
        String key = request.requestId().toString();
        String hash = hash(json.writeValueAsString(request.items()));
        var previous = batches.findByUserIdAndRequestKey(userId, key);
        if (previous.isPresent()) {
            if (!previous.get().getPayloadHash().equals(hash)) {
                throw new ApiException(HttpStatus.CONFLICT, "BATCH_REQUEST_CONFLICT", "이미 사용한 등록 요청입니다. 목록을 확인한 뒤 새로 추가해 주세요.");
            }
            return json.readValue(previous.get().getResponseJson(), new TypeReference<List<IngredientResponse>>() { });
        }
        // Validate every row before writing. Any error rolls back the whole batch and its receipt.
        var errors = new LinkedHashMap<String, String>();
        for (int index = 0; index < request.items().size(); index++) {
            try { validation.validateDates(request.items().get(index)); }
            catch (ApiException error) {
                int row = index;
                error.getFieldErrors().forEach((field, message) -> errors.put("items[" + row + "]." + field,
                    (row + 1) + "번째 재료: " + message));
            }
        }
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "재료의 날짜를 확인해 주세요.", errors);
        LocalDate today = LocalDate.now(clock);
        var saved = ingredients.saveAllAndFlush(request.items().stream().map(item -> new Ingredient(user,
            item.name().strip(), item.category(), item.quantity(), item.unit(), item.purchaseDate(),
            item.expirationDate(), item.storageType())).toList());
        var response = saved.stream().map(item -> IngredientResponse.from(item, today)).toList();
        batches.saveAndFlush(new IngredientBatchReceipt(user, key, hash, json.writeValueAsString(response)));
        return response;
    }

    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
