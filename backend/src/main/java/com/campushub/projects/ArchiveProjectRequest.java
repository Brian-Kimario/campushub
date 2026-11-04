package com.campushub.projects;

import jakarta.validation.constraints.NotNull;

/** Body of {@code POST /projects/{id}/archive}. */
public record ArchiveProjectRequest(@NotNull Integer version) {
}
