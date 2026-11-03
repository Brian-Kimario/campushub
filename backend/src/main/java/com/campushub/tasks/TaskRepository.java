package com.campushub.tasks;

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
}
