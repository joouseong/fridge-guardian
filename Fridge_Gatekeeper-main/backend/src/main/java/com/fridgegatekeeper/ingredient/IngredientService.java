package com.fridgegatekeeper.ingredient;

import com.fridgegatekeeper.common.ApiException;
import com.fridgegatekeeper.user.UserAccount;
import com.fridgegatekeeper.user.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class IngredientService {
    private final IngredientRepository ingredients;
    private final UserRepository users;
    private final Clock clock;

    public IngredientService(IngredientRepository ingredients, UserRepository users, Clock clock) {
        this.ingredients = ingredients;
        this.users = users;
        this.clock = clock;
    }

    public List<IngredientResponse> list(Long userId, String sort) {
        LocalDate today = LocalDate.now(clock);
        Comparator<Ingredient> expiration = Comparator.comparing(Ingredient::getExpirationDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Ingredient::getName).thenComparing(Ingredient::getId);
        Comparator<Ingredient> order = switch (sort) {
            case "expiration" -> expiration;
            case "category" -> Comparator.comparing(Ingredient::getCategory).thenComparing(expiration);
            case "storage" -> Comparator.comparing(Ingredient::getStorageType).thenComparing(expiration);
            default -> throw ApiException.badRequest("정렬은 expiration, category, storage 중 하나를 선택해 주세요.");
        };
        return ingredients.findAllByUserId(userId).stream().sorted(order)
            .map(ingredient -> IngredientResponse.from(ingredient, today)).toList();
    }

    public IngredientResponse get(Long userId, Long id) {
        return IngredientResponse.from(owned(userId, id), LocalDate.now(clock));
    }

    @Transactional
    public IngredientResponse create(Long userId, IngredientRequest request) {
        validateDates(request);
        UserAccount user = users.findById(userId).orElseThrow(() ->
            new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "다시 로그인해 주세요."));
        Ingredient ingredient = new Ingredient(user, request.name().strip(), request.category(), request.quantity(),
            request.unit(), request.purchaseDate(), request.expirationDate(), request.storageType());
        return IngredientResponse.from(ingredients.saveAndFlush(ingredient), LocalDate.now(clock));
    }

    @Transactional
    public IngredientResponse update(Long userId, Long id, IngredientRequest request) {
        Ingredient ingredient = owned(userId, id);
        validateDates(request);
        if (request.version() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "수정 버전이 필요합니다.",
                Map.of("version", "목록에서 받은 version 값을 함께 보내 주세요."));
        }
        if (request.version() != ingredient.getVersion()) {
            throw new ApiException(HttpStatus.CONFLICT, "STALE_VERSION", "다른 화면에서 수정된 식재료입니다. 새로고침 후 다시 시도해 주세요.");
        }
        ingredient.update(request.name().strip(), request.category(), request.quantity(), request.unit(),
            request.purchaseDate(), request.expirationDate(), request.storageType());
        // flush 후 증가한 version을 응답해야 다음 수정에서도 최신 버전을 사용할 수 있습니다.
        ingredients.flush();
        return IngredientResponse.from(ingredient, LocalDate.now(clock));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        ingredients.delete(owned(userId, id));
        ingredients.flush();
    }

    private Ingredient owned(Long userId, Long id) {
        // 다른 사람의 ID도 404로 처리하여 식재료 존재 여부를 노출하지 않습니다.
        return ingredients.findByIdAndUserId(id, userId).orElseThrow(() ->
            ApiException.notFound("식재료를 찾을 수 없습니다."));
    }

    void validateDates(IngredientRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        LocalDate today = LocalDate.now(clock);
        if (request.purchaseDate().isAfter(today)) errors.put("purchaseDate", "구매 날짜는 오늘 이후일 수 없습니다.");
        if (request.expirationDate() != null && request.purchaseDate().isAfter(request.expirationDate())) {
            errors.put("expirationDate", "유통기한은 구매 날짜와 같거나 이후여야 합니다.");
        }
        // MySQL DATE의 지원 범위도 API 단계에서 검사합니다.
        if (request.purchaseDate().getYear() < 1000 || request.purchaseDate().getYear() > 9999) {
            errors.put("purchaseDate", "날짜의 연도는 1000~9999 범위여야 합니다.");
        }
        if (request.expirationDate() != null && (request.expirationDate().getYear() < 1000 || request.expirationDate().getYear() > 9999)) {
            errors.put("expirationDate", "날짜의 연도는 1000~9999 범위여야 합니다.");
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "날짜를 확인해 주세요.", errors);
        }
    }
}
