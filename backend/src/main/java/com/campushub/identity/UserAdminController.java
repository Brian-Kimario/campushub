package com.campushub.identity;

import com.campushub.identity.AdminUserDtos.AdminUserResponse;
import com.campushub.identity.AdminUserDtos.CreateUserRequest;
import com.campushub.identity.AdminUserDtos.UpdateUserRequest;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Administrator user management (API_CONTRACT §4). The /api/v1/admin/** rule in SecurityConfig limits it to ADMIN. */
@RestController
@RequestMapping("/admin/users")
public class UserAdminController {

    private final UserAdminService admin;

    public UserAdminController(UserAdminService admin) {
        this.admin = admin;
    }

    @GetMapping
    public PageResponse<AdminUserResponse> list(@RequestParam(required = false) Role role,
                                                @RequestParam(required = false) UserStatus status,
                                                @RequestParam(required = false) String q,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @RequestParam(required = false) String sort) {
        return admin.list(role, status, q, page, size, sort);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserResponse create(@AuthenticationPrincipal AuthenticatedUser caller,
                                    @Valid @RequestBody CreateUserRequest body,
                                    HttpServletRequest request) {
        return admin.create(caller, body, request);
    }

    @PatchMapping("/{id}")
    public AdminUserResponse update(@AuthenticationPrincipal AuthenticatedUser caller,
                                    @PathVariable UUID id,
                                    @Valid @RequestBody UpdateUserRequest body,
                                    HttpServletRequest request) {
        return admin.update(caller, id, body, request);
    }

    @PostMapping("/{id}/deactivate")
    public AdminUserResponse deactivate(@AuthenticationPrincipal AuthenticatedUser caller,
                                        @PathVariable UUID id,
                                        HttpServletRequest request) {
        return admin.deactivate(caller, id, request);
    }

    @PostMapping("/{id}/reactivate")
    public AdminUserResponse reactivate(@AuthenticationPrincipal AuthenticatedUser caller,
                                        @PathVariable UUID id,
                                        HttpServletRequest request) {
        return admin.reactivate(caller, id, request);
    }

    @PostMapping("/{id}/reset-password")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserResponse resetPassword(@AuthenticationPrincipal AuthenticatedUser caller,
                                           @PathVariable UUID id,
                                           HttpServletRequest request) {
        return admin.resetPassword(caller, id, request);
    }
}
