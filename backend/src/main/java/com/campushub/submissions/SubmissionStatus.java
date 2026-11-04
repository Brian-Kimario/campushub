package com.campushub.submissions;

/** Revision states (STATE_MACHINES §5.1, without DRAFT: the MVP revision is sealed on creation). */
public enum SubmissionStatus {
    SUBMITTED,
    UNDER_REVIEW,
    CHANGES_REQUESTED,
    ACCEPTED;

    /** A decision is recorded once the status is CHANGES_REQUESTED or ACCEPTED. */
    public boolean isDecided() {
        return this == CHANGES_REQUESTED || this == ACCEPTED;
    }
}
