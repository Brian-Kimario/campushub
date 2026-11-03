package com.campushub.submissions;

import com.campushub.identity.Role;
import com.campushub.identity.audit.AuditOutcome;
import com.campushub.identity.audit.AuditService;
import com.campushub.identity.security.AuthenticatedUser;
import com.campushub.projects.Project;
import com.campushub.projects.ProjectService;
import com.campushub.shared.errors.ApiException;
import com.campushub.shared.errors.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Submission use cases. Visibility follows the project. Only the project owner submits, and only
 * faculty review. Order: role (403 FORBIDDEN_ROLE), visibility (404), object condition (403
 * FORBIDDEN_STATE), archived (422), then state (409).
 */
@Service
public class SubmissionService {

    private static final String SUBMISSION = "submission";

    private final SubmissionRepository submissions;
    private final ProjectService projects;
    private final AuditService audit;
    private final Clock clock;

    public SubmissionService(SubmissionRepository submissions, ProjectService projects, AuditService audit, Clock clock) {
        this.submissions = submissions;
        this.projects = projects;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SubmissionResponse> list(AuthenticatedUser caller, UUID projectId) {
        projects.visibleProject(caller, projectId);
        return submissions.findByProjectIdOrderByRevisionNoAsc(projectId).stream()
                .map(SubmissionService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SubmissionResponse get(AuthenticatedUser caller, UUID id) {
        Submission submission = findSubmission(id);
        projectFor(caller, submission);
        return toResponse(submission);
    }

    /** Revision 1 on a new project, or the next revision after changes were requested. */
    @Transactional
    public SubmissionResponse create(AuthenticatedUser caller, UUID projectId, CreateSubmissionRequest body,
                                     HttpServletRequest request) {
        Project project = projects.visibleProject(caller, projectId);
        requireTeamWriter(caller, project);
        ProjectService.requireNotArchived(project);
        int revisionNo = nextRevisionNo(submissions.findFirstByProjectIdOrderByRevisionNoDesc(projectId));
        Submission submission = new Submission(UUID.randomUUID(), projectId, revisionNo, body.title(),
                body.description(), body.link(), caller.userId(), clock.instant());
        submissions.saveAndFlush(submission);
        audit.record("submission.created", caller.userId(), SUBMISSION, submission.getId().toString(),
                AuditOutcome.SUCCESS, request, Map.of());
        return toResponse(submission);
    }

    /** Faculty only. SUBMITTED → UNDER_REVIEW, and the reviewer is recorded. */
    @Transactional
    public SubmissionResponse startReview(AuthenticatedUser caller, UUID id, HttpServletRequest request) {
        requireRole(caller, Role.FACULTY);
        Submission submission = findSubmission(id);
        Project project = projectFor(caller, submission);
        ProjectService.requireNotArchived(project);
        if (submission.getStatus() != SubmissionStatus.SUBMITTED) {
            throw new ApiException(ErrorCode.INVALID_TRANSITION,
                    "A review can be started only for a submission that is waiting for review.");
        }
        submission.startReview(caller.userId());
        submissions.saveAndFlush(submission);
        audit.record("submission.review_started", caller.userId(), SUBMISSION, id.toString(),
                AuditOutcome.SUCCESS, request, Map.of());
        return toResponse(submission);
    }

    /**
     * Faculty only. Records ACCEPTED or CHANGES_REQUESTED. A decision that already exists is
     * REVIEW_EXISTS; a review that was never started is INVALID_TRANSITION; feedback is required for
     * CHANGES_REQUESTED.
     */
    @Transactional
    public SubmissionResponse decide(AuthenticatedUser caller, UUID id, ReviewSubmissionRequest body,
                                     HttpServletRequest request) {
        requireRole(caller, Role.FACULTY);
        Submission submission = findSubmission(id);
        Project project = projectFor(caller, submission);
        ProjectService.requireNotArchived(project);
        if (submission.getStatus().isDecided()) {
            throw new ApiException(ErrorCode.REVIEW_EXISTS, "A decision is already recorded for this submission.");
        }
        if (submission.getStatus() != SubmissionStatus.UNDER_REVIEW) {
            throw new ApiException(ErrorCode.INVALID_TRANSITION,
                    "The review must be started before a decision is recorded.");
        }
        if (body.decision() == ReviewDecision.CHANGES_REQUESTED && body.feedback() == null) {
            throw new ApiException(ErrorCode.FEEDBACK_REQUIRED, "Feedback is required when requesting changes.");
        }
        submission.decide(body.decision(), caller.userId(), body.feedback(), clock.instant());
        submissions.saveAndFlush(submission);
        String action = body.decision() == ReviewDecision.ACCEPTED ? "submission.accepted" : "submission.changes_requested";
        audit.record(action, caller.userId(), SUBMISSION, id.toString(), AuditOutcome.SUCCESS, request, Map.of());
        return toResponse(submission);
    }

    private Submission findSubmission(UUID id) {
        return submissions.findById(id).orElseThrow(SubmissionService::notFound);
    }

    /** The submission's project if the caller can see it. A hidden project looks exactly like a missing submission. */
    private Project projectFor(AuthenticatedUser caller, Submission submission) {
        return projects.findVisible(caller, submission.getProjectId()).orElseThrow(SubmissionService::notFound);
    }

    private static void requireRole(AuthenticatedUser caller, Role role) {
        if (caller.role() != role) {
            throw new ApiException(ErrorCode.FORBIDDEN_ROLE, "Your role cannot perform this action.");
        }
    }

    private void requireTeamWriter(AuthenticatedUser caller, Project project) {
        if (!projects.canWriteTeamWork(caller, project)) {
            throw new ApiException(ErrorCode.FORBIDDEN_STATE, "Only the project owner or a team member can submit work.");
        }
    }

    private static int nextRevisionNo(Optional<Submission> latest) {
        if (latest.isEmpty()) {
            return 1;
        }
        if (latest.get().getStatus() != SubmissionStatus.CHANGES_REQUESTED) {
            throw new ApiException(ErrorCode.INVALID_TRANSITION,
                    "A new revision can be created only after changes are requested.");
        }
        return latest.get().getRevisionNo() + 1;
    }

    private static SubmissionResponse toResponse(Submission submission) {
        return new SubmissionResponse(submission.getId(), submission.getProjectId(), submission.getRevisionNo(),
                submission.getTitle(), submission.getDescription(), submission.getLink(), submission.getStatus(),
                submission.getSubmittedBy(), submission.getSubmittedAt(), submission.getReviewedBy(),
                submission.getReviewedAt(), submission.getFeedback(), submission.getVersion());
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND, "No submission was found.");
    }
}
