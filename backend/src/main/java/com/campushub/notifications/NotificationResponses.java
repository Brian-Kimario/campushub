package com.campushub.notifications;

import java.time.Instant;
import java.util.UUID;

/** Response shapes for notifications (V15). */
public final class NotificationResponses {

    private NotificationResponses() {
    }

    /** One notification. The message is rendered on the server from the type, project title and actor name. */
    public record NotificationResponse(UUID id, NotificationType type, String message, UUID projectId,
                                       Instant createdAt, Instant readAt) {
    }

    public record UnreadCount(long count) {
    }

    public record MarkAllResult(int updated) {
    }
}
