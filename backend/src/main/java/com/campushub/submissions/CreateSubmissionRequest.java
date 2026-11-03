package com.campushub.submissions;

import com.campushub.projects.Text;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /projects/{id}/submissions}. The link must be https. */
public record CreateSubmissionRequest(
        @NotBlank @Size(min = 3, max = 200) String title,
        @Size(max = 5000) String description,
        @NotBlank @Size(max = 500) @Pattern(regexp = "https://\\S+") String link) {

    public CreateSubmissionRequest {
        title = Text.clean(title);
        description = Text.clean(description);
        link = Text.clean(link);
    }
}
