package com.campushub.dashboard;

import com.campushub.submissions.SubmissionStatus;
import java.util.UUID;

/** One row of a grouped submission count, used by the dashboard queries. */
public record SubmissionStatusCount(UUID projectId, SubmissionStatus status, long total) {
}
