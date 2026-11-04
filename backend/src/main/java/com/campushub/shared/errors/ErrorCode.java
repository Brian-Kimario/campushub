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
    FIELD_NOT_EDITABLE(HttpStatus.BAD_REQUEST, "Field cannot be edited"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
    FORBIDDEN_ROLE(HttpStatus.FORBIDDEN, "Role does not permit this action"),
    FORBIDDEN_STATE(HttpStatus.FORBIDDEN, "Action not permitted in the current state"),
    PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "Password change required"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    STALE_VERSION(HttpStatus.CONFLICT, "Version conflict"),
    INVALID_TRANSITION(HttpStatus.CONFLICT, "Invalid state transition"),
    VERSION_SEALED(HttpStatus.CONFLICT, "Version is sealed"),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "File is too large"),
    DUPLICATE(HttpStatus.CONFLICT, "Duplicate value"),
    CATALOG_ITEM_IN_USE(HttpStatus.CONFLICT, "Catalog item is in use"),
    TEAM_FULL(HttpStatus.CONFLICT, "Team is full"),
    PRECONDITION_NOT_MET(HttpStatus.UNPROCESSABLE_ENTITY, "Precondition not met"),
    PROJECT_NOT_OPEN_FOR_MEMBERSHIP(HttpStatus.UNPROCESSABLE_ENTITY, "Project is not open for membership"),
    RANGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "Date range too large"),
    PROJECT_ARCHIVED(HttpStatus.UNPROCESSABLE_ENTITY, "Project is archived"),
    ROLE_CHANGE_NOT_SUPPORTED(HttpStatus.UNPROCESSABLE_ENTITY, "Role change is not supported"),
    SUPERVISOR_NOT_ELIGIBLE(HttpStatus.UNPROCESSABLE_ENTITY, "Supervisor not eligible"),
    REVIEW_EXISTS(HttpStatus.CONFLICT, "Review already recorded"),
    FEEDBACK_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "Feedback required"),
    FILE_TYPE_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "File type not allowed"),
    FILE_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "File limit exceeded"),
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
