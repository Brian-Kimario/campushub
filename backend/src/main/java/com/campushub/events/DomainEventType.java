package com.campushub.events;

/**
 * Event types written to the outbox (V16). The wire name is the stable value on the Kafka topic and in the
 * outbox CHECK constraint; renaming an enum constant does not change it.
 */
public enum DomainEventType {
    INVITATION_RECEIVED("invitation.received"),
    INVITATION_ACCEPTED("invitation.accepted"),
    INVITATION_DECLINED("invitation.declined"),
    MEMBER_REMOVED("member.removed"),
    SUBMISSION_REVIEWED("submission.reviewed");

    private final String wireName;

    DomainEventType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
