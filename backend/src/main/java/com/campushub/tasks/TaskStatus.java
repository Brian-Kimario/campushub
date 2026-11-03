package com.campushub.tasks;

/** Task states (STATE_MACHINES §3.1). */
public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    IN_REVIEW,
    BLOCKED,
    DONE
}
