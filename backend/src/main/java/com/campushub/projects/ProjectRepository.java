package com.campushub.projects;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    /**
     * Scoped search. A null argument leaves that dimension open. The service chooses the student
     * or supervisor argument from the caller's role, so callers cannot widen their own scope.
     * A student scope covers owned projects and projects where they are a team member.
     */
    @Query("""
            select p from Project p
            where (:studentId is null or p.ownerId = :studentId
                   or p.id in (select m.projectId from ProjectMember m where m.userId = :studentId))
              and (:supervisorId is null or p.supervisorId = :supervisorId)
              and (:status is null or p.status = :status)
            """)
    Page<Project> search(@Param("studentId") UUID studentId, @Param("supervisorId") UUID supervisorId,
                         @Param("status") ProjectStatus status, Pageable pageable);

    /** Locks the project row for the rest of the transaction, so team-size checks cannot race. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = :id")
    Optional<Project> lockById(@Param("id") UUID id);

    /** Projects with a due date in the range, for the calendar. Visibility is filtered by the caller. */
    @Query("select p from Project p where p.dueDate between :from and :to")
    List<Project> findDueBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
