package com.fridgeguardian.shopping;

import com.fridgeguardian.user.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shopping-items")
public class ShoppingController {
    private final ShoppingService shopping;

    public ShoppingController(ShoppingService shopping) { this.shopping = shopping; }

    @GetMapping
    public List<ShoppingDtos.Response> list(Authentication authentication) {
        return shopping.list(CurrentUser.id(authentication));
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public List<ShoppingDtos.Response> add(Authentication authentication, @Valid @RequestBody ShoppingDtos.CreateRequest request) {
        return shopping.add(CurrentUser.id(authentication), List.of(request));
    }

    @PostMapping("/bulk") @ResponseStatus(HttpStatus.CREATED)
    public List<ShoppingDtos.Response> addBulk(Authentication authentication, @Valid @RequestBody ShoppingDtos.BulkRequest request) {
        return shopping.add(CurrentUser.id(authentication), request.items());
    }

    @PostMapping("/from-recipe") @ResponseStatus(HttpStatus.CREATED)
    public List<ShoppingDtos.Response> fromRecipe(Authentication authentication, @Valid @RequestBody ShoppingDtos.FromRecipeRequest request) {
        return shopping.addMissingFromRecipe(CurrentUser.id(authentication), request);
    }

    @PutMapping("/{id}/checked")
    public ShoppingDtos.Response check(Authentication authentication, @PathVariable Long id,
                                       @RequestBody ShoppingDtos.CheckRequest request) {
        return shopping.setChecked(CurrentUser.id(authentication), id, request.checked());
    }

    @DeleteMapping("/checked") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearChecked(Authentication authentication) { shopping.clearChecked(CurrentUser.id(authentication)); }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication, @PathVariable Long id) { shopping.delete(CurrentUser.id(authentication), id); }
}
