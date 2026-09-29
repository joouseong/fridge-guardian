package com.fridgeguardian.notification;

import com.fridgeguardian.common.ApiException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotificationService {
    private static final int LIMIT = 50;
    private final NotificationRepository notifications;

    public NotificationService(NotificationRepository notifications) { this.notifications = notifications; }

    public record Item(Long id, Long ingredientId, String ingredientName, Notification.Type type, LocalDate notifyDate,
                       Notification.Status status, LocalDateTime createdAt) {
        static Item from(Notification n) {
            return new Item(n.getId(), n.getIngredientId(), n.getIngredientName(), n.getType(), n.getNotifyDate(),
                n.getStatus(), n.getCreatedAt());
        }
    }

    public record Inbox(long unreadCount, List<Item> items) { }

    public Inbox inbox(Long userId) {
        List<Item> items = notifications.findAllByUserIdOrderByIdDesc(userId, PageRequest.of(0, LIMIT)).stream().map(Item::from).toList();
        return new Inbox(notifications.countByUserIdAndStatusNot(userId, Notification.Status.READ), items);
    }

    @Transactional
    public Item markRead(Long userId, Long id) {
        Notification notification = notifications.findByIdAndUserId(id, userId)
            .orElseThrow(() -> ApiException.notFound("알림을 찾을 수 없습니다."));
        notification.markRead();
        return Item.from(notifications.saveAndFlush(notification));
    }

    @Transactional
    public int markAllRead(Long userId) { return notifications.markAllRead(userId); }
}
