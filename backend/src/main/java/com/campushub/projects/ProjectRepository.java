package com.campushub.projects;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    /**
     * Scoped search. A null argument leaves that dimension open. The service chooses the owner or
     * supervisor argument from the caller's role, so callers cannot widen their own scope.
     */
    @Query("""
            select p from Project p
            where (:ownerId is null or p.ownerId = :ownerId)
              and (:supervisorId is null or p.supervisorId = :supervisorId)
              and (:status is null or p.status = :status)
            """)
    Page<Project> search(@Param("ownerId") UUID ownerId, @Param("supervisorId") UUID supervisorId,
                         @Param("status") ProjectStatus status, Pageable pageable);
}
