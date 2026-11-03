package com.campushub.shared.errors;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps every error to RFC 9457 Problem Details with a documented {@code errorCode}
 * (ADR-012). Framework errors without a CampusHub code (for example 405) keep the
 * framework's status and carry no {@code errorCode}; that gap is recorded in
 * IMPLEMENTATION_LOG for Phase 2.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Object> handleApiException(ApiException ex, WebRequest request) {
        return problem(ex.errorCode(), ex.getMessage(), request, null);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(fieldError -> {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("field", fieldError.getField());
            entry.put("reason", Objects.requireNonNullElse(fieldError.getDefaultMessage(), "invalid"));
            errors.add(entry);
        });
        return problem(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.", request, errors);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return problem(ErrorCode.VALIDATION_FAILED, "The request body is missing or malformed.", request, null);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return problem(ErrorCode.NOT_FOUND, "No resource exists at this address.", request, null);
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    ResponseEntity<Object> handleDatabaseUnavailable(DataAccessResourceFailureException ex, WebRequest request) {
        String requestId = UUID.randomUUID().toString();
        log.error("Database unavailable, requestId={}", requestId, ex);
        return problem(ErrorCode.DEPENDENCY_UNAVAILABLE, "A required service is unavailable.", request, null, requestId);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        String requestId = UUID.randomUUID().toString();
        log.error("Unhandled error, requestId={}", requestId, ex);
        return problem(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred.", request, null, requestId);
    }

    private ResponseEntity<Object> problem(
            ErrorCode code, String detail, WebRequest request, List<Map<String, String>> errors) {
        return problem(code, detail, request, errors, UUID.randomUUID().toString());
    }

    private ResponseEntity<Object> problem(
            ErrorCode code, String detail, WebRequest request, List<Map<String, String>> errors, String requestId) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(code.status(), detail);
        body.setType(URI.create(code.problemType()));
        body.setTitle(code.title());
        body.setInstance(URI.create(pathOf(request)));
        body.setProperty("errorCode", code.name());
        body.setProperty("requestId", requestId);
        if (errors != null) {
            body.setProperty("errors", errors);
        }
        return ResponseEntity.status(code.status()).body(body);
    }

    private static String pathOf(WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest) {
            HttpServletRequest httpRequest = servletRequest.getRequest();
            return httpRequest.getRequestURI();
        }
        return "/";
    }
}
