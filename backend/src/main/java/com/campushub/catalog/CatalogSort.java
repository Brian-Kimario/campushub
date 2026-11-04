package com.campushub.catalog;

import com.campushub.shared.errors.ApiException;
import com.campushub.shared.errors.ErrorCode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Sort;

/**
 * Parses the catalog {@code sort} parameter, {@code field,asc} or {@code field,desc}. Only the fields in the
 * caller's allowlist are accepted, and {@code id} is always the tie-breaker (FR-CAT-02).
 */
final class CatalogSort {

    private CatalogSort() {
    }

    static Sort parse(String raw, String defaultField, Set<String> allowed) {
        if (raw == null || raw.isBlank()) {
            return Sort.by(Sort.Direction.ASC, defaultField, "id");
        }
        String[] parts = raw.split(",", -1);
        boolean valid = parts.length == 2 && allowed.contains(parts[0])
                && ("asc".equalsIgnoreCase(parts[1]) || "desc".equalsIgnoreCase(parts[1]));
        if (!valid) {
            throw ApiException.withErrors(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.",
                    List.of(Map.of("field", "sort", "reason",
                            "must be one of " + allowed.stream().sorted().map(f -> f + ",asc or " + f + ",desc")
                                    .toList())));
        }
        return Sort.by(Sort.Direction.fromString(parts[1]), parts[0], "id");
    }

    /** The sort as applied, for the list envelope in API_CONTRACT §1.1. */
    static List<String> applied(Sort sort) {
        return sort.stream()
                .map(order -> order.getProperty() + "," + order.getDirection().name().toLowerCase(Locale.ROOT))
                .toList();
    }
}
