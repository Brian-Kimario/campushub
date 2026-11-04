package com.campushub.identity;

import com.campushub.catalog.DepartmentRepository;
import com.campushub.identity.audit.AuditOutcome;
import com.campushub.identity.audit.AuditService;
import com.campushub.identity.security.AuthenticatedUser;
import com.campushub.shared.errors.ApiException;
import com.campushub.shared.errors.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Self-service profile reads and edits (API_CONTRACT §4, /users/me). */
@Service
public class ProfileService {

    private static final String USER = "USER";
    private static final Set<String> NOT_EDITABLE = Set.of("role", "status", "email", "institutionalId");
    private static final Set<String> EDITABLE = Set.of("displayName", "departmentId", "program", "cohort",
            "designation", "bio");

    private final UserAccountRepository users;
    private final DepartmentRepository departments;
    private final AuditService audit;
    private final Clock clock;

    public ProfileService(UserAccountRepository users, DepartmentRepository departments, AuditService audit,
                          Clock clock) {
        this.users = users;
        this.departments = departments;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProfileResponse profile(AuthenticatedUser caller) {
        return ProfileResponse.from(load(caller));
    }

    /**
     * Applies only the keys present in the body. Role, status, email and institutional ID are rejected
     * with FIELD_NOT_EDITABLE rather than ignored, so a client never believes a change was saved when it was not.
     */
    @Transactional
    public ProfileResponse update(AuthenticatedUser caller, Map<String, Object> body, HttpServletRequest request) {
        UserAccount account = load(caller);
        List<Map<String, String>> errors = new ArrayList<>();

        for (String key : body.keySet()) {
            if (NOT_EDITABLE.contains(key)) {
                throw ApiException.withErrors(ErrorCode.FIELD_NOT_EDITABLE, "One or more fields cannot be edited.",
                        List.of(Map.of("field", key, "reason", "cannot be edited on your profile")));
            }
            if (!EDITABLE.contains(key)) {
                errors.add(Map.of("field", key, "reason", "is not a recognised profile field"));
            }
        }

        String displayName = account.getDisplayName();
        UUID departmentId = account.getDepartmentId();
        String program = account.getProgram();
        String cohort = account.getCohort();
        String designation = account.getDesignation();
        String bio = account.getBio();

        if (body.containsKey("displayName")) {
            displayName = text(body.get("displayName"), "displayName", 2, 100, true, errors);
        }
        if (body.containsKey("program")) {
            program = text(body.get("program"), "program", 1, 120, false, errors);
        }
        if (body.containsKey("cohort")) {
            cohort = text(body.get("cohort"), "cohort", 1, 20, false, errors);
        }
        if (body.containsKey("designation")) {
            designation = text(body.get("designation"), "designation", 1, 120, false, errors);
        }
        if (body.containsKey("bio")) {
            bio = text(body.get("bio"), "bio", 1, 1000, false, errors);
        }
        if (body.containsKey("departmentId")) {
            departmentId = departmentId(body.get("departmentId"), errors);
        }

        if (!errors.isEmpty()) {
            throw ApiException.withErrors(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.", errors);
        }

        account.updateProfile(displayName, departmentId, program, cohort, designation, bio, clock.instant());
        audit.record("user.profile_updated", caller.userId(), USER, caller.userId().toString(),
                AuditOutcome.SUCCESS, request, Map.of());
        return ProfileResponse.from(account);
    }

    private UserAccount load(AuthenticatedUser caller) {
        return users.findById(caller.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Authentication is required."));
    }

    /**
     * Trims the value and treats blank as an explicit clear. Failures are added to {@code errors} and
     * return null, so every field is checked before the request is rejected.
     */
    private static String text(Object raw, String field, int min, int max, boolean required,
                               List<Map<String, String>> errors) {
        if (raw == null) {
            if (required) {
                errors.add(Map.of("field", field, "reason", "is required"));
            }
            return null;
        }
        if (!(raw instanceof String value)) {
            errors.add(Map.of("field", field, "reason", "must be a string"));
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            if (required) {
                errors.add(Map.of("field", field, "reason", "is required"));
            }
            return null;
        }
        if (trimmed.length() < min || trimmed.length() > max) {
            errors.add(Map.of("field", field, "reason", "must be between " + min + " and " + max + " characters"));
            return null;
        }
        return trimmed;
    }

    /** A department must exist and be active; null or blank clears the link. */
    private UUID departmentId(Object raw, List<Map<String, String>> errors) {
        if (raw == null || (raw instanceof String s && s.isBlank())) {
            return null;
        }
        if (!(raw instanceof String value)) {
            errors.add(Map.of("field", "departmentId", "reason", "must be a string"));
            return null;
        }
        UUID id;
        try {
            id = UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            errors.add(Map.of("field", "departmentId", "reason", "must be a valid identifier"));
            return null;
        }
        if (departments.findById(id).filter(d -> d.isActive()).isEmpty()) {
            errors.add(Map.of("field", "departmentId", "reason", "is not an active department"));
            return null;
        }
        return id;
    }
}
