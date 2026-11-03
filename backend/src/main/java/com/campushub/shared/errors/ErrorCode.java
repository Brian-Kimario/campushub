package com.campushub.shared.errors;

import java.util.Locale;
import org.springframework.http.HttpStatus;

/**
 * Error codes from API_CONTRACT §3. Each code is added here in the same change that
 * first produces it. A new code is added here and
 * to API_CONTRACT §3 in the same change (ADR-012).
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
    FORBIDDEN_ROLE(HttpStatus.FORBIDDEN, "Role does not permit this action"),
    FORBIDDEN_STATE(HttpStatus.FORBIDDEN, "Action not permitted in the current state"),
    PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "Password change required"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
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
