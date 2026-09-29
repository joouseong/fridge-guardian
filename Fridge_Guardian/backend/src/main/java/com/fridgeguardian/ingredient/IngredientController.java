package com.fridgeguardian.ingredient;

import com.fridgeguardian.user.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ingredients")
public class IngredientController {
    private final IngredientService ingredients;
    private final IngredientBatchService batches;
    public IngredientController(IngredientService ingredients, IngredientBatchService batches) {
        this.ingredients = ingredients;
        this.batches = batches;
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
        return ingredients.create(CurrentUser.id(authentication), request);
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
