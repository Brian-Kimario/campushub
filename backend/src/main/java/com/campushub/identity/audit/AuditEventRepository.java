package com.campushub.identity.audit;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    /**
     * Filtered audit search. A null argument leaves that filter open. Both ends of the time range are
     * inclusive. Ordering comes from the pageable, so callers choose the direction.
     */
    @Query("""
            select e from AuditEvent e
            where (:actorId is null or e.actorId = :actorId)
              and (:action is null or e.action = :action)
              and (:targetType is null or e.targetType = :targetType)
              and (:targetId is null or e.targetId = :targetId)
              and (:from is null or e.occurredAt >= :from)
              and (:to is null or e.occurredAt <= :to)
            """)
    Page<AuditEvent> search(@Param("actorId") UUID actorId, @Param("action") String action,
                            @Param("targetType") String targetType, @Param("targetId") String targetId,
                            @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);
}
