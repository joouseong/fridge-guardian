package com.fridgeguardian.user.preference;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ExcludedItemRepository extends JpaRepository<ExcludedItem, Long> {
    List<ExcludedItem> findAllByUserIdOrderByIdAsc(Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ExcludedItem e where e.userId = :userId")
    void deleteAllByUserId(Long userId);
}
