package com.campushub.certifications;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One of the caller's own certifications. */
public record CertificationResponse(
        UUID id,
        String title,
        String issuer,
        LocalDate issuedOn,
        String credentialUrl,
        Instant createdAt) {

    static CertificationResponse from(Certification certification) {
        return new CertificationResponse(certification.getId(), certification.getTitle(),
                certification.getIssuer(), certification.getIssuedOn(), certification.getCredentialUrl(),
                certification.getCreatedAt());
    }
}
