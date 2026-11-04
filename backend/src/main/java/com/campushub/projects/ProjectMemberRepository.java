package com.campushub.projects;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

    boolean existsByProjectIdAndUserId(UUID projectId, UUID userId);

    long countByProjectId(UUID projectId);

    List<ProjectMember> findByProjectIdOrderByJoinedAtAscIdAsc(UUID projectId);

    List<ProjectMember> findByUserId(UUID userId);

    void deleteByProjectIdAndUserId(UUID projectId, UUID userId);
}
