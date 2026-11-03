package com.campushub.shared.errors;

/**
 * An expected failure with a documented error code. The message is safe to
 * return to the client; it never contains secrets, stack traces, or stored data.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode, String safeDetail) {
        super(safeDetail);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
