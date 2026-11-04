package com.campushub.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Request and response shapes for the administrator user endpoints (API_CONTRACT §4). */
final class AdminUserDtos {

    private AdminUserDtos() {
    }

    /**
     * {@code temporaryPassword} is set only on the response that creates or resets the password, and it is
     * shown once. Null fields are omitted from JSON.
     */
    record AdminUserResponse(UUID id, String email, String institutionalId, String displayName, Role role,
                             UserStatus status, boolean mustChangePassword, UUID departmentId,
                             Instant createdAt, String temporaryPassword) {
        static AdminUserResponse from(UserAccount account, String temporaryPassword) {
            return new AdminUserResponse(account.getId(), account.getEmail(), account.getInstitutionalId(),
                    account.getDisplayName(), account.getRole(), account.getStatus(),
                    account.isMustChangePassword(), account.getDepartmentId(), account.getCreatedAt(),
                    temporaryPassword);
        }
    }

    record CreateUserRequest(@NotBlank @Email @Size(max = 255) String email,
                             @Size(max = 32) String institutionalId,
                             @NotBlank @Size(min = 2, max = 100) String displayName,
                             @NotNull Role role,
                             UUID departmentId) {
    }

    /**
     * Omitted fields are unchanged. A blank text value clears that field. {@code departmentId} cannot be
     * cleared here in the MVP; it can only be set to an active department.
     */
    record UpdateUserRequest(@Size(min = 2, max = 100) String displayName,
                             Role role,
                             UUID departmentId,
                             @Size(max = 120) String program,
                             @Size(max = 20) String cohort,
                             @Size(max = 120) String designation,
                             @Size(max = 1000) String bio) {
    }
}
