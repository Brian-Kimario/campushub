package com.campushub.projects;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Body of {@code PUT /projects/{id}/supervisor}. */
public record AssignSupervisorRequest(@NotNull UUID supervisorId, @NotNull Integer version) {
}
