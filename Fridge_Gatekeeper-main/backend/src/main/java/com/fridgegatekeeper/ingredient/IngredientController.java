package com.fridgegatekeeper.ingredient;

import com.fridgegatekeeper.user.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ingredients")
public class IngredientController {
    private final IngredientService ingredients;
    private final IngredientBatchService batches;
    public IngredientController(IngredientService ingredients, IngredientBatchService batches) {
        this.ingredients = ingredients;
        this.batches = batches;
    }

    @GetMapping
    public List<IngredientResponse> list(Authentication authentication,
                                        @RequestParam(defaultValue = "expiration") String sort) {
        return ingredients.list(CurrentUser.id(authentication), sort);
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

    @PostMapping("/batch") @ResponseStatus(HttpStatus.CREATED)
    public List<IngredientResponse> createBatch(Authentication authentication, @Valid @RequestBody IngredientBatchRequest request) {
        return batches.create(CurrentUser.id(authentication), request);
    }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication, @PathVariable Long id) {
        ingredients.delete(CurrentUser.id(authentication), id);
    }
}
