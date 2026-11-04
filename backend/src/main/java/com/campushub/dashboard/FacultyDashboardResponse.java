package com.campushub.dashboard;

import java.util.List;

/**
 * Faculty supervision overview (Phase 10.2). Only projects the caller supervises are counted. Awaiting
 * review means SUBMITTED or UNDER_REVIEW. Reviewed means CHANGES_REQUESTED or ACCEPTED.
 */
public record FacultyDashboardResponse(
        long assignedProjects,
        long activeProjects,
        long submissionsAwaitingReview,
        long submissionsReviewed,
        long projectsNeedingAttention,
        List<AssignedProject> projects) {
}
