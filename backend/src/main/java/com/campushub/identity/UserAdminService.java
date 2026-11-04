package com.campushub.identity;

import com.campushub.catalog.CatalogSort;
import com.campushub.catalog.DepartmentRepository;
import com.campushub.identity.AdminUserDtos.AdminUserResponse;
import com.campushub.identity.AdminUserDtos.CreateUserRequest;
import com.campushub.identity.AdminUserDtos.UpdateUserRequest;
import com.campushub.identity.audit.AuditOutcome;
import com.campushub.identity.audit.AuditService;
import com.campushub.identity.security.AuthenticatedUser;
import com.campushub.shared.errors.ApiException;
import com.campushub.shared.errors.ErrorCode;
import com.campushub.shared.web.PageResponse;
import com.campushub.shared.web.Pagination;
import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administrator account management (API_CONTRACT §4). Every write is audited. Deactivation and password
 * reset revoke existing tokens through {@code revokedAfter}, so the change takes effect on the next request.
 */
@Service
public class UserAdminService {

    private static final String USER = "USER";
    private static final Set<String> SORT_FIELDS = Set.of("displayName", "email", "createdAt");
    private static final String TEMPORARY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int TEMPORARY_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserAccountRepository users;
    private final DepartmentRepository departments;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final Clock clock;

    public UserAdminService(UserAccountRepository users, DepartmentRepository departments,
                            PasswordEncoder passwordEncoder, AuditService audit, Clock clock) {
        this.users = users;
        this.departments = departments;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> list(Role role, UserStatus status, String q, int page, int size,
                                                String sort) {
        var order = CatalogSort.parse(sort, "displayName", SORT_FIELDS);
        Page<AdminUserResponse> result = users.search(role, status, prefix(q),
                        Pagination.request(page, size, order))
                .map(account -> AdminUserResponse.from(account, null));
        return PageResponse.of(result, CatalogSort.applied(order));
    }

    @Transactional
    public AdminUserResponse create(AuthenticatedUser caller, CreateUserRequest body, HttpServletRequest request) {
        String email = body.email().trim().toLowerCase(Locale.ROOT);
        String institutionalId = blankToNull(body.institutionalId());
        UUID departmentId = body.departmentId() == null ? null : requireActiveDepartment(body.departmentId());
        if (users.existsByEmail(email) || (institutionalId != null && users.existsByInstitutionalId(institutionalId))) {
            throw duplicate();
        }

        Instant now = clock.instant();
        String temporary = temporaryPassword();
        UserAccount account = new UserAccount(UUID.randomUUID(), email, body.displayName().trim(),
                passwordEncoder.encode(temporary), body.role(), true, now);
        if (institutionalId != null) {
            account.assignInstitutionalId(institutionalId, now);
        }
        account.updateProfile(account.getDisplayName(), departmentId, null, null, null, null, now);
        try {
            users.saveAndFlush(account);
        } catch (DataIntegrityViolationException race) {
            throw duplicate();
        }
        audit.record("admin.user.created", caller.userId(), USER, account.getId().toString(),
                AuditOutcome.SUCCESS, request, Map.of());
        return AdminUserResponse.from(account, temporary);
    }

    @Transactional
    public AdminUserResponse update(AuthenticatedUser caller, UUID id, UpdateUserRequest body,
                                    HttpServletRequest request) {
        UserAccount target = load(id);
        if (body.role() != null && body.role() != target.getRole()) {
            throw new ApiException(ErrorCode.ROLE_CHANGE_NOT_SUPPORTED,
                    "Changing a user's role is not supported in this release.");
        }
        UUID departmentId = body.departmentId() == null
                ? target.getDepartmentId()
                : requireActiveDepartment(body.departmentId());
        target.updateProfile(
                body.displayName() == null ? target.getDisplayName() : body.displayName().trim(),
                departmentId,
                body.program() == null ? target.getProgram() : blankToNull(body.program()),
                body.cohort() == null ? target.getCohort() : blankToNull(body.cohort()),
                body.designation() == null ? target.getDesignation() : blankToNull(body.designation()),
                body.bio() == null ? target.getBio() : blankToNull(body.bio()),
                clock.instant());
        audit.record("admin.user.updated", caller.userId(), USER, id.toString(), AuditOutcome.SUCCESS, request,
                Map.of());
        return AdminUserResponse.from(target, null);
    }

    @Transactional
    public AdminUserResponse deactivate(AuthenticatedUser caller, UUID id, HttpServletRequest request) {
        UserAccount target = load(id);
        if (target.getId().equals(caller.userId())) {
            throw new ApiException(ErrorCode.PRECONDITION_NOT_MET, "You cannot deactivate your own account.");
        }
        if (target.getRole() == Role.ADMIN && target.getStatus() == UserStatus.ACTIVE
                && users.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE) <= 1) {
            throw new ApiException(ErrorCode.PRECONDITION_NOT_MET,
                    "At least one active administrator is required.");
        }
        target.deactivate(clock.instant());
        audit.record("admin.user.deactivated", caller.userId(), USER, id.toString(), AuditOutcome.SUCCESS, request,
                Map.of());
        return AdminUserResponse.from(target, null);
    }

    @Transactional
    public AdminUserResponse reactivate(AuthenticatedUser caller, UUID id, HttpServletRequest request) {
        UserAccount target = load(id);
        target.reactivate(clock.instant());
        audit.record("admin.user.reactivated", caller.userId(), USER, id.toString(), AuditOutcome.SUCCESS, request,
                Map.of());
        return AdminUserResponse.from(target, null);
    }

    /** Issues a new temporary password, forces a change at next sign-in, and revokes existing tokens. */
    @Transactional
    public AdminUserResponse resetPassword(AuthenticatedUser caller, UUID id, HttpServletRequest request) {
        UserAccount target = load(id);
        String temporary = temporaryPassword();
        Instant now = clock.instant();
        target.changePassword(passwordEncoder.encode(temporary), true, now, now);
        audit.record("admin.user.password_reset", caller.userId(), USER, id.toString(), AuditOutcome.SUCCESS,
                request, Map.of());
        return AdminUserResponse.from(target, temporary);
    }

    private UserAccount load(UUID id) {
        return users.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found."));
    }

    private UUID requireActiveDepartment(UUID departmentId) {
        if (departments.findById(departmentId).filter(d -> d.isActive()).isEmpty()) {
            throw ApiException.withErrors(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.",
                    List.of(Map.of("field", "departmentId", "reason", "is not an active department")));
        }
        return departmentId;
    }

    private static ApiException duplicate() {
        return new ApiException(ErrorCode.DUPLICATE, "An account with this email or institutional ID already exists.");
    }

    /** Lowercase, trimmed, with LIKE wildcards removed so the search stays a prefix match. */
    private static String prefix(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        return q.trim().replace("%", "").replace("_", "").toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String temporaryPassword() {
        StringBuilder password = new StringBuilder(TEMPORARY_LENGTH);
        for (int i = 0; i < TEMPORARY_LENGTH; i++) {
            password.append(TEMPORARY_ALPHABET.charAt(RANDOM.nextInt(TEMPORARY_ALPHABET.length())));
        }
        return password.toString();
    }
}
