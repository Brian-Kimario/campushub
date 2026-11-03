package com.campushub.dashboard;

import com.campushub.projects.ProjectStatus;
import java.util.UUID;

/** A project the faculty member supervises, with the number of revisions waiting for a decision. */
public record AssignedProject(UUID id, String title, ProjectStatus status, long awaitingReview) {
}
