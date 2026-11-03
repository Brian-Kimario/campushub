package com.campushub.certifications;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificationRepository extends JpaRepository<Certification, UUID> {

    Page<Certification> findByOwnerId(UUID ownerId, Pageable pageable);

    /** Scoped to the owner, so another student's row reads as absent (NS-01). */
    Optional<Certification> findByIdAndOwnerId(UUID id, UUID ownerId);
}
