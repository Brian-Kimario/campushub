package com.campushub.certifications;

import com.campushub.identity.Role;
import com.campushub.identity.audit.AuditOutcome;
import com.campushub.identity.audit.AuditService;
import com.campushub.identity.security.AuthenticatedUser;
import com.campushub.shared.errors.ApiException;
import com.campushub.shared.errors.ErrorCode;
import com.campushub.shared.web.PageResponse;
import com.campushub.shared.web.Pagination;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Certification use cases (AUTHORIZATION_MATRIX §3.9, API_CONTRACT §11).
 *
 * <p>Only a student reaches these endpoints, and only for their own rows. Any other role gets 403
 * FORBIDDEN_ROLE. A row that belongs to another student reads as NOT_FOUND (NS-01), so the response
 * does not reveal that it exists. An update with a stale version is refused with STALE_VERSION (409).
 */
@Service
public class CertificationService {

    private static final List<String> SORT = List.of("issuedOn,desc", "id,asc");
    private static final String CERTIFICATION = "certification";

    private final CertificationRepository certifications;
    private final AuditService audit;
    private final Clock clock;

    public CertificationService(CertificationRepository certifications, AuditService audit, Clock clock) {
        this.certifications = certifications;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<CertificationResponse> own(AuthenticatedUser caller, int page, int size) {
        requireStudent(caller);
        var pageable = Pagination.request(page, size, Sort.by(Sort.Direction.DESC, "issuedOn")
                .and(Sort.by(Sort.Direction.ASC, "id")));
        Page<CertificationResponse> result = certifications.findByOwnerId(caller.userId(), pageable)
                .map(CertificationResponse::from);
        return PageResponse.of(result, SORT);
    }

    @Transactional
    public CertificationResponse create(AuthenticatedUser caller, CreateCertificationRequest body,
                                        HttpServletRequest request) {
        requireStudent(caller);
        requireValidDates(body.issuedOn(), body.expiresOn());
        var certification = new Certification(UUID.randomUUID(), caller.userId(), body.title(), body.issuer(),
                body.issuedOn(), body.expiresOn(), body.credentialId(), body.credentialUrl(), clock.instant());
        certifications.saveAndFlush(certification);
        audit.record("certification.created", caller.userId(), CERTIFICATION, certification.getId().toString(),
                AuditOutcome.SUCCESS, request, Map.of());
        return CertificationResponse.from(certification);
    }

    @Transactional
    public CertificationResponse update(AuthenticatedUser caller, UUID id, UpdateCertificationRequest body,
                                        HttpServletRequest request) {
        requireStudent(caller);
        Certification certification = certifications.findByIdAndOwnerId(id, caller.userId())
                .orElseThrow(CertificationService::notFound);
        if (!Objects.equals(certification.getVersion(), body.version())) {
            throw new ApiException(ErrorCode.STALE_VERSION, "This certification changed. Reload it and try again.");
        }
        requireValidDates(body.issuedOn(), body.expiresOn());
        certification.update(body.title(), body.issuer(), body.issuedOn(), body.expiresOn(),
                body.credentialId(), body.credentialUrl());
        certifications.saveAndFlush(certification);
        audit.record("certification.updated", caller.userId(), CERTIFICATION, id.toString(),
                AuditOutcome.SUCCESS, request, Map.of());
        return CertificationResponse.from(certification);
    }

    @Transactional
    public void delete(AuthenticatedUser caller, UUID id, HttpServletRequest request) {
        requireStudent(caller);
        Certification certification = certifications.findByIdAndOwnerId(id, caller.userId())
                .orElseThrow(CertificationService::notFound);
        certifications.delete(certification);
        audit.record("certification.deleted", caller.userId(), CERTIFICATION, id.toString(),
                AuditOutcome.SUCCESS, request, Map.of());
    }

    private static void requireStudent(AuthenticatedUser caller) {
        if (caller.role() != Role.STUDENT) {
            throw new ApiException(ErrorCode.FORBIDDEN_ROLE, "Your role cannot perform this action.");
        }
    }

    /** An expiry date cannot come before the issue date. Refused with VALIDATION_FAILED (400). */
    private static void requireValidDates(LocalDate issuedOn, LocalDate expiresOn) {
        if (expiresOn != null && expiresOn.isBefore(issuedOn)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "The expiry date cannot be before the issue date.");
        }
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND, "Certification not found.");
    }
}
