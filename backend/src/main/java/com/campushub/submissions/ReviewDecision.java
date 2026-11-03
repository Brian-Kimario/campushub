package com.campushub.submissions;

/** The two outcomes a supervisor can record (STATE_MACHINES §5.2, V3 and V4). */
public enum ReviewDecision {
    ACCEPTED(SubmissionStatus.ACCEPTED),
    CHANGES_REQUESTED(SubmissionStatus.CHANGES_REQUESTED);

    private final SubmissionStatus status;

    ReviewDecision(SubmissionStatus status) {
        this.status = status;
    }

    public SubmissionStatus status() {
        return status;
    }
}
