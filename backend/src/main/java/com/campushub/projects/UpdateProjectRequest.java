package com.campushub.projects;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Body of {@code PATCH /projects/{id}}. The editable fields are sent in full: a null optional field
 * clears it. {@code version} is the version the client last read (API_CONTRACT §1).
 */
public record UpdateProjectRequest(
        @NotNull Integer version,
        @NotBlank @Size(min = 3, max = 200) String title,
        @Size(max = 5000) String description,
        @Size(max = 120) String subject,
        LocalDate startDate,
        LocalDate dueDate) {

    public UpdateProjectRequest {
        title = Text.clean(title);
        description = Text.clean(description);
        subject = Text.clean(subject);
    }
}
