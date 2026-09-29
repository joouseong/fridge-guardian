package com.fridgegatekeeper.ingredient;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {
    // user.id 조건을 쿼리에 포함하여 다른 사용자의 식재료가 조회되지 않도록 합니다.
    List<Ingredient> findAllByUserId(Long userId);
    Optional<Ingredient> findByIdAndUserId(Long id, Long userId);
}
