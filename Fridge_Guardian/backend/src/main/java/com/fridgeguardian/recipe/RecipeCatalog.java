package com.fridgeguardian.recipe;

import com.fridgeguardian.common.ApiException;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모든 사용자가 함께 쓰는 공통 레시피 조회. 자주 읽고 거의 바뀌지 않으므로 캐시합니다.
 * (Redis 사용 시 공통 레시피 캐시, 아니면 프로세스 메모리 캐시.) 사용자별 재고·추천 결과는 캐시하지 않습니다.
 */
@Service
@Transactional(readOnly = true)
public class RecipeCatalog {
    public static final String CACHE = "recipes";
    private final RecipeRepository recipes;

    public RecipeCatalog(RecipeRepository recipes) { this.recipes = recipes; }

    @Cacheable(cacheNames = CACHE, key = "'all'")
    public List<RecipeData> all() {
        return recipes.findAll().stream().map(RecipeData::from).toList();
    }

    @Cacheable(cacheNames = CACHE, key = "'id:' + #id")
    public RecipeData get(Long id) {
        return recipes.findById(id).map(RecipeData::from)
            .orElseThrow(() -> ApiException.notFound("레시피를 찾을 수 없습니다."));
    }

    @CacheEvict(cacheNames = CACHE, allEntries = true)
    public void evictAll() { }
}
