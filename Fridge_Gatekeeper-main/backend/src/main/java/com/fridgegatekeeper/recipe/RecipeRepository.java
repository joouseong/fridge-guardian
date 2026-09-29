package com.fridgegatekeeper.recipe;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeRepository extends JpaRepository<Recipe,Long> {
    // 재료를 함께 읽어 레시피마다 추가 쿼리가 발생하는 N+1 문제를 방지합니다.
    @Override @EntityGraph(attributePaths="ingredients")
    List<Recipe> findAll();
    @Override @EntityGraph(attributePaths="ingredients")
    Optional<Recipe> findById(Long id);
}
