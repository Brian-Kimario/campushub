package com.campushub.submissions;

import java.time.Instant;
import java.util.UUID;

public record SubmissionResponse(
        UUID id,
        UUID projectId,
        int revisionNo,
        String title,
        String description,
        String link,
        SubmissionStatus status,
        UUID submittedBy,
        Instant submittedAt,
        UUID reviewedBy,
        Instant reviewedAt,
        String feedback,
        int version) {
}
