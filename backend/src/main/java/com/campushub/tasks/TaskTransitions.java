package com.campushub.tasks;

import java.util.Map;
import java.util.Optional;

/**
 * The ten legal task transitions (STATE_MACHINES §3.2) with their audit action. Any pair not in the
 * map is refused with 409 INVALID_TRANSITION, including same-state requests. The MVP has no assignee,
 * so the project owner performs every transition (see IMPLEMENTATION_LOG §11).
 */
final class TaskTransitions {

    private static final Map<String, String> AUDIT_ACTIONS = Map.ofEntries(
            Map.entry("TODO>IN_PROGRESS", "task.started"),
            Map.entry("TODO>BLOCKED", "task.blocked"),
            Map.entry("IN_PROGRESS>IN_REVIEW", "task.review_requested"),
            Map.entry("IN_PROGRESS>BLOCKED", "task.blocked"),
            Map.entry("IN_PROGRESS>TODO", "task.reset"),
            Map.entry("IN_REVIEW>DONE", "task.completed"),
            Map.entry("IN_REVIEW>IN_PROGRESS", "task.returned"),
            Map.entry("BLOCKED>IN_PROGRESS", "task.unblocked"),
            Map.entry("BLOCKED>TODO", "task.unblocked"),
            Map.entry("DONE>IN_PROGRESS", "task.reopened"));

    private TaskTransitions() {
    }

    static Optional<String> auditAction(TaskStatus from, TaskStatus to) {
        return Optional.ofNullable(AUDIT_ACTIONS.get(from + ">" + to));
    }
}
