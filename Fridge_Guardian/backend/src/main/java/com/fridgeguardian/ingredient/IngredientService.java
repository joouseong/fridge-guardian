package com.fridgeguardian.ingredient;

import com.fridgeguardian.common.ApiException;
import com.fridgeguardian.ingredient.image.ImageStorage;
import com.fridgeguardian.ingredient.image.ImageValidator;
import com.fridgeguardian.user.UserAccount;
import com.fridgeguardian.user.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class IngredientService {
    private final IngredientRepository ingredients;
    private final UserRepository users;
    private final ImageStorage images;
    private final ImageValidator imageValidator;
    private final Clock clock;

    public IngredientService(IngredientRepository ingredients, UserRepository users, ImageStorage images,
                             ImageValidator imageValidator, Clock clock) {
        this.ingredients = ingredients;
        this.users = users;
        this.images = images;
        this.imageValidator = imageValidator;
        this.clock = clock;
    }

    public List<IngredientResponse> list(Long userId, String sort) { return list(userId, sort, IngredientFilter.NONE); }

    public List<IngredientResponse> list(Long userId, String sort, IngredientFilter filter) {
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
            .map(ingredient -> IngredientResponse.from(ingredient, today)).filter(filter::matches).toList();
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
            request.unit(), request.purchaseDate(), request.expirationDate(), request.storageType(),
            request.memoOrNull(), request.barcodeOrNull());
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
        // 사진은 이 요청으로 바꾸지 않습니다. 새 사진을 올리지 않으면 기존 사진이 그대로 유지됩니다.
        ingredient.update(request.name().strip(), request.category(), request.quantity(), request.unit(),
            request.purchaseDate(), request.expirationDate(), request.storageType(),
            request.memoOrNull(), request.barcodeOrNull());
        // flush 후 증가한 version을 응답해야 다음 수정에서도 최신 버전을 사용할 수 있습니다.
        ingredients.flush();
        return IngredientResponse.from(ingredient, LocalDate.now(clock));
    }

    /** 새 사진으로 교체합니다. 이전 사진 파일은 DB 반영이 성공한 뒤에 지웁니다. */
    @Transactional
    public IngredientResponse replaceImage(Long userId, Long id, MultipartFile file) {
        Ingredient ingredient = owned(userId, id);
        var image = imageValidator.validate(file);
        String stored = images.save(image.content(), image.extension());
        onRollback(() -> images.delete(stored));
        String previous = ingredient.getImagePath();
        ingredient.setImagePath(stored);
        ingredients.flush();
        if (previous != null) afterCommit(() -> images.delete(previous));
        return IngredientResponse.from(ingredient, LocalDate.now(clock));
    }

    /** 사진을 지우고 기본 이미지로 돌아갑니다. */
    @Transactional
    public IngredientResponse removeImage(Long userId, Long id) {
        Ingredient ingredient = owned(userId, id);
        String previous = ingredient.getImagePath();
        ingredient.setImagePath(null);
        ingredients.flush();
        if (previous != null) afterCommit(() -> images.delete(previous));
        return IngredientResponse.from(ingredient, LocalDate.now(clock));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Ingredient ingredient = owned(userId, id);
        String image = ingredient.getImagePath();
        ingredients.delete(ingredient);
        ingredients.flush();
        // PRD: 식재료를 삭제하면 서버에 업로드된 사진도 함께 지웁니다. 롤백되면 파일은 남겨 둡니다.
        if (image != null) afterCommit(() -> images.delete(image));
    }

    private Ingredient owned(Long userId, Long id) {
        // 다른 사람의 ID도 404로 처리하여 식재료 존재 여부를 노출하지 않습니다.
        return ingredients.findByIdAndUserId(id, userId).orElseThrow(() ->
            ApiException.notFound("식재료를 찾을 수 없습니다."));
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) { action.run(); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }

    private static void onRollback(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) action.run();
            }
        });
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
