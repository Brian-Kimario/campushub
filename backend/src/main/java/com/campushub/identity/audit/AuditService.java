package com.campushub.identity.audit;

import com.campushub.identity.security.JwtProperties;
import com.campushub.shared.errors.ApiException;
import com.campushub.shared.errors.ErrorCode;
import com.campushub.shared.web.PageResponse;
import com.campushub.shared.web.Pagination;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Writes audit rows (ADR-023). Metadata passes an allowlist, so credentials and free text cannot leak
 * into the log by accident. The client IP is hashed with HMAC-SHA256 keyed by the JWT secret, so the
 * raw address is never stored.
 */
@Service
public class AuditService {

    private static final Set<String> ALLOWED_METADATA_KEYS = Set.of("reason");
    private static final int MAX_REASON_LENGTH = 200;

    private final AuditEventRepository events;
    private final JwtProperties properties;
    private final Clock clock;

    public AuditService(AuditEventRepository events, JwtProperties properties, Clock clock) {
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * @param request the current request, or null for scheduled work; supplies the hashed IP only
     */
    public void record(String action, UUID actorId, String targetType, String targetId,
                       AuditOutcome outcome, HttpServletRequest request, Map<String, String> metadata) {
        events.save(new AuditEvent(
                clock.instant(),
                actorId,
                action,
                targetType,
                targetId,
                outcome,
                UUID.randomUUID().toString(),
                request == null ? null : hashAddress(request.getRemoteAddr()),
                sanitize(metadata)));
    }

    private Map<String, Object> sanitize(Map<String, String> metadata) {
        Map<String, Object> clean = new LinkedHashMap<>();
        if (metadata == null) {
            return clean;
        }
        metadata.forEach((key, value) -> {
            if (ALLOWED_METADATA_KEYS.contains(key) && value != null) {
                clean.put(key, value.length() > MAX_REASON_LENGTH
                        ? value.substring(0, MAX_REASON_LENGTH)
                        : value);
            }
        });
        return clean;
    }

    private String hashAddress(String address) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(address.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("IP hashing failed", ex);
        }
    }

    /**
     * Administrator search. Newest first unless {@code sort} is {@code occurredAt,asc}. The sort is
     * the only ordering offered, so the query never sorts by a column a caller picked.
     */
    public PageResponse<AuditEventResponse> search(UUID actorId, String action, String targetType, String targetId,
                                                   Instant from, Instant to, String sort, int page, int size) {
        Sort.Direction direction = sortDirection(sort);
        if (from != null && to != null && from.isAfter(to)) {
            throw ApiException.withErrors(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.",
                    List.of(Map.of("field", "from", "reason", "must not be after to")));
        }
        Pageable pageable = Pagination.request(page, size, Sort.by(direction, "occurredAt", "id"));
        Page<AuditEvent> found = events.search(actorId, action, targetType, targetId, from, to, pageable);
        return PageResponse.of(found.map(AuditEventResponse::from),
                List.of("occurredAt," + direction.name().toLowerCase()));
    }

    private static Sort.Direction sortDirection(String sort) {
        if (sort == null || "occurredAt,desc".equals(sort)) {
            return Sort.Direction.DESC;
        }
        if ("occurredAt,asc".equals(sort)) {
            return Sort.Direction.ASC;
        }
        throw ApiException.withErrors(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.",
                List.of(Map.of("field", "sort", "reason", "must be occurredAt,asc or occurredAt,desc")));
    }
}
