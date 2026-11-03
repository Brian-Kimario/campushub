package com.campushub.identity;

import java.util.UUID;

/** The user summary returned by login and /auth/me (API_CONTRACT §2). Never carries credentials or lockout state. */
public record UserSummary(
        UUID id,
        String email,
        String institutionalId,
        String displayName,
        Role role,
        UserStatus status,
        boolean mustChangePassword,
        UUID departmentId) {

    public static UserSummary from(UserAccount account) {
        return new UserSummary(
                account.getId(),
                account.getEmail(),
                account.getInstitutionalId(),
                account.getDisplayName(),
                account.getRole(),
                account.getStatus(),
                account.isMustChangePassword(),
                account.getDepartmentId());
    }
}
