package com.campushub.tasks;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Task endpoints (API_CONTRACT §8, MVP subset). */
@RestController
public class TaskController {

    private final TaskService tasks;

    public TaskController(TaskService tasks) {
        this.tasks = tasks;
    }

    @GetMapping("/projects/{projectId}/tasks")
    public PageResponse<TaskResponse> list(@AuthenticationPrincipal AuthenticatedUser caller,
                                           @PathVariable UUID projectId,
                                           @RequestParam(required = false) TaskStatus status,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return tasks.list(caller, projectId, status, page, size);
    }

    @PostMapping("/projects/{projectId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID projectId,
                               @Valid @RequestBody CreateTaskRequest body, HttpServletRequest request) {
        return tasks.create(caller, projectId, body, request);
    }

    @GetMapping("/tasks/{id}")
    public TaskResponse get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return tasks.get(caller, id);
    }

    @PatchMapping("/tasks/{id}")
    public TaskResponse update(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
                               @Valid @RequestBody UpdateTaskRequest body, HttpServletRequest request) {
        return tasks.update(caller, id, body, request);
    }

    @PostMapping("/tasks/{id}/transitions")
    public TaskResponse transition(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
                                   @Valid @RequestBody TransitionTaskRequest body, HttpServletRequest request) {
        return tasks.transition(caller, id, body, request);
    }
}
