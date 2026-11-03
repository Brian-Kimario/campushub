package com.campushub.shared.web;

import java.util.List;
import org.springframework.data.domain.Page;

/** The list envelope from API_CONTRACT §1.1. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<String> sort) {

    public static <T> PageResponse<T> of(Page<T> page, List<String> sort) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), sort);
    }
}
