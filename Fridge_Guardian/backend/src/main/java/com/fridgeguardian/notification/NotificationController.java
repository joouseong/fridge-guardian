package com.fridgeguardian.notification;

import com.fridgeguardian.user.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) { this.notifications = notifications; }

    @GetMapping
    public NotificationService.Inbox inbox(Authentication authentication) {
        return notifications.inbox(CurrentUser.id(authentication));
    }

    @PutMapping("/{id}/read")
    public NotificationService.Item read(Authentication authentication, @PathVariable Long id) {
        return notifications.markRead(CurrentUser.id(authentication), id);
    }

    @PutMapping("/read-all") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(Authentication authentication) { notifications.markAllRead(CurrentUser.id(authentication)); }
}
