package com.campushub.projects;

import com.campushub.identity.Role;
import com.campushub.identity.UserAccount;
import com.campushub.identity.UserAccountRepository;
import com.campushub.identity.UserStatus;
import com.campushub.identity.audit.AuditOutcome;
import com.campushub.identity.audit.AuditService;
import com.campushub.identity.security.AuthenticatedUser;
import com.campushub.projects.MembershipResponses.InvitationResponse;
import com.campushub.projects.MembershipResponses.MemberResponse;
import com.campushub.shared.errors.ApiException;
import com.campushub.shared.errors.ErrorCode;
import com.campushub.shared.web.PageResponse;
import com.campushub.shared.web.Pagination;
import com.campushub.tasks.TaskRepository;
import com.campushub.tasks.TaskStatus;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Team membership and invitations (STATE_MACHINES §6). The owner invites and removes members; the
 * invitee accepts or declines. A team holds at most five people including the owner. Invitations
 * grant nothing until accepted, and removing a member revokes access at once because visibility
 * reads the membership row on every request.
 */
@Service
public class MembershipService {

    /** Team size limit including the owner (OD-3). */
    static final int TEAM_LIMIT = 5;
    static final Duration INVITATION_LIFETIME = Duration.ofDays(14);

    private static final List<String> SORT = List.of("createdAt,desc", "id,asc");
    private static final String PROJECT = "project";
    private static final String INVITATION_CREATED = "project.invitation_created";
    private static final String INVITATION_ACCEPTED = "project.invitation_accepted";
    private static final String INVITATION_DECLINED = "project.invitation_declined";
    private static final String MEMBER_REMOVED = "project.member_removed";

    private final ProjectService projectService;
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final ProjectInvitationRepository invitations;
    private final UserAccountRepository users;
    private final TaskRepository tasks;
    private final AuditService audit;
    private final Clock clock;

    public MembershipService(ProjectService projectService, ProjectRepository projects,
                             ProjectMemberRepository members, ProjectInvitationRepository invitations,
                             UserAccountRepository users, TaskRepository tasks, AuditService audit, Clock clock) {
        this.projectService = projectService;
        this.projects = projects;
        this.members = members;
        this.invitations = invitations;
        this.users = users;
        this.tasks = tasks;
        this.audit = audit;
        this.clock = clock;
    }

    /** The owner first, then members in the order they joined. Visible to anyone who can see the project. */
    @Transactional(readOnly = true)
    public List<MemberResponse> members(AuthenticatedUser caller, UUID projectId) {
        Project project = projectService.visibleProject(caller, projectId);
        List<UUID> memberIds = members.findByProjectIdOrderByJoinedAtAscIdAsc(projectId).stream()
                .map(ProjectMember::getUserId)
                .toList();
        Map<UUID, UserAccount> people = peopleById(memberIds, project.getOwnerId());
        List<MemberResponse> result = new ArrayList<>();
        result.add(new MemberResponse(project.getOwnerId(), displayName(people, project.getOwnerId()), "OWNER",
                project.getCreatedAt()));
        members.findByProjectIdOrderByJoinedAtAscIdAsc(projectId).forEach(member ->
                result.add(new MemberResponse(member.getUserId(), displayName(people, member.getUserId()),
                        member.getRole(), member.getJoinedAt())));
        return result;
    }

    /** Owner only. Refuses removing the owner and leaves the team with at least the owner. */
    @Transactional
    public void removeMember(AuthenticatedUser caller, UUID projectId, UUID userId, HttpServletRequest request) {
        Project project = requireOwnerOf(caller, projectId);
        if (project.isOwnedBy(userId)) {
            throw new ApiException(ErrorCode.PRECONDITION_NOT_MET, "The project owner cannot be removed.");
        }
        Project locked = lockForMembership(projectId);
        if (!members.existsByProjectIdAndUserId(locked.getId(), userId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "No team member was found.");
        }
        members.deleteByProjectIdAndUserId(locked.getId(), userId);
        tasks.unassignOpenTasks(locked.getId(), userId, TaskStatus.DONE, clock.instant());
        audit.record(MEMBER_REMOVED, caller.userId(), PROJECT, projectId.toString(), AuditOutcome.SUCCESS,
                request, Map.of());
    }

    /** Owner only. The invitee must be an active student who is not already on the team. */
    @Transactional
    public InvitationResponse invite(AuthenticatedUser caller, UUID projectId, InviteRequest body,
                                     HttpServletRequest request) {
        Project project = requireOwnerOf(caller, projectId);
        Project locked = lockForMembership(projectId);
        Instant now = clock.instant();
        if (teamSize(locked) >= TEAM_LIMIT) {
            throw new ApiException(ErrorCode.TEAM_FULL, "The team already has five members.");
        }
        UserAccount invitee = users.findByEmail(normalize(body.inviteeEmail()))
                .filter(account -> account.getRole() == Role.STUDENT && account.getStatus() == UserStatus.ACTIVE)
                .filter(account -> !locked.isOwnedBy(account.getId()))
                .orElseThrow(() -> ApiException.withErrors(ErrorCode.VALIDATION_FAILED,
                        "One or more fields are invalid.",
                        List.of(Map.of("field", "inviteeEmail", "reason", "This person cannot be invited."))));
        expireStalePending(locked.getId(), invitee.getId(), now);
        if (members.existsByProjectIdAndUserId(locked.getId(), invitee.getId())
                || invitations.existsByProjectIdAndInviteeIdAndStatus(locked.getId(), invitee.getId(),
                        InvitationStatus.PENDING)) {
            throw new ApiException(ErrorCode.DUPLICATE, "This person is already on the team or has a pending invitation.");
        }
        ProjectInvitation invitation = new ProjectInvitation(UUID.randomUUID(), locked.getId(), invitee.getId(),
                caller.userId(), now, now.plus(INVITATION_LIFETIME));
        invitations.saveAndFlush(invitation);
        audit.record(INVITATION_CREATED, caller.userId(), PROJECT, projectId.toString(), AuditOutcome.SUCCESS,
                request, Map.of());
        return toResponse(invitation, Map.of(invitee.getId(), invitee,
                caller.userId(), users.findById(caller.userId()).orElse(null)), project.getTitle(), now);
    }

    /** Owner, administrator. Visible to them only, so a member cannot list who else was invited. */
    @Transactional(readOnly = true)
    public PageResponse<InvitationResponse> invitationsForProject(AuthenticatedUser caller, UUID projectId,
                                                                  int page, int size) {
        Project project = projectService.visibleProject(caller, projectId);
        if (!(project.isOwnedBy(caller.userId()) || caller.role() == Role.ADMIN)) {
            throw new ApiException(ErrorCode.FORBIDDEN_STATE, "Only the project owner or an administrator can view invitations.");
        }
        Page<ProjectInvitation> found = invitations.findByProjectIdOrderByCreatedAtDescIdAsc(projectId,
                Pagination.request(page, size, Sort.unsorted()));
        return PageResponse.of(pageOf(found, project.getTitle(), clock.instant()), SORT);
    }

    /** Invitations addressed to the caller, newest first. Students only. */
    @Transactional(readOnly = true)
    public PageResponse<InvitationResponse> myInvitations(AuthenticatedUser caller, int page, int size) {
        requireStudent(caller);
        Page<ProjectInvitation> found = invitations.findByInviteeIdOrderByCreatedAtDescIdAsc(caller.userId(),
                Pagination.request(page, size, Sort.unsorted()));
        Instant now = clock.instant();
        Map<UUID, String> titles = projectTitles(found.getContent());
        return PageResponse.of(found.map(invitation -> toResponse(invitation, peopleFor(invitation), titles.get(invitation.getProjectId()), now)), SORT);
    }

    /** Invitee only. Creates the membership in the same transaction, under the project lock. */
    @Transactional
    public InvitationResponse accept(AuthenticatedUser caller, UUID id, HttpServletRequest request) {
        ProjectInvitation invitation = findPendingForInvitee(caller, id);
        Project locked = lockForMembership(invitation.getProjectId());
        Instant now = clock.instant();
        if (teamSize(locked) >= TEAM_LIMIT) {
            throw new ApiException(ErrorCode.TEAM_FULL, "The team already has five members.");
        }
        if (members.existsByProjectIdAndUserId(locked.getId(), caller.userId())) {
            throw new ApiException(ErrorCode.DUPLICATE, "You are already on this team.");
        }
        members.saveAndFlush(new ProjectMember(UUID.randomUUID(), locked.getId(), caller.userId(), now));
        invitation.accept(now);
        invitations.saveAndFlush(invitation);
        audit.record(INVITATION_ACCEPTED, caller.userId(), PROJECT, locked.getId().toString(),
                AuditOutcome.SUCCESS, request, Map.of());
        return toResponse(invitation, peopleFor(invitation), locked.getTitle(), now);
    }

    /** Invitee only. Declining grants nothing and does not change the team. */
    @Transactional
    public InvitationResponse decline(AuthenticatedUser caller, UUID id, HttpServletRequest request) {
        ProjectInvitation invitation = findPendingForInvitee(caller, id);
        Instant now = clock.instant();
        invitation.decline(now);
        invitations.saveAndFlush(invitation);
        Project project = projects.findById(invitation.getProjectId()).orElseThrow(MembershipService::notFound);
        audit.record(INVITATION_DECLINED, caller.userId(), PROJECT, project.getId().toString(),
                AuditOutcome.SUCCESS, request, Map.of());
        return toResponse(invitation, peopleFor(invitation), project.getTitle(), now);
    }

    /** The project if the caller owns it; anything else is 404 or 403 as in the rest of the project API. */
    private Project requireOwnerOf(AuthenticatedUser caller, UUID projectId) {
        Project project = projectService.visibleProject(caller, projectId);
        if (!project.isOwnedBy(caller.userId())) {
            throw new ApiException(ErrorCode.FORBIDDEN_STATE, "Only the project owner can manage the team.");
        }
        return project;
    }

    /** Locks the project row and refuses membership changes on archived projects (422). */
    private Project lockForMembership(UUID projectId) {
        Project locked = projects.lockById(projectId).orElseThrow(MembershipService::notFound);
        if (locked.isArchived()) {
            throw new ApiException(ErrorCode.PROJECT_NOT_OPEN_FOR_MEMBERSHIP, "The project is archived and its team is closed.");
        }
        return locked;
    }

    private ProjectInvitation findPendingForInvitee(AuthenticatedUser caller, UUID id) {
        requireStudent(caller);
        ProjectInvitation invitation = invitations.findByIdAndInviteeId(id, caller.userId())
                .orElseThrow(MembershipService::invitationNotFound);
        InvitationStatus effective = invitation.effectiveStatus(clock.instant());
        if (effective == InvitationStatus.EXPIRED) {
            throw new ApiException(ErrorCode.INVALID_TRANSITION, "This invitation has expired.");
        }
        if (effective != InvitationStatus.PENDING) {
            throw new ApiException(ErrorCode.INVALID_TRANSITION, "This invitation has already been answered.");
        }
        return invitation;
    }

    /**
     * Expiry is derived on read, but no hourly job stores it yet (see CHANGELOG E.4). A stored PENDING row
     * past its deadline would otherwise block a fresh invitation through the duplicate check and the
     * pending_key unique index, so it is stored as EXPIRED here, under the project lock.
     */
    private void expireStalePending(UUID projectId, UUID inviteeId, Instant now) {
        invitations.findByProjectIdAndInviteeIdAndStatus(projectId, inviteeId, InvitationStatus.PENDING)
                .filter(stale -> stale.effectiveStatus(now) == InvitationStatus.EXPIRED)
                .ifPresent(stale -> {
                    stale.expire();
                    invitations.saveAndFlush(stale);
                });
    }

    private long teamSize(Project project) {
        return 1 + members.countByProjectId(project.getId());
    }

    private static void requireStudent(AuthenticatedUser caller) {
        if (caller.role() != Role.STUDENT) {
            throw new ApiException(ErrorCode.FORBIDDEN_ROLE, "Your role cannot perform this action.");
        }
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private Map<UUID, UserAccount> peopleById(List<UUID> ids, UUID ownerId) {
        List<UUID> all = new ArrayList<>(ids);
        all.add(ownerId);
        return users.findAllById(all.stream().filter(Objects::nonNull).distinct().toList()).stream()
                .collect(java.util.stream.Collectors.toMap(UserAccount::getId, account -> account));
    }

    private static String displayName(Map<UUID, UserAccount> people, UUID id) {
        UserAccount account = people.get(id);
        return account == null ? null : account.getDisplayName();
    }

    private Map<UUID, UserAccount> peopleFor(ProjectInvitation invitation) {
        return users.findAllById(List.of(invitation.getInviteeId(), invitation.getInviterId())).stream()
                .collect(java.util.stream.Collectors.toMap(UserAccount::getId, account -> account));
    }

    private Map<UUID, String> projectTitles(List<ProjectInvitation> items) {
        List<UUID> ids = items.stream().map(ProjectInvitation::getProjectId).distinct().toList();
        return projects.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(Project::getId, Project::getTitle));
    }

    private Page<InvitationResponse> pageOf(Page<ProjectInvitation> found, String projectTitle, Instant now) {
        Map<UUID, UserAccount> people = users.findAllById(found.getContent().stream()
                        .flatMap(invitation -> java.util.stream.Stream.of(invitation.getInviteeId(), invitation.getInviterId()))
                        .distinct().toList()).stream()
                .collect(java.util.stream.Collectors.toMap(UserAccount::getId, account -> account));
        return found.map(invitation -> toResponse(invitation, people, projectTitle, now));
    }

    private InvitationResponse toResponse(ProjectInvitation invitation, Map<UUID, UserAccount> people,
                                          String projectTitle, Instant now) {
        return new InvitationResponse(invitation.getId(), invitation.getProjectId(), projectTitle,
                person(people, invitation.getInviteeId()), person(people, invitation.getInviterId()),
                invitation.effectiveStatus(now), invitation.getExpiresAt(), invitation.getCreatedAt(),
                invitation.getRespondedAt(), invitation.getVersion());
    }

    private static PersonSummary person(Map<UUID, UserAccount> people, UUID id) {
        UserAccount account = id == null ? null : people.get(id);
        return account == null ? null : new PersonSummary(account.getId(), account.getDisplayName());
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND, "No project was found.");
    }

    private static ApiException invitationNotFound() {
        return new ApiException(ErrorCode.NOT_FOUND, "No invitation was found.");
    }
}
