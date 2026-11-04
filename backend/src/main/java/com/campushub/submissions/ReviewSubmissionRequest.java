package com.campushub.submissions;

import com.campushub.projects.Text;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /submissions/{id}/review}. Feedback is required when the decision is CHANGES_REQUESTED. */
public record ReviewSubmissionRequest(
        @NotNull ReviewDecision decision,
        @Size(max = 2000) String feedback) {

    public ReviewSubmissionRequest {
        feedback = Text.clean(feedback);
    }
}
