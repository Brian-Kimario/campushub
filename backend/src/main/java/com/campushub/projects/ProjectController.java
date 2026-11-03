package com.campushub.projects;

import com.campushub.identity.security.AuthenticatedUser;
import com.campushub.shared.web.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Project endpoints (API_CONTRACT §6, MVP subset). */
@RestController
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectService projects;

    public ProjectController(ProjectService projects) {
        this.projects = projects;
    }

    @GetMapping
    public PageResponse<ProjectResponse> list(@AuthenticationPrincipal AuthenticatedUser caller,
                                              @RequestParam(required = false) ProjectStatus status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return projects.list(caller, status, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@AuthenticationPrincipal AuthenticatedUser caller,
                                  @Valid @RequestBody CreateProjectRequest body, HttpServletRequest request) {
        return projects.create(caller, body, request);
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return projects.get(caller, id);
    }

    @PatchMapping("/{id}")
    public ProjectResponse update(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
                                  @Valid @RequestBody UpdateProjectRequest body, HttpServletRequest request) {
        return projects.update(caller, id, body, request);
    }

    @PutMapping("/{id}/supervisor")
    public ProjectResponse assignSupervisor(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
                                            @Valid @RequestBody AssignSupervisorRequest body,
                                            HttpServletRequest request) {
        return projects.assignSupervisor(caller, id, body, request);
    }

    @PostMapping("/{id}/archive")
    public ProjectResponse archive(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
                                   @Valid @RequestBody ArchiveProjectRequest body, HttpServletRequest request) {
        return projects.archive(caller, id, body, request);
    }
}
