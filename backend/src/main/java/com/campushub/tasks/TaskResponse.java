package com.campushub.tasks;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Task as returned by the API (API_CONTRACT §8). {@code createdBy} is the user id. */
public record TaskResponse(
        UUID id,
        UUID projectId,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        String blockedReason,
        LocalDate dueDate,
        Instant completedAt,
        UUID createdBy,
        int version,
        Instant createdAt,
        Instant updatedAt) {
}
