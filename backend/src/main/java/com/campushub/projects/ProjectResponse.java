package com.campushub.projects;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Project as returned by the API. Carries no membership or invitation data (not in the MVP). */
public record ProjectResponse(
        UUID id,
        String title,
        String description,
        String subject,
        ProjectStatus status,
        LocalDate startDate,
        LocalDate dueDate,
        PersonSummary owner,
        PersonSummary supervisor,
        int version,
        Instant createdAt,
        Instant updatedAt) {
}
