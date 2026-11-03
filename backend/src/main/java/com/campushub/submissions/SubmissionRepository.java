package com.campushub.submissions;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    List<Submission> findByProjectIdOrderByRevisionNoAsc(UUID projectId);

    Optional<Submission> findFirstByProjectIdOrderByRevisionNoDesc(UUID projectId);

    /** Serializes uploads to one revision, so the file-count check and the insert cannot interleave. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Submission s where s.id = :id")
    Optional<Submission> lockById(@Param("id") UUID id);
}
