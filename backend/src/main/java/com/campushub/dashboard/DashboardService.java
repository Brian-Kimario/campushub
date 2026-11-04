package com.campushub.dashboard;

import com.campushub.identity.Role;
import com.campushub.identity.security.AuthenticatedUser;
import com.campushub.projects.Project;
import com.campushub.projects.ProjectStatus;
import com.campushub.shared.errors.ApiException;
import com.campushub.shared.errors.ErrorCode;
import com.campushub.submissions.SubmissionStatus;
import com.campushub.tasks.TaskStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard use cases (Phase 10). A student sees their own projects. Faculty see only the projects they
 * supervise (AUTHORIZATION_MATRIX §3). Other roles are refused with FORBIDDEN_ROLE.
 */
@Service
public class DashboardService {

    private static final int RECENT_LIMIT = 5;
    private static final int UPCOMING_LIMIT = 5;
    private static final int ASSIGNED_LIMIT = 20;

    private final DashboardRepository dashboard;
    private final Clock clock;

    public DashboardService(DashboardRepository dashboard, Clock clock) {
        this.dashboard = dashboard;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public StudentDashboardResponse student(AuthenticatedUser caller) {
        requireRole(caller, Role.STUDENT);
        UUID owner = caller.userId();

        Map<TaskStatus, Long> tasksByStatus = new EnumMap<>(TaskStatus.class);
        Map<UUID, Long> tasksPerProject = new HashMap<>();
        Map<UUID, Long> donePerProject = new HashMap<>();
        for (TaskStatusCount row : dashboard.taskCountsForOwner(owner)) {
            tasksByStatus.merge(row.status(), row.total(), Long::sum);
            tasksPerProject.merge(row.projectId(), row.total(), Long::sum);
            if (row.status() == TaskStatus.DONE) {
                donePerProject.merge(row.projectId(), row.total(), Long::sum);
            }
        }
        long tasksTotal = tasksByStatus.values().stream().mapToLong(Long::longValue).sum();
        long tasksDone = tasksByStatus.getOrDefault(TaskStatus.DONE, 0L);

        long submissionsTotal = 0;
        long awaitingReview = 0;
        for (SubmissionStatusCount row : dashboard.submissionCountsForOwner(owner)) {
            submissionsTotal += row.total();
            if (isAwaitingReview(row.status())) {
                awaitingReview += row.total();
            }
        }

        List<ProjectProgress> recent = dashboard
                .findByOwnerIdOrderByCreatedAtDescIdAsc(owner, PageRequest.of(0, RECENT_LIMIT))
                .stream()
                .map(project -> progressOf(project, tasksPerProject, donePerProject))
                .toList();

        List<UpcomingTask> upcoming = dashboard.upcomingTasksForOwner(owner, LocalDate.now(clock),
                PageRequest.of(0, UPCOMING_LIMIT));

        return new StudentDashboardResponse(
                dashboard.countByOwnerId(owner),
                dashboard.countByOwnerIdAndStatus(owner, ProjectStatus.ACTIVE),
                tasksTotal,
                tasksByStatus.getOrDefault(TaskStatus.TODO, 0L),
                tasksByStatus.getOrDefault(TaskStatus.IN_PROGRESS, 0L),
                tasksByStatus.getOrDefault(TaskStatus.IN_REVIEW, 0L),
                tasksByStatus.getOrDefault(TaskStatus.BLOCKED, 0L),
                tasksDone,
                percent(tasksDone, tasksTotal),
                submissionsTotal,
                awaitingReview,
                dashboard.countSkills(owner),
                dashboard.countCertifications(owner),
                recent,
                upcoming);
    }

    @Transactional(readOnly = true)
    public FacultyDashboardResponse faculty(AuthenticatedUser caller) {
        requireRole(caller, Role.FACULTY);
        UUID supervisor = caller.userId();

        Map<UUID, Long> awaitingPerProject = new HashMap<>();
        long reviewed = 0;
        for (SubmissionStatusCount row : dashboard.submissionCountsForSupervisor(supervisor)) {
            if (isAwaitingReview(row.status())) {
                awaitingPerProject.merge(row.projectId(), row.total(), Long::sum);
            } else if (row.status().isDecided()) {
                reviewed += row.total();
            }
        }
        long awaiting = awaitingPerProject.values().stream().mapToLong(Long::longValue).sum();

        List<AssignedProject> projects = dashboard
                .findBySupervisorIdOrderByCreatedAtDescIdAsc(supervisor, PageRequest.of(0, ASSIGNED_LIMIT))
                .stream()
                .map(project -> new AssignedProject(project.getId(), project.getTitle(), project.getStatus(),
                        awaitingPerProject.getOrDefault(project.getId(), 0L)))
                .toList();

        return new FacultyDashboardResponse(
                dashboard.countBySupervisorId(supervisor),
                dashboard.countBySupervisorIdAndStatus(supervisor, ProjectStatus.ACTIVE),
                awaiting,
                reviewed,
                awaitingPerProject.values().stream().filter(count -> count > 0).count(),
                projects);
    }

    private static ProjectProgress progressOf(Project project, Map<UUID, Long> tasks, Map<UUID, Long> done) {
        long total = tasks.getOrDefault(project.getId(), 0L);
        long completed = done.getOrDefault(project.getId(), 0L);
        return new ProjectProgress(project.getId(), project.getTitle(), project.getStatus(), total, completed,
                percent(completed, total), project.getCreatedAt());
    }

    /** Null when there is nothing to measure, so an empty project is never shown as complete. */
    private static Integer percent(long done, long total) {
        return total == 0 ? null : (int) Math.round(100.0 * done / total);
    }

    private static boolean isAwaitingReview(SubmissionStatus status) {
        return status == SubmissionStatus.SUBMITTED || status == SubmissionStatus.UNDER_REVIEW;
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse admin(AuthenticatedUser caller) {
        requireRole(caller, Role.ADMIN);

        Map<Role, Long> usersByRole = new EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            usersByRole.put(role, 0L);
        }
        for (GroupCount<Role> row : dashboard.activeUsersByRole()) {
            usersByRole.put(row.key(), row.total());
        }

        Map<ProjectStatus, Long> projectsByStatus = new EnumMap<>(ProjectStatus.class);
        for (ProjectStatus status : ProjectStatus.values()) {
            projectsByStatus.put(status, 0L);
        }
        for (GroupCount<ProjectStatus> row : dashboard.projectsByStatus()) {
            projectsByStatus.put(row.key(), row.total());
        }

        return new AdminDashboardResponse(usersByRole, projectsByStatus);
    }

    private static void requireRole(AuthenticatedUser caller, Role required) {
        if (caller.role() != required) {
            throw new ApiException(ErrorCode.FORBIDDEN_ROLE, "Your role cannot perform this action.");
        }
    }
}
