package com.fridgeguardian.shopping;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ShoppingItemRepository extends JpaRepository<ShoppingItem, Long> {
    List<ShoppingItem> findAllByUserIdOrderByCheckedAscIdDesc(Long userId);
    Optional<ShoppingItem> findByIdAndUserId(Long id, Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ShoppingItem s where s.userId = :userId and s.checked = true")
    int deleteChecked(Long userId);
}
