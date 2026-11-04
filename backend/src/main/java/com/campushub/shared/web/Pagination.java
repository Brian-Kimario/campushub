package com.campushub.shared.web;

import com.campushub.shared.errors.ApiException;
import com.campushub.shared.errors.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Page and size rules from API_CONTRACT §1: page is 0-based, size is 1 to 100. */
public final class Pagination {

    public static final int MAX_SIZE = 100;

    private Pagination() {
    }

    public static Pageable request(int page, int size, Sort sort) {
        List<Map<String, String>> errors = new ArrayList<>();
        if (page < 0) {
            errors.add(Map.of("field", "page", "reason", "must be 0 or greater"));
        }
        if (size < 1 || size > MAX_SIZE) {
            errors.add(Map.of("field", "size", "reason", "must be between 1 and " + MAX_SIZE));
        }
        if (!errors.isEmpty()) {
            throw ApiException.withErrors(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.", errors);
        }
        return PageRequest.of(page, size, sort);
    }
}
