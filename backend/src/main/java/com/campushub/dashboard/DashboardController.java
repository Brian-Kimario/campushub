package com.campushub.dashboard;

import com.campushub.identity.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard endpoints (Phase 10). Role and ownership rules are enforced in {@link DashboardService}. */
@RestController
@RequestMapping("/users/me/dashboard")
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/student")
    public StudentDashboardResponse student(@AuthenticationPrincipal AuthenticatedUser caller) {
        return dashboard.student(caller);
    }

    @GetMapping("/faculty")
    public FacultyDashboardResponse faculty(@AuthenticationPrincipal AuthenticatedUser caller) {
        return dashboard.faculty(caller);
    }
}
