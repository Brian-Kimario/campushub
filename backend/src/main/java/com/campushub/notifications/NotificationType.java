package com.campushub.notifications;

/** The events that create a notification. Each value has one message template in NotificationService. */
public enum NotificationType {
    INVITATION_RECEIVED,
    INVITATION_ACCEPTED,
    INVITATION_DECLINED,
    MEMBER_REMOVED,
    SUBMISSION_ACCEPTED,
    SUBMISSION_CHANGES_REQUESTED
}
