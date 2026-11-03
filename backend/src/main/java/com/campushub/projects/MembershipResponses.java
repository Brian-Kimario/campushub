package com.campushub.projects;

import java.time.Instant;
import java.util.UUID;

/** Response shapes for team membership and invitations (API_CONTRACT §6.1). */
public final class MembershipResponses {

    private MembershipResponses() {
    }

    /** One team member. The owner appears once with role OWNER; other members have role MEMBER. */
    public record MemberResponse(UUID userId, String displayName, String role, Instant joinedAt) {
    }

    public record InvitationResponse(UUID id, UUID projectId, String projectTitle, PersonSummary invitee,
                                     PersonSummary inviter, InvitationStatus status, Instant expiresAt,
                                     Instant createdAt, Instant respondedAt, int version) {
    }
}
