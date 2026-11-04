package com.campushub.tasks;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    @Query("""
            select t from Task t
            where t.projectId = :projectId
              and (:status is null or t.status = :status)
            """)
    Page<Task> search(@Param("projectId") UUID projectId, @Param("status") TaskStatus status, Pageable pageable);

    @Query("""
            select t from Task t
            where t.dueDate between :from and :to
            order by t.dueDate asc, t.id asc
            """)
    List<Task> findDueBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Clears the assignee on the member's tasks that are not DONE, when the member leaves the team
     * (FR-TEAM-03). Bumps the version so that a client holding the old assignment gets 409 on its next save.
     */
    @Modifying
    @Query("""
            update Task t
               set t.assigneeId = null, t.version = t.version + 1, t.updatedAt = :now
             where t.projectId = :projectId
               and t.assigneeId = :userId
               and t.status <> :done
            """)
    int unassignOpenTasks(@Param("projectId") UUID projectId, @Param("userId") UUID userId,
                          @Param("done") TaskStatus done, @Param("now") Instant now);
}
