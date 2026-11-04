package com.campushub.milestones;

import com.campushub.projects.Text;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Body of {@code POST /projects/{id}/milestones}. A null {@code allowLate} means false. */
public record CreateMilestoneRequest(
        @NotBlank @Size(min = 3, max = 200) String title,
        @NotNull Instant dueAt,
        Boolean allowLate) {

    public CreateMilestoneRequest {
        title = Text.clean(title);
    }
}
