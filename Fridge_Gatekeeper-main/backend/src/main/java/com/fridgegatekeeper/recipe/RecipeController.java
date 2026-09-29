package com.fridgegatekeeper.recipe;
import com.fridgegatekeeper.recommendation.RecipeRecommendation;
import com.fridgegatekeeper.recommendation.RecommendationService;
import com.fridgegatekeeper.user.CurrentUser;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/recipes")
public class RecipeController {
    private final RecommendationService recommendations;
    public RecipeController(RecommendationService recommendations) { this.recommendations=recommendations; }
    @GetMapping("/recommendations")
    public List<RecipeRecommendation> recommend(Authentication authentication,@RequestParam(defaultValue="2") int servings) {
        return recommendations.recommend(CurrentUser.id(authentication),servings);
    }
    @GetMapping("/{id}")
    public RecipeRecommendation detail(Authentication authentication,@PathVariable Long id,
                                       @RequestParam(defaultValue="2") int servings) {
        return recommendations.detail(CurrentUser.id(authentication),id,servings);
    }
}
