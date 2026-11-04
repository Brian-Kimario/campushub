package com.campushub.identity;

import java.util.UUID;

/** The caller's full profile returned by GET and PATCH /users/me (API_CONTRACT §4). */
public record ProfileResponse(
        UUID id,
        String email,
        String institutionalId,
        String displayName,
        Role role,
        UserStatus status,
        boolean mustChangePassword,
        UUID departmentId,
        String program,
        String cohort,
        String designation,
        String bio) {

    static ProfileResponse from(UserAccount account) {
        return new ProfileResponse(
                account.getId(),
                account.getEmail(),
                account.getInstitutionalId(),
                account.getDisplayName(),
                account.getRole(),
                account.getStatus(),
                account.isMustChangePassword(),
                account.getDepartmentId(),
                account.getProgram(),
                account.getCohort(),
                account.getDesignation(),
                account.getBio());
    }
}
