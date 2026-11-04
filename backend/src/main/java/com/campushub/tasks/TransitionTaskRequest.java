package com.campushub.tasks;

import com.campushub.projects.Text;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /tasks/{id}/transitions}. {@code blockedReason} is required when {@code to} is BLOCKED. */
public record TransitionTaskRequest(
        @NotNull TaskStatus to,
        @NotNull Integer version,
        @Size(min = 3, max = 500) String blockedReason) {

    public TransitionTaskRequest {
        blockedReason = Text.clean(blockedReason);
    }
}
