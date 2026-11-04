package com.campushub.milestones;

import com.campushub.projects.Text;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Body of {@code PATCH /milestones/{id}}. The owner sends title and dueAt; the supervisor sends
 * allowLate. A null field means "not sent". Sending a field the caller may not edit is refused
 * with 400 FIELD_NOT_EDITABLE (AUTHORIZATION_MATRIX §3.5).
 */
public record UpdateMilestoneRequest(
        @NotNull Integer version,
        @Size(min = 3, max = 200) String title,
        Instant dueAt,
        Boolean allowLate) {

    public UpdateMilestoneRequest {
        title = Text.clean(title);
    }
}
