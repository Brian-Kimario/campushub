package com.campushub.dashboard;

import com.campushub.tasks.TaskStatus;
import java.util.UUID;

/** One row of a grouped task count, used by the dashboard queries. */
public record TaskStatusCount(UUID projectId, TaskStatus status, long total) {
}
