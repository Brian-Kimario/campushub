package com.campushub.tasks;

import com.campushub.projects.Text;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/** Body of {@code POST /projects/{id}/tasks}. A null priority means MEDIUM. */
public record CreateTaskRequest(
        @NotBlank @Size(min = 3, max = 200) String title,
        @Size(max = 5000) String description,
        TaskPriority priority,
        LocalDate dueDate,
        UUID assigneeId) {

    public CreateTaskRequest {
        title = Text.clean(title);
        description = Text.clean(description);
    }
}
