package com.campushub.dashboard;

import com.campushub.tasks.TaskStatus;
import java.time.LocalDate;
import java.util.UUID;

/** An open task with a due date, shown on the student dashboard. */
public record UpcomingTask(UUID id, String title, UUID projectId, LocalDate dueDate, TaskStatus status) {
}
