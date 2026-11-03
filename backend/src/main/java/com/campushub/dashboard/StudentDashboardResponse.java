package com.campushub.dashboard;

import java.util.List;

/**
 * Student overview (Phase 10.1). Task states follow STATE_MACHINES §3.1: pending means TODO, and
 * progressPercent is DONE over all tasks, or null when there are no tasks.
 */
public record StudentDashboardResponse(
        long projectsTotal,
        long projectsActive,
        long tasksTotal,
        long tasksPending,
        long tasksInProgress,
        long tasksInReview,
        long tasksBlocked,
        long tasksDone,
        Integer progressPercent,
        long submissionsTotal,
        long submissionsAwaitingReview,
        long skillsCount,
        long certificationsCount,
        List<ProjectProgress> recentProjects,
        List<UpcomingTask> upcomingTasks) {
}
