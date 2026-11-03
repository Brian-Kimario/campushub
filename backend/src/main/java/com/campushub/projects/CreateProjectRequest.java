package com.campushub.projects;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Body of {@code POST /projects}. Text is trimmed before validation runs. */
public record CreateProjectRequest(
        @NotBlank @Size(min = 3, max = 200) String title,
        @Size(max = 5000) String description,
        @Size(max = 120) String subject,
        LocalDate startDate,
        LocalDate dueDate) {

    public CreateProjectRequest {
        title = Text.clean(title);
        description = Text.clean(description);
        subject = Text.clean(subject);
    }
}
