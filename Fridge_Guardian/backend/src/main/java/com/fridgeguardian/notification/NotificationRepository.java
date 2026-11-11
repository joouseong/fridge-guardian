package com.fridgeguardian.notification;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findAllByUserIdOrderByIdDesc(Long userId, Pageable pageable);
    Optional<Notification> findByIdAndUserId(Long id, Long userId);
    long countByUserIdAndStatusNot(Long userId, Notification.Status status);
    boolean existsByIngredientIdAndTypeAndNotifyDate(Long ingredientId, Notification.Type type, LocalDate notifyDate);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.status = com.fridgeguardian.notification.Notification.Status.READ "
        + "where n.userId = :userId and n.status <> com.fridgeguardian.notification.Notification.Status.READ")
    int markAllRead(Long userId);
}
