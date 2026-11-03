package com.campushub.shared.errors;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.ProblemDetail;

/**
 * Builds the RFC 9457 body for every CampusHub error (API_CONTRACT §3). Shared by the
 * MVC exception handler and the security filter, so 401 and 403 responses from the
 * filter chain have the same shape as every other error.
 */
public final class ProblemFactory {

    private ProblemFactory() {
    }

    public static ProblemDetail create(
            ErrorCode code, String detail, String path, String requestId, List<Map<String, String>> errors) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(code.status(), detail);
        body.setType(URI.create(code.problemType()));
        body.setTitle(code.title());
        body.setInstance(URI.create(path));
        body.setProperty("errorCode", code.name());
        body.setProperty("requestId", requestId);
        if (errors != null) {
            body.setProperty("errors", errors);
        }
        return body;
    }
}
