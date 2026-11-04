package com.campushub.notifications;

import com.campushub.identity.UserAccount;
import com.campushub.identity.UserAccountRepository;
import com.campushub.identity.security.AuthenticatedUser;
import com.campushub.notifications.NotificationResponses.MarkAllResult;
import com.campushub.notifications.NotificationResponses.NotificationResponse;
import com.campushub.notifications.NotificationResponses.UnreadCount;
import com.campushub.projects.Project;
import com.campushub.projects.ProjectRepository;
import com.campushub.shared.errors.ApiException;
import com.campushub.shared.errors.ErrorCode;
import com.campushub.shared.web.PageResponse;
import com.campushub.shared.web.Pagination;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Database-backed notifications with read state (V15). Each user sees only their own rows; anything else
 * is 404. Membership changes call {@link #notify} inside their own transaction, so a rolled-back change
 * also rolls back its notification.
 */
@Service
public class NotificationService {

    private static final List<String> SORT = List.of("createdAt,desc", "id,asc");
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
    private static final String UNKNOWN_ACTOR = "Someone";
    private static final String UNKNOWN_PROJECT = "a project";

    private final NotificationRepository notifications;
    private final ProjectRepository projects;
    private final UserAccountRepository users;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, ProjectRepository projects,
                               UserAccountRepository users, Clock clock) {
        this.notifications = notifications;
        this.projects = projects;
        this.users = users;
        this.clock = clock;
    }

    /** Writes one notification in the caller's transaction. Message text is rendered later, not stored. */
    @Transactional
    public void notify(UUID recipientId, NotificationType type, UUID actorId, UUID projectId) {
        notifications.saveAndFlush(new Notification(UUID.randomUUID(), recipientId, actorId, type, projectId,
                clock.instant()));
    }

    /** The caller's notifications, newest first. Set {@code unreadOnly} to list only unread ones. */
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(AuthenticatedUser caller, boolean unreadOnly, int page, int size) {
        Pageable pageable = Pagination.request(page, size, NEWEST_FIRST);
        Page<Notification> found = unreadOnly
                ? notifications.findByRecipientIdAndReadAtIsNull(caller.userId(), pageable)
                : notifications.findByRecipientId(caller.userId(), pageable);
        List<NotificationResponse> rows = toResponses(found.getContent());
        return PageResponse.of(new PageImpl<>(rows, found.getPageable(), found.getTotalElements()), SORT);
    }

    @Transactional(readOnly = true)
    public UnreadCount unreadCount(AuthenticatedUser caller) {
        return new UnreadCount(notifications.countByRecipientIdAndReadAtIsNull(caller.userId()));
    }

    /** Marks one of the caller's notifications read. Calling it again keeps the first read time. */
    @Transactional
    public NotificationResponse markRead(AuthenticatedUser caller, UUID id) {
        Notification notification = notifications.findByIdAndRecipientId(id, caller.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No notification was found."));
        notification.markRead(clock.instant());
        notifications.saveAndFlush(notification);
        return toResponses(List.of(notification)).get(0);
    }

    /** Marks every unread notification of the caller read, and reports how many changed. */
    @Transactional
    public MarkAllResult markAllRead(AuthenticatedUser caller) {
        return new MarkAllResult(notifications.markAllRead(caller.userId(), clock.instant()));
    }

    /** Resolves project titles and actor names in two queries for the whole page. */
    private List<NotificationResponse> toResponses(List<Notification> items) {
        Map<UUID, String> titles = projects.findAllById(items.stream()
                        .map(Notification::getProjectId).filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(Project::getId, Project::getTitle));
        Map<UUID, String> names = users.findAllById(items.stream()
                        .map(Notification::getActorId).filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(UserAccount::getId, UserAccount::getDisplayName));
        List<NotificationResponse> result = new ArrayList<>();
        for (Notification item : items) {
            String actor = names.getOrDefault(item.getActorId(), UNKNOWN_ACTOR);
            String title = titles.get(item.getProjectId());
            result.add(new NotificationResponse(item.getId(), item.getType(),
                    message(item.getType(), actor, title), item.getProjectId(), item.getCreatedAt(), item.getReadAt()));
        }
        return result;
    }

    private static String message(NotificationType type, String actor, String projectTitle) {
        String project = projectTitle == null ? UNKNOWN_PROJECT : "\"" + projectTitle + "\"";
        return switch (type) {
            case INVITATION_RECEIVED -> "You were invited to join " + project + ".";
            case INVITATION_ACCEPTED -> actor + " accepted your invitation to " + project + ".";
            case INVITATION_DECLINED -> actor + " declined your invitation to " + project + ".";
            case MEMBER_REMOVED -> "You were removed from " + project + ".";
            case SUBMISSION_ACCEPTED -> actor + " accepted your submission in " + project + ".";
            case SUBMISSION_CHANGES_REQUESTED -> actor + " requested changes to your submission in " + project + ".";
        };
    }
}
