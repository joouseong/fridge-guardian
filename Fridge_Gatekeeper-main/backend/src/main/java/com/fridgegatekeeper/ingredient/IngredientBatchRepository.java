package com.fridgegatekeeper.ingredient;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientBatchRepository extends JpaRepository<IngredientBatchReceipt, Long> {
    Optional<IngredientBatchReceipt> findByUserIdAndRequestKey(Long userId, String requestKey);
}
