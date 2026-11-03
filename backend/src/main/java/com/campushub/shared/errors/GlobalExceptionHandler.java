package com.campushub.shared.errors;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps every error to RFC 9457 Problem Details with a documented {@code errorCode}
 * (ADR-012). Framework errors without a CampusHub code (for example 405) keep the
 * framework's status and carry no {@code errorCode}; that gap is recorded in
 * IMPLEMENTATION_LOG §10.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Object> handleApiException(ApiException ex, WebRequest request) {
        return problem(ex.errorCode(), ex.getMessage(), request, ex.errors(), UUID.randomUUID().toString(), ex.retryAfterSeconds());
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

    /** A request body over the framework ceiling (spring.servlet.multipart) is FILE_TOO_LARGE, not a generic 413. */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return problem(ErrorCode.FILE_TOO_LARGE, "The file is larger than the allowed size.", request, null);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return problem(ErrorCode.NOT_FOUND, "No resource exists at this address.", request, null);
    }

    /** A path ID that is not a UUID is concealed as NOT_FOUND (API_CONTRACT §1); a bad query value is a validation error. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Object> handleTypeMismatch(MethodArgumentTypeMismatchException ex, WebRequest request) {
        if (ex.getParameter() != null && ex.getParameter().hasParameterAnnotation(PathVariable.class)) {
            return problem(ErrorCode.NOT_FOUND, "No resource exists at this address.", request, null);
        }
        return problem(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.", request,
                List.of(Map.of("field", ex.getName(), "reason", "invalid")));
    }

    /** The row changed between read and write (API_CONTRACT §1 versioning fields). */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<Object> handleStaleVersion(ObjectOptimisticLockingFailureException ex, WebRequest request) {
        return problem(ErrorCode.STALE_VERSION, "The record changed since it was read. Reload and try again.", request, null);
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
        return problem(code, detail, request, errors, UUID.randomUUID().toString(), null);
    }

    private ResponseEntity<Object> problem(
            ErrorCode code, String detail, WebRequest request, List<Map<String, String>> errors, String requestId) {
        return problem(code, detail, request, errors, requestId, null);
    }

    private ResponseEntity<Object> problem(
            ErrorCode code, String detail, WebRequest request, List<Map<String, String>> errors,
            String requestId, Long retryAfterSeconds) {
        ProblemDetail body = ProblemFactory.create(code, detail, pathOf(request), requestId, errors);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(code.status());
        if (retryAfterSeconds != null) {
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        }
        return response.body(body);
    }

    private static String pathOf(WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest) {
            HttpServletRequest httpRequest = servletRequest.getRequest();
            return httpRequest.getRequestURI();
        }
        return "/";
    }
}
