package com.campushub.dashboard;

import com.campushub.projects.ProjectStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * A project with its task progress. {@code progressPercent} is done tasks over all tasks. It is null
 * when the project has no tasks, so an empty project never reads as complete.
 */
public record ProjectProgress(
        UUID id,
        String title,
        ProjectStatus status,
        long taskTotal,
        long taskDone,
        Integer progressPercent,
        Instant createdAt) {
}
