package com.campushub.milestones;

import java.time.Instant;
import java.util.UUID;

public record MilestoneResponse(
        UUID id,
        UUID projectId,
        int sequence,
        String title,
        Instant dueAt,
        boolean allowLate,
        MilestoneStatus status,
        int version,
        Instant createdAt,
        Instant updatedAt) {

    static MilestoneResponse from(Milestone milestone) {
        return new MilestoneResponse(milestone.getId(), milestone.getProjectId(), milestone.getSequence(),
                milestone.getTitle(), milestone.getDueAt(), milestone.isAllowLate(), milestone.getStatus(),
                milestone.getVersion(), milestone.getCreatedAt(), milestone.getUpdatedAt());
    }
}
