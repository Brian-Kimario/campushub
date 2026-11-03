package com.campushub.notifications;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipientId(UUID recipientId, Pageable pageable);

    Page<Notification> findByRecipientIdAndReadAtIsNull(UUID recipientId, Pageable pageable);

    Optional<Notification> findByIdAndRecipientId(UUID id, UUID recipientId);

    long countByRecipientIdAndReadAtIsNull(UUID recipientId);

    /** Marks every unread row for the recipient. Bumps version so optimistic readers see the change. */
    @Modifying
    @Query("update Notification n set n.readAt = :now, n.version = n.version + 1 "
            + "where n.recipientId = :recipientId and n.readAt is null")
    int markAllRead(@Param("recipientId") UUID recipientId, @Param("now") Instant now);
}
