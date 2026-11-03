package com.campushub.identity.security;

import com.campushub.shared.errors.ErrorCode;
import com.campushub.shared.errors.ProblemFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/** Writes 401 and 403 responses from the security chain in the same RFC 9457 shape as every other error. */
@Component
public class ProblemResponder {

    private final ObjectMapper objectMapper;

    public ProblemResponder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void unauthenticated(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(request, response, ErrorCode.UNAUTHENTICATED, "Authentication is required.");
    }

    public void forbiddenRole(HttpServletRequest request, HttpServletResponse response) throws IOException {
        write(request, response, ErrorCode.FORBIDDEN_ROLE, "Your role does not permit this action.");
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String detail)
            throws IOException {
        ProblemDetail body = ProblemFactory.create(
                code, detail, request.getRequestURI(), UUID.randomUUID().toString(), null);
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
