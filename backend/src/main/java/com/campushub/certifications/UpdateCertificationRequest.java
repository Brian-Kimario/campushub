package com.campushub.certifications;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Body of {@code PUT /users/me/certifications/{id}}. The same rules as creation apply to every field.
 * {@code version} is the version the client last read; a stale value is refused with STALE_VERSION.
 */
public record UpdateCertificationRequest(
        @NotNull Integer version,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 200) String issuer,
        @NotNull LocalDate issuedOn,
        LocalDate expiresOn,
        @Size(max = 100) String credentialId,
        @Size(max = 500) @Pattern(regexp = "^https://\\S+$", message = "must be an https URL") String credentialUrl) {
}
