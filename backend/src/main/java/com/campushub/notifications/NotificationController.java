package com.campushub.notifications;

import com.campushub.identity.security.AuthenticatedUser;
import com.campushub.notifications.NotificationResponses.MarkAllResult;
import com.campushub.notifications.NotificationResponses.NotificationResponse;
import com.campushub.notifications.NotificationResponses.UnreadCount;
import com.campushub.shared.web.PageResponse;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The caller's own notifications (V15). Other users' notifications are 404. */
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public PageResponse<NotificationResponse> list(@AuthenticationPrincipal AuthenticatedUser caller,
                                                   @RequestParam(defaultValue = "false") boolean unreadOnly,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return notifications.list(caller, unreadOnly, page, size);
    }

    @GetMapping("/unread-count")
    public UnreadCount unreadCount(@AuthenticationPrincipal AuthenticatedUser caller) {
        return notifications.unreadCount(caller);
    }

    @PostMapping("/{id}/read")
    public NotificationResponse markRead(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return notifications.markRead(caller, id);
    }

    @PostMapping("/read-all")
    public MarkAllResult markAllRead(@AuthenticationPrincipal AuthenticatedUser caller) {
        return notifications.markAllRead(caller);
    }
}
