package com.campushub.tasks;

import com.campushub.identity.audit.AuditOutcome;
import com.campushub.identity.audit.AuditService;
import com.campushub.identity.security.AuthenticatedUser;
import com.campushub.projects.Project;
import com.campushub.projects.ProjectService;
import com.campushub.shared.errors.ApiException;
import com.campushub.shared.errors.ErrorCode;
import com.campushub.shared.web.PageResponse;
import com.campushub.shared.web.Pagination;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Task use cases. Visibility follows the project: a caller who cannot see the project gets 404 for
 * its tasks too. Only the project owner writes; a visible non-owner gets 403 FORBIDDEN_STATE
 * (AUTHORIZATION_MATRIX §1). Order after that: archived, then version, then transition legality.
 */
@Service
public class TaskService {

    private static final List<String> SORT = List.of("createdAt,asc", "id,asc");
    private static final String TASK = "task";

    private final TaskRepository tasks;
    private final ProjectService projects;
    private final AuditService audit;
    private final Clock clock;

    public TaskService(TaskRepository tasks, ProjectService projects, AuditService audit, Clock clock) {
        this.tasks = tasks;
        this.projects = projects;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> list(AuthenticatedUser caller, UUID projectId, TaskStatus status,
                                           int page, int size) {
        projects.visibleProject(caller, projectId);
        Pageable pageable = Pagination.request(page, size, Sort.by(Sort.Direction.ASC, "createdAt", "id"));
        return PageResponse.of(tasks.search(projectId, status, pageable).map(TaskService::toResponse), SORT);
    }

    @Transactional(readOnly = true)
    public TaskResponse get(AuthenticatedUser caller, UUID id) {
        Task task = findTask(id);
        projectFor(caller, task);
        return toResponse(task);
    }

    @Transactional
    public TaskResponse create(AuthenticatedUser caller, UUID projectId, CreateTaskRequest body,
                               HttpServletRequest request) {
        Project project = projects.visibleProject(caller, projectId);
        requireOwner(caller, project);
        ProjectService.requireNotArchived(project);
        TaskPriority priority = body.priority() == null ? TaskPriority.MEDIUM : body.priority();
        Task task = new Task(UUID.randomUUID(), projectId, caller.userId(), body.title(), body.description(),
                priority, body.dueDate(), clock.instant());
        tasks.save(task);
        audit.record("task.created", caller.userId(), TASK, task.getId().toString(),
                AuditOutcome.SUCCESS, request, Map.of());
        return toResponse(task);
    }

    @Transactional
    public TaskResponse update(AuthenticatedUser caller, UUID id, UpdateTaskRequest body, HttpServletRequest request) {
        Task task = findTask(id);
        Project project = projectFor(caller, task);
        requireOwner(caller, project);
        ProjectService.requireNotArchived(project);
        if (body.status() != null) {
            throw new ApiException(ErrorCode.FIELD_NOT_EDITABLE,
                    "Status changes go through POST /tasks/{id}/transitions.");
        }
        requireVersion(task, body.version());
        TaskPriority priority = body.priority() == null ? TaskPriority.MEDIUM : body.priority();
        task.updateDetails(body.title(), body.description(), priority, body.dueDate(), clock.instant());
        tasks.saveAndFlush(task);
        audit.record("task.updated", caller.userId(), TASK, id.toString(), AuditOutcome.SUCCESS, request, Map.of());
        return toResponse(task);
    }

    @Transactional
    public TaskResponse transition(AuthenticatedUser caller, UUID id, TransitionTaskRequest body,
                                   HttpServletRequest request) {
        Task task = findTask(id);
        Project project = projectFor(caller, task);
        requireOwner(caller, project);
        ProjectService.requireNotArchived(project);
        requireVersion(task, body.version());

        TaskStatus from = task.getStatus();
        TaskStatus to = body.to();
        String action = TaskTransitions.auditAction(from, to).orElseThrow(() -> new ApiException(
                ErrorCode.INVALID_TRANSITION, "The task cannot move from " + from + " to " + to + "."));
        if (to == TaskStatus.BLOCKED && body.blockedReason() == null) {
            throw ApiException.withErrors(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.",
                    List.of(Map.of("field", "blockedReason", "reason", "required when the status is BLOCKED")));
        }
        task.moveTo(to, body.blockedReason(), clock.instant());
        tasks.saveAndFlush(task);
        audit.record(action, caller.userId(), TASK, id.toString(), AuditOutcome.SUCCESS, request, Map.of());
        return toResponse(task);
    }

    private Task findTask(UUID id) {
        return tasks.findById(id).orElseThrow(TaskService::notFound);
    }

    /** The task's project if the caller can see it. A hidden project looks exactly like a missing task. */
    private Project projectFor(AuthenticatedUser caller, Task task) {
        return projects.findVisible(caller, task.getProjectId()).orElseThrow(TaskService::notFound);
    }

    private static void requireOwner(AuthenticatedUser caller, Project project) {
        if (!project.isOwnedBy(caller.userId())) {
            throw new ApiException(ErrorCode.FORBIDDEN_STATE, "Only the project owner can change its tasks.");
        }
    }

    private static void requireVersion(Task task, Integer expectedVersion) {
        if (!Objects.equals(task.getVersion(), expectedVersion)) {
            throw new ApiException(ErrorCode.STALE_VERSION, "The task changed since it was read. Reload and try again.");
        }
    }

    private static TaskResponse toResponse(Task task) {
        return new TaskResponse(task.getId(), task.getProjectId(), task.getTitle(), task.getDescription(),
                task.getStatus(), task.getPriority(), task.getBlockedReason(), task.getDueDate(),
                task.getCompletedAt(), task.getCreatedBy(), task.getVersion(), task.getCreatedAt(),
                task.getUpdatedAt());
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND, "No task was found.");
    }
}
