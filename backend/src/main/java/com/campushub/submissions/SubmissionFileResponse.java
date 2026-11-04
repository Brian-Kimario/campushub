package com.campushub.submissions;

import java.time.Instant;
import java.util.UUID;

/** A file on a submission. The storage key is never returned: the download endpoint is the only way to read the bytes. */
public record SubmissionFileResponse(
        UUID id,
        UUID submissionId,
        String originalName,
        String contentType,
        long sizeBytes,
        String sha256,
        UUID uploadedBy,
        Instant uploadedAt) {
}
