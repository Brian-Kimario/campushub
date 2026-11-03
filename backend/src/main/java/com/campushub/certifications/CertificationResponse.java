package com.campushub.certifications;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One of the caller's own certifications. {@code version} is sent back on update. */
public record CertificationResponse(
        UUID id,
        String title,
        String issuer,
        LocalDate issuedOn,
        LocalDate expiresOn,
        String credentialId,
        String credentialUrl,
        Instant createdAt,
        int version) {

    static CertificationResponse from(Certification certification) {
        return new CertificationResponse(certification.getId(), certification.getTitle(),
                certification.getIssuer(), certification.getIssuedOn(), certification.getExpiresOn(),
                certification.getCredentialId(), certification.getCredentialUrl(), certification.getCreatedAt(),
                certification.getVersion());
    }
}
