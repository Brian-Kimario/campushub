package com.campushub.certifications;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Body of {@code POST /users/me/certifications}. The credential URL must be https (API_CONTRACT §11).
 * Its content is never fetched by the server (SECURITY.md, SSRF).
 */
public record CreateCertificationRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 200) String issuer,
        @NotNull LocalDate issuedOn,
        LocalDate expiresOn,
        @Size(max = 100) String credentialId,
        @Size(max = 500) @Pattern(regexp = "^https://\\S+$", message = "must be an https URL") String credentialUrl) {
}
