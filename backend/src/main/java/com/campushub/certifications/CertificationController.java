package com.campushub.certifications;

import com.campushub.identity.security.AuthenticatedUser;
import com.campushub.shared.web.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Certification endpoints (API_CONTRACT §11). Owner rules are enforced in {@link CertificationService}. */
@RestController
@RequestMapping("/users/me/certifications")
public class CertificationController {

    private final CertificationService certifications;

    public CertificationController(CertificationService certifications) {
        this.certifications = certifications;
    }

    @GetMapping
    public PageResponse<CertificationResponse> own(@AuthenticationPrincipal AuthenticatedUser caller,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return certifications.own(caller, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CertificationResponse create(@AuthenticationPrincipal AuthenticatedUser caller,
                                        @Valid @RequestBody CreateCertificationRequest body,
                                        HttpServletRequest request) {
        return certifications.create(caller, body, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
                       HttpServletRequest request) {
        certifications.delete(caller, id, request);
    }
}
