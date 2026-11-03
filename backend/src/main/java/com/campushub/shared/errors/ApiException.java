package com.campushub.shared.errors;

import java.util.List;
import java.util.Map;

/**
 * An expected failure with a documented error code. The message is safe to
 * return to the client; it never contains secrets, stack traces, or stored data.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Long retryAfterSeconds;
    private List<Map<String, String>> errors;

    public ApiException(ErrorCode errorCode, String safeDetail) {
        this(errorCode, safeDetail, null);
    }

    /** @param retryAfterSeconds value for the {@code Retry-After} header, or null when not applicable */
    public ApiException(ErrorCode errorCode, String safeDetail, Long retryAfterSeconds) {
        super(safeDetail);
        this.errorCode = errorCode;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /** A failure that lists the fields that were refused, as {@code errors[]} in the problem body. */
    public static ApiException withErrors(ErrorCode errorCode, String safeDetail, List<Map<String, String>> errors) {
        ApiException exception = new ApiException(errorCode, safeDetail);
        exception.errors = errors;
        return exception;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public List<Map<String, String>> errors() {
        return errors;
    }
}
