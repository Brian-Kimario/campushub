package com.campushub.tasks;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
