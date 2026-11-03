package com.campushub.dashboard;

import com.campushub.identity.Role;
import com.campushub.projects.ProjectStatus;
import java.util.Map;

/**
 * Administrator overview (FR-DASH-03). Every role and every project status appears as a key, with zero where
 * nothing matches, so the shape is stable. Only ACTIVE accounts count as active users. Term summaries are not
 * included: projects have no term yet (FR-CAT-01, FR-PROJ-01).
 */
public record AdminDashboardResponse(
        Map<Role, Long> activeUsersByRole,
        Map<ProjectStatus, Long> projectsByStatus) {
}
