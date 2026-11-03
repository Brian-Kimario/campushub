package com.campushub.identity;

import com.campushub.identity.security.AuthenticatedUser;
import com.campushub.shared.errors.ApiException;
import com.campushub.shared.errors.ErrorCode;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only endpoints that stand in for real feature routes. They exercise the role rule
 * (/admin/**), the forced-password-change gate (any non-exempt path), and the cross-user rule
 * (a record owned by another user is reported as NOT_FOUND, never FORBIDDEN_ROLE).
 */
@RestController
class ProbeController {

    @GetMapping("/probe/ping")
    Map<String, String> ping(@AuthenticationPrincipal AuthenticatedUser caller) {
        return Map.of("status", "ok");
    }

    @GetMapping("/admin/probe")
    Map<String, String> adminOnly() {
        return Map.of("status", "ok");
    }

    /** Owner-only record: a different non-admin caller gets NOT_FOUND so the record's existence stays hidden. */
    @GetMapping("/probe/records/{ownerId}")
    Map<String, String> record(@PathVariable UUID ownerId, @AuthenticationPrincipal AuthenticatedUser caller) {
        if (!caller.userId().equals(ownerId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "No record was found.");
        }
        return Map.of("ownerId", ownerId.toString());
    }
}
