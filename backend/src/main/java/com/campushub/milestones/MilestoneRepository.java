package com.campushub.milestones;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MilestoneRepository extends JpaRepository<Milestone, UUID> {

    List<Milestone> findByProjectIdOrderBySequenceAsc(UUID projectId);

    @Query("select coalesce(max(m.sequence), 0) from Milestone m where m.projectId = :projectId")
    int maxSequence(@Param("projectId") UUID projectId);

    /** Milestones due in the half-open instant range [from, to), for the calendar. */
    @Query("""
            select m from Milestone m
            where m.dueAt >= :from and m.dueAt < :to
            order by m.dueAt asc, m.id asc
            """)
    List<Milestone> findDueBetween(@Param("from") Instant from, @Param("to") Instant to);
}
