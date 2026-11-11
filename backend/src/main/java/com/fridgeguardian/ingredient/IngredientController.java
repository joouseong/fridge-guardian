package com.fridgeguardian.ingredient;

import com.fridgeguardian.ingredient.barcode.OpenFoodFactsImageImporter;
import com.fridgeguardian.user.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ingredients")
public class IngredientController {
    private static final Logger log = LoggerFactory.getLogger(IngredientController.class);
    private final IngredientService ingredients;
    private final IngredientBatchService batches;
    private final OpenFoodFactsImageImporter barcodeImages;
    public IngredientController(IngredientService ingredients, IngredientBatchService batches,
                                OpenFoodFactsImageImporter barcodeImages) {
        this.ingredients = ingredients;
        this.batches = batches;
        this.barcodeImages = barcodeImages;
    }

    /** 이름 검색(q)·카테고리·보관 위치·상태 필터를 모두 선택적으로 조합할 수 있습니다. */
    @GetMapping
    public List<IngredientResponse> list(Authentication authentication,
                                        @RequestParam(defaultValue = "expiration") String sort,
                                        @RequestParam(required = false) String q,
                                        @RequestParam(required = false) Category category,
                                        @RequestParam(required = false) StorageType storage,
                                        @RequestParam(required = false) String status) {
        return ingredients.list(CurrentUser.id(authentication), sort, new IngredientFilter(q, category, storage, status));
    }

    @GetMapping("/{id}")
    public IngredientResponse get(Authentication authentication, @PathVariable Long id) {
        return ingredients.get(CurrentUser.id(authentication), id);
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public IngredientResponse create(Authentication authentication, @Valid @RequestBody IngredientRequest request) {
        Long userId = CurrentUser.id(authentication);
        IngredientResponse created = ingredients.create(userId, request);
        try {
            return barcodeImages.importForBarcode(request.barcodeOrNull()).map(image -> {
                try {
                    return ingredients.replaceImageFromBarcode(userId, created.id(), image.content(), image.attributionUrl());
                } catch (RuntimeException error) {
                    log.info("Open Food Facts image could not be saved for ingredient {}", created.id());
                    return created;
                }
            }).orElse(created);
        } catch (RuntimeException error) {
            log.info("Open Food Facts image import skipped for ingredient {}", created.id());
            return created;
        }
    }

    @PutMapping("/{id}")
    public IngredientResponse update(Authentication authentication, @PathVariable Long id,
                                     @Valid @RequestBody IngredientRequest request) {
        return ingredients.update(CurrentUser.id(authentication), id, request);
    }

    /** 사진 업로드/교체(최대 5MB, JPG·PNG·WEBP). 사진을 보내지 않고 수정만 하면 기존 사진이 유지됩니다. */
    @PostMapping(path = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IngredientResponse uploadImage(Authentication authentication, @PathVariable Long id,
                                          @RequestPart("file") MultipartFile file) {
        return ingredients.replaceImage(CurrentUser.id(authentication), id, file);
    }

    @DeleteMapping("/{id}/image")
    public IngredientResponse removeImage(Authentication authentication, @PathVariable Long id) {
        return ingredients.removeImage(CurrentUser.id(authentication), id);
    }

    @PostMapping("/batch") @ResponseStatus(HttpStatus.CREATED)
    public List<IngredientResponse> createBatch(Authentication authentication, @Valid @RequestBody IngredientBatchRequest request) {
        return batches.create(CurrentUser.id(authentication), request);
    }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication, @PathVariable Long id) {
        ingredients.delete(CurrentUser.id(authentication), id);
    }
}
