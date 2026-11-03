package com.campushub.submissions;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionFileRepository extends JpaRepository<SubmissionFile, UUID> {

    List<SubmissionFile> findBySubmissionIdOrderByUploadedAtAsc(UUID submissionId);

    long countBySubmissionId(UUID submissionId);
}
