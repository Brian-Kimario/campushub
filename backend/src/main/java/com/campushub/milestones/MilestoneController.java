package com.campushub.milestones;

import com.campushub.identity.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Milestone endpoints (API_CONTRACT §7). */
@RestController
public class MilestoneController {

    private final MilestoneService milestones;

    public MilestoneController(MilestoneService milestones) {
        this.milestones = milestones;
    }

    @GetMapping("/projects/{projectId}/milestones")
    public List<MilestoneResponse> list(@AuthenticationPrincipal AuthenticatedUser caller,
                                        @PathVariable UUID projectId) {
        return milestones.list(caller, projectId);
    }

    @PostMapping("/projects/{projectId}/milestones")
    @ResponseStatus(HttpStatus.CREATED)
    public MilestoneResponse create(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID projectId,
                                    @Valid @RequestBody CreateMilestoneRequest body, HttpServletRequest request) {
        return milestones.create(caller, projectId, body, request);
    }

    @PatchMapping("/milestones/{id}")
    public MilestoneResponse update(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
                                    @Valid @RequestBody UpdateMilestoneRequest body, HttpServletRequest request) {
        return milestones.update(caller, id, body, request);
    }

    @PostMapping("/milestones/{id}/close")
    public MilestoneResponse close(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
                                   HttpServletRequest request) {
        return milestones.close(caller, id, request);
    }
}
