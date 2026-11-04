package com.campushub.milestones;

import com.campushub.identity.audit.AuditOutcome;
import com.campushub.identity.audit.AuditService;
import com.campushub.identity.security.AuthenticatedUser;
import com.campushub.projects.Project;
import com.campushub.projects.ProjectRepository;
import com.campushub.projects.ProjectService;
import com.campushub.shared.errors.ApiException;
import com.campushub.shared.errors.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Milestone use cases (API_CONTRACT §7, STATE_MACHINES §4, AUTHORIZATION_MATRIX §3.5). Visibility
 * follows the project: a caller who cannot see the project gets 404. The owner creates and edits
 * title and due date; the supervisor edits allowLate; either may close. Other visible callers,
 * including admins, get 403 FORBIDDEN_STATE. Order after that: archived, closed, field, version.
 */
@Service
public class MilestoneService {

    private static final String MILESTONE = "milestone";

    private final MilestoneRepository milestones;
    private final ProjectRepository projectRows;
    private final ProjectService projects;
    private final AuditService audit;
    private final Clock clock;

    public MilestoneService(MilestoneRepository milestones, ProjectRepository projectRows, ProjectService projects,
                            AuditService audit, Clock clock) {
        this.milestones = milestones;
        this.projectRows = projectRows;
        this.projects = projects;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MilestoneResponse> list(AuthenticatedUser caller, UUID projectId) {
        projects.visibleProject(caller, projectId);
        return milestones.findByProjectIdOrderBySequenceAsc(projectId).stream()
                .map(MilestoneResponse::from)
                .toList();
    }

    @Transactional
    public MilestoneResponse create(AuthenticatedUser caller, UUID projectId, CreateMilestoneRequest body,
                                    HttpServletRequest request) {
        Project visible = projects.visibleProject(caller, projectId);
        if (!visible.isOwnedBy(caller.userId())) {
            throw forbiddenState("Only the project owner can add milestones.");
        }
        // Lock the project row so that two creates cannot take the same sequence number.
        Project project = projectRows.lockById(projectId).orElseThrow(MilestoneService::notFound);
        ProjectService.requireNotArchived(project);

        Milestone milestone = new Milestone(UUID.randomUUID(), projectId, milestones.maxSequence(projectId) + 1,
                body.title(), body.dueAt(), Boolean.TRUE.equals(body.allowLate()), clock.instant());
        milestones.saveAndFlush(milestone);
        audit.record("milestone.created", caller.userId(), MILESTONE, milestone.getId().toString(),
                AuditOutcome.SUCCESS, request, Map.of());
        return MilestoneResponse.from(milestone);
    }

    @Transactional
    public MilestoneResponse update(AuthenticatedUser caller, UUID id, UpdateMilestoneRequest body,
                                    HttpServletRequest request) {
        Milestone milestone = findMilestone(id);
        Project project = projectFor(caller, milestone);
        boolean owner = project.isOwnedBy(caller.userId());
        boolean supervisor = project.isSupervisedBy(caller.userId());
        if (!owner && !supervisor) {
            throw forbiddenState("Only the project owner or its supervisor can edit milestones.");
        }
        ProjectService.requireNotArchived(project);
        if (!milestone.isOpen()) {
            throw new ApiException(ErrorCode.INVALID_TRANSITION, "A closed milestone cannot be edited.");
        }
        if (owner && body.allowLate() != null) {
            throw new ApiException(ErrorCode.FIELD_NOT_EDITABLE, "Only the supervisor can change allowLate.");
        }
        if (!owner && (body.title() != null || body.dueAt() != null)) {
            throw new ApiException(ErrorCode.FIELD_NOT_EDITABLE, "Only the owner can change the title or due date.");
        }
        requireVersion(milestone, body.version());

        if (owner) {
            milestone.updateOwnerFields(body.title(), body.dueAt(), clock.instant());
        } else {
            milestone.updateAllowLate(body.allowLate(), clock.instant());
        }
        milestones.saveAndFlush(milestone);
        audit.record("milestone.updated", caller.userId(), MILESTONE, id.toString(), AuditOutcome.SUCCESS,
                request, Map.of());
        return MilestoneResponse.from(milestone);
    }

    @Transactional
    public MilestoneResponse close(AuthenticatedUser caller, UUID id, HttpServletRequest request) {
        Milestone milestone = findMilestone(id);
        Project project = projectFor(caller, milestone);
        if (!project.isOwnedBy(caller.userId()) && !project.isSupervisedBy(caller.userId())) {
            throw forbiddenState("Only the project owner or its supervisor can close a milestone.");
        }
        ProjectService.requireNotArchived(project);
        if (!milestone.isOpen()) {
            throw new ApiException(ErrorCode.INVALID_TRANSITION, "The milestone is already closed.");
        }
        milestone.close(clock.instant());
        milestones.saveAndFlush(milestone);
        audit.record("milestone.closed", caller.userId(), MILESTONE, id.toString(), AuditOutcome.SUCCESS,
                request, Map.of());
        return MilestoneResponse.from(milestone);
    }

    private Milestone findMilestone(UUID id) {
        return milestones.findById(id).orElseThrow(MilestoneService::notFound);
    }

    /** The milestone's project if the caller can see it. A hidden project looks exactly like a missing milestone. */
    private Project projectFor(AuthenticatedUser caller, Milestone milestone) {
        return projects.findVisible(caller, milestone.getProjectId()).orElseThrow(MilestoneService::notFound);
    }

    private static void requireVersion(Milestone milestone, Integer expectedVersion) {
        if (!Objects.equals(milestone.getVersion(), expectedVersion)) {
            throw new ApiException(ErrorCode.STALE_VERSION,
                    "The milestone changed since it was read. Reload and try again.");
        }
    }

    private static ApiException forbiddenState(String message) {
        return new ApiException(ErrorCode.FORBIDDEN_STATE, message);
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND, "No milestone was found.");
    }
}
