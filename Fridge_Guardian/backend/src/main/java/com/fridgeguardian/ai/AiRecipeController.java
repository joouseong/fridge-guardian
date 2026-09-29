package com.fridgeguardian.ai;

import com.fridgeguardian.user.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/recipes")
public class AiRecipeController {
    private final AiRecipeService recipes;

    public AiRecipeController(AiRecipeService recipes) { this.recipes = recipes; }

    /** 선택한 보유 재료로 AI 레시피를 만듭니다. 키가 없으면 503 AI_NOT_CONFIGURED입니다. */
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public AiRecipeDtos.Response generate(Authentication authentication, @Valid @RequestBody AiRecipeDtos.Request request) {
        return recipes.generate(CurrentUser.id(authentication), request);
    }

    @GetMapping
    public List<AiRecipeDtos.Response> history(Authentication authentication) {
        return recipes.history(CurrentUser.id(authentication));
    }

    @PutMapping("/{id}/favorite")
    public AiRecipeDtos.Response favorite(Authentication authentication, @PathVariable Long id,
                                          @RequestBody AiRecipeDtos.FavoriteRequest request) {
        return recipes.setFavorite(CurrentUser.id(authentication), id, request.favorite());
    }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication, @PathVariable Long id) {
        recipes.delete(CurrentUser.id(authentication), id);
    }
}
