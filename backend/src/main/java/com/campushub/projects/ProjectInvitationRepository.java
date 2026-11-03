package com.campushub.projects;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectInvitationRepository extends JpaRepository<ProjectInvitation, UUID> {

    Optional<ProjectInvitation> findByIdAndInviteeId(UUID id, UUID inviteeId);

    boolean existsByProjectIdAndInviteeIdAndStatus(UUID projectId, UUID inviteeId, InvitationStatus status);

    Optional<ProjectInvitation> findByProjectIdAndInviteeIdAndStatus(UUID projectId, UUID inviteeId, InvitationStatus status);

    Page<ProjectInvitation> findByProjectIdOrderByCreatedAtDescIdAsc(UUID projectId, Pageable pageable);

    Page<ProjectInvitation> findByInviteeIdOrderByCreatedAtDescIdAsc(UUID inviteeId, Pageable pageable);
}
