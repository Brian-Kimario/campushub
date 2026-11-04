package com.campushub.dashboard;

import com.campushub.identity.Role;
import com.campushub.projects.Project;
import com.campushub.projects.ProjectStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Aggregate reads for the dashboards (Phase 10). Every query is scoped by the caller's id, and the
 * grouped queries return counts rather than rows, so no dashboard loads records only to count them.
 */
public interface DashboardRepository extends Repository<Project, UUID> {

    long countByOwnerId(UUID ownerId);

    long countByOwnerIdAndStatus(UUID ownerId, ProjectStatus status);

    List<Project> findByOwnerIdOrderByCreatedAtDescIdAsc(UUID ownerId, Pageable pageable);

    long countBySupervisorId(UUID supervisorId);

    long countBySupervisorIdAndStatus(UUID supervisorId, ProjectStatus status);

    List<Project> findBySupervisorIdOrderByCreatedAtDescIdAsc(UUID supervisorId, Pageable pageable);

    @Query("""
            select new com.campushub.dashboard.TaskStatusCount(t.projectId, t.status, count(t))
            from Task t
            where t.projectId in (select p.id from Project p where p.ownerId = :ownerId)
            group by t.projectId, t.status
            """)
    List<TaskStatusCount> taskCountsForOwner(@Param("ownerId") UUID ownerId);

    @Query("""
            select new com.campushub.dashboard.SubmissionStatusCount(s.projectId, s.status, count(s))
            from Submission s
            where s.projectId in (select p.id from Project p where p.ownerId = :ownerId)
            group by s.projectId, s.status
            """)
    List<SubmissionStatusCount> submissionCountsForOwner(@Param("ownerId") UUID ownerId);

    @Query("""
            select new com.campushub.dashboard.SubmissionStatusCount(s.projectId, s.status, count(s))
            from Submission s
            where s.projectId in (select p.id from Project p where p.supervisorId = :supervisorId)
            group by s.projectId, s.status
            """)
    List<SubmissionStatusCount> submissionCountsForSupervisor(@Param("supervisorId") UUID supervisorId);

    /** Open tasks with a due date from today onward, across the caller's projects, earliest first. */
    @Query("""
            select new com.campushub.dashboard.UpcomingTask(t.id, t.title, t.projectId, t.dueDate, t.status)
            from Task t
            where t.projectId in (select p.id from Project p where p.ownerId = :ownerId)
              and t.status <> com.campushub.tasks.TaskStatus.DONE
              and t.dueDate >= :today
            order by t.dueDate asc, t.id asc
            """)
    List<UpcomingTask> upcomingTasksForOwner(@Param("ownerId") UUID ownerId, @Param("today") LocalDate today,
                                             Pageable pageable);

    @Query("""
            select new com.campushub.dashboard.GroupCount(u.role, count(u))
            from UserAccount u
            where u.status = com.campushub.identity.UserStatus.ACTIVE
            group by u.role
            """)
    List<GroupCount<Role>> activeUsersByRole();

    @Query("""
            select new com.campushub.dashboard.GroupCount(p.status, count(p))
            from Project p
            group by p.status
            """)
    List<GroupCount<ProjectStatus>> projectsByStatus();

    @Query("select count(c) from Certification c where c.ownerId = :ownerId")
    long countCertifications(@Param("ownerId") UUID ownerId);

    @Query("select count(us) from UserSkill us where us.key.userId = :ownerId")
    long countSkills(@Param("ownerId") UUID ownerId);
}
