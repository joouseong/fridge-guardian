package com.fridgeguardian.ai;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRecipeRepository extends JpaRepository<AiRecipe, Long> {
    List<AiRecipe> findAllByUserIdOrderByIdDesc(Long userId, Pageable pageable);
    Optional<AiRecipe> findByIdAndUserId(Long id, Long userId);
}
