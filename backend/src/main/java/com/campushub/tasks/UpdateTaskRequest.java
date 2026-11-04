package com.campushub.tasks;

import com.campushub.projects.Text;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Body of {@code PATCH /tasks/{id}}. Full-field semantics for the editable fields, so a null
 * {@code assigneeId} unassigns the task. {@code status} is accepted only so that sending it can be
 * refused with 400 FIELD_NOT_EDITABLE (FR-TASK-03).
 */
public record UpdateTaskRequest(
        @NotNull Integer version,
        @NotBlank @Size(min = 3, max = 200) String title,
        @Size(max = 5000) String description,
        TaskPriority priority,
        LocalDate dueDate,
        UUID assigneeId,
        String status) {

    public UpdateTaskRequest {
        title = Text.clean(title);
        description = Text.clean(description);
    }
}
