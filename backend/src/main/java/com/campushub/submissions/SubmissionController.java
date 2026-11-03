package com.campushub.submissions;

import com.campushub.identity.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Submission endpoints (API_CONTRACT §10, MVP subset). */
@RestController
public class SubmissionController {

    private final SubmissionService submissions;

    public SubmissionController(SubmissionService submissions) {
        this.submissions = submissions;
    }

    @GetMapping("/projects/{projectId}/submissions")
    public List<SubmissionResponse> list(@AuthenticationPrincipal AuthenticatedUser caller,
                                         @PathVariable UUID projectId) {
        return submissions.list(caller, projectId);
    }

    @PostMapping("/projects/{projectId}/submissions")
    @ResponseStatus(HttpStatus.CREATED)
    public SubmissionResponse create(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID projectId,
                                     @Valid @RequestBody CreateSubmissionRequest body, HttpServletRequest request) {
        return submissions.create(caller, projectId, body, request);
    }

    @GetMapping("/submissions/{id}")
    public SubmissionResponse get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return submissions.get(caller, id);
    }

    @PostMapping("/submissions/{id}/review/start")
    public SubmissionResponse startReview(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
                                          HttpServletRequest request) {
        return submissions.startReview(caller, id, request);
    }

    @PostMapping("/submissions/{id}/review")
    @ResponseStatus(HttpStatus.CREATED)
    public SubmissionResponse decide(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
                                     @Valid @RequestBody ReviewSubmissionRequest body, HttpServletRequest request) {
        return submissions.decide(caller, id, body, request);
    }
}
