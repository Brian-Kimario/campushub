package com.campushub.shared.errors;

import java.util.Locale;
import org.springframework.http.HttpStatus;

/**
 * Error codes from API_CONTRACT §3. Phase 1 defines only the codes its own
 * endpoints and framework handlers can produce. A new code is added here and
 * to API_CONTRACT §3 in the same change (ADR-012).
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error"),
    DEPENDENCY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Dependency unavailable");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    /** Problem type URI: {@code urn:campushub:problem:<code lower-kebab>} (API_CONTRACT §3). */
    public String problemType() {
        return "urn:campushub:problem:" + name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
