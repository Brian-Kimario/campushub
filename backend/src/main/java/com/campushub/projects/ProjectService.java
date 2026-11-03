package com.campushub.projects;

import com.campushub.identity.Role;
import com.campushub.identity.UserAccount;
import com.campushub.identity.UserAccountRepository;
import com.campushub.identity.UserStatus;
import com.campushub.identity.audit.AuditOutcome;
import com.campushub.identity.audit.AuditService;
import com.campushub.identity.security.AuthenticatedUser;
import com.campushub.shared.errors.ApiException;
import com.campushub.shared.errors.ErrorCode;
import com.campushub.shared.web.PageResponse;
import com.campushub.shared.web.Pagination;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Project use cases with the authorization order from AUTHORIZATION_MATRIX §1: role first, then
 * visibility (404 for anything the caller cannot see), then object conditions (403 FORBIDDEN_STATE),
 * then state and version checks.
 */
@Service
public class ProjectService {

    private static final List<String> SORT = List.of("createdAt,desc", "id,desc");
    private static final String PROJECT = "project";
    private static final String PROJECT_CREATED = "project.created";
    private static final String PROJECT_UPDATED = "project.updated";
    private static final String PROJECT_SUPERVISOR_ASSIGNED = "project.supervisor_assigned";
    private static final String PROJECT_ARCHIVED = "project.archived";

    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final UserAccountRepository users;
    private final AuditService audit;
    private final Clock clock;

    public ProjectService(ProjectRepository projects, ProjectMemberRepository members, UserAccountRepository users,
                          AuditService audit, Clock clock) {
        this.projects = projects;
        this.members = members;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Students see the projects they own or belong to as a team member, faculty the projects they
     * supervise, and admins all of them.
     */
    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> list(AuthenticatedUser caller, ProjectStatus status, int page, int size) {
        UUID studentId = caller.role() == Role.STUDENT ? caller.userId() : null;
        UUID supervisorId = caller.role() == Role.FACULTY ? caller.userId() : null;
        Pageable pageable = Pagination.request(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<Project> found = projects.search(studentId, supervisorId, status, pageable);
        Map<UUID, UserAccount> people = peopleFor(found.getContent());
        return PageResponse.of(found.map(project -> toResponse(project, people)), SORT);
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(AuthenticatedUser caller, UUID id) {
        Project project = visibleProject(caller, id);
        return toResponse(project, peopleFor(List.of(project)));
    }

    @Transactional
    public ProjectResponse create(AuthenticatedUser caller, CreateProjectRequest body, HttpServletRequest request) {
        requireRole(caller, Role.STUDENT);
        checkDates(body.startDate(), body.dueDate());
        Project project = new Project(UUID.randomUUID(), caller.userId(), body.title(), body.description(),
                body.subject(), body.startDate(), body.dueDate(), clock.instant());
        projects.save(project);
        audit.record(PROJECT_CREATED, caller.userId(), PROJECT, project.getId().toString(),
                AuditOutcome.SUCCESS, request, Map.of());
        return toResponse(project, peopleFor(List.of(project)));
    }

    /** Only the owner edits details. Other visible callers get 403 FORBIDDEN_STATE; invisible ones get 404. */
    @Transactional
    public ProjectResponse update(AuthenticatedUser caller, UUID id, UpdateProjectRequest body,
                                  HttpServletRequest request) {
        Project project = visibleProject(caller, id);
        if (!project.isOwnedBy(caller.userId())) {
            throw new ApiException(ErrorCode.FORBIDDEN_STATE, "Only the project owner can change these details.");
        }
        requireWritable(project, body.version());
        checkDates(body.startDate(), body.dueDate());
        project.updateDetails(body.title(), body.description(), body.subject(), body.startDate(), body.dueDate(),
                clock.instant());
        projects.saveAndFlush(project);
        audit.record(PROJECT_UPDATED, caller.userId(), PROJECT, id.toString(), AuditOutcome.SUCCESS, request, Map.of());
        return toResponse(project, peopleFor(List.of(project)));
    }

    /** Admin only. The supervisor must be an active faculty account (422 SUPERVISOR_NOT_ELIGIBLE otherwise). */
    @Transactional
    public ProjectResponse assignSupervisor(AuthenticatedUser caller, UUID id, AssignSupervisorRequest body,
                                            HttpServletRequest request) {
        requireRole(caller, Role.ADMIN);
        Project project = projects.findById(id).orElseThrow(ProjectService::notFound);
        requireWritable(project, body.version());
        UserAccount supervisor = users.findById(body.supervisorId())
                .filter(account -> account.getRole() == Role.FACULTY && account.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.SUPERVISOR_NOT_ELIGIBLE,
                        "The supervisor must be an active faculty account."));
        project.assignSupervisor(supervisor.getId(), clock.instant());
        projects.saveAndFlush(project);
        audit.record(PROJECT_SUPERVISOR_ASSIGNED, caller.userId(), PROJECT, id.toString(),
                AuditOutcome.SUCCESS, request, Map.of());
        return toResponse(project, peopleFor(List.of(project)));
    }

    /** Admin only. An archived project is read-only from then on. */
    @Transactional
    public ProjectResponse archive(AuthenticatedUser caller, UUID id, ArchiveProjectRequest body,
                                   HttpServletRequest request) {
        requireRole(caller, Role.ADMIN);
        Project project = projects.findById(id).orElseThrow(ProjectService::notFound);
        requireWritable(project, body.version());
        project.archive(clock.instant());
        projects.saveAndFlush(project);
        audit.record(PROJECT_ARCHIVED, caller.userId(), PROJECT, id.toString(), AuditOutcome.SUCCESS, request, Map.of());
        return toResponse(project, peopleFor(List.of(project)));
    }

    /** The project if the caller can see it, otherwise 404. Also used by the task slice. */
    public Project visibleProject(AuthenticatedUser caller, UUID id) {
        return findVisible(caller, id).orElseThrow(ProjectService::notFound);
    }

    /** Empty when the project is missing or hidden, so callers cannot tell the two apart. */
    public Optional<Project> findVisible(AuthenticatedUser caller, UUID id) {
        return projects.findById(id).filter(project -> canSee(caller, project));
    }

    /** Owner or team member: may create and change tasks and submissions. Supervisors and admins may not. */
    public boolean canWriteTeamWork(AuthenticatedUser caller, Project project) {
        return project.isOwnedBy(caller.userId()) || isMember(caller, project);
    }

    /** Whether the caller may see the project. Calendar and list views filter with this too. */
    public boolean canSee(AuthenticatedUser caller, Project project) {
        return switch (caller.role()) {
            case ADMIN -> true;
            case STUDENT -> project.isOwnedBy(caller.userId()) || isMember(caller, project);
            case FACULTY -> project.isSupervisedBy(caller.userId());
        };
    }

    private boolean isMember(AuthenticatedUser caller, Project project) {
        return caller.role() == Role.STUDENT
                && members.existsByProjectIdAndUserId(project.getId(), caller.userId());
    }

    private static void requireRole(AuthenticatedUser caller, Role role) {
        if (caller.role() != role) {
            throw new ApiException(ErrorCode.FORBIDDEN_ROLE, "Your role cannot perform this action.");
        }
    }

    /** Archived projects refuse all writes; a stale version is refused after that (422 before 409). */
    private static void requireWritable(Project project, Integer expectedVersion) {
        if (project.isArchived()) {
            throw new ApiException(ErrorCode.PROJECT_ARCHIVED, "The project is archived and read-only.");
        }
        if (!Objects.equals(project.getVersion(), expectedVersion)) {
            throw new ApiException(ErrorCode.STALE_VERSION, "The project changed since it was read. Reload and try again.");
        }
    }

    /** Refuses writes to an archived project (422 PROJECT_ARCHIVED). */
    public static void requireNotArchived(Project project) {
        if (project.isArchived()) {
            throw new ApiException(ErrorCode.PROJECT_ARCHIVED, "The project is archived and read-only.");
        }
    }

    private static void checkDates(LocalDate startDate, LocalDate dueDate) {
        if (startDate != null && dueDate != null && dueDate.isBefore(startDate)) {
            throw ApiException.withErrors(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.",
                    List.of(Map.of("field", "dueDate", "reason", "must be on or after startDate")));
        }
    }

    private Map<UUID, UserAccount> peopleFor(Collection<Project> items) {
        Set<UUID> ids = items.stream()
                .flatMap(project -> Stream.of(project.getOwnerId(), project.getSupervisorId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return users.findAllById(ids).stream()
                .collect(Collectors.toMap(UserAccount::getId, account -> account));
    }

    private static ProjectResponse toResponse(Project project, Map<UUID, UserAccount> people) {
        return new ProjectResponse(
                project.getId(),
                project.getTitle(),
                project.getDescription(),
                project.getSubject(),
                project.getStatus(),
                project.getStartDate(),
                project.getDueDate(),
                person(people, project.getOwnerId()),
                person(people, project.getSupervisorId()),
                project.getVersion(),
                project.getCreatedAt(),
                project.getUpdatedAt());
    }

    private static PersonSummary person(Map<UUID, UserAccount> people, UUID id) {
        UserAccount account = id == null ? null : people.get(id);
        return account == null ? null : new PersonSummary(account.getId(), account.getDisplayName());
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND, "No project was found.");
    }
}
